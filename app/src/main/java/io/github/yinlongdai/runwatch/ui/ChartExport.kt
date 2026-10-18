package io.github.yinlongdai.runwatch.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.ChartMath
import io.github.yinlongdai.runwatch.data.Parsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Holds the last drawn plot of a [LineChart] (axes, labels and lines, without
 * the touch cursor or header buttons) so it can be exported as an image.
 */
class ChartCapture internal constructor(internal val layer: GraphicsLayer)

@Composable
fun rememberChartCapture(): ChartCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ChartCapture(layer) }
}

/** What goes around the plot in an exported image. */
class ChartImageText(
    val title: String,
    val subtitle: String,
    /** Smoothing / log scale / zoom notes, or empty. */
    val notes: String,
    /** "last / min / max" for a single series, else null. */
    val stats: String?,
    /** Series names and colors; drawn as a legend when there is more than one. */
    val legend: List<Pair<String, Int>>,
    val footer: String,
)

/**
 * Returns a function that turns a captured chart into a PNG (title, run and
 * project, legend or stats, the plot, a footer) and opens the share sheet.
 * Colors follow the current theme's card colors, matching what's on screen.
 */
@Composable
fun rememberChartSharer(): (capture: ChartCapture, chart: ChartData, subtitle: String, zoomed: Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val palette = ExportColors(colors.surfaceContainerHighest, colors.onSurface, colors.onSurfaceVariant)
    return remember(context, palette) {
        { capture: ChartCapture, chart: ChartData, subtitle: String, zoomed: Boolean ->
            scope.launch {
                try {
                    val plot = capture.layer.toImageBitmap().asAndroidBitmap()
                    val text = imageText(chart, subtitle, zoomed)
                    val file = withContext(Dispatchers.Default) {
                        val image = ChartImage.render(plot, text, palette, context.resources.displayMetrics.density)
                        ChartImage.write(context, image, chart.title)
                    }
                    ChartImage.share(context, file, chart.title)
                } catch (e: Exception) {
                    Toast.makeText(context, "Couldn't export the chart: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

data class ExportColors(val background: Color, val text: Color, val muted: Color)

private fun imageText(chart: ChartData, subtitle: String, zoomed: Boolean): ChartImageText {
    val single = chart.series.singleOrNull()
    val stats = single?.let { ChartMath.stats(it.y) }?.let {
        "last ${Parsers.formatNumber(it.last)}   min ${Parsers.formatNumber(it.min)}   max ${Parsers.formatNumber(it.max)}"
    }
    val notes = listOfNotNull(
        "x: ${chart.xLabel}",
        ChartSettings.smoothing.takeIf { it > 0f }?.let { "smoothing ${"%.2f".format(Locale.US, it)}" },
        "log y".takeIf { ChartSettings.logY },
        "outliers hidden".takeIf { ChartSettings.clipOutliers },
        "zoomed".takeIf { zoomed },
    ).joinToString(" · ")
    val legend = if (chart.series.size > 1) {
        chart.series.mapIndexed { i, s ->
            s.label to ChartPalette[(if (s.colorIndex >= 0) s.colorIndex else i) % ChartPalette.size].toArgb()
        }
    } else emptyList()
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    return ChartImageText(chart.title, subtitle, notes, stats, legend, "RunWatch · $time")
}

/** Draws and shares exported chart images. Plain Android canvas, no Compose needed. */
object ChartImage {

    fun render(plot: Bitmap, text: ChartImageText, colors: ExportColors, density: Float): Bitmap {
        // Hardware bitmaps can't be drawn onto a software canvas.
        val src = if (plot.config == Bitmap.Config.HARDWARE) plot.copy(Bitmap.Config.ARGB_8888, false) else plot
        val pad = 16 * density
        val width = src.width + 2 * pad
        val inner = src.width.toFloat()

        fun paint(sizeSp: Float, color: Color, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp * density
            this.color = color.toArgb()
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        val titlePaint = paint(17f, colors.text, bold = true)
        val subPaint = paint(12f, colors.muted)
        val smallPaint = paint(11f, colors.muted)
        val legendPaint = paint(12f, colors.text)
        fun lineHeight(p: Paint) = p.fontMetrics.let { it.descent - it.ascent }
        fun fit(s: String, p: TextPaint) = TextUtils.ellipsize(s, p, inner, TextUtils.TruncateAt.END).toString()

        // Legend entries flow onto as many rows as needed.
        val dot = 8 * density
        val gap = 14 * density
        val rows = mutableListOf<MutableList<Pair<String, Int>>>()
        var x = 0f
        text.legend.forEach { entry ->
            val name = TextUtils.ellipsize(entry.first, legendPaint, inner - dot - 6 * density, TextUtils.TruncateAt.END).toString()
            val w = dot + 6 * density + legendPaint.measureText(name)
            if (rows.isEmpty() || x + w > inner) { rows += mutableListOf<Pair<String, Int>>(); x = 0f }
            rows.last() += name to entry.second
            x += w + gap
        }

        val lines = buildList {
            add(text.subtitle to subPaint)
            if (text.notes.isNotEmpty()) add(text.notes to smallPaint)
            text.stats?.let { add(it to smallPaint) }
        }
        val headerHeight = lineHeight(titlePaint) + lines.sumOf { lineHeight(it.second).toDouble() + 2 * density }.toFloat()
        val legendHeight = rows.size * (lineHeight(legendPaint) + 4 * density)
        val height = pad + headerHeight + 10 * density + legendHeight + src.height + 10 * density + lineHeight(smallPaint) + pad

        val out = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(colors.background.toArgb())
        var y = pad
        fun drawLine(s: String, p: TextPaint) {
            y -= p.fontMetrics.ascent
            canvas.drawText(fit(s, p), pad, y, p)
            y += p.fontMetrics.descent
        }
        drawLine(text.title, titlePaint)
        lines.forEach { (s, p) -> y += 2 * density; drawLine(s, p) }
        y += 10 * density

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        rows.forEach { row ->
            var lx = pad
            val baseline = y - legendPaint.fontMetrics.ascent
            val center = y + lineHeight(legendPaint) / 2
            row.forEach { (name, color) ->
                dotPaint.color = color
                canvas.drawCircle(lx + dot / 2, center, dot / 2, dotPaint)
                lx += dot + 6 * density
                canvas.drawText(name, lx, baseline, legendPaint)
                lx += legendPaint.measureText(name) + gap
            }
            y += lineHeight(legendPaint) + 4 * density
        }

        canvas.drawBitmap(src, pad, y, null)
        y += src.height + 10 * density
        drawLine(text.footer, smallPaint)
        return out
    }

    fun write(context: Context, image: Bitmap, title: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Only the latest export is kept; the share target copies what it needs.
        dir.listFiles()?.forEach { it.delete() }
        val name = title.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "chart" }.take(60)
        val file = File(dir, "$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun share(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            // Lets the share sheet show a preview of the image.
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share chart"))
    }
}

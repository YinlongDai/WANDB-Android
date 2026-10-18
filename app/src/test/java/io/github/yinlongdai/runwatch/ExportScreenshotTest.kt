package io.github.yinlongdai.runwatch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.yinlongdai.runwatch.ui.ChartImage
import io.github.yinlongdai.runwatch.ui.ChartImageText
import io.github.yinlongdai.runwatch.ui.ChartPalette
import io.github.yinlongdai.runwatch.ui.ExportColors
import org.junit.Rule
import org.junit.Test
import kotlin.math.exp

/** Layout of an exported chart image: header, legend or stats, plot, footer. */
class ExportScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, environment = testEnvironment())

    private val density = 2f
    private val colors = ExportColors(Color(0xFF2B2D33), Color(0xFFE6E7EB), Color(0xFFA8ABB4))

    // Stand-in for the captured plot: a few decaying curves.
    private fun plot(lines: Int): Bitmap {
        val bmp = Bitmap.createBitmap(700, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val axis = Paint().apply { color = 0xFF555860.toInt(); strokeWidth = 2f }
        canvas.drawLine(40f, 300f, 700f, 300f, axis)
        repeat(lines) { k ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ChartPalette[k].toArgb(); strokeWidth = 4f }
            var prev = 0f to 0f
            for (i in 0..66) {
                val x = 40f + i * 10f
                val y = 290f - 260f * exp(-i / (12f + 8f * k)).toFloat().let { 1 - it }
                if (i > 0) canvas.drawLine(prev.first, prev.second, x, y, p)
                prev = x to y
            }
        }
        return bmp
    }

    @Test
    fun exportedImages() {
        val single = ChartImage.render(
            plot(1),
            ChartImageText(
                "train/loss", "my-team/mnist · bright-sun-42", "x: step · smoothing 0.60",
                "last 0.0412   min 0.0398   max 2.31", emptyList(), "RunWatch · 2026-10-18 14:32",
            ),
            colors, density,
        )
        val compare = ChartImage.render(
            plot(4),
            ChartImageText(
                "val/accuracy", "my-team/mnist · 4 runs", "x: step · zoomed", null,
                listOf("bright-sun-42", "quiet-river-7", "a-run-with-a-rather-long-name-12", "lr-sweep-3")
                    .mapIndexed { i, n -> n to ChartPalette[i].toArgb() },
                "RunWatch · 2026-10-18 14:32",
            ),
            colors, density,
        )
        paparazzi.snapshot {
            Column(Modifier.background(Color(0xFF6E7B8B)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(single.asImageBitmap(), null)
                Image(compare.asImageBitmap(), null)
            }
        }
    }
}

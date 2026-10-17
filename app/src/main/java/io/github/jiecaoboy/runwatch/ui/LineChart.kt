package io.github.jiecaoboy.runwatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jiecaoboy.runwatch.data.ChartData
import io.github.jiecaoboy.runwatch.data.ChartMath
import io.github.jiecaoboy.runwatch.data.Parsers
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

private class PreparedSeries(
    val label: String,
    val x: DoubleArray,
    val raw: DoubleArray,
    val smooth: DoubleArray?,
)

private class Prepared(
    val series: List<PreparedSeries>,
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
)

private const val MAX_POINTS = 400

private fun prepare(chart: ChartData, smoothing: Float, logY: Boolean): Prepared? {
    val out = chart.series.mapNotNull { s ->
        var x = s.x
        var y = s.y
        if (logY) {
            val keep = y.indices.filter { y[it] > 0 }
            x = DoubleArray(keep.size) { x[keep[it]] }
            y = DoubleArray(keep.size) { log10(s.y[keep[it]]) }
        }
        if (x.isEmpty()) return@mapNotNull null
        val smooth = if (smoothing > 0f) ChartMath.smooth(y, smoothing.toDouble()) else null
        val idx = ChartMath.downsampleIndices(x.size, y, MAX_POINTS)
        PreparedSeries(
            label = s.label,
            x = DoubleArray(idx.size) { x[idx[it]] },
            raw = DoubleArray(idx.size) { y[idx[it]] },
            smooth = smooth?.let { sm -> DoubleArray(idx.size) { sm[idx[it]] } },
        )
    }
    if (out.isEmpty()) return null
    var xMin = out.minOf { it.x.first() }
    var xMax = out.maxOf { it.x.last() }
    var yMin = out.minOf { it.raw.min() }
    var yMax = out.maxOf { it.raw.max() }
    if (xMin == xMax) { xMin -= 1; xMax += 1 }
    if (yMin == yMax) {
        val pad = if (yMin == 0.0) 1.0 else abs(yMin) * 0.1
        yMin -= pad; yMax += pad
    } else {
        val pad = (yMax - yMin) * 0.05
        yMin -= pad; yMax += pad
    }
    return Prepared(out, xMin, xMax, yMin, yMax)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    chart: ChartData,
    smoothing: Float,
    logY: Boolean,
    modifier: Modifier = Modifier,
) {
    val prepared = remember(chart, smoothing, logY) { prepare(chart, smoothing, logY) }
    var cursorX by remember(chart) { mutableStateOf<Double?>(null) }
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val leftPad = 46.dp
    val bottomPad = 18.dp

    Column(modifier) {
        Text(chart.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (prepared == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (logY) "No positive values to plot on a log scale" else "No data yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                )
            }
            return@Column
        }

        // Value readout for the touched position (nearest point per series).
        val readout = cursorX?.let { cx ->
            val parts = prepared.series.mapIndexed { i, s ->
                val j = nearestIndex(s.x, cx)
                val v = (s.smooth ?: s.raw)[j]
                val shown = if (logY) 10.0.pow(v) else v
                Triple(i, s.label, Parsers.formatNumber(shown)) to s.x[j]
            }
            val xVal = parts.firstOrNull()?.second
            Pair(xVal, parts.map { it.first })
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = 6.dp)
                .pointerInput(prepared) {
                    detectTapGestures(onPress = { pos ->
                        cursorX = toDataX(pos.x, size.width.toFloat(), leftPad.toPx(), prepared)
                        tryAwaitRelease()
                    })
                }
                .pointerInput(prepared) {
                    detectHorizontalDragGestures(
                        onDragEnd = { },
                        onHorizontalDrag = { change, _ ->
                            cursorX = toDataX(change.position.x, size.width.toFloat(), leftPad.toPx(), prepared)
                        },
                    )
                },
        ) {
            val left = leftPad.toPx()
            val bottom = size.height - bottomPad.toPx()
            val width = size.width - left
            fun px(x: Double) = left + ((x - prepared.xMin) / (prepared.xMax - prepared.xMin) * width).toFloat()
            fun py(y: Double) = bottom - ((y - prepared.yMin) / (prepared.yMax - prepared.yMin) * bottom).toFloat()

            // Y grid + labels
            for (t in ChartMath.niceTicks(prepared.yMin, prepared.yMax, 4)) {
                val y = py(t)
                if (y < 0 || y > bottom) continue
                drawLine(axisColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val text = if (logY) ChartMath.compact(10.0.pow(t)) else ChartMath.compact(t)
                val layout = measurer.measure(text, labelStyle)
                drawText(layout, topLeft = Offset(left - layout.size.width - 6f, y - layout.size.height / 2f))
            }
            // X labels
            for (t in ChartMath.niceTicks(prepared.xMin, prepared.xMax, 4)) {
                val x = px(t)
                if (x < left || x > size.width) continue
                val layout = measurer.measure(ChartMath.compact(t), labelStyle)
                val lx = (x - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, bottom + 3f))
            }
            drawLine(axisColor, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.5f)

            prepared.series.forEachIndexed { i, s ->
                val color = ChartPalette[i % ChartPalette.size]
                fun pathOf(ys: DoubleArray) = Path().apply {
                    moveTo(px(s.x[0]), py(ys[0]))
                    for (k in 1 until s.x.size) lineTo(px(s.x[k]), py(ys[k]))
                }
                if (s.smooth != null) {
                    drawPath(pathOf(s.raw), color.copy(alpha = 0.25f), style = Stroke(width = 1.5f))
                    drawPath(pathOf(s.smooth), color, style = Stroke(width = 2.5f))
                } else {
                    drawPath(pathOf(s.raw), color, style = Stroke(width = 2.5f))
                }
                if (s.x.size == 1) drawCircle(color, 4f, Offset(px(s.x[0]), py(s.raw[0])))
            }

            cursorX?.let { cx ->
                val x = px(cx).coerceIn(left, size.width)
                drawLine(labelColor, Offset(x, 0f), Offset(x, bottom), strokeWidth = 1f)
                prepared.series.forEachIndexed { i, s ->
                    val j = nearestIndex(s.x, cx)
                    val v = (s.smooth ?: s.raw)[j]
                    drawCircle(ChartPalette[i % ChartPalette.size], 5f, Offset(px(s.x[j]), py(v)))
                }
            }
        }

        if (readout != null) {
            val (xVal, values) = readout
            Text(
                buildString {
                    append(chart.xLabel).append(' ').append(xVal?.let { Parsers.formatNumber(it) } ?: "")
                    values.forEach { (_, label, v) ->
                        append("   ")
                        if (prepared.series.size > 1) append(label).append(": ")
                        append(v)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                maxLines = 2,
            )
        }
        if (prepared.series.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                prepared.series.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(ChartPalette[i % ChartPalette.size], CircleShape))
                        Text(" " + s.label, style = MaterialTheme.typography.labelSmall, color = labelColor)
                    }
                }
            }
        }
    }
}

private fun toDataX(px: Float, width: Float, left: Float, p: Prepared): Double {
    val frac = ((px - left) / (width - left)).coerceIn(0f, 1f)
    return p.xMin + frac * (p.xMax - p.xMin)
}

private fun nearestIndex(xs: DoubleArray, x: Double): Int {
    var lo = 0
    var hi = xs.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (xs[mid] < x) lo = mid + 1 else hi = mid
    }
    return if (lo > 0 && abs(xs[lo - 1] - x) < abs(xs[lo] - x)) lo - 1 else lo
}

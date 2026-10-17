package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.ChartMath
import io.github.yinlongdai.runwatch.data.Parsers
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

private class PreparedSeries(
    val label: String,
    val color: Int,
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
    /** Data range before any zoom window, for the full-screen zoom gestures. */
    val fullX: ClosedFloatingPointRange<Double>,
    /** True when outlier clipping actually cut points off. */
    val clipped: Boolean,
)

private const val MAX_POINTS = 400

private fun prepare(
    chart: ChartData,
    smoothing: Float,
    logY: Boolean,
    clipOutliers: Boolean,
    xWindow: ClosedFloatingPointRange<Double>?,
): Prepared? {
    var fullMin = Double.POSITIVE_INFINITY
    var fullMax = Double.NEGATIVE_INFINITY
    val out = chart.series.mapNotNull { s ->
        var x = s.x
        var y = s.y
        if (logY) {
            val keep = y.indices.filter { y[it] > 0 }
            x = DoubleArray(keep.size) { x[keep[it]] }
            y = DoubleArray(keep.size) { log10(s.y[keep[it]]) }
        }
        if (x.isEmpty()) return@mapNotNull null
        fullMin = minOf(fullMin, x.first())
        fullMax = maxOf(fullMax, x.last())
        // Smooth the whole series first so zooming doesn't change the EMA.
        var smooth = if (smoothing > 0f) ChartMath.smooth(y, smoothing.toDouble()) else null
        if (xWindow != null) {
            // Keep one point beyond each edge so lines run to the plot border.
            val from = (x.indexOfFirst { it >= xWindow.start } - 1).coerceAtLeast(0)
            val to = (x.indexOfLast { it <= xWindow.endInclusive } + 1).coerceAtMost(x.size - 1)
            if (from > to || x.indexOfFirst { it >= xWindow.start } < 0) return@mapNotNull null
            x = x.copyOfRange(from, to + 1)
            y = y.copyOfRange(from, to + 1)
            smooth = smooth?.copyOfRange(from, to + 1)
        }
        val idx = ChartMath.downsampleIndices(x.size, y, MAX_POINTS)
        PreparedSeries(
            label = s.label,
            color = s.colorIndex,
            x = DoubleArray(idx.size) { x[idx[it]] },
            raw = DoubleArray(idx.size) { y[idx[it]] },
            smooth = smooth?.let { sm -> DoubleArray(idx.size) { sm[idx[it]] } },
        )
    }
    if (out.isEmpty()) return null
    var xMin = xWindow?.start ?: out.minOf { it.x.first() }
    var xMax = xWindow?.endInclusive ?: out.maxOf { it.x.last() }
    val dataMin = out.minOf { it.raw.min() }
    val dataMax = out.maxOf { it.raw.max() }
    var yMin = dataMin
    var yMax = dataMax
    if (clipOutliers) {
        ChartMath.robustRange(out.map { it.smooth ?: it.raw })?.let { (lo, hi) -> if (hi > lo) { yMin = lo; yMax = hi } }
    }
    val clipped = yMin > dataMin || yMax < dataMax
    if (xMin == xMax) { xMin -= 1; xMax += 1 }
    if (yMin == yMax) {
        val pad = if (yMin == 0.0) 1.0 else abs(yMin) * 0.1
        yMin -= pad; yMax += pad
    } else {
        val pad = (yMax - yMin) * 0.05
        yMin -= pad; yMax += pad
    }
    if (fullMin == fullMax) { fullMin -= 1; fullMax += 1 }
    return Prepared(out, xMin, xMax, yMin, yMax, fullMin..fullMax, clipped)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    chart: ChartData,
    smoothing: Float,
    logY: Boolean,
    modifier: Modifier = Modifier,
    clipOutliers: Boolean = false,
    height: Dp = 180.dp,
    showStats: Boolean = true,
    /** Visible X range; null shows everything. */
    xWindow: ClosedFloatingPointRange<Double>? = null,
    /** When set, two fingers zoom/pan the X axis and double-tap resets; reports the new window (null = all). */
    onXWindowChange: ((ClosedFloatingPointRange<Double>?) -> Unit)? = null,
    /** Small buttons on the right of the title row (pin, expand, ...). */
    actions: @Composable RowScope.() -> Unit = {},
) {
    val prepared = remember(chart, smoothing, logY, clipOutliers, xWindow) {
        prepare(chart, smoothing, logY, clipOutliers, xWindow)
    }
    var cursorX by remember(chart) { mutableStateOf<Double?>(null) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val bottomPad = 18.dp

    // Y ticks and their measured labels. The gutter is exactly as wide as the
    // widest label (plus a small gap), so short labels don't waste space.
    val yAxis = remember(prepared, logY, labelStyle) {
        if (prepared == null) return@remember null
        val ticks = ChartMath.niceTicks(prepared.yMin, prepared.yMax, 4)
            .filter { it >= prepared.yMin && it <= prepared.yMax }
        val labels = if (logY) ticks.map { ChartMath.compact(10.0.pow(it)) } else ChartMath.axisLabels(ticks)
        ticks.zip(labels.map { measurer.measure(it, labelStyle) })
    }
    val gapPx = with(density) { 4.dp.toPx() }
    val leftPx = (yAxis?.maxOfOrNull { it.second.size.width }?.toFloat() ?: 0f) + gapPx
    // Read by the long-lived zoom gesture handler, which must not restart on redraw.
    val latestLeft = rememberUpdatedState(leftPx)
    val latestOnWindow = rememberUpdatedState(onXWindowChange)

    // Value at the touched position (nearest point per series), or null.
    val readout: List<String>? = cursorX?.let { cx ->
        prepared?.series?.map { s ->
            val v = (s.smooth ?: s.raw)[nearestIndex(s.x, cx)]
            Parsers.formatNumber(if (logY) 10.0.pow(v) else v)
        }
    }
    val readoutX = cursorX?.let { cx -> prepared?.series?.firstOrNull()?.let { it.x[nearestIndex(it.x, cx)] } }

    // last / min / max of the (single) series as logged, before smoothing.
    val stats = remember(chart) {
        chart.series.singleOrNull()?.let { ChartMath.stats(it.y) }
    }

    Column(modifier) {
        // Title row; the touched value shows on the right so the card doesn't change height.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chart.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (readoutX != null) {
                Text(
                    buildString {
                        append(chart.xLabel).append(' ').append(Parsers.formatNumber(readoutX))
                        if (readout != null && readout.size == 1) append(" · ").append(readout[0])
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            actions()
        }
        if (showStats && stats != null) {
            Text(
                buildString {
                    append("last ").append(Parsers.formatNumber(stats.last))
                    append("   min ").append(Parsers.formatNumber(stats.min))
                    append("   max ").append(Parsers.formatNumber(stats.max))
                    if (prepared?.clipped == true) append("   · outliers hidden")
                },
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (prepared == null) {
            Box(Modifier.fillMaxWidth().height(height - 20.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (logY) "No positive values to plot on a log scale" else "No data yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                )
            }
            return@Column
        }
        val latestPrepared = rememberUpdatedState(prepared)

        val gestures = if (onXWindowChange != null) {
            // Keyed on Unit so the handler survives redraws; a pinch changes the
            // data every frame, and restarting here would drop the gesture.
            Modifier.pointerInput(Unit) {
                var lastTapUp = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val p0 = latestPrepared.value
                    var window = p0.xMin..p0.xMax
                    var multiTouch = false
                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val left = latestLeft.value
                        val plotWidth = (size.width - left).coerceAtLeast(1f)
                        if (pressed.size >= 2) {
                            // Two fingers zoom/pan the X axis. The window is tracked here so
                            // each step builds on the last, not on a frame-old redraw.
                            multiTouch = true
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val zoom = event.calculateZoom().toDouble()
                            val pan = event.calculatePan().x.toDouble()
                            if (zoom != 1.0 || pan != 0.0) {
                                window = ChartMath.zoomWindow(
                                    full = p0.fullX,
                                    current = window,
                                    anchor = ((centroid.x - left) / plotWidth).toDouble().coerceIn(0.0, 1.0),
                                    zoom = zoom,
                                    pan = pan / plotWidth,
                                )
                                val isFull = window.start <= p0.fullX.start && window.endInclusive >= p0.fullX.endInclusive
                                latestOnWindow.value?.invoke(if (isFull) null else window)
                            }
                            cursorX = null
                            event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && !multiTouch) {
                            // One finger reads values (not right after a pinch, when one finger lifts first).
                            val c = pressed[0]
                            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            cursorX = toDataX(c.position.x, size.width.toFloat(), left, latestPrepared.value)
                            c.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    // Double-tap resets the zoom.
                    val upTime = currentEvent.changes.firstOrNull()?.uptimeMillis ?: 0L
                    if (!multiTouch && !moved && upTime - down.uptimeMillis < 250) {
                        if (upTime - lastTapUp < 350) {
                            latestOnWindow.value?.invoke(null)
                            cursorX = null
                            lastTapUp = 0L
                        } else {
                            lastTapUp = upTime
                        }
                    }
                }
            }
        } else {
            Modifier
                .pointerInput(prepared, leftPx) {
                    // Tap toggles the cursor: tap again near it to clear.
                    detectTapGestures(onTap = { pos ->
                        val x = toDataX(pos.x, size.width.toFloat(), leftPx, prepared)
                        val cur = cursorX
                        val near = cur != null &&
                            abs(pos.x - toPx(cur, size.width.toFloat(), leftPx, prepared)) < 24.dp.toPx()
                        cursorX = if (near) null else x
                    })
                }
                .pointerInput(prepared, leftPx) {
                    detectHorizontalDragGestures { change, _ ->
                        cursorX = toDataX(change.position.x, size.width.toFloat(), leftPx, prepared)
                    }
                }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(top = 6.dp)
                .then(gestures),
        ) {
            if (size.width <= leftPx || size.height <= bottomPad.toPx()) return@Canvas
            val left = leftPx
            val labelHalf = (yAxis?.firstOrNull()?.second?.size?.height ?: 0) / 2f
            // Leave room above the plot so the top Y label isn't clipped.
            val top = labelHalf
            val bottom = size.height - bottomPad.toPx()
            val width = size.width - left
            fun px(x: Double) = left + ((x - prepared.xMin) / (prepared.xMax - prepared.xMin) * width).toFloat()
            fun py(y: Double) = bottom - ((y - prepared.yMin) / (prepared.yMax - prepared.yMin) * (bottom - top)).toFloat()

            // Y grid + right-aligned labels
            yAxis?.forEach { (t, layout) ->
                val y = py(t)
                drawLine(axisColor, Offset(left, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val ly = (y - layout.size.height / 2f).coerceIn(0f, maxOf(0f, size.height - layout.size.height))
                drawText(layout, topLeft = Offset(left - gapPx - layout.size.width, ly))
            }
            // X labels
            val xTicks = ChartMath.niceTicks(prepared.xMin, prepared.xMax, 4).filter { px(it) in left..size.width }
            xTicks.zip(ChartMath.axisLabels(xTicks)).forEach { (t, text) ->
                val layout = measurer.measure(text, labelStyle)
                val lx = (px(t) - layout.size.width / 2f).coerceIn(left, maxOf(left, size.width - layout.size.width))
                drawText(layout, topLeft = Offset(lx, bottom + 3f))
            }
            drawLine(axisColor, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.5f)

            // Lines are clipped to the plot so zoomed or outlier-clipped data stays inside it.
            clipRect(left = left, top = 0f, right = size.width, bottom = bottom) {
                prepared.series.forEachIndexed { i, s ->
                    val color = seriesColor(s, i)
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
            }

            cursorX?.let { cx ->
                val x = px(cx).coerceIn(left, size.width)
                drawLine(labelColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
                prepared.series.forEachIndexed { i, s ->
                    val j = nearestIndex(s.x, cx)
                    val v = (s.smooth ?: s.raw)[j]
                    val y = py(v)
                    if (y in top..bottom) drawCircle(seriesColor(s, i), 5f, Offset(px(s.x[j]), y))
                }
            }
        }

        if (prepared.series.size > 1) {
            // Legend; while touching, each entry also shows that series' value.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                prepared.series.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(seriesColor(s, i), CircleShape))
                        Text(
                            " " + s.label + (readout?.getOrNull(i)?.let { ": $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                        )
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

private fun toPx(x: Double, width: Float, left: Float, p: Prepared): Float =
    left + ((x - p.xMin) / (p.xMax - p.xMin) * (width - left)).toFloat()

private fun nearestIndex(xs: DoubleArray, x: Double): Int {
    var lo = 0
    var hi = xs.size - 1
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (xs[mid] < x) lo = mid + 1 else hi = mid
    }
    return if (lo > 0 && abs(xs[lo - 1] - x) < abs(xs[lo] - x)) lo - 1 else lo
}

private fun seriesColor(s: PreparedSeries, position: Int) =
    ChartPalette[(if (s.color >= 0) s.color else position) % ChartPalette.size]

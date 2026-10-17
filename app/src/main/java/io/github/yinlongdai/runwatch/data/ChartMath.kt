package io.github.yinlongdai.runwatch.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object ChartMath {

    /**
     * Time-weighted EMA with debiasing, matching the W&B web UI's default
     * smoothing. weight is in [0, 1); 0 returns the input unchanged.
     */
    fun smooth(y: DoubleArray, weight: Double): DoubleArray {
        if (weight <= 0.0 || y.isEmpty()) return y
        val w = weight.coerceAtMost(0.999)
        val out = DoubleArray(y.size)
        var last = 0.0
        var debias = 1.0
        for (i in y.indices) {
            last = last * w + (1 - w) * y[i]
            debias *= w
            out[i] = last / (1 - debias)
        }
        return out
    }

    /**
     * Reduce a series to at most maxPoints by keeping the min and max of each
     * bucket, so spikes survive. Returns indices into the original arrays.
     */
    fun downsampleIndices(size: Int, y: DoubleArray, maxPoints: Int): IntArray {
        if (size <= maxPoints || maxPoints < 4) return IntArray(size) { it }
        val buckets = maxPoints / 2
        val out = ArrayList<Int>(maxPoints)
        for (b in 0 until buckets) {
            val start = (b.toLong() * size / buckets).toInt()
            val end = ((b + 1).toLong() * size / buckets).toInt().coerceAtMost(size)
            if (start >= end) continue
            var lo = start
            var hi = start
            for (i in start until end) {
                if (y[i] < y[lo]) lo = i
                if (y[i] > y[hi]) hi = i
            }
            if (lo == hi) out += lo else { out += minOf(lo, hi); out += maxOf(lo, hi) }
        }
        return out.toIntArray()
    }

    /** "Nice" tick values covering [min, max], roughly count of them. */
    fun niceTicks(min: Double, max: Double, count: Int = 4): List<Double> {
        if (!min.isFinite() || !max.isFinite()) return emptyList()
        if (min == max) return listOf(min)
        val span = max - min
        val rough = span / count
        val mag = 10.0.pow(floor(log10(rough)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= rough }
        val first = kotlin.math.ceil(min / step) * step
        val ticks = mutableListOf<Double>()
        var t = first
        while (t <= max + step * 1e-9) {
            ticks += if (abs(t) < step * 1e-9) 0.0 else t
            t += step
        }
        return ticks
    }

    /**
     * Labels for evenly spaced axis ticks, all with the same precision and only
     * as many digits as the tick step needs: 0.05 step -> "0.10", "0.15";
     * 25000 step -> "25k", "50k"; 2e-5 step -> "2e-5", "4e-5". Keeps the
     * Y-axis gutter as narrow as the data allows.
     */
    fun axisLabels(ticks: List<Double>): List<String> {
        if (ticks.size < 2) return ticks.map(::compact)
        val step = abs(ticks[1] - ticks[0])
        val maxAbs = ticks.maxOf { abs(it) }
        if (step == 0.0 || !step.isFinite()) return ticks.map(::compact)
        val (div, suffix) = when {
            maxAbs >= 1e9 -> 1e9 to "B"
            maxAbs >= 1e6 -> 1e6 to "M"
            maxAbs >= 1e4 -> 1e3 to "k"
            else -> 1.0 to ""
        }
        if (div == 1.0 && maxAbs < 1e-3) {
            val digits = (floor(log10(maxAbs)) - floor(log10(step))).toInt().coerceIn(0, 4)
            return ticks.map { t ->
                if (t == 0.0) "0" else cleanExponent(String.format(java.util.Locale.US, "%.${digits}e", t))
            }
        }
        val decimals = decimalsFor(step / div)
        return ticks.map { t ->
            if (suffix.isNotEmpty() && abs(t) < step * 1e-6) return@map "0" // not "0.0B"
            val s = String.format(java.util.Locale.US, "%.${decimals}f", t / div)
            // "-0.00" -> "0.00"
            (if (s.startsWith('-') && s.drop(1).all { it == '0' || it == '.' }) s.drop(1) else s) + suffix
        }
    }

    /** Fewest decimals that show [step] exactly (2.5 -> 1, 0.05 -> 2, 20 -> 0). */
    private fun decimalsFor(step: Double): Int {
        for (d in 0..8) {
            val scaled = step * 10.0.pow(d)
            if (abs(scaled - Math.rint(scaled)) < 1e-6 * maxOf(1.0, scaled)) return d
        }
        return 8
    }

    /** "2.0e-05" -> "2.0e-5", "1e+03" -> "1e3". */
    private fun cleanExponent(s: String): String =
        s.replace(Regex("e([+-])0*(\\d)")) { m ->
            "e" + (if (m.groupValues[1] == "-") "-" else "") + m.groupValues[2]
        }

    /** Compact axis label: 1.2k, 3.4M, 0.0012, 1e-6. */
    fun compact(v: Double): String {
        val a = abs(v)
        return when {
            v == 0.0 -> "0"
            a >= 1e9 -> trim(v / 1e9) + "B"
            a >= 1e6 -> trim(v / 1e6) + "M"
            a >= 1e4 -> trim(v / 1e3) + "k"
            a >= 1 -> trim(v)
            a >= 1e-3 -> String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
            else -> String.format(java.util.Locale.US, "%.1e", v)
        }
    }

    private fun trim(v: Double): String =
        String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}

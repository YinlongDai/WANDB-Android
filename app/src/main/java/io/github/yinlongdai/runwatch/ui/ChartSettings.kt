package io.github.yinlongdai.runwatch.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.yinlongdai.runwatch.data.Prefs

/**
 * Chart view options shared by a run's Charts tab, the full-screen chart and
 * the compare screen, so switching between them keeps the same view. Only the
 * X axis is saved across launches.
 */
object ChartSettings {
    var smoothing by mutableFloatStateOf(0f)
    var logY by mutableStateOf(false)
    var clipOutliers by mutableStateOf(false)

    private var loaded = false
    private var xKeyState by mutableStateOf("_step")

    fun xKey(prefs: Prefs): String {
        if (!loaded) { xKeyState = prefs.chartXKey; loaded = true }
        return xKeyState
    }

    fun setXKey(prefs: Prefs, value: String) {
        xKeyState = value
        loaded = true
        prefs.chartXKey = value
    }

    fun xLabel(xKey: String) = if (xKey == "_runtime") "min" else "step"
}

package io.github.yinlongdai.runwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.Series
import io.github.yinlongdai.runwatch.ui.LineChart
import org.junit.Rule
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sin

/**
 * Renders charts on the JVM so layout changes can be checked without a device.
 * `./gradlew recordPaparazziDebug` writes PNGs to app/src/test/snapshots/images.
 */
class ChartScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3200), environment = testEnvironment())

    private fun series(label: String, n: Int, f: (Int) -> Double) =
        Series(label, DoubleArray(n) { it.toDouble() }, DoubleArray(n, f))

    @Test
    fun outliersAndCompare() {
        val spiky = series("loss", 300) { if (it == 150) 40.0 else 1.5 * exp(-it / 80.0) + 0.2 }
        val runs = (0 until 3).map { r ->
            Series("run-$r", DoubleArray(200) { it.toDouble() }, DoubleArray(200) { (1.2 - r * 0.2) * exp(-it / (50.0 + r * 25)) + 0.1 }, colorIndex = r)
        }
        paparazzi.snapshot {
            MaterialTheme {
                Surface {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            LineChart(ChartData("loss (spike, outliers ignored)", listOf(spiky), "step"), 0f, false,
                                Modifier.padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), clipOutliers = true)
                        }
                        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            LineChart(ChartData("loss (spike, all points)", listOf(spiky), "step"), 0f, false,
                                Modifier.padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp))
                        }
                        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            LineChart(ChartData("val/loss · 3 runs", runs, "step"), 0.5f, false,
                                Modifier.padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), showStats = false)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun charts() {
        val loss = series("loss", 500) { 2.5 * exp(-it / 120.0) + 0.05 * sin(it / 3.0) + 0.1 }
        val acc = series("val/acc", 200) { 0.98 + 0.009 * (1 - exp(-it / 40.0)) }
        val tokens = series("tokens", 300) { it * 3.2e6 }
        val lr = series("lr", 300) { 3e-4 * exp(-it / 200.0) }
        val gpus = (0 until 4).map { g -> Series("GPU $g", DoubleArray(120) { it / 2.0 }, DoubleArray(120) { 70.0 + 20 * sin(it / 9.0 + g) }) }
        paparazzi.snapshot {
            MaterialTheme {
                Surface {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ChartData("loss", listOf(loss), "step"),
                            ChartData("val/acc", listOf(acc), "step"),
                            ChartData("tokens", listOf(tokens), "step"),
                            ChartData("lr", listOf(lr), "step"),
                            ChartData("GPU utilization (%)", gpus, "minutes"),
                        ).forEach { chart ->
                            Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                LineChart(
                                    chart,
                                    smoothing = if (chart.title == "loss") 0.6f else 0f,
                                    logY = false,
                                    modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

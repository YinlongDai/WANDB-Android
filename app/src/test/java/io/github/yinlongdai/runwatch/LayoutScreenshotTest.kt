package io.github.yinlongdai.runwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.Series
import io.github.yinlongdai.runwatch.ui.GlassTopBar
import io.github.yinlongdai.runwatch.ui.LineChart
import androidx.compose.material3.Text
import dev.chrisbanes.haze.rememberHazeState
import io.github.yinlongdai.runwatch.ui.PillSearchField
import io.github.yinlongdai.runwatch.ui.RunTabsBar
import io.github.yinlongdai.runwatch.ui.RunWatchTheme
import io.github.yinlongdai.runwatch.ui.SearchLayout
import org.junit.Rule
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sin

/** Run screen layout: bottom tabs plus the search bar in each placement. */
class LayoutScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = NightMode.NIGHT), environment = testEnvironment())

    private val charts = (0 until 4).map { k ->
        ChartData(
            listOf("loss", "accuracy", "grad_norm", "lr")[k],
            listOf(Series("s", DoubleArray(200) { it.toDouble() }, DoubleArray(200) { exp(-it / (60.0 + k * 30)) + 0.04 * sin(it / 4.0) })),
            "step",
        )
    }

    private fun runScreen(atBottom: Boolean, query: String) {
        paparazzi.snapshot {
            RunWatchTheme {
                // Layoutlib can't render blur, so this shows the no-blur fallback
                // (Android < 12 or "Glass effects" off): bars must still be opaque enough to read.
                val glass = rememberHazeState(blurEnabled = false)
                Scaffold(
                    topBar = { GlassTopBar(glass, title = { Text("bright-sun-42") }) },
                    bottomBar = { RunTabsBar(0, glass) {} },
                ) { padding ->
                    SearchLayout(
                        atBottom = atBottom,
                        barPadding = padding,
                        glass = glass,
                        search = { m, floating -> PillSearchField(query, {}, "Filter 42 metrics", m, floating, glass) },
                    ) { listPadding ->
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(charts.size) { i ->
                                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                    LineChart(charts[i], 0f, false, Modifier.padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun searchAtBottom() = runScreen(atBottom = true, query = "")

    @Test
    fun searchAtTopWithQuery() = runScreen(atBottom = false, query = "train/lo")
}

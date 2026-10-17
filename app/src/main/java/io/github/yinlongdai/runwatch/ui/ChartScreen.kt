package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yinlongdai.runwatch.data.ApiException
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.Prefs
import io.github.yinlongdai.runwatch.data.Series
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Samples requested for the full-screen view: more detail to zoom into than the 500 used for cards. */
private const val FULL_SAMPLES = 2000

/**
 * One metric, full screen, for one run or several (from Compare, one colored
 * line per run). Two fingers zoom and pan the X axis, one finger reads values,
 * double-tap resets. Shares smoothing / log / outlier / X-axis settings with
 * the Charts tab.
 */
@Composable
fun ChartScreen(
    prefs: Prefs,
    entity: String,
    project: String,
    runNames: List<String>,
    metric: String,
    onBack: () -> Unit,
) {
    val api = remember { prefs.api() } ?: run { CenteredMessage("Signed out."); return }
    val glass = rememberGlass(prefs)
    val xKey = ChartSettings.xKey(prefs)
    var series by remember(xKey) { mutableStateOf<List<Series>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var window by remember(xKey) { mutableStateOf<ClosedFloatingPointRange<Double>?>(null) }

    PollingEffect(xKey, intervalMs = 15_000) {
        try {
            series = coroutineScope {
                runNames.mapIndexed { i, name ->
                    async {
                        val run = api.runState(entity, project, name)
                        val s = api.sampledHistory(entity, project, name, listOf(metric), samples = FULL_SAMPLES, xKey = xKey).first()
                        run to Series(if (runNames.size > 1) run.displayName else metric, s.x, s.y, colorIndex = if (runNames.size > 1) i else -1)
                    }
                }.awaitAll()
            }.let { results ->
                running = results.any { it.first.isRunning }
                results.map { it.second }.filter { !it.isEmpty }
            }
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        running
    }

    Scaffold(
        topBar = {
            GlassTopBar(
                state = glass,
                title = {
                    Column {
                        Text(metric, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (runNames.size > 1) "$entity/$project · ${runNames.size} runs" else "$entity/$project/${runNames.first()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (window != null) {
                        IconButton(onClick = { window = null }) { Icon(Icons.Filled.ZoomOutMap, "Reset zoom") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(glass)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 8.dp),
        ) {
            ChartControls(
                ChartSettings.smoothing, { ChartSettings.smoothing = it },
                ChartSettings.logY, { ChartSettings.logY = it },
                ChartSettings.clipOutliers, { ChartSettings.clipOutliers = it },
                xKey, { ChartSettings.setXKey(prefs, it) },
            )
            Text(
                if (window == null) "Pinch to zoom · drag to read values" else "Two fingers to zoom or move · double-tap to reset",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val s = series
            when {
                s == null && error != null -> CenteredMessage(error!!)
                s == null -> CenteredLoading()
                s.isEmpty() -> CenteredMessage("No data for $metric yet.")
                else -> Card(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        LineChart(
                            chart = ChartData(metric, s, ChartSettings.xLabel(xKey)),
                            smoothing = ChartSettings.smoothing,
                            logY = ChartSettings.logY,
                            clipOutliers = ChartSettings.clipOutliers,
                            // Title + stats rows take about 44dp; the plot gets the rest.
                            height = (maxHeight - 64.dp).coerceAtLeast(160.dp),
                            showStats = s.size == 1,
                            xWindow = window,
                            onXWindowChange = { window = it },
                            modifier = ChartPadding,
                        )
                    }
                }
            }
        }
    }
}

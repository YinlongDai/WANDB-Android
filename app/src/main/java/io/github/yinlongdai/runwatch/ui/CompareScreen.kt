package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yinlongdai.runwatch.data.ApiException
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.Prefs
import io.github.yinlongdai.runwatch.data.RunDetail
import io.github.yinlongdai.runwatch.data.Series
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val COMPARE_PAGE = 12

/**
 * Overlays the same metrics from several runs of one project: one chart per
 * metric, one colored line per run (the same color for a run on every chart).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareScreen(
    prefs: Prefs,
    entity: String,
    project: String,
    runNames: List<String>,
    onBack: () -> Unit,
    onExpandMetric: (String) -> Unit = {},
) {
    val api = remember { prefs.api() } ?: run { CenteredMessage("Signed out."); return }
    val glass = rememberGlass(prefs)
    var details by remember { mutableStateOf<List<RunDetail>?>(null) }
    var charts by remember { mutableStateOf<Map<String, List<Series>>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(COMPARE_PAGE) }
    val xKey = ChartSettings.xKey(prefs)

    // Run metadata, refreshed while any compared run is live.
    PollingEffect(runNames, intervalMs = 30_000) {
        try {
            details = coroutineScope { runNames.map { async { api.runDetail(entity, project, it) } }.awaitAll() }
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        details?.any { it.run.isRunning } == true
    }

    val runs = details
    // Every metric logged by any of the runs, in the usual order (top-level first, then by name).
    val allKeys = remember(runs) {
        runs.orEmpty().flatMap { it.metricKeys }.distinct()
            .sortedWith(compareBy({ it.contains('/') }, { it.lowercase() }))
    }
    val keys = allKeys.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    val visible = keys.take(limit)

    PollingEffect(visible, xKey, runs?.size, intervalMs = 30_000) {
        val rs = runs ?: return@PollingEffect false
        if (visible.isEmpty()) return@PollingEffect false
        try {
            // One request per run, all visible metrics at once, in parallel.
            val perRun = coroutineScope {
                rs.map { d ->
                    async {
                        val mine = visible.filter { it in d.metricKeys }
                        mine.zip(api.sampledHistory(entity, project, d.run.name, mine, xKey = xKey))
                    }
                }.awaitAll()
            }
            charts = charts + visible.associateWith { key ->
                perRun.mapIndexedNotNull { i, pairs ->
                    pairs.firstOrNull { it.first == key }?.second?.let { s ->
                        Series(rs[i].run.displayName, s.x, s.y, colorIndex = i)
                    }
                }
            }
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        rs.any { it.run.isRunning }
    }

    Scaffold(
        topBar = {
            GlassTopBar(
                state = glass,
                title = {
                    Column {
                        Text("Compare ${runNames.size} runs", maxLines = 1)
                        Text(
                            "$entity/$project",
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
            )
        },
    ) { padding ->
        when {
            runs == null && error != null -> CenteredMessage(error!!)
            runs == null -> CenteredLoading()
            else -> SearchLayout(
                atBottom = prefs.searchAtBottom,
                barPadding = padding,
                glass = glass,
                search = { m, floating ->
                    PillSearchField(query, { query = it; limit = COMPARE_PAGE }, "Filter ${allKeys.size} metrics", m, floating, glass)
                },
            ) { listPadding ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = listPadding.calculateTopPadding() + 4.dp,
                        bottom = listPadding.calculateBottomPadding() + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Which color is which run.
                    item {
                        FlowRow(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            runs.forEachIndexed { i, d ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(ChartPalette[i % ChartPalette.size], CircleShape))
                                    Text(" " + d.run.displayName, style = MaterialTheme.typography.labelMedium)
                                    StateBadge(d.run.state, Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                    item {
                        ChartControls(
                            ChartSettings.smoothing, { ChartSettings.smoothing = it },
                            ChartSettings.logY, { ChartSettings.logY = it },
                            ChartSettings.clipOutliers, { ChartSettings.clipOutliers = it },
                            xKey, { ChartSettings.setXKey(prefs, it) },
                        )
                    }
                    error?.let { msg ->
                        item { Text("Couldn't load history: $msg", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
                    }
                    if (charts.isEmpty() && visible.isNotEmpty()) item {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                    }
                    if (visible.isEmpty()) item {
                        Text(
                            if (allKeys.isEmpty()) "These runs have no logged metrics." else "No metrics match “$query”.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(visible, key = { it }) { key ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            val series = charts[key]
                            val expand: @Composable RowScope.() -> Unit = {
                                SmallIconButton(Icons.Filled.OpenInFull, "Expand $key") { onExpandMetric(key) }
                            }
                            if (series == null) {
                                Row(ChartPadding, verticalAlignment = Alignment.CenterVertically) {
                                    Text(key, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    expand()
                                }
                            } else {
                                LineChart(
                                    chart = ChartData(key, series, ChartSettings.xLabel(xKey)),
                                    smoothing = ChartSettings.smoothing,
                                    logY = ChartSettings.logY,
                                    clipOutliers = ChartSettings.clipOutliers,
                                    showStats = false,
                                    modifier = ChartPadding,
                                    actions = expand,
                                )
                            }
                        }
                    }
                    if (keys.size > limit) item {
                        TextButton(onClick = { limit += COMPARE_PAGE }, modifier = Modifier.fillMaxWidth()) {
                            Text("Show ${minOf(COMPARE_PAGE, keys.size - limit)} more of ${keys.size - limit}")
                        }
                    }
                }
            }
        }
    }
}

package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yinlongdai.runwatch.data.Ansi
import io.github.yinlongdai.runwatch.data.ApiException
import io.github.yinlongdai.runwatch.data.ChartData
import io.github.yinlongdai.runwatch.data.LogLine
import io.github.yinlongdai.runwatch.data.Prefs
import io.github.yinlongdai.runwatch.data.RunDetail
import io.github.yinlongdai.runwatch.data.Series
import io.github.yinlongdai.runwatch.data.WandbApi
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private data class RunTab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    RunTab("Charts", Icons.AutoMirrored.Filled.ShowChart),
    RunTab("System", Icons.Filled.Memory),
    RunTab("Logs", Icons.Filled.Terminal),
    RunTab("Overview", Icons.Filled.Info),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    prefs: Prefs,
    entity: String,
    project: String,
    runName: String,
    onBack: () -> Unit,
    onExpandMetric: (key: String) -> Unit = {},
) {
    val api = remember { prefs.api() } ?: run { CenteredMessage("Signed out."); return }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var detail by remember { mutableStateOf<RunDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmStop by remember { mutableStateOf(false) }

    // Reload metadata (state, summary, metric keys) every 15 s while the run is live.
    PollingEffect(runName, intervalMs = 15_000) {
        try {
            detail = api.runDetail(entity, project, runName)
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        detail?.run?.isRunning == true
    }

    val run = detail?.run
    val glass = rememberGlass(prefs)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // Tabs live at the bottom, within thumb reach; labels never wrap.
        bottomBar = { RunTabsBar(tab, glass) { tab = it } },
        topBar = {
            GlassTopBar(
                state = glass,
                title = {
                    Column {
                        Text(run?.displayName ?: runName, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                actions = {
                    if (run != null) StateBadge(run.state, Modifier.padding(end = 4.dp))
                    if (run?.isRunning == true) {
                        IconButton(onClick = { confirmStop = true }) {
                            Icon(Icons.Filled.StopCircle, "Stop run", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Each tab draws under the glass bars and is the glass source itself.
        Box(Modifier.fillMaxSize()) {
            val d = detail
            when {
                d == null && error != null -> CenteredMessage(error!!)
                d == null -> CenteredLoading()
                else -> when (tab) {
                    0 -> ChartsTab(api, entity, project, d, prefs, glass, padding, onExpandMetric)
                    1 -> SystemTab(api, entity, project, runName, d.run.isRunning, glass, padding)
                    2 -> LogsTab(api, entity, project, runName, d.run.isRunning, glass, padding)
                    else -> OverviewTab(d, glass, padding)
                }
            }
        }
    }

    if (confirmStop && run != null) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop this run?") },
            text = { Text("${run.displayName} will be asked to stop at its next heartbeat, the same as pressing Stop in the W&B web app.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    scope.launch {
                        try {
                            api.stopRun(run.id)
                            snackbar.showSnackbar("Stop requested. The run will exit shortly.")
                        } catch (e: ApiException) {
                            snackbar.showSnackbar("Couldn't stop run: ${e.message}")
                        }
                    }
                }) { Text("Stop run", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun RunTabsBar(selected: Int, glass: HazeState?, onSelect: (Int) -> Unit) {
    val items: @Composable RowScope.() -> Unit = {
        TABS.forEachIndexed { i, t ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(t.icon, null) },
                label = { Text(t.label, maxLines = 1, softWrap = false) },
            )
        }
    }
    if (glass != null) GlassNavigationBar(glass, items) else NavigationBar(content = items)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChartControls(
    smoothing: Float,
    onSmoothing: (Float) -> Unit,
    logY: Boolean,
    onLogY: (Boolean) -> Unit,
    clipOutliers: Boolean,
    onClipOutliers: (Boolean) -> Unit,
    xKey: String? = null,
    onXKey: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Smoothing", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = smoothing,
                onValueChange = onSmoothing,
                valueRange = 0f..0.99f,
                modifier = Modifier.weight(1f),
            )
            Text("%.2f".format(smoothing), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = logY, onClick = { onLogY(!logY) }, label = { Text("Log Y") })
            FilterChip(selected = clipOutliers, onClick = { onClipOutliers(!clipOutliers) }, label = { Text("Ignore outliers") })
            if (xKey != null) {
                FilterChip(
                    selected = xKey == "_runtime",
                    onClick = { onXKey(if (xKey == "_runtime") "_step" else "_runtime") },
                    label = { Text(if (xKey == "_runtime") "X: time" else "X: step") },
                )
            }
        }
    }
}

private const val CHART_PAGE = 20

@Composable
private fun ChartsTab(
    api: WandbApi,
    entity: String,
    project: String,
    detail: RunDetail,
    prefs: Prefs,
    glass: HazeState,
    barPadding: PaddingValues,
    onExpand: (String) -> Unit,
) {
    val runName = detail.run.name
    val share = rememberChartSharer()
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(CHART_PAGE) }
    val xKey = ChartSettings.xKey(prefs)
    var series by remember(xKey) { mutableStateOf<Map<String, Series>>(emptyMap()) }
    var loading by remember(xKey) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val pinPrefix = "$entity/$project/"
    var pinned by remember { mutableStateOf(prefs.pinnedMetrics) }
    fun togglePin(key: String) {
        val id = pinPrefix + key
        prefs.pinnedMetrics = if (id in pinned) pinned - id else pinned + id
        pinned = prefs.pinnedMetrics
    }

    val keys = detail.metricKeys.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    // Pinned metrics come first, in their own section.
    val (pinnedKeys, otherKeys) = keys.partition { pinPrefix + it in pinned }
    val ordered = pinnedKeys + otherKeys
    val visible = ordered.take(limit)

    PollingEffect(visible, xKey, intervalMs = 15_000) {
        if (visible.isEmpty()) { loading = false; return@PollingEffect false }
        try {
            val fetched = api.sampledHistory(entity, project, runName, visible, xKey = xKey)
            series = series + visible.zip(fetched).toMap()
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        loading = false
        detail.run.isRunning
    }

    if (detail.metricKeys.isEmpty()) {
        CenteredMessage(if (detail.run.isRunning) "No metrics logged yet. Waiting for wandb.log()…" else "This run has no logged metrics.")
        return
    }

    SearchLayout(
        atBottom = prefs.searchAtBottom,
        barPadding = barPadding,
        glass = glass,
        search = { m, floating ->
            PillSearchField(query, { query = it; limit = CHART_PAGE }, "Filter ${detail.metricKeys.size} metrics", m, floating, glass)
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
            if (loading && series.isEmpty()) item {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            }
            if (visible.isEmpty()) item {
                Text(
                    "No metrics match \u201c$query\u201d.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            // Section header whenever the section changes: "Pinned", then the metric prefix ("train/loss" -> "train").
            fun sectionOf(k: String) = if (k in pinnedKeys) "Pinned" else k.substringBeforeLast('/', "").ifEmpty { "Charts" }
            visible.forEachIndexed { i, key ->
                val section = sectionOf(key)
                if (i == 0 || sectionOf(visible[i - 1]) != section) {
                    item(key = "section:$i:$section") {
                        Text(
                            section,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                    }
                }
                item(key = key) {
                    MetricCard(
                        key = key,
                        series = series[key],
                        xLabel = ChartSettings.xLabel(xKey),
                        // In "Pinned" the prefix isn't shown by a header, so keep the full name.
                        title = if (key in pinnedKeys) key else key.substringAfterLast('/'),
                        pinned = key in pinnedKeys,
                        onTogglePin = { togglePin(key) },
                        onExpand = { onExpand(key) },
                        onShare = { capture, chart -> share(capture, chart.copy(title = key), "$entity/$project · ${detail.run.displayName}", false) },
                    )
                }
            }
            if (ordered.size > limit) item {
                TextButton(onClick = { limit += CHART_PAGE }, modifier = Modifier.fillMaxWidth()) {
                    Text("Show ${minOf(CHART_PAGE, ordered.size - limit)} more of ${ordered.size - limit}")
                }
            }
        }
    }
}

@Composable
private fun SystemTab(
    api: WandbApi,
    entity: String,
    project: String,
    runName: String,
    running: Boolean,
    glass: HazeState,
    barPadding: PaddingValues,
) {
    var charts by remember { mutableStateOf<List<ChartData>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    PollingEffect(runName, intervalMs = 30_000) {
        try {
            charts = api.systemMetrics(entity, project, runName)
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        running
    }

    val list = charts
    when {
        list == null && error != null -> CenteredMessage(error!!)
        list == null -> CenteredLoading()
        list.isEmpty() -> CenteredMessage("No system metrics recorded for this run.")
        else -> LazyColumn(
            Modifier.fillMaxSize().glassSource(glass),
            contentPadding = PaddingValues(
                top = barPadding.calculateTopPadding() + 4.dp,
                bottom = barPadding.calculateBottomPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.title }) { chart ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    LineChart(chart, smoothing = 0f, logY = false, modifier = ChartPadding, showStats = chart.series.size == 1)
                }
            }
        }
    }
}

@Composable
private fun LogsTab(
    api: WandbApi,
    entity: String,
    project: String,
    runName: String,
    running: Boolean,
    glass: HazeState,
    barPadding: PaddingValues,
) {
    var lines by remember { mutableStateOf<List<LogLine>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var follow by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    PollingEffect(runName, intervalMs = 5_000) {
        try {
            lines = api.consoleTail(entity, project, runName)
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        running
    }

    val list = lines
    LaunchedEffect(list?.size, follow) {
        if (follow && !list.isNullOrEmpty()) listState.scrollToItem(list.size - 1)
    }

    Column(Modifier.fillMaxSize().glassSource(glass).padding(top = barPadding.calculateTopPadding())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    list == null -> ""
                    running -> "Last ${list.size} lines · refreshing every 5 s"
                    else -> "Last ${list.size} lines"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = follow, onClick = { follow = !follow }, label = { Text("Follow") })
        }
        HorizontalDivider()
        when {
            list == null && error != null -> CenteredMessage(error!!)
            list == null -> CenteredLoading()
            list.isEmpty() -> CenteredMessage("No console output yet.")
            else -> SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                    contentPadding = PaddingValues(bottom = barPadding.calculateBottomPadding()),
                ) {
                    items(list, key = { it.number }) { line ->
                        Text(
                            ansiText(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ansiText(line: LogLine): AnnotatedString {
    val errorColor = MaterialTheme.colorScheme.error
    return buildAnnotatedString {
        for (span in Ansi.parse(line.text)) {
            val color = span.fg?.let { AnsiColors[it] } ?: if (line.isError) errorColor else androidx.compose.ui.graphics.Color.Unspecified
            withStyle(SpanStyle(color = color, fontWeight = if (span.bold) FontWeight.Bold else null)) {
                append(span.text)
            }
        }
    }
}

@Composable
private fun OverviewTab(detail: RunDetail, glass: HazeState, barPadding: PaddingValues) {
    val run = detail.run
    val created = parseWandbTime(run.createdAt)
    val end = if (run.isRunning) Instant.now() else parseWandbTime(run.heartbeatAt)
    val runtime = if (created != null && end != null) formatDuration(Duration.between(created, end).seconds) else ""

    LazyColumn(
        Modifier.fillMaxSize().glassSource(glass),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = barPadding.calculateTopPadding(),
            bottom = barPadding.calculateBottomPadding() + 8.dp,
        ),
    ) {
        item { SectionTitle("Run") }
        item { KeyValue("ID", run.name) }
        item { KeyValue("State", run.state) }
        run.user?.let { item { KeyValue("User", it) } }
        item { KeyValue("Created", "${run.createdAt.replace('T', ' ')} UTC (${relativeTime(run.createdAt)})") }
        if (runtime.isNotEmpty()) item { KeyValue("Runtime", runtime) }
        run.group?.let { item { KeyValue("Group", it) } }
        run.jobType?.let { item { KeyValue("Job type", it) } }
        if (run.tags.isNotEmpty()) item { KeyValue("Tags", run.tags.joinToString(", ")) }

        if (detail.summary.isNotEmpty()) {
            item { SectionTitle("Summary") }
            items(detail.summary, key = { "s:" + it.first }) { (k, v) -> KeyValue(k, v) }
        }
        item { SectionTitle("Config") }
        if (detail.config.isEmpty()) item {
            Text(
                "No config was logged for this run. Pass config=… to wandb.init() to record hyperparameters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(detail.config, key = { "c:" + it.first }) { (k, v) -> KeyValue(k, v) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        SelectionContainer(Modifier.weight(0.55f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Tight left padding: the Y-axis labels already sit inside the chart's own gutter. */
val ChartPadding = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)

@Composable
private fun MetricCard(
    key: String,
    series: Series?,
    xLabel: String,
    title: String,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onExpand: () -> Unit,
    onShare: (ChartCapture, ChartData) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val capture = rememberChartCapture()
        val chart = series?.let { ChartData(title = title, series = listOf(it), xLabel = xLabel) }
        val actions: @Composable RowScope.() -> Unit = {
            if (chart != null) SmallIconButton(Icons.Filled.Share, "Share $key as image") { onShare(capture, chart) }
            SmallIconButton(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                if (pinned) "Unpin $key" else "Pin $key",
                tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onTogglePin,
            )
            SmallIconButton(Icons.Filled.OpenInFull, "Expand $key", onClick = onExpand)
        }
        if (chart == null) {
            Row(ChartPadding, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                actions()
            }
        } else {
            LineChart(
                chart = chart,
                smoothing = ChartSettings.smoothing,
                logY = ChartSettings.logY,
                clipOutliers = ChartSettings.clipOutliers,
                modifier = ChartPadding,
                actions = actions,
                capture = capture,
            )
        }
    }
}

/** Compact 32dp icon button for chart headers. */
@Composable
fun SmallIconButton(
    icon: ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yinlongdai.runwatch.alerts.RunAlertWorker
import io.github.yinlongdai.runwatch.data.ApiException
import io.github.yinlongdai.runwatch.data.Parsers
import io.github.yinlongdai.runwatch.data.Prefs
import io.github.yinlongdai.runwatch.data.Run
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RunsScreen(
    prefs: Prefs,
    entity: String,
    project: String,
    onOpenRun: (Run) -> Unit,
    onCompare: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val api = remember { prefs.api() }
    val scope = rememberCoroutineScope()
    val path = "$entity/$project"
    var runs by remember { mutableStateOf<List<Run>?>(null) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var total by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var onlyActive by remember { mutableStateOf(false) }
    var isStarred by remember { mutableStateOf(path in prefs.starred) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // Summary metric shown on each row, remembered per project. Summaries are only
    // fetched once a column is wanted, since they can be large.
    var column by remember { mutableStateOf(prefs.runsColumn(path)) }
    var wantSummary by remember { mutableStateOf(column != null) }
    var sort by rememberSaveable { mutableStateOf(RunSort.Newest) }
    var pickerOpen by remember { mutableStateOf(false) }

    // Refresh the first page every 15 s while any run is live; otherwise load once.
    PollingEffect(path, reload, wantSummary, intervalMs = 15_000) {
        if (api == null) return@PollingEffect false
        try {
            val page = api.runs(entity, project, withSummary = wantSummary)
            val current = runs
            // Keep any extra pages the user loaded, replacing the refreshed first page.
            runs = if (current == null || current.size <= page.runs.size) page.runs
            else page.runs + current.drop(page.runs.size).filter { r -> page.runs.none { it.name == r.name } }
            if (current == null || current.size <= page.runs.size) {
                cursor = page.endCursor
                hasMore = page.hasNextPage
            }
            total = page.totalCount
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
        refreshing = false
        runs?.any { it.isRunning } == true
    }

    // Long-press runs to select them for side-by-side comparison.
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    fun toggleSelect(run: Run) {
        selected = if (run.name in selected) selected - run.name
        else if (selected.size < MAX_COMPARE) selected + run.name else selected
    }
    val glass = rememberGlass(prefs)

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                GlassTopBar(
                    state = glass,
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptyList() }) { Icon(Icons.Filled.Close, "Clear selection") }
                    },
                    actions = {
                        TextButton(enabled = selected.size >= 2, onClick = { onCompare(selected) }) {
                            Icon(Icons.AutoMirrored.Filled.CompareArrows, null, Modifier.padding(end = 6.dp))
                            Text("Compare")
                        }
                    },
                )
            } else {
                GlassTopBar(
                    state = glass,
                    title = {
                        Column {
                            Text(project, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "$entity · $total runs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = {
                            prefs.toggleStar(path)
                            isStarred = path in prefs.starred
                            RunAlertWorker.sync(context)
                        }) {
                            if (isStarred) Icon(Icons.Filled.Star, "Unstar", tint = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.Outlined.StarBorder, "Star for alerts")
                        }
                        IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    },
                )
            }
        },
    ) { padding ->
        val list = runs
        SearchLayout(
            atBottom = prefs.searchAtBottom,
            barPadding = padding,
            glass = glass,
            search = { m, floating -> PillSearchField(query, { query = it }, "Search runs, tags, groups", m, floating, glass) },
        ) { listPadding ->
            when {
                list == null && error != null -> CenteredMessage(error!!, "Retry") { reload++ }
                list == null -> CenteredLoading()
                list.isEmpty() -> CenteredMessage("No runs in this project yet.")
                else -> {
                    val summaryKeys = remember(list) { summaryColumns(list) }
                    val shown = sortRuns(
                        list.filter { (!onlyActive || it.isRunning) && it.matches(query) },
                        column,
                        sort,
                    )
                    InsetPullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { refreshing = true; reload++ },
                        topInset = listPadding.calculateTopPadding(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
                            item {
                                FlowRow(
                                    Modifier.padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(selected = !onlyActive, onClick = { onlyActive = false }, label = { Text("All") })
                                    FilterChip(selected = onlyActive, onClick = { onlyActive = true }, label = { Text("Running") })
                                    Box {
                                        FilterChip(
                                            selected = column != null,
                                            onClick = { wantSummary = true; pickerOpen = true },
                                            label = { Text(column ?: "Metric column", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                                            modifier = Modifier.widthIn(max = 220.dp),
                                        )
                                        DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                                            if (column != null) {
                                                DropdownMenuItem(text = { Text("None") }, onClick = {
                                                    column = null; sort = RunSort.Newest; prefs.setRunsColumn(path, null); pickerOpen = false
                                                })
                                            }
                                            if (summaryKeys.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text(if (list.none { it.summary.isNotEmpty() }) "Loading metrics…" else "No numeric summary metrics") },
                                                    onClick = {},
                                                    enabled = false,
                                                )
                                            }
                                            summaryKeys.forEach { k ->
                                                DropdownMenuItem(text = { Text(k) }, onClick = {
                                                    column = k; prefs.setRunsColumn(path, k); pickerOpen = false
                                                    if (sort == RunSort.Newest) sort = RunSort.HighFirst
                                                })
                                            }
                                        }
                                    }
                                    if (column != null) {
                                        FilterChip(
                                            selected = sort != RunSort.Newest,
                                            onClick = { sort = sort.next() },
                                            label = { Text(sort.label) },
                                        )
                                    }
                                }
                            }
                            if (error != null) item {
                                Text(
                                    "Couldn't refresh: $error",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp, 4.dp),
                                )
                            }
                            if (shown.isEmpty()) item {
                                Text(
                                    if (query.isNotBlank()) "No loaded runs match \u201c$query\u201d." else "No running runs among the loaded ones.",
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            if (selected.isEmpty() && shown.size >= 2) item {
                                Text(
                                    "Tip: long-press runs to compare them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                            items(shown, key = { it.name }) { run ->
                                RunRow(
                                    run = run,
                                    column = column,
                                    selected = run.name in selected,
                                    onClick = { if (selected.isNotEmpty()) toggleSelect(run) else onOpenRun(run) },
                                    onLongClick = { toggleSelect(run) },
                                )
                                HorizontalDivider()
                            }
                            if (hasMore && !onlyActive && query.isBlank()) item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                                    else TextButton(onClick = {
                                        val a = api ?: return@TextButton
                                        loadingMore = true
                                        scope.launch {
                                            try {
                                                val page = a.runs(entity, project, cursor, withSummary = wantSummary)
                                                val existing = runs.orEmpty()
                                                runs = existing + page.runs.filter { r -> existing.none { it.name == r.name } }
                                                cursor = page.endCursor
                                                hasMore = page.hasNextPage
                                            } catch (e: ApiException) {
                                                error = e.message
                                            } finally {
                                                loadingMore = false
                                            }
                                        }
                                    }) { Text("Load more") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Most runs that fit legibly on one chart (one palette color each). */
const val MAX_COMPARE = 8

/** Order of the runs list when a metric column is shown. */
enum class RunSort(val label: String) {
    Newest("Newest first"), HighFirst("Highest first"), LowFirst("Lowest first");

    fun next() = entries[(ordinal + 1) % entries.size]
}

/** Search over name, id, tags, group, job type and user. */
fun Run.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return displayName.contains(q, true) || name.contains(q, true) || tags.any { it.contains(q, true) } ||
        group?.contains(q, true) == true || jobType?.contains(q, true) == true || user?.contains(q, true) == true
}

/** Summary keys worth offering as a column: user metrics first, W&B "_" keys except runtime left out. */
fun summaryColumns(runs: List<Run>): List<String> =
    runs.flatMap { it.summary.keys }.distinct()
        .filter { !it.startsWith("_") || it == "_runtime" }
        .sortedWith(compareBy({ it.startsWith("_") }, { it.contains('/') }, { it.lowercase() }))

/** Runs without the metric sink to the bottom; [RunSort.Newest] keeps the server's order. */
fun sortRuns(runs: List<Run>, column: String?, sort: RunSort): List<Run> {
    if (column == null || sort == RunSort.Newest) return runs
    val (has, missing) = runs.partition { column in it.summary }
    val sorted = if (sort == RunSort.HighFirst) has.sortedByDescending { it.summary[column] } else has.sortedBy { it.summary[column] }
    return sorted + missing
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunRow(run: Run, column: String?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                run.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StateBadge(run.state, Modifier.padding(start = 8.dp))
        }
        val meta = listOfNotNull(
            run.user,
            relativeTime(run.createdAt).takeIf { it.isNotEmpty() },
            runDuration(run).takeIf { it.isNotEmpty() }?.let { if (run.isRunning) "running $it" else "ran $it" },
            run.group,
            run.jobType,
        ).joinToString(" · ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (column != null) {
                Text(
                    run.summary[column]?.let { Parsers.formatNumber(it) } ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (run.tags.isNotEmpty()) {
            Text(
                run.tags.joinToString("  ") { "#$it" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

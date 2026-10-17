package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yinlongdai.runwatch.alerts.RunAlertWorker
import io.github.yinlongdai.runwatch.data.ApiException
import io.github.yinlongdai.runwatch.data.Prefs
import io.github.yinlongdai.runwatch.data.Run
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunsScreen(
    prefs: Prefs,
    entity: String,
    project: String,
    onOpenRun: (Run) -> Unit,
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

    // Refresh the first page every 15 s while any run is live; otherwise load once.
    PollingEffect(path, reload, intervalMs = 15_000) {
        if (api == null) return@PollingEffect false
        try {
            val page = api.runs(entity, project)
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

    Scaffold(
        topBar = {
            TopAppBar(
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
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlyActive, onClick = { onlyActive = false }, label = { Text("All") })
                FilterChip(selected = onlyActive, onClick = { onlyActive = true }, label = { Text("Running") })
            }
            val list = runs
            when {
                list == null && error != null -> CenteredMessage(error!!, "Retry") { reload++ }
                list == null -> CenteredLoading()
                list.isEmpty() -> CenteredMessage("No runs in this project yet.")
                else -> {
                    val shown = if (onlyActive) list.filter { it.isRunning } else list
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { refreshing = true; reload++ },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (error != null) item {
                                Text(
                                    "Couldn't refresh: $error",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp, 4.dp),
                                )
                            }
                            if (shown.isEmpty()) item {
                                Text("No running runs among the loaded ones.", modifier = Modifier.padding(16.dp))
                            }
                            items(shown, key = { it.name }) { run ->
                                RunRow(run) { onOpenRun(run) }
                                HorizontalDivider()
                            }
                            if (hasMore && !onlyActive) item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                                    else TextButton(onClick = {
                                        val a = api ?: return@TextButton
                                        loadingMore = true
                                        scope.launch {
                                            try {
                                                val page = a.runs(entity, project, cursor)
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

@Composable
private fun RunRow(run: Run, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Text(
            meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

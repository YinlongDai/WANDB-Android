package io.github.yinlongdai.runwatch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.yinlongdai.runwatch.alerts.RunAlertWorker
import io.github.yinlongdai.runwatch.data.ApiException
import io.github.yinlongdai.runwatch.data.Prefs
import io.github.yinlongdai.runwatch.data.Project
import io.github.yinlongdai.runwatch.data.Viewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    prefs: Prefs,
    onOpenProject: (Project) -> Unit,
    onOpenSettings: () -> Unit,
    onAuthError: () -> Unit,
) {
    val context = LocalContext.current
    val api = remember { prefs.api() }
    var viewer by remember { mutableStateOf<Viewer?>(null) }
    var entity by remember { mutableStateOf(prefs.lastEntity) }
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var starred by remember { mutableStateOf(prefs.starred) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // Starring a project turns on alerts, so ask for notification permission the first time.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedPermission by remember { mutableStateOf(false) }
    LaunchedEffect(starred) {
        if (starred.isNotEmpty() && prefs.alertsEnabled && !askedPermission && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedPermission = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (api == null) { onAuthError(); return@LaunchedEffect }
        try {
            val v = api.viewer()
            viewer = v
            if (entity == null || entity !in v.entities) entity = v.entity
        } catch (e: ApiException) {
            if (e.isAuthError) onAuthError() else error = e.message
        }
    }

    LaunchedEffect(entity, reload) {
        val e = entity ?: return@LaunchedEffect
        if (api == null) return@LaunchedEffect
        prefs.lastEntity = e
        if (!refreshing) projects = null // pull-to-refresh keeps the list on screen
        error = null
        try {
            projects = api.projects(e)
        } catch (ex: ApiException) {
            error = ex.message
        }
        refreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { menuOpen = true }, enabled = (viewer?.entities?.size ?: 0) > 1) {
                            Text(entity ?: "Projects", style = MaterialTheme.typography.titleLarge)
                            if ((viewer?.entities?.size ?: 0) > 1) Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            viewer?.entities?.forEach { e ->
                                DropdownMenuItem(
                                    text = { Text(if (e == viewer?.entity) "$e (personal)" else e) },
                                    onClick = { entity = e; menuOpen = false },
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter projects") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val list = projects
            when {
                error != null && list == null -> CenteredMessage(error!!, "Retry") { reload++ }
                list == null -> CenteredLoading()
                list.isEmpty() -> CenteredMessage("No projects in $entity yet.")
                else -> {
                    val filtered = list.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    val (pinned, rest) = filtered.partition { it.path in starred }
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { refreshing = true; reload++ },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (pinned.isNotEmpty()) {
                                item { SectionHeader("Starred · alerts on") }
                                items(pinned, key = { "s/" + it.path }) { p ->
                                    ProjectRow(p, true, onOpenProject) {
                                        prefs.toggleStar(p.path); starred = prefs.starred; RunAlertWorker.sync(context)
                                    }
                                }
                                item { HorizontalDivider() }
                            }
                            items(rest, key = { it.path }) { p ->
                                ProjectRow(p, false, onOpenProject) {
                                    prefs.toggleStar(p.path); starred = prefs.starred; RunAlertWorker.sync(context)
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
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProjectRow(p: Project, isStarred: Boolean, onOpen: (Project) -> Unit, onToggleStar: () -> Unit) {
    ListItem(
        headlineContent = { Text(p.name) },
        supportingContent = { Text("Created ${relativeTime(p.createdAt)}") },
        trailingContent = {
            IconButton(onClick = onToggleStar) {
                if (isStarred) Icon(Icons.Filled.Star, "Unstar", tint = MaterialTheme.colorScheme.primary)
                else Icon(Icons.Outlined.StarBorder, "Star")
            }
        },
        modifier = Modifier.clickable { onOpen(p) },
    )
}

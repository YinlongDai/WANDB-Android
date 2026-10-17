package io.github.yinlongdai.runwatch.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.yinlongdai.runwatch.alerts.RunAlertWorker
import io.github.yinlongdai.runwatch.data.Prefs

private fun enc(s: String) = Uri.encode(s)

private fun NavHostController.goToRun(entity: String, project: String, run: String) =
    navigate("run/${enc(entity)}/${enc(project)}/${enc(run)}")

@Composable
fun RunWatchNav(pendingRunPath: String?, onPendingConsumed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val nav = rememberNavController()
    val start = if (prefs.apiKey == null) "login" else "projects"

    // Deep link from a run alert notification.
    LaunchedEffect(pendingRunPath) {
        val parts = pendingRunPath?.split('/') ?: return@LaunchedEffect
        if (parts.size == 3 && prefs.apiKey != null) {
            nav.navigate("runs/${enc(parts[0])}/${enc(parts[1])}")
            nav.goToRun(parts[0], parts[1], parts[2])
        }
        onPendingConsumed()
    }

    NavHost(navController = nav, startDestination = start) {
        composable("login") {
            LoginScreen(prefs = prefs, onSignedIn = {
                RunAlertWorker.sync(context)
                nav.navigate("projects") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("projects") {
            ProjectsScreen(
                prefs = prefs,
                onOpenProject = { p -> nav.navigate("runs/${enc(p.entity)}/${enc(p.name)}") },
                onOpenSettings = { nav.navigate("settings") },
                onAuthError = { nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } } },
            )
        }
        composable("runs/{entity}/{project}") { entry ->
            val entity = entry.arguments?.getString("entity").orEmpty()
            val project = entry.arguments?.getString("project").orEmpty()
            RunsScreen(
                prefs = prefs,
                entity = entity,
                project = project,
                onOpenRun = { run -> nav.goToRun(entity, project, run.name) },
                onCompare = { names -> nav.navigate("compare/${enc(entity)}/${enc(project)}/${enc(names.joinToString(","))}") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("compare/{entity}/{project}/{runs}") { entry ->
            val entity = entry.arguments?.getString("entity").orEmpty()
            val project = entry.arguments?.getString("project").orEmpty()
            val runs = entry.arguments?.getString("runs").orEmpty()
            CompareScreen(
                prefs = prefs,
                entity = entity,
                project = project,
                runNames = runs.split(',').filter { it.isNotEmpty() },
                onBack = { nav.popBackStack() },
                onExpandMetric = { key -> nav.navigate("chart/${enc(entity)}/${enc(project)}/${enc(runs)}/${enc(key)}") },
            )
        }
        // {runs} is one run name, or several comma-separated (from Compare).
        composable("chart/{entity}/{project}/{runs}/{metric}") { entry ->
            ChartScreen(
                prefs = prefs,
                entity = entry.arguments?.getString("entity").orEmpty(),
                project = entry.arguments?.getString("project").orEmpty(),
                runNames = entry.arguments?.getString("runs").orEmpty().split(',').filter { it.isNotEmpty() },
                metric = entry.arguments?.getString("metric").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("run/{entity}/{project}/{run}") { entry ->
            val entity = entry.arguments?.getString("entity").orEmpty()
            val project = entry.arguments?.getString("project").orEmpty()
            val run = entry.arguments?.getString("run").orEmpty()
            RunDetailScreen(
                prefs = prefs,
                entity = entity,
                project = project,
                runName = run,
                onBack = { nav.popBackStack() },
                onExpandMetric = { key -> nav.navigate("chart/${enc(entity)}/${enc(project)}/${enc(run)}/${enc(key)}") },
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onBack = { nav.popBackStack() },
                onSignedOut = {
                    RunAlertWorker.sync(context)
                    nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
    }
}

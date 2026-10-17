package io.github.jiecaoboy.runwatch.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.jiecaoboy.runwatch.alerts.RunAlertWorker
import io.github.jiecaoboy.runwatch.data.Prefs

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
                onBack = { nav.popBackStack() },
            )
        }
        composable("run/{entity}/{project}/{run}") { entry ->
            RunDetailScreen(
                prefs = prefs,
                entity = entry.arguments?.getString("entity").orEmpty(),
                project = entry.arguments?.getString("project").orEmpty(),
                runName = entry.arguments?.getString("run").orEmpty(),
                onBack = { nav.popBackStack() },
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

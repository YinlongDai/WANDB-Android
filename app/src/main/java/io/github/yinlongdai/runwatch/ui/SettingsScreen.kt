package io.github.yinlongdai.runwatch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.yinlongdai.runwatch.BuildConfig
import io.github.yinlongdai.runwatch.alerts.RunAlertWorker
import io.github.yinlongdai.runwatch.data.Prefs
import io.github.yinlongdai.runwatch.update.Updater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    var alerts by remember { mutableStateOf(prefs.alertsEnabled) }
    var notifyFinish by remember { mutableStateOf(prefs.notifyOnFinish) }
    var searchAtBottom by remember { mutableStateOf(prefs.searchAtBottom) }
    fun hasPermission() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var permitted by remember { mutableStateOf(hasPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Failure alerts") },
                supportingContent = {
                    Text("Checks starred projects about every 15 minutes and notifies you when a running run crashes or fails.")
                },
                trailingContent = {
                    Switch(checked = alerts, onCheckedChange = {
                        alerts = it
                        prefs.alertsEnabled = it
                        RunAlertWorker.sync(context)
                        if (it && !hasPermission() && Build.VERSION.SDK_INT >= 33) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                },
            )
            ListItem(
                headlineContent = { Text("Also notify when runs finish") },
                trailingContent = {
                    Switch(checked = notifyFinish, enabled = alerts, onCheckedChange = {
                        notifyFinish = it
                        prefs.notifyOnFinish = it
                    })
                },
            )
            if (alerts && !permitted) {
                ListItem(
                    headlineContent = { Text("Notifications are blocked") },
                    supportingContent = { Text("Allow notifications so alerts can reach you.") },
                    trailingContent = {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text("Allow") }
                    },
                )
            }
            ListItem(
                headlineContent = { Text("Starred projects") },
                supportingContent = {
                    Text(prefs.starred.sorted().joinToString("\n").ifEmpty { "None yet. Tap the star on a project to watch it." })
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Search bar at bottom") },
                supportingContent = { Text("Float search boxes above the bottom of the screen, within thumb reach. Off keeps them above the list.") },
                trailingContent = {
                    Switch(checked = searchAtBottom, onCheckedChange = {
                        searchAtBottom = it
                        prefs.searchAtBottom = it
                    })
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("API host") },
                supportingContent = { Text(prefs.baseUrl) },
            )
            ListItem(
                headlineContent = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Removes your API key from this device.") },
                modifier = Modifier.padding(0.dp),
                trailingContent = {
                    TextButton(onClick = { prefs.signOut(); onSignedOut() }) { Text("Sign out") }
                },
            )
            HorizontalDivider()
            UpdateSettingsRow()
            Text(
                "RunWatch is an unofficial, open-source client and is not affiliated with Weights & Biases or CoreWeave.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun UpdateSettingsRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = Updater.state
    ListItem(
        headlineContent = { Text("Version ${BuildConfig.VERSION_NAME}") },
        supportingContent = {
            Text(
                when {
                    !Updater.enabled -> "Development build: updates are installed from Android Studio or CI."
                    state is Updater.State.Checking -> "Checking for updates…"
                    state is Updater.State.UpToDate -> "You have the latest version."
                    state is Updater.State.Available -> "Version ${state.info.versionName} is available."
                    state is Updater.State.Downloading -> "Downloading ${(state.progress * 100).toInt()}%…"
                    state is Updater.State.Failed -> state.message
                    else -> "Updates come from this app's GitHub Releases."
                }
            )
        },
        trailingContent = {
            if (Updater.enabled) {
                if (state is Updater.State.Available) {
                    TextButton(onClick = { scope.launch { Updater.downloadAndInstall(context, state.info) } }) { Text("Update") }
                } else {
                    TextButton(
                        enabled = state !is Updater.State.Checking && state !is Updater.State.Downloading,
                        onClick = { scope.launch { Updater.check(force = true) } },
                    ) { Text("Check") }
                }
            }
        },
    )
}

package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yinlongdai.runwatch.update.UpdateInfo
import io.github.yinlongdai.runwatch.update.Updater
import kotlinx.coroutines.launch

/** "Update available" card; checks GitHub Releases once per app launch. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { Updater.check() }
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    when (val s = Updater.state) {
        is Updater.State.Available ->
            if (dismissed != s.info.versionName) UpdateCard(s.info, null, modifier) { dismissed = s.info.versionName }
        is Updater.State.Downloading -> UpdateCard(s.info, s.progress, modifier) { }
        else -> Unit
    }
}

@Composable
private fun UpdateCard(info: UpdateInfo, progress: Float?, modifier: Modifier, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Update available: ${info.versionName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (info.notes.isNotEmpty()) {
                        Text(
                            info.notes.lineSequence().first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp, end = 8.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Later") }
                    Button(
                        onClick = { scope.launch { Updater.downloadAndInstall(context, info) } },
                        modifier = Modifier.padding(start = 4.dp),
                    ) { Text("Update") }
                }
            }
        }
    }
}

package io.github.jiecaoboy.runwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jiecaoboy.runwatch.data.ApiException
import io.github.jiecaoboy.runwatch.data.Prefs
import io.github.jiecaoboy.runwatch.data.WandbApi
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(prefs: Prefs, onSignedIn: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(prefs.baseUrl) }
    var showHost by remember { mutableStateOf(prefs.baseUrl != WandbApi.DEFAULT_BASE_URL) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text("RunWatch", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Monitor your Weights & Biases runs from your phone. Unofficial client.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim(); error = null },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uri.openUri("https://wandb.ai/authorize") }) {
                Text("Get your API key at wandb.ai/authorize")
            }
            if (showHost) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim(); error = null },
                    label = { Text("API host") },
                    supportingText = { Text("Change only for a dedicated or self-hosted W&B server") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { showHost = true }) { Text("Use a different server…") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val baseUrl = host.ifBlank { WandbApi.DEFAULT_BASE_URL }
                            val viewer = WandbApi(baseUrl, key).viewer()
                            prefs.baseUrl = baseUrl
                            prefs.apiKey = key
                            prefs.lastEntity = viewer.entity
                            onSignedIn()
                        } catch (e: ApiException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = key.length >= 20 && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Sign in")
            }
            Text(
                "Your key is stored only on this device and sent only to the API host above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

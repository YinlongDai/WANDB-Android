package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Runs [block] while the screen is visible. If it returns true, runs again
 * after [intervalMs]; returning false stops polling until the screen is
 * shown again or [keys] change.
 */
@Composable
fun PollingEffect(vararg keys: Any?, intervalMs: Long, block: suspend () -> Boolean) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    LaunchedEffect(owner, *keys) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (!current()) break
                delay(intervalMs)
            }
        }
    }
}

@Composable
fun StateBadge(state: String, modifier: Modifier = Modifier) {
    val style = stateStyle(state)
    Row(
        modifier = modifier
            .background(style.color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).background(style.color, CircleShape))
        Text(style.label, style = MaterialTheme.typography.labelMedium, color = style.color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun CenteredMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
        }
    }
}

/** W&B timestamps are UTC without an offset, e.g. "2026-10-17T03:12:44". */
fun parseWandbTime(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(s.removeSuffix("Z")).toInstant(ZoneOffset.UTC) }.getOrNull()
}

fun relativeTime(s: String?, now: Instant = Instant.now()): String {
    val t = parseWandbTime(s) ?: return ""
    val secs = Duration.between(t, now).seconds
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60}m ago"
        secs < 86_400 -> "${secs / 3600}h ago"
        secs < 30 * 86_400 -> "${secs / 86_400}d ago"
        else -> "${secs / (30 * 86_400)}mo ago"
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds < 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

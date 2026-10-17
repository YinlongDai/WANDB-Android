package io.github.yinlongdai.runwatch.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Gold = Color(0xFFFFCC33)
private val DarkGold = Color(0xFF7A5C00)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF261A00),
    secondary = Color(0xFF9AA3B5),
    background = Color(0xFF15171C),
    surface = Color(0xFF15171C),
    surfaceVariant = Color(0xFF262A33),
)

private val LightColors = lightColorScheme(
    primary = DarkGold,
    secondary = Color(0xFF515A6B),
)

@Composable
fun RunWatchTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(LocalContext.current)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Distinct line colors that read on both light and dark backgrounds. */
val ChartPalette = listOf(
    Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFFEF4444),
    Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF84CC16),
)

data class StateStyle(val label: String, val color: Color)

@Composable
@ReadOnlyComposable
fun stateStyle(state: String): StateStyle = when (state) {
    "running" -> StateStyle("Running", Color(0xFF10B981))
    "pending" -> StateStyle("Pending", Color(0xFF06B6D4))
    "finished" -> StateStyle("Finished", Color(0xFF3B82F6))
    "crashed" -> StateStyle("Crashed", Color(0xFFEF4444))
    "failed" -> StateStyle("Failed", Color(0xFFEF4444))
    "killed" -> StateStyle("Stopped", MaterialTheme.colorScheme.outline)
    "preempted", "preempting" -> StateStyle("Preempted", Color(0xFFF59E0B))
    else -> StateStyle(state.replaceFirstChar { it.uppercase() }, MaterialTheme.colorScheme.outline)
}

/** ANSI 16-color palette for console logs; null means default text color. */
val AnsiColors = listOf(
    Color(0xFF4B5563), Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFFEAB308),
    Color(0xFF3B82F6), Color(0xFFD946EF), Color(0xFF06B6D4), Color(0xFFD1D5DB),
    Color(0xFF9CA3AF), Color(0xFFF87171), Color(0xFF4ADE80), Color(0xFFFDE047),
    Color(0xFF60A5FA), Color(0xFFF0ABFC), Color(0xFF67E8F9), Color(0xFFF9FAFB),
)

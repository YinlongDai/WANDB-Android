@file:OptIn(ExperimentalHazeApi::class)

package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.yinlongdai.runwatch.data.Prefs

/*
 * "Liquid glass" bars: the content scrolls underneath and shows through,
 * blurred. Kept cheap on purpose:
 * - only the bars (top bar, bottom tabs, floating search) are glass; chart
 *   panels are drawn normally;
 * - the blur runs on a downscaled copy of the content (HazeInputScale.Auto)
 *   with no noise texture;
 * - below Android 12, or with "Glass effects" off in Settings, there is no
 *   blur at all, only a translucent tint.
 */

/** Per-screen glass state; pass it to [glassSource] on the scrolling content and to the bars. */
@Composable
fun rememberGlass(prefs: Prefs): HazeState {
    val enabled = remember { prefs.glassEnabled }
    return rememberHazeState(blurEnabled = enabled && HazeDefaults.blurEnabled())
}

/** Marks the content that glass bars blur. Must not be an ancestor of a glass bar. */
fun Modifier.glassSource(state: HazeState?): Modifier =
    if (state == null) this else hazeSource(state)

@Composable
@ReadOnlyComposable
private fun glassStyle(): HazeStyle {
    val surface = MaterialTheme.colorScheme.surface
    return HazeStyle(
        backgroundColor = surface,
        tints = listOf(HazeTint(surface.copy(alpha = 0.62f))),
        blurRadius = 24.dp,
        noiseFactor = 0f,
        // No blur: a nearly opaque tint keeps text over busy charts readable.
        fallbackTint = HazeTint(surface.copy(alpha = 0.94f)),
    )
}

/** Thin bright edge that sells the "glass" look; brighter at the top like a lit rim. */
@Composable
fun glassRim(): BorderStroke {
    val dark = isSystemInDarkTheme()
    val top = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.85f)
    val bottom = if (dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.06f)
    return BorderStroke(1.dp, Brush.verticalGradient(listOf(top, bottom)))
}

/** Glass background for a shaped element such as the floating search bar. */
@Composable
fun Modifier.glassPanel(state: HazeState, shape: Shape): Modifier =
    this
        .clip(shape)
        .hazeEffect(state, glassStyle()) { inputScale = HazeInputScale.Auto }
        .border(glassRim(), shape)

/**
 * Top app bar whose blur feathers out: full strength behind the title, fading
 * to nothing over a short strip below the bar, so content slides under it
 * without a hard edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    state: HazeState,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .hazeEffect(state, glassStyle()) {
                inputScale = HazeInputScale.Auto
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f,
                    preferPerformance = true,
                )
            },
    ) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            // The feather strip: blur fades out across this extra space below the bar.
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}

/** Bottom navigation bar on glass, with a hairline highlight along its top edge. */
@Composable
fun GlassNavigationBar(state: HazeState, content: @Composable RowScope.() -> Unit) {
    val rim = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    NavigationBar(
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        modifier = Modifier
            .hazeEffect(state, glassStyle()) { inputScale = HazeInputScale.Auto }
            .drawBehind { drawLine(rim, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) },
        content = content,
    )
}

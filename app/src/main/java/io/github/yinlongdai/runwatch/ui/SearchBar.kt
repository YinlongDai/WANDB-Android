package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

/** Height of the floating bar plus its margins, reserved at the end of lists so the last item isn't hidden. */
val FloatingSearchInset = 84.dp

/**
 * Rounded, borderless search field. When [floating] it hovers over the list:
 * on glass if [glass] is given, otherwise as a raised, outlined pill.
 */
@Composable
fun PillSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
    glass: HazeState? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val onGlass = floating && glass != null
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (onGlass) Modifier.glassPanel(glass!!, CircleShape) else Modifier),
        shape = CircleShape,
        color = when {
            onGlass -> Color.Transparent
            floating -> colors.surfaceContainerHighest
            else -> colors.surfaceContainerHigh
        },
        shadowElevation = if (floating && !onGlass) 6.dp else 0.dp,
        tonalElevation = if (floating && !onGlass) 3.dp else 0.dp,
        // Shadows barely show on dark backgrounds; the outline keeps the floating bar distinct from cards.
        border = if (floating && !onGlass) BorderStroke(1.dp, colors.outlineVariant) else null,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.onSurfaceVariant)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, "Clear search") }
            }
        }
    }
}

/**
 * Lays out a list with a search bar either floating over its bottom edge
 * (above the keyboard when it is open) or fixed above it, per the user's
 * "Search bar at bottom" setting.
 *
 * [barPadding] is the Scaffold padding for the screen's bars. The list draws
 * underneath them (so glass bars have something to blur) and [content]
 * receives the padding it should apply as its contentPadding. The list is the
 * [glass] source; the search bar sits beside it, never inside.
 */
@Composable
fun SearchLayout(
    atBottom: Boolean,
    search: @Composable (modifier: Modifier, floating: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    barPadding: PaddingValues = PaddingValues(0.dp),
    glass: HazeState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val top = barPadding.calculateTopPadding()
    val bottom = barPadding.calculateBottomPadding()
    if (atBottom) {
        Box(modifier.fillMaxSize().consumeWindowInsets(barPadding)) {
            Box(Modifier.fillMaxSize().glassSource(glass)) {
                content(PaddingValues(top = top, bottom = bottom + FloatingSearchInset))
            }
            search(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottom)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                true,
            )
        }
    } else {
        Column(modifier.fillMaxSize().padding(top = top)) {
            search(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), false)
            Box(Modifier.weight(1f).glassSource(glass)) { content(PaddingValues(bottom = bottom)) }
        }
    }
}

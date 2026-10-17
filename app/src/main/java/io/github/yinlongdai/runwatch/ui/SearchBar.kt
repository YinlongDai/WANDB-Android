package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Height of the floating bar plus its margins, reserved at the end of lists so the last item isn't hidden. */
val FloatingSearchInset = 84.dp

/**
 * Rounded, borderless search field. [floating] adds a shadow so it reads as a
 * layer above the content it hovers over.
 */
@Composable
fun PillSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    floating: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        color = if (floating) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        shadowElevation = if (floating) 6.dp else 0.dp,
        tonalElevation = if (floating) 3.dp else 0.dp,
        // Shadows barely show on dark backgrounds; the outline keeps the floating bar distinct from cards.
        border = if (floating) BorderStroke(1.dp, colors.outlineVariant) else null,
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
 * "Search bar at bottom" setting. [content] receives the padding its list
 * should add so nothing ends up under the floating bar.
 */
@Composable
fun SearchLayout(
    atBottom: Boolean,
    search: @Composable (modifier: Modifier, floating: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (atBottom) {
        Box(modifier.fillMaxSize()) {
            content(PaddingValues(bottom = FloatingSearchInset))
            search(
                Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                true,
            )
        }
    } else {
        Column(modifier.fillMaxSize()) {
            search(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), false)
            Box(Modifier.weight(1f)) { content(PaddingValues(0.dp)) }
        }
    }
}

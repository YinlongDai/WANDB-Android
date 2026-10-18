package io.github.yinlongdai.runwatch.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Long-press a row to select it (or deselect it, if it was selected), then keep
 * the finger down and drag: every row between the first and the current one
 * follows, like picking photos in a gallery. Dragging near the top or bottom
 * edge scrolls the list. Rows take part when their LazyColumn key is in [keys].
 *
 * The rows themselves should only handle taps: this modifier watches touches
 * before they reach the rows and swallows the rest of a long-press, so lifting
 * the finger never counts as a tap as well.
 */
@Composable
fun dragToSelect(
    state: LazyListState,
    keys: List<String>,
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val latestKeys by rememberUpdatedState(keys)
    val latestSelected by rememberUpdatedState(selected)
    val latestOnChange by rememberUpdatedState(onSelectedChange)

    return Modifier.pointerInput(state) {
        val edge = 56.dp.toPx()
        val maxSpeed = 18.dp.toPx()
        var autoScroll by mutableFloatStateOf(0f)
        var pointerY = 0f
        // Set during a drag: re-applies the selection for whatever row is under the finger.
        var track: (() -> Unit)? = null

        // The key of the row at a y position within the list.
        fun keyAt(y: Float): String? {
            val info = state.layoutInfo
            val offset = y + info.viewportStartOffset
            val item = info.visibleItemsInfo.firstOrNull { offset >= it.offset && offset < it.offset + it.size }
            return (item?.key as? String)?.takeIf { it in latestKeys }
        }

        coroutineScope {
            launch {
                snapshotFlow { autoScroll }.collectLatest { speed ->
                    if (speed == 0f) return@collectLatest
                    while (true) {
                        state.scrollBy(speed)
                        track?.invoke()
                        withFrameNanos { }
                    }
                }
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startKey = keyAt(down.position.y) ?: return@awaitEachGesture

                // Wait out the long-press; a lift, a second finger or movement (a scroll) ends it.
                // Null when the time runs out with the finger still down and still.
                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                    } while (
                        event.changes.size == 1 && change != null && change.pressed && !change.isConsumed &&
                        (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
                    )
                }
                if (ended != null) return@awaitEachGesture

                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val initial = latestSelected
                var last = startKey
                fun applyTo(key: String) {
                    val next = dragSelection(latestKeys, initial, startKey, key) ?: return
                    latestOnChange(next)
                    if (key != last) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    last = key
                }
                applyTo(startKey)
                pointerY = down.position.y
                track = { keyAt(pointerY)?.let(::applyTo) }

                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // Keep the rows from seeing a tap and the list from scrolling under the finger.
                        event.changes.forEach { it.consume() }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        pointerY = change.position.y
                        val top = state.layoutInfo.beforeContentPadding + edge
                        val bottom = size.height - state.layoutInfo.afterContentPadding - edge
                        autoScroll = when {
                            pointerY < top -> -maxSpeed * ((top - pointerY) / edge).coerceAtMost(1f)
                            pointerY > bottom -> maxSpeed * ((pointerY - bottom) / edge).coerceAtMost(1f)
                            else -> 0f
                        }
                        track?.invoke()
                    }
                } finally {
                    autoScroll = 0f
                    track = null
                }
            }
        }
    }
}

/**
 * The selection while dragging from [start] to [current] in [order]: every key
 * between them takes the opposite of [start]'s state in [initial], the rest
 * keep theirs. Newly selected keys are added from [start] towards [current].
 * Null if either key is not in [order].
 */
fun dragSelection(order: List<String>, initial: List<String>, start: String, current: String): List<String>? {
    val a = order.indexOf(start)
    val b = order.indexOf(current)
    if (a < 0 || b < 0) return null
    val range = if (a <= b) order.subList(a, b + 1) else order.subList(b, a + 1).asReversed()
    return if (start !in initial) initial + range.filter { it !in initial } else initial - range.toSet()
}

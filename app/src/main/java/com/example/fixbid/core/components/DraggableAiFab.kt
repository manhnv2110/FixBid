package com.example.fixbid.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating AI assistant button anchored as a draggable overlay inside the
 * provided [BoxScope]. The user can grab the button and drop it anywhere
 * inside the area; the position survives configuration changes via
 * [rememberSaveable] and stays clamped to the visible bounds when the
 * parent resizes.
 *
 * Use this from a parent `Box(modifier = Modifier.fillMaxSize())` instead
 * of the `Scaffold(floatingActionButton = …)` slot so the FAB can travel
 * across the whole content area, not just the bottom-end corner.
 *
 * The [storageKey] lets multiple screens (customer, worker) keep their
 * own remembered position without colliding.
 */
@Composable
fun BoxScope.DraggableAiFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    storageKey: String = "ai_fab_default"
) {
    BoxWithConstraints(
        modifier = Modifier
            .matchParentSize()
            .padding(end = 16.dp, bottom = 16.dp)
    ) {
        val density = LocalDensity.current
        val viewConfig = LocalViewConfiguration.current
        val fabSizeDp = 56.dp
        val fabSizePx = with(density) { fabSizeDp.toPx() }

        val maxXPx = with(density) { maxWidth.toPx() } - fabSizePx
        val maxYPx = with(density) { maxHeight.toPx() } - fabSizePx
        // Touch slop in pixels — same threshold the platform uses for any
        // other drag detection. Using LocalViewConfiguration keeps mouse
        // events on the emulator behaving the same as touch on a phone.
        val touchSlopPx = viewConfig.touchSlop

        // Default anchor: bottom-right corner. Persist the user's placement
        // across recompositions and process death.
        var offsetX by rememberSaveable(storageKey) { mutableFloatStateOf(Float.NaN) }
        var offsetY by rememberSaveable(storageKey) { mutableFloatStateOf(Float.NaN) }

        LaunchedEffect(maxXPx, maxYPx) {
            if (maxXPx <= 0f || maxYPx <= 0f) return@LaunchedEffect
            // First placement → anchor to bottom-right.
            if (offsetX.isNaN() || offsetY.isNaN()) {
                offsetX = maxXPx
                offsetY = maxYPx
            } else {
                // Re-clamp on resize / orientation change so the FAB stays
                // visible if the available area shrinks.
                offsetX = offsetX.coerceIn(0f, maxXPx)
                offsetY = offsetY.coerceIn(0f, maxYPx)
            }
        }

        val safeOffsetX = if (offsetX.isNaN()) maxXPx else offsetX
        val safeOffsetY = if (offsetY.isNaN()) maxYPx else offsetY

        Surface(
            modifier = modifier
                .offset { IntOffset(safeOffsetX.roundToInt(), safeOffsetY.roundToInt()) }
                .size(fabSizeDp)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                // ONE pointer-input block handles both drag and tap so neither
                // gesture detector races to consume the event before the
                // other. We follow the same flow `Modifier.draggable` uses
                // internally: wait for ACTION_DOWN, accumulate movement
                // until it crosses the platform touch slop — at that point
                // it's a drag, otherwise on ACTION_UP it's a tap.
                .pointerInput(maxXPx, maxYPx, touchSlopPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var totalDrag = 0f

                        while (true) {
                            // Use the Initial pass so we see the events
                            // before any descendants (none here, but it
                            // makes the behaviour deterministic on mouse).
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break

                            // The pointer was lifted — decide whether this
                            // was a tap or the end of a drag.
                            if (!change.pressed) {
                                if (!dragging) onClick()
                                break
                            }

                            val delta = change.positionChange()

                            if (!dragging) {
                                totalDrag += abs(delta.x) + abs(delta.y)
                                if (totalDrag > touchSlopPx) {
                                    dragging = true
                                }
                            }

                            if (dragging) {
                                // Consume the change so any parent scroll
                                // container doesn't try to scroll the page
                                // while we're moving the FAB.
                                change.consume()
                                // Read the latest offset directly from the
                                // mutable state so each pointer event moves
                                // relative to where the FAB currently is,
                                // not where it was when the gesture started.
                                val currentX = if (offsetX.isNaN()) maxXPx else offsetX
                                val currentY = if (offsetY.isNaN()) maxYPx else offsetY
                                offsetX = (currentX + delta.x).coerceIn(0f, maxXPx)
                                offsetY = (currentY + delta.y).coerceIn(0f, maxYPx)
                            }
                        }
                    }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Trợ lý AI",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

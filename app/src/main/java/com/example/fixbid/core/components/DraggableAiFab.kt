package com.example.fixbid.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
        val fabSizeDp = 56.dp
        val fabSizePx = with(density) { fabSizeDp.toPx() }

        val maxXPx = with(density) { maxWidth.toPx() } - fabSizePx
        val maxYPx = with(density) { maxHeight.toPx() } - fabSizePx

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

        // Track whether the most recent gesture was a real drag so we don't
        // fire the click handler when the user releases after moving it.
        var dragged by remember { mutableStateOf(false) }

        Surface(
            modifier = modifier
                .offset { IntOffset(safeOffsetX.roundToInt(), safeOffsetY.roundToInt()) }
                .size(fabSizeDp)
                .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                .pointerInput(maxXPx, maxYPx) {
                    detectDragGestures(
                        onDragStart = { dragged = false },
                        onDragEnd = { /* keep dragged=true so the synthetic tap is suppressed */ },
                        onDragCancel = { dragged = false }
                    ) { change, dragAmount ->
                        change.consume()
                        dragged = true
                        offsetX = (safeOffsetX + dragAmount.x).coerceIn(0f, maxXPx)
                        offsetY = (safeOffsetY + dragAmount.y).coerceIn(0f, maxYPx)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (!dragged) onClick() else dragged = false }
                    )
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

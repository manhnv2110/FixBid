package com.example.fixbid.core.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared shimmer-style skeleton loaders. Used in place of full-screen spinners
 * so the user gets an immediate sense of layout / progress instead of an
 * indefinite blank with a circle. Faster perceived performance, more polished
 * feel — matches what users expect from production-grade apps.
 *
 * The shimmer is a 3-stop linear gradient that translates horizontally over
 * 1.2s on loop. We compute the gradient endpoints from a shared infinite
 * transition so every skeleton on the same screen shimmers in sync — looks
 * intentional rather than chaotic.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton-shimmer-translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.7f),
            highlight.copy(alpha = 0.95f),
            base.copy(alpha = 0.7f)
        ),
        start = Offset(translateAnim - 400f, 0f),
        end = Offset(translateAnim, 0f)
    )
}

/** A single rectangular shimmer block — the building block for higher-level skeletons. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    cornerRadius: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush())
    )
}

/** Circular shimmer block — for avatars / icon placeholders. */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

/**
 * Card-shaped skeleton matching the BookingCard / WorkerJobCard footprint.
 * One avatar placeholder + 3 lines of varying width — enough to suggest the
 * general layout while saying "loading" without blocking the user.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonCircle(size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    SkeletonBlock(modifier = Modifier.width(140.dp), height = 14.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonBlock(modifier = Modifier.width(80.dp), height = 11.dp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SkeletonBlock(height = 12.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBlock(modifier = Modifier.width(220.dp), height = 12.dp)
        }
    }
}

/**
 * List skeleton — typically used as the body of a screen while the real
 * data loads. The default 3 cards is enough to give the impression of a
 * populated list without wasting energy painting offscreen blocks.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    cardCount: Int = 3
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(cardCount) { idx ->
            SkeletonCard()
            if (idx != cardCount - 1) Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

package com.example.fixbid.core.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fixbid.ui.theme.StatusColorsTheme

/**
 * Read-only star rating display, supports half stars.
 */
@Composable
fun StarRatingBar(
    rating: Double,
    modifier: Modifier = Modifier,
    starSize: Dp = 16.dp,
    color: Color = StatusColorsTheme.current.rating
) {
    Row(modifier = modifier) {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Filled.Star
                rating >= i - 0.5 -> Icons.Filled.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (rating >= i - 0.5) color else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(starSize)
            )
        }
    }
}

/**
 * Interactive star rating selector for capturing a 1–5 rating from the user.
 */
@Composable
fun StarRatingInput(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 44.dp,
    color: Color = StatusColorsTheme.current.rating
) {
    Row(modifier = modifier) {
        for (i in 1..5) {
            val interactionSource = remember { MutableInteractionSource() }
            Icon(
                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "$i sao",
                tint = if (i <= rating) color else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(starSize)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onRatingChange(i) }
            )
        }
    }
}

package com.example.fixbid.presentation.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.domain.model.AiSuggestion
import com.example.fixbid.domain.model.AiSuggestionIcon
import com.example.fixbid.domain.model.AiSuggestionKind
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * Horizontal strip of AI suggestion chips. Each chip is keyed by [AiSuggestion.id]
 * so adding/removing chips animates cleanly. The strip is hidden entirely when
 * [suggestions] is empty so it doesn't leave dead space on the screen.
 *
 * UX:
 *  - Header: small ✨ icon + "Trợ lý AI gợi ý" label so users immediately
 *    associate the row with the chatbot vibe.
 *  - Chips use the primary color tinted at 10% so they stand out from regular
 *    Material chips without overpowering the surrounding cards.
 *  - Each chip dispatches via [onSuggestionClick]; callers route by
 *    [AiSuggestion.kind] (open chat with prefill / run inline / navigate).
 */
@Composable
fun AiSuggestionStrip(
    suggestions: List<AiSuggestion>,
    modifier: Modifier = Modifier,
    onSuggestionClick: (AiSuggestion) -> Unit
) {
    if (suggestions.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        AiHeader(label = "Trợ lý AI gợi ý")
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(suggestions, key = { it.id }) { suggestion ->
                AiSuggestionChip(
                    suggestion = suggestion,
                    onClick = { onSuggestionClick(suggestion) }
                )
            }
        }
    }
}

@Composable
private fun AiHeader(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AiSparkleBadge(size = 22.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AiSparkleBadge(size: androidx.compose.ui.unit.Dp) {
    // Subtle pulse so the badge reads as "alive" without being annoying.
    val infinite = rememberInfiniteTransition(label = "ai-pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai-pulse-alpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun AiSuggestionChip(suggestion: AiSuggestion, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = mapIcon(suggestion.iconKey),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = suggestion.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            suggestion.helper?.takeIf { it.isNotBlank() }?.let { helper ->
                Text(
                    text = helper,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact "Hỏi AI" pill button. Used on screens where a full suggestion strip
 * is too busy (e.g. inside a card header) but a one-tap shortcut still adds
 * value. Tapping prefills the chatbot with [prompt].
 */
@Composable
fun AiAssistShortcut(
    label: String = "Hỏi AI",
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Inline card that renders the result of an [AiSuggestionKind.INLINE_ANALYZE]
 * call. The state machine is tiny:
 *   - `state == Idle` → render nothing.
 *   - `state == Loading` → spinner + "Đang phân tích…" body.
 *   - `state == Result` → markdown answer + "Mở chat để hỏi tiếp" CTA.
 *   - `state == Error` → red-tinted banner with "Thử lại" affordance.
 *
 * The card auto-dismisses on tap of the close button so the screen stays clean
 * after the user has read the answer.
 */
@Composable
fun InlineAiAnalysisCard(
    state: InlineAiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onOpenChat: () -> Unit = {}
) {
    val visible = state !is InlineAiState.Idle
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiSparkleBadge(size = 24.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Trợ lý AI",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (state !is InlineAiState.Loading) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            when (state) {
                is InlineAiState.Idle -> Unit
                is InlineAiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = state.label,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is InlineAiState.Result -> {
                    MarkdownText(
                        markdown = state.markdown,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onOpenChat) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hỏi thêm trong chat", fontSize = 12.sp)
                        }
                        if (state.allowRetry) {
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Phân tích lại", fontSize = 12.sp)
                            }
                        }
                    }
                }
                is InlineAiState.Error -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Thử lại", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Minimal state model for [InlineAiAnalysisCard]. */
sealed interface InlineAiState {
    data object Idle : InlineAiState
    data class Loading(val label: String = "Đang phân tích…") : InlineAiState
    data class Result(val markdown: String, val allowRetry: Boolean = true) : InlineAiState
    data class Error(val message: String) : InlineAiState
}

private fun mapIcon(key: AiSuggestionIcon): ImageVector = when (key) {
    AiSuggestionIcon.Sparkle -> Icons.Filled.AutoAwesome
    AiSuggestionIcon.Question -> Icons.Outlined.HelpOutline
    AiSuggestionIcon.Compare -> Icons.Outlined.Compare
    AiSuggestionIcon.Edit -> Icons.Outlined.Edit
    AiSuggestionIcon.Check -> Icons.Outlined.Check
    AiSuggestionIcon.Insights -> Icons.Outlined.Insights
    AiSuggestionIcon.Warning -> Icons.Outlined.WarningAmber
}

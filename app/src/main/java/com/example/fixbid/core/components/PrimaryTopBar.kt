package com.example.fixbid.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The primary (filled) header used at the top of the main shell tabs — home,
 * dashboard, history, profile. It draws the brand-coloured surface, handles the
 * status-bar inset, and lays out an optional [subtitle] plus [actions] (bells).
 *
 * Back-button detail screens keep using [AppHeader]; this one is specifically
 * for the top-level tabs that previously each hand-rolled their own header.
 *
 * Title alignment:
 *   - Default ([centerTitle] = false): title aligned start, takes remaining
 *     space between leading and actions. Good for screens with a subtitle.
 *   - [centerTitle] = true: uses a 3-slot Box stack (leading | center title
 *     | actions). The title sits true-center regardless of leading/actions
 *     widths — feels more "Material You" tab header. We disable subtitle in
 *     this layout because there's no clean way to vertically stack while
 *     keeping the title centered horizontally without measuring children.
 */
@Composable
fun PrimaryTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    centerTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 2.dp
    ) {
        if (centerTitle) {
            // 3-slot layout — leading + actions float on top of the centered
            // title. We give the title a horizontal padding equal to the
            // status bar / row padding so its text never overlaps the side
            // slots when the title is long (it just truncates instead).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 14.dp)
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Centered title — pinned in the middle regardless of slot
                // widths. Side padding leaves room for typical leading/action
                // icons (max ~120dp combined) without overlap.
                val titleClickModifier = if (onTitleClick != null) {
                    Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable { onTitleClick() }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                } else {
                    Modifier
                }
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = titleClickModifier.padding(horizontal = 56.dp)
                )

                // Leading slot — anchored left.
                if (leading != null) {
                    Box(
                        modifier = Modifier.align(Alignment.CenterStart),
                        contentAlignment = Alignment.Center
                    ) {
                        leading()
                    }
                }

                // Actions slot — anchored right. Wrapped in a Row so the
                // existing trailing-lambda stays compatible (notification
                // bells, chat bells, etc.).
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 14.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) {
                    leading()
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val titleClickModifier = if (onTitleClick != null) {
                        Modifier
                            .fillMaxWidth(0.5f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .clickable { onTitleClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    }
                    Column(
                        modifier = titleClickModifier,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

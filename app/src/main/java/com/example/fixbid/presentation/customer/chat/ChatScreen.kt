package com.example.fixbid.presentation.customer.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.domain.model.Message
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One-to-one chat screen between the customer and the worker.
 *
 * Layout decisions:
 *  - A custom rich header replaces the generic AppHeader so we can show the
 *    counterpart's avatar + an "Online" hint, matching modern chat apps.
 *  - Messages are grouped: consecutive messages from the same sender share
 *    a single tail (only the last bubble in the run gets the tail corner),
 *    and the timestamp + read receipt only appear on the last message.
 *  - A date separator row is inserted whenever the day changes, so users
 *    can scan history without having to read raw timestamps.
 *  - The input bar uses a pill-shaped TextField and a circular send button
 *    that visually disables when the message is empty.
 */
@Composable
fun ChatScreen(
    onBackClick: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Collect one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ChatEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ChatEvent.ScrollToBottom -> {
                    val lastIndex = uiState.messages.lastIndex
                    if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
                }
            }
        }
    }

    // Auto-scroll when message count changes
    LaunchedEffect(uiState.messages.size) {
        val lastIndex = uiState.messages.lastIndex
        if (lastIndex >= 0) {
            coroutineScope.launch { listState.animateScrollToItem(lastIndex) }
        }
    }

    // Keep the latest messages in view when the keyboard opens. With adjustResize
    // the message list shrinks as the IME slides in; without this the bottom
    // messages would be clipped behind the input bar. We watch the IME bottom
    // inset and, once it settles open, pin the list to the most recent message.
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeVisible = imeBottom > 0
    LaunchedEffect(isImeVisible, imeBottom) {
        if (isImeVisible) {
            val lastIndex = uiState.messages.lastIndex
            if (lastIndex >= 0) listState.scrollToItem(lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChatHeader(
            workerName = viewModel.workerName,
            onBackClick = onBackClick
        )

        // Messages area
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.messages.isEmpty() -> {
                    EmptyChatPlaceholder(workerName = viewModel.workerName)
                }
                else -> {
                    val groupedItems = remember(uiState.messages) {
                        buildChatItems(uiState.messages)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            count = groupedItems.size,
                            key = { idx ->
                                when (val item = groupedItems[idx]) {
                                    is ChatItem.DayHeader -> "day_${item.dayKey}"
                                    is ChatItem.MessageItem ->
                                        item.message.id.ifBlank { "msg_${item.message.createdAt}_$idx" }
                                }
                            }
                        ) { idx ->
                            when (val item = groupedItems[idx]) {
                                is ChatItem.DayHeader -> DayDividerRow(label = item.label)
                                is ChatItem.MessageItem -> MessageBubble(
                                    message = item.message,
                                    isMine = item.message.senderId == uiState.currentUserId,
                                    showTail = item.isLastInRun,
                                    showMeta = item.isLastInRun,
                                    extraTopSpacing = item.isFirstInRun
                                )
                            }
                        }
                    }
                }
            }

            // Error banner
            uiState.errorMessage?.let { err ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        ChatInputBar(
            text = uiState.inputText,
            onTextChange = viewModel::onInputChange,
            onSend = viewModel::sendMessage,
            isSending = uiState.isSending
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(
    workerName: String,
    onBackClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Avatar with a small online dot. Server avatar isn't surfaced
            // in ChatViewModel today, so we render initials in a soft fill.
            val initial = workerName.trim().firstOrNull()?.uppercase() ?: "?"
            Box(modifier = Modifier.size(40.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(com.example.fixbid.ui.theme.StatusColorsTheme.current.positive)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workerName,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Đang hoạt động",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            IconButton(onClick = { /* reserved for future actions */ }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Tuỳ chọn",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// ─── Day separator + grouping helpers ────────────────────────────────────────

private sealed interface ChatItem {
    data class DayHeader(val dayKey: String, val label: String) : ChatItem
    data class MessageItem(
        val message: Message,
        val isFirstInRun: Boolean,
        val isLastInRun: Boolean
    ) : ChatItem
}

private fun buildChatItems(messages: List<Message>): List<ChatItem> {
    if (messages.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val dayLabelFormatter = DateTimeFormatter.ofPattern("EEEE, dd 'tháng' MM yyyy", Locale("vi", "VN"))

    val items = mutableListOf<ChatItem>()
    var lastDay: LocalDate? = null

    messages.forEachIndexed { index, message ->
        val day = Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
        if (day != lastDay) {
            val label = when (day) {
                today -> "Hôm nay"
                yesterday -> "Hôm qua"
                else -> day.format(dayLabelFormatter).replaceFirstChar { it.uppercase() }
            }
            items.add(ChatItem.DayHeader(dayKey = day.toString(), label = label))
            lastDay = day
        }

        val prev = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        val prevDay = prev?.let { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        val nextDay = next?.let { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        val isFirstInRun = prev == null || prev.senderId != message.senderId || prevDay != day
        val isLastInRun = next == null || next.senderId != message.senderId || nextDay != day

        items.add(
            ChatItem.MessageItem(
                message = message,
                isFirstInRun = isFirstInRun,
                isLastInRun = isLastInRun
            )
        )
    }
    return items
}

@Composable
private fun DayDividerRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        Text(
            text = label,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

// ─── Message bubble ──────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    showTail: Boolean,
    showMeta: Boolean,
    extraTopSpacing: Boolean
) {
    val bubbleColor = if (isMine)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (isMine)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    // When the same sender posts multiple messages in a row only the last
    // bubble carries the "tail" corner; intermediate bubbles get a uniform
    // stack of rounded edges.
    val cornerLg = 18.dp
    val cornerSm = 6.dp
    val shape = when {
        isMine && showTail -> RoundedCornerShape(cornerLg, cornerLg, cornerSm, cornerLg)
        isMine -> RoundedCornerShape(cornerLg, cornerLg, cornerLg, cornerLg)
        !isMine && showTail -> RoundedCornerShape(cornerLg, cornerLg, cornerLg, cornerSm)
        else -> RoundedCornerShape(cornerLg)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (extraTopSpacing) 8.dp else 1.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(start = if (isMine) 60.dp else 0.dp, end = if (isMine) 0.dp else 60.dp)
        ) {
            Surface(
                shape = shape,
                color = bubbleColor,
                shadowElevation = if (isMine) 0.dp else 1.dp
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }

            if (showMeta && message.createdAt > 0L) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = message.createdAt.toFormattedDate("HH:mm"),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (isMine) {
                        Icon(
                            imageVector = if (message.isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (message.isRead) "Đã đọc" else "Đã gửi",
                            tint = if (message.isRead)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyChatPlaceholder(workerName: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = "Bắt đầu cuộc trò chuyện",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Hãy gửi tin nhắn đầu tiên cho ${workerName.ifBlank { "người này" }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Input bar ───────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Use the union of IME + navigation-bar insets so we apply whichever
                // is larger, instead of stacking them. Chaining navigationBarsPadding()
                // and imePadding() double-counts the bar when the keyboard is open and
                // pushes the input field up over the messages.
                .windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill-shaped multiline text field, visually decoupled from the
            // send button so the action target is unambiguous.
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                BasicChatField(
                    value = text,
                    onValueChange = onTextChange
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            val canSend = text.trim().isNotBlank() && !isSending
            Surface(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = if (canSend) 4.dp else 0.dp,
                        shape = CircleShape,
                        clip = false
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                shape = CircleShape,
                color = if (canSend)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gửi",
                            tint = if (canSend)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicChatField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = "Nhắn tin...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 5,
            keyboardOptions = KeyboardOptions.Default
        )
    }
}

package com.example.fixbid.presentation.customer.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.fixbid.core.utils.toFormattedDate
import com.example.fixbid.domain.model.ChatPresence
import com.example.fixbid.domain.model.Message
import com.example.fixbid.domain.model.MessageType
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
 * Realtime contracts (provided by [ChatViewModel]):
 *  - Messages stream in via Postgres CDC, applied as deltas — no polling.
 *  - The header subtitle reflects live presence + typing state.
 *  - The 📎 button opens the system photo picker; selected images are
 *    uploaded to Storage and sent as `MessageType.IMAGE`.
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

    // Photo picker — uses the system PickVisualMedia which doesn't require
    // any storage permission and respects user privacy on Android 13+.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() } ?: return@runCatching
                val ext = context.contentResolver.getType(uri)
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() } ?: "jpg"
                val fileName = "img_${System.currentTimeMillis()}.$ext"
                viewModel.sendImage(bytes, fileName)
            }.onFailure {
                Toast.makeText(context, "Không đọc được ảnh", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Collect one-shot events.
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ChatEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ChatEvent.ScrollToBottom -> {
                    if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(0)
                }
            }
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch { listState.animateScrollToItem(0) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ChatHeader(
            workerName = viewModel.workerName,
            avatarUrl = uiState.counterpartAvatarUrl,
            presence = uiState.presence,
            onBackClick = onBackClick
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
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
                        buildChatItems(uiState.messages).reversed()
                    }
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
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
            onAttachImage = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            isSending = uiState.isSending || uiState.isUploadingImage
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun ChatHeader(
    workerName: String,
    avatarUrl: String?,
    presence: ChatPresence,
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

            val initial = workerName.trim().firstOrNull()?.uppercase() ?: "?"
            Box(modifier = Modifier.size(40.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = initial,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                // Live presence dot — green when the counterparty has the
                // chat open right now, grey otherwise.
                val dotColor = if (presence.online)
                    com.example.fixbid.ui.theme.StatusColorsTheme.current.positive
                else
                    com.example.fixbid.ui.theme.StatusColorsTheme.current.neutral
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
                            .background(dotColor)
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
                    text = presenceLabel(presence),
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

private fun presenceLabel(presence: ChatPresence): String = when {
    presence.isTyping -> "Đang nhập…"
    presence.online -> "Đang hoạt động"
    presence.lastSeenAt != null -> "Hoạt động ${humanise(presence.lastSeenAt)}"
    else -> "Ngoại tuyến"
}

private fun humanise(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    val minutes = (diff / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "vừa xong"
        minutes < 60 -> "$minutes phút trước"
        minutes < 24 * 60 -> "${minutes / 60} giờ trước"
        else -> "${minutes / (24 * 60)} ngày trước"
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
            // Image messages are rendered as a tappable preview without the
            // bubble background colouring, so the photo dominates visually.
            if (message.type == MessageType.IMAGE && !message.imageUrl.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(cornerLg),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = if (isMine) 0.dp else 1.dp
                ) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Ảnh đã gửi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 220.dp, height = 220.dp)
                            .clip(RoundedCornerShape(cornerLg))
                    )
                }
            } else {
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
    onAttachImage: () -> Unit,
    isSending: Boolean
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.ime.union(WindowInsets.navigationBars)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach button: leading the input so it's always reachable.
            IconButton(onClick = onAttachImage, enabled = !isSending) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = "Đính kèm ảnh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
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

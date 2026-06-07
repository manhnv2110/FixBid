package com.example.fixbid.presentation.chatbot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.fixbid.domain.model.ChatbotMessage
import com.example.fixbid.domain.model.ChatbotRole
import com.example.fixbid.domain.model.ToolProgress
import com.example.fixbid.domain.repository.ProactivePrompt
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatbotScreen(
    onBackClick: () -> Unit = {},
    onNavigateRoute: (String) -> Unit = {},
    viewModel: ChatbotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is ChatbotNavEvent.Navigate -> onNavigateRoute(event.route)
            }
        }
    }

    // Auto-scroll: tail the latest message AND keep scrolling while it's
    // streaming so each delta lands at the bottom of the viewport.
    val streamingTextLen = uiState.messages.lastOrNull()?.takeIf { it.isStreaming }?.text?.length ?: 0
    LaunchedEffect(uiState.messages.size, streamingTextLen) {
        val count = uiState.messages.size
        if (count > 0) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Trợ lý FixBid", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                "Hỗ trợ bởi AI",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::resetConversation,
                        enabled = !uiState.isThinking
                    ) {
                        Icon(
                            Icons.Outlined.RestartAlt,
                            contentDescription = "Bắt đầu hội thoại mới"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Proactive prompt card — shown above the message list when the
            // assistant has something contextual to suggest (new bid arrived,
            // worker on the way, payment received…).
            AnimatedVisibility(visible = uiState.proactivePrompt != null) {
                uiState.proactivePrompt?.let { prompt ->
                    ProactivePromptCard(
                        prompt = prompt,
                        onAccept = viewModel::acceptProactivePrompt,
                        onDismiss = viewModel::dismissProactivePrompt
                    )
                }
            }

            val reversedMessages = remember(uiState.messages) { uiState.messages.reversed() }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(reversedMessages, key = { it.id }) { msg ->
                    MessageBubble(
                        msg = msg,
                        onNavigate = viewModel::onNavigationClick,
                        onConfirmAction = { action -> viewModel.confirmAction(msg.id, action) },
                        onDismissAction = { viewModel.dismissAction(msg.id) }
                    )
                }
            }

            // Suggestion chips while the conversation is fresh (one assistant
            // greeting, no user messages yet).
            if (uiState.messages.count { it.role == ChatbotRole.USER } == 0 && !uiState.isThinking) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.suggestions.forEach { s ->
                        SuggestionChip(
                            onClick = { viewModel.sendSuggestion(s) },
                            label = { Text(s, fontSize = 12.sp) }
                        )
                    }
                }
            }

            ChatInputBar(
                value = uiState.input,
                enabled = !uiState.isThinking,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::send
            )
        }
    }
}

// ─── Bubble ─────────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    msg: ChatbotMessage,
    onNavigate: (String) -> Unit,
    onConfirmAction: (com.example.fixbid.domain.repository.AiPendingAction) -> Unit = {},
    onDismissAction: () -> Unit = {}
) {
    val isUser = msg.role == ChatbotRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Surface(
                color = when {
                    msg.isError -> MaterialTheme.colorScheme.errorContainer
                    isUser -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Live tool-progress chips appear ABOVE the streamed text
                    // so the user sees what the agent is doing in real time.
                    if (msg.toolProgress.isNotEmpty()) {
                        msg.toolProgress.forEach { tp ->
                            ToolProgressLine(
                                progress = tp,
                                onSurface = !isUser
                            )
                        }
                        if (msg.text.isNotEmpty()) Spacer(Modifier.height(6.dp))
                    }

                    if (msg.text.isNotEmpty()) {
                        AssistantOrUserText(
                            text = msg.text,
                            isUser = isUser,
                            isError = msg.isError,
                            isStreaming = msg.isStreaming
                        )
                    } else if (msg.isStreaming && msg.toolProgress.isEmpty()) {
                        // No text + no tool yet → typing dots so the bubble
                        // never looks empty.
                        TypingDots()
                    }
                }
            }
            // Inline navigation suggestion (open_screen tool / post-action route).
            if (!msg.navigationRoute.isNullOrBlank() && !msg.isStreaming) {
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = { onNavigate(msg.navigationRoute) },
                    label = { Text("Mở màn hình", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            // Pending action confirmation card (cancel / review / bid…).
            val action = msg.pendingAction
            if (action != null && !msg.actionResolved && !msg.isStreaming) {
                Spacer(Modifier.height(8.dp))
                ConfirmActionCard(
                    title = action.title,
                    summary = action.summary,
                    onConfirm = { onConfirmAction(action) },
                    onDismiss = onDismissAction
                )
            }
        }
    }
}

/** Markdown rendering for the assistant; plain text for the user. */
@Composable
private fun AssistantOrUserText(
    text: String,
    isUser: Boolean,
    isError: Boolean,
    isStreaming: Boolean
) {
    val color = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    if (isUser) {
        // Users never type Markdown intentionally — render verbatim so a
        // `*` doesn't suddenly italicise their message.
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    } else {
        // Assistant: full Markdown — bold/italic/lists/code/links.
        // We append a soft caret (▌) while streaming so the user sees the
        // bubble actively grow.
        val displayText = if (isStreaming) "$text ▌" else text
        MarkdownText(
            markdown = displayText,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = color,
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            // Tap a link → fall through to default OS handler.
            linkColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ToolProgressLine(progress: ToolProgress, onSurface: Boolean) {
    val baseColor = if (onSurface) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        if (progress.finished) {
            Icon(
                imageVector = if (progress.success) Icons.Outlined.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (progress.success) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(13.dp)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (progress.finished) "Đã ${progress.displayName}"
            else "Đang ${progress.displayName}…",
            fontSize = 11.sp,
            color = baseColor
        )
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "typing_alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .alpha(alpha)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            if (i < 2) Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun ConfirmActionCard(
    title: String,
    summary: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Huỷ") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Xác nhận") }
            }
        }
    }
}

@Composable
private fun ProactivePromptCard(
    prompt: ProactivePrompt,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onAccept)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prompt.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = prompt.body,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Bỏ qua",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nhắn cho trợ lý...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(Modifier.width(8.dp))
            val canSend = value.isNotBlank() && enabled
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Gửi",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

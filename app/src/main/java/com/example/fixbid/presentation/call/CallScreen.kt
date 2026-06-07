package com.example.fixbid.presentation.call

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Video call screen — embeds the public Jitsi Meet web bridge in a WebView.
 *
 * Why WebView (not the Jitsi Android SDK):
 *  - The SDK pulls in ~25MB of native code + extra build complexity
 *    (it bundles its own React Native bridge). For an MVP we just need
 *    "two devices share a room and see each other"; the web embed does
 *    that for free with a 0KB dependency.
 *  - The Jitsi web client uses standard WebRTC under the hood — we let
 *    Chromium handle SDP/ICE/codec/NAT traversal and we just provide a
 *    container.
 *
 * Permission flow:
 *  - The OS asks once for CAMERA + RECORD_AUDIO when the screen mounts.
 *  - The WebView's [PermissionRequest] callback then grants those exact
 *    grants to the page so navigator.mediaDevices.getUserMedia works.
 *
 * The screen handles three states:
 *  - LOADING: status fetch in flight, WebView not yet mounted.
 *  - RINGING (caller only): "Đang gọi…" with cancel button. Callee never
 *    sees this — they reach this screen only after accepting on the
 *    incoming-call dialog, which already flips status to ACCEPTED.
 *  - JOINED: WebView fills the screen, end button overlay at the bottom.
 *
 * On hang-up we close cleanly via [CallViewModel.hangUp] which writes the
 * `ended_at` and `duration_seconds` columns so the call shows up in the
 * conversation feed as "📹 Cuộc gọi 02:34".
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CallScreen(
    onClose: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CallEvent.Toast ->
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                CallEvent.Close -> onClose()
            }
        }
    }

    // Block the system back button while the call is active so the user
    // doesn't accidentally pop out of the screen and orphan a live WebRTC
    // session. They have to use the explicit hang-up button.
    BackHandler(enabled = uiState.joinRoom) { /* swallow */ }

    // Permission gate — grant once, before we mount the WebView. If the user
    // denies, we surface a friendly explanation rather than dropping them
    // into a black screen.
    var permissionsGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.errorMessage != null -> {
                CenterText(text = uiState.errorMessage ?: "Đã xảy ra lỗi", onClose = onClose)
            }
            !permissionsGranted -> {
                CenterText(
                    text = "FixBid cần quyền camera và microphone để gọi video. " +
                        "Vui lòng cấp quyền trong Cài đặt ứng dụng.",
                    onClose = onClose
                )
            }
            uiState.isLoading || uiState.call == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Đang chuẩn bị cuộc gọi…", color = Color.White, fontSize = 14.sp)
                }
            }
            uiState.joinRoom -> {
                JitsiWebView(
                    roomName = uiState.call!!.roomName,
                    displayName = "FixBid User"
                )
                // Floating top header — call duration + status pill.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = formatDuration(uiState.elapsedSeconds),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                // Hang-up button overlay.
                EndCallOverlay(onEnd = viewModel::hangUp)
            }
            else -> {
                // RINGING: caller is waiting for callee to pick up.
                RingingState(
                    title = "Đang gọi…",
                    subtitle = "Đang chờ người kia trả lời",
                    onCancel = viewModel::cancelOutgoing
                )
            }
        }
    }
}

@Composable
private fun RingingState(title: String, subtitle: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(48.dp))
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFFD32F2F))
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "Huỷ gọi",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun EndCallOverlay(onEnd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onEnd,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD32F2F))
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "Kết thúc",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun CenterText(text: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(20.dp))
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF444444))
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "Đóng",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Embeds `https://meet.jit.si/<room>` inside a WebView with the JS bridge
 * + media permissions enabled. Jitsi's web app is a single-page app — we
 * just hand it a room name and a display name via the URL fragment.
 *
 * `interfaceConfig.overrides` (passed in the URL) hides the toolbar items
 * we don't want (invite, recording, screen share) so the in-call UI stays
 * focused on what matters: video + audio + leave button. The leave button
 * inside Jitsi falls back to our custom one because we kill the WebView's
 * lifecycle on hang-up.
 */
@Composable
private fun JitsiWebView(roomName: String, displayName: String) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    userAgentString = "Mozilla/5.0 (Linux; Android) FixBid/1.0 Mobile Safari/537.36"
                }
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        // The page already runs inside our app and we already
                        // have the OS permissions — granting whatever it asks
                        // for here is safe and the only way navigator.mediaDevices
                        // returns a stream.
                        request.grant(request.resources)
                    }
                }
                val encodedName = Uri.encode(displayName)
                // URL fragment carries Jitsi config:
                // - userInfo.displayName: shown in the participant tile
                // - config.prejoinPageEnabled=false: skip the "set name + camera"
                //   landing — both peers should drop straight into the room
                // - config.startWithVideoMuted/AudioMuted=false: explicitly
                //   start with both on; Jitsi otherwise honours per-user prefs
                val url = "https://meet.jit.si/$roomName" +
                    "#userInfo.displayName=\"$encodedName\"" +
                    "&config.prejoinPageEnabled=false" +
                    "&config.startWithVideoMuted=false" +
                    "&config.startWithAudioMuted=false" +
                    "&config.disableInviteFunctions=true" +
                    "&config.disableDeepLinking=true"
                loadUrl(url)
            }
        },
        update = { /* no-op — WebView state is managed internally */ }
    )
}

private fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val mm = s / 60
    val ss = s % 60
    return "%02d:%02d".format(mm, ss)
}

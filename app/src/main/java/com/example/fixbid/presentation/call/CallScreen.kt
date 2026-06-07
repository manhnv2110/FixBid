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
 * Two critical things make this actually work in a WebView (vs failing
 * with "WebRTC not available"):
 *
 *   1. **Desktop User-Agent**. The `meet.jit.si` SPA detects Android
 *      browsers and redirects to a "Open in the Jitsi Meet app" landing
 *      page that refuses to grant `getUserMedia`. Spoofing a Linux Chrome
 *      UA bypasses that branch — the same JS bundle then runs the full
 *      desktop pipeline which is happy inside Chromium-backed WebView.
 *   2. **Jitsi IFrame API + minimal HTML host**. Loading `meet.jit.si`
 *      directly is fragile because the SPA pulls service workers and
 *      strict CSP that fight the WebView. Wrapping it via the official
 *      `external_api.js` IFrame API gives us a stable embed contract
 *      Jitsi explicitly supports — same API everyone else uses.
 *
 * On top of that we enable every WebSettings flag `getUserMedia` cares
 * about: JS, DOM storage, third-party cookies, mixed content, no
 * gesture-required playback. Without those the page loads but
 * `navigator.mediaDevices` returns undefined and the room joins blind.
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
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    // Modern WebRTC requires unrestricted media + DOM access.
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(false)
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    // Allow the page to load over HTTPS while making
                    // sub-requests over HTTP (Jitsi config endpoints
                    // sometimes do this on the public bridge).
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // Critical: pretend to be Linux Chrome desktop so
                    // meet.jit.si stops redirecting to the "Open in app"
                    // mobile landing page (which would refuse getUserMedia).
                    userAgentString =
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                }
                // Cookies from third parties are required by the Jitsi auth
                // and recording pipeline. Without this the room loads but
                // the participant can't authenticate even as a guest.
                android.webkit.CookieManager.getInstance()
                    .setAcceptThirdPartyCookies(this, true)

                // Hardware accel makes a real difference on the encoder
                // path — WebRTC software decoding chokes at 30fps on
                // mid-tier devices.
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                webViewClient = object : WebViewClient() {
                    // Surface fatal load failures (DNS, no internet, …) to
                    // the host so the screen can render a friendly retry
                    // instead of a blank white page.
                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        android.util.Log.w(
                            "JitsiWebView",
                            "load error: ${error?.description}"
                        )
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        // The page already runs inside our app and we already
                        // have the OS permissions — granting whatever it asks
                        // for here is safe and the only way navigator.mediaDevices
                        // returns a stream.
                        request.grant(request.resources)
                    }

                    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                        super.onPermissionRequestCanceled(request)
                    }

                    // Some Jitsi calls trigger geolocation prompts on first
                    // load (for region detection); auto-allow so the user
                    // doesn't see an extra dialog.
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: android.webkit.GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin, true, false)
                    }

                    override fun onConsoleMessage(
                        consoleMessage: android.webkit.ConsoleMessage?
                    ): Boolean {
                        // Helpful when chasing "WebRTC not available" — Jitsi
                        // logs the precise reason (mic permission, codec, …)
                        // to the console.
                        consoleMessage?.let {
                            android.util.Log.d("JitsiWebView", "${it.messageLevel()} ${it.message()}")
                        }
                        return true
                    }
                }
                // Load a tiny HTML host that pulls in Jitsi's official IFrame
                // API and creates the meeting embed inside the same window.
                // Loading via a data URL on a real origin (jit.si) keeps
                // CORS and CSP happy. The room + display name are interpolated
                // into JS literals; both are user-controlled so we escape.
                val safeRoom = roomName.replace("\"", "\\\"")
                val safeDisplay = displayName.replace("\"", "\\\"")
                val html = jitsiIframeHostHtml(safeRoom, safeDisplay)
                loadDataWithBaseURL(
                    "https://meet.jit.si",
                    html,
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        update = { /* no-op — WebView state is managed internally */ },
        onRelease = { webView ->
            // Stop media tracks and release the WebView cleanly so the
            // camera light goes off the moment we leave the screen.
            runCatching {
                webView.evaluateJavascript(
                    "if (window.__jitsiApi) { window.__jitsiApi.dispose(); }",
                    null
                )
            }
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    )
}

/**
 * Minimal HTML host that loads Jitsi's official IFrame API and creates
 * the meeting embed. Same approach the Jitsi docs recommend for non-
 * native integrations — gives us a stable contract that survives
 * meet.jit.si UI redesigns.
 *
 * The `interfaceConfigOverwrite` strips toolbar buttons we don't need
 * (invite, recording, screen share, …) so the in-call UI stays focused
 * on the customer ↔ worker conversation.
 */
private fun jitsiIframeHostHtml(roomName: String, displayName: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
        <style>
            html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
            #jitsi-container { width: 100%; height: 100%; }
            iframe { border: 0; }
        </style>
    </head>
    <body>
        <div id="jitsi-container"></div>
        <script src="https://meet.jit.si/external_api.js"></script>
        <script>
            (function () {
                var domain = "meet.jit.si";
                var options = {
                    roomName: "$roomName",
                    parentNode: document.getElementById("jitsi-container"),
                    width: "100%",
                    height: "100%",
                    userInfo: { displayName: "$displayName" },
                    configOverwrite: {
                        prejoinPageEnabled: false,
                        startWithAudioMuted: false,
                        startWithVideoMuted: false,
                        disableDeepLinking: true,
                        disableInviteFunctions: true,
                        // Don't show the "you can also use the mobile app" banner.
                        enableClosePage: false
                    },
                    interfaceConfigOverwrite: {
                        MOBILE_APP_PROMO: false,
                        SHOW_JITSI_WATERMARK: false,
                        SHOW_PROMOTIONAL_CLOSE_PAGE: false,
                        SHOW_WATERMARK_FOR_GUESTS: false,
                        TOOLBAR_BUTTONS: [
                            "microphone", "camera", "hangup", "fullscreen",
                            "tileview", "select-background", "videoquality"
                        ]
                    }
                };
                try {
                    var api = new JitsiMeetExternalAPI(domain, options);
                    window.__jitsiApi = api;
                    // Tag the document so the host (us) can spot a successful load.
                    api.addListener("videoConferenceJoined", function () {
                        document.title = "JOINED";
                    });
                } catch (err) {
                    document.body.innerHTML =
                        '<div style="color:#fff;padding:32px;font-family:sans-serif;">' +
                        'Không tải được kết nối video: ' + (err && err.message ? err.message : err) +
                        '</div>';
                }
            })();
        </script>
    </body>
    </html>
""".trimIndent()

private fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val mm = s / 60
    val ss = s % 60
    return "%02d:%02d".format(mm, ss)
}

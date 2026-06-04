package com.example.fixbid.presentation.worker.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.fixbid.core.components.AppHeader
import com.example.fixbid.domain.model.Booking
import com.example.fixbid.ui.theme.AccentGreen
import com.example.fixbid.ui.theme.StatusColorsTheme
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerNavigationScreen(
    onBackClick: () -> Unit,
    viewModel: WorkerNavigationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    // Trigger the system permission dialog the first time the screen reports it needs
    // location access.
    LaunchedEffect(uiState.needsLocationPermission) {
        if (uiState.needsLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Chỉ đường tới khách",
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            uiState.booking?.let { booking ->
                NavigationBottomBar(
                    booking = booking,
                    distanceMeters = uiState.distanceMeters,
                    durationSeconds = uiState.durationSeconds,
                    isRouteReady = uiState.routePoints.isNotEmpty(),
                    onRecenter = viewModel::refreshWorkerLocationAndRoute,
                    onOpenExternalMap = {
                        openExternalMap(
                            context = context,
                            destination = uiState.customerLocation,
                            address = booking.address
                        )
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.customerLocation == null && uiState.addressUnresolved ->
                    UnresolvedAddressState(
                        booking = uiState.booking,
                        onOpenExternalMap = {
                            openExternalMap(
                                context = context,
                                destination = null,
                                address = uiState.booking?.address.orEmpty()
                            )
                        }
                    )
                uiState.customerLocation != null -> MapContent(
                    customerLocation = uiState.customerLocation!!,
                    workerLocation = uiState.workerLocation,
                    routePoints = uiState.routePoints,
                    address = uiState.booking?.address.orEmpty()
                )
                else -> ErrorState(
                    message = uiState.errorMessage ?: "Không tải được dữ liệu",
                    onRetry = viewModel::load
                )
            }

            // Toast-style errors (e.g. routing failed) shown without blocking the map.
            uiState.errorMessage?.let { message ->
                if (uiState.customerLocation != null) {
                    LaunchedEffect(message) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        viewModel.dismissError()
                    }
                }
            }
        }
    }
}

// ─── Map host ────────────────────────────────────────────────────────────────

@Composable
private fun MapContent(
    customerLocation: GeoPoint,
    workerLocation: GeoPoint?,
    routePoints: List<GeoPoint>,
    address: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val accentRoute = StatusColorsTheme.current.inProgress.toArgb()

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Hide built-in zoom buttons; we provide our own Material FABs.
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
            controller.setZoom(15.0)
            controller.setCenter(customerLocation)
        }
    }

    // Bridge the host Composable lifecycle to the MapView so tile fetching pauses
    // while the user is somewhere else in the app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.overlays.clear()

                // Customer marker (destination).
                val customerMarker = Marker(view).apply {
                    position = customerLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Khách hàng"
                    icon = ContextCompatDrawableTinted(
                        context = view.context,
                        resId = android.R.drawable.ic_menu_mylocation,
                        tint = primary
                    )
                }
                view.overlays.add(customerMarker)

                // Worker marker (origin) only when we know where they are.
                workerLocation?.let { wp ->
                    val workerMarker = Marker(view).apply {
                        position = wp
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Vị trí của bạn"
                        icon = ContextCompatDrawableTinted(
                            context = view.context,
                            resId = android.R.drawable.ic_menu_compass,
                            tint = AccentGreen.toArgb()
                        )
                    }
                    view.overlays.add(workerMarker)
                }

                // Route polyline.
                if (routePoints.size >= 2) {
                    val polyline = Polyline(view).apply {
                        setPoints(routePoints)
                        outlinePaint.color = accentRoute
                        outlinePaint.strokeWidth = 12f
                        outlinePaint.isAntiAlias = true
                    }
                    view.overlays.add(polyline)

                    // Auto-fit camera to the whole route the first time we receive it.
                    val box = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(routePoints)
                    view.post {
                        runCatching { view.zoomToBoundingBox(box, true, 96) }
                    }
                } else {
                    view.controller.setCenter(workerLocation ?: customerLocation)
                }

                view.invalidate()
            }
        )

        // Top-aligned address chip — keeps the customer's address visible while the
        // map is panned around.
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = address.ifBlank { "Vị trí khách hàng" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Floating zoom + recenter controls anchored to the trailing edge.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapFab(icon = Icons.Outlined.Add, contentDescription = "Phóng to") {
                mapView.controller.zoomIn()
            }
            MapFab(icon = Icons.Outlined.Remove, contentDescription = "Thu nhỏ") {
                mapView.controller.zoomOut()
            }
            MapFab(icon = Icons.Outlined.MyLocation, contentDescription = "Vị trí của bạn") {
                workerLocation?.let { mapView.controller.animateTo(it) }
            }
        }
    }
}

@Composable
private fun MapFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

// ─── Bottom info bar ────────────────────────────────────────────────────────

@Composable
private fun NavigationBottomBar(
    booking: Booking,
    distanceMeters: Double,
    durationSeconds: Double,
    isRouteReady: Boolean,
    onRecenter: () -> Unit,
    onOpenExternalMap: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Customer summary row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.customer?.fullName ?: "Khách hàng",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = booking.address,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Route stats
            Row(modifier = Modifier.fillMaxWidth()) {
                StatChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Route,
                    label = "Quãng đường",
                    value = if (isRouteReady) formatDistance(distanceMeters) else "—",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                StatChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.DirectionsCar,
                    label = "Thời gian",
                    value = if (isRouteReady && durationSeconds > 0) formatDuration(durationSeconds) else "—",
                    tint = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f).height(48.dp),
                    onClick = onRecenter,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cập nhật", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    modifier = Modifier.weight(1f).height(48.dp),
                    onClick = onOpenExternalMap,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Outlined.NavigateNext, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mở app bản đồ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(tint.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ─── Empty / loading / error states ─────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Đang tải bản đồ…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium
            ) { Text("Thử lại") }
        }
    }
}

@Composable
private fun UnresolvedAddressState(booking: Booking?, onOpenExternalMap: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Không xác định toạ độ",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Khách chưa cung cấp toạ độ chính xác. Bạn có thể mở app bản đồ với địa chỉ:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            booking?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it.address,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onOpenExternalMap,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.NavigateNext, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mở app bản đồ")
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun ContextCompatDrawableTinted(
    context: android.content.Context,
    resId: Int,
    tint: Int
): android.graphics.drawable.Drawable? {
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId)
        ?: return null
    val mutable = drawable.mutate()
    androidx.core.graphics.drawable.DrawableCompat.setTint(mutable, tint)
    return mutable
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1_000) "%.1f km".format(meters / 1_000.0)
    else "${meters.roundToInt()} m"

private fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes phút"
        minutes == 0 -> "$hours giờ"
        else -> "$hours giờ $minutes phút"
    }
}

/**
 * Hand off turn-by-turn navigation to the user's preferred map app via the standard
 * `geo:` intent. This works with Google Maps, OsmAnd, Maps.me, and any other Android
 * map app that registers for the scheme — meaning the worker keeps using whatever
 * navigation experience they already trust.
 */
private fun openExternalMap(
    context: android.content.Context,
    destination: GeoPoint?,
    address: String
) {
    val uri = if (destination != null) {
        val encodedLabel = Uri.encode(address.ifBlank { "Khách hàng" })
        Uri.parse("geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}($encodedLabel)")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Không tìm thấy app bản đồ", Toast.LENGTH_SHORT).show()
    }
}

package com.example.fixbid.presentation.customer.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.fixbid.core.map.MapStyles
import com.example.fixbid.data.location.GeocoderRepository
import com.example.fixbid.data.location.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Full-height Material 3 bottom sheet with a MapLibre map. The user drags the map
 * under a fixed center pin, the address is reverse-geocoded as the map settles, and a
 * "Confirm" button returns both the typed address and the precise coordinates.
 *
 * Patterns kept consistent with the rest of the app:
 *   - same `ModalBottomSheet` chrome as `JobDetailScreen`'s sheets
 *   - `AppHeader`-style title row with a Material icon avatar
 *   - primary-tinted floating zoom controls mirroring `WorkerNavigationScreen`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressPickerSheet(
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialAddress: String,
    locationRepository: LocationRepository,
    geocoderRepository: GeocoderRepository,
    onDismiss: () -> Unit,
    onConfirm: (latitude: Double, longitude: Double, address: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val sheetHeight = (configuration.screenHeightDp * 0.85f).dp

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Default to Hanoi when neither initial coords nor a fix have arrived yet.
    val fallbackLat = 21.0285
    val fallbackLng = 105.8542
    var selectedLat by remember { mutableStateOf(initialLatitude ?: fallbackLat) }
    var selectedLng by remember { mutableStateOf(initialLongitude ?: fallbackLng) }
    var displayAddress by remember { mutableStateOf(initialAddress) }
    var isResolvingAddress by remember { mutableStateOf(false) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            // MapLibre needs lifecycle bookkeeping even when hosted by Compose; we
            // call onCreate/onStart eagerly here and route the rest of the events
            // through the lifecycle observer below.
            onCreate(null)
        }
    }
    val mapHolder = remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Debounce: only reverse-geocode after the map has settled for ~400 ms. Keeping
    // the in-flight job in a MutableState lets the camera-idle listener cancel the
    // previous one without leaking the scope.
    val pendingGeocodeJob = remember { mutableStateOf<Job?>(null) }

    DisposableEffect(mapView) {
        val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
            val map = mapHolder.value ?: return@OnCameraIdleListener
            val center = map.cameraPosition.target ?: return@OnCameraIdleListener
            onMapMoved(
                latitude = center.latitude,
                longitude = center.longitude,
                scope = scope,
                pendingJobHolder = pendingGeocodeJob,
                geocoderRepository = geocoderRepository,
                onLatLngChange = { lat, lng -> selectedLat = lat; selectedLng = lng },
                onAddressChange = { displayAddress = it },
                onLoadingChange = { isResolvingAddress = it }
            )
        }

        // The map isn't ready yet when DisposableEffect runs, so we attach the
        // listener in the getMapAsync callback below and remove it on dispose.
        mapView.getMapAsync { mlMap ->
            mapHolder.value = mlMap
            mlMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(selectedLat, selectedLng))
                .zoom(if (initialLatitude != null && initialLongitude != null) 17.0 else 14.0)
                .build()
            mlMap.setStyle(Style.Builder().fromUri(MapStyles.DEFAULT))
            mlMap.uiSettings.apply {
                isAttributionEnabled = true
                isLogoEnabled = false
                isCompassEnabled = false
                isRotateGesturesEnabled = false
            }
            mlMap.addOnCameraIdleListener(cameraIdleListener)
        }

        onDispose {
            mapHolder.value?.removeOnCameraIdleListener(cameraIdleListener)
            pendingGeocodeJob.value?.cancel()
        }
    }

    // Initial reverse geocode if we opened with a coordinate but no formatted address.
    LaunchedEffect(Unit) {
        if (initialLatitude != null && initialLongitude != null && initialAddress.isBlank()) {
            isResolvingAddress = true
            displayAddress = geocoderRepository.reverseGeocode(initialLatitude, initialLongitude)
                ?: ""
            isResolvingAddress = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = sheetHeight, max = sheetHeight)
        ) {
            // Title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chọn vị trí trên bản đồ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Kéo bản đồ để di chuyển ghim đỏ vào đúng vị trí",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Map area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { mapView.also { it.onStart(); it.onResume() } }
                )

                // Centered fixed pin overlay — feels like the Google Maps "drag to
                // pinpoint" pattern users already know.
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = "Vị trí đã chọn",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                )
                // Tiny dot at the geographic centre so the user can see exactly
                // where the bottom of the pin "lands".
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )

                // Floating controls
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallMapFab(Icons.Outlined.Add, "Phóng to") {
                        mapHolder.value?.animateCamera(CameraUpdateFactory.zoomIn())
                    }
                    SmallMapFab(Icons.Outlined.Remove, "Thu nhỏ") {
                        mapHolder.value?.animateCamera(CameraUpdateFactory.zoomOut())
                    }
                    SmallMapFab(
                        icon = Icons.Outlined.MyLocation,
                        contentDescription = "Vị trí hiện tại",
                        loading = isFetchingLocation
                    ) {
                        scope.launch {
                            isFetchingLocation = true
                            val location = locationRepository.getCurrentLocation()
                            isFetchingLocation = false
                            if (location != null) {
                                mapHolder.value?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(location.latitude, location.longitude),
                                        17.0
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Bottom info + confirm
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vị trí đã chọn",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isResolvingAddress) {
                            Text(
                                text = "Đang tìm địa chỉ…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = displayAddress.ifBlank {
                                    "%.5f, %.5f".format(selectedLat, selectedLng)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val resolved = displayAddress.ifBlank {
                            "%.5f, %.5f".format(selectedLat, selectedLng)
                        }
                        onConfirm(selectedLat, selectedLng, resolved)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isResolvingAddress,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dùng địa chỉ này", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun SmallMapFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Cancel any in-flight reverse-geocode and schedule a new debounced one tied to the
 * map's current center. Keeping this as a top-level helper lets both the scroll and
 * zoom listeners share the exact same logic.
 */
private fun onMapMoved(
    latitude: Double,
    longitude: Double,
    scope: CoroutineScope,
    pendingJobHolder: MutableState<Job?>,
    geocoderRepository: GeocoderRepository,
    onLatLngChange: (Double, Double) -> Unit,
    onAddressChange: (String) -> Unit,
    onLoadingChange: (Boolean) -> Unit
) {
    onLatLngChange(latitude, longitude)
    pendingJobHolder.value?.cancel()
    pendingJobHolder.value = scope.launch {
        delay(400)
        onLoadingChange(true)
        val resolved = geocoderRepository.reverseGeocode(latitude, longitude)
        onAddressChange(resolved.orEmpty())
        onLoadingChange(false)
    }
}

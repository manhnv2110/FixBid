package com.example.fixbid.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Tiny wrapper around Google Play services' Fused Location Provider.
 *
 * The repository always checks permissions before issuing a request so callers don't
 * trigger the dreaded Lint `MissingPermission` crash, and converts the callback-style
 * GMS API into a single suspend call.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val client by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Request a fresh, high-accuracy fix. Returns null when permissions are missing,
     * the user has location services off, or the device is unable to acquire a fix
     * (e.g. emulator without provided coords).
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasFineLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
    }
}

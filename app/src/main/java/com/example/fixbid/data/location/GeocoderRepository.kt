package com.example.fixbid.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves human-readable addresses into latitude/longitude (and vice-versa) using
 * Android's built-in Geocoder.
 *
 * The Geocoder is backed by the OS-supplied service (typically Google on GMS devices,
 * OSM-based forks on others), but the result is just a `(lat, lng)` pair plus the
 * formatted address — no Google-specific tokens are needed and the call is allowed
 * offline on devices with cached data.
 */
@Singleton
class GeocoderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class GeoPoint(
        val latitude: Double,
        val longitude: Double,
        /** Formatted, human-readable address. May be blank when unavailable. */
        val formattedAddress: String = ""
    )

    /**
     * Forward-geocode: street address → lat/lng.
     */
    suspend fun resolveAddress(address: String): GeoPoint? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent() || address.isBlank()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            // The classic blocking API stays available on every API level even though
            // Tiramisu offers an async overload — we already run on Dispatchers.IO so
            // a synchronous call here is fine and keeps the code consistent.
            @Suppress("DEPRECATION")
            val results: List<Address>? = geocoder.getFromLocationName(address, 1)
            results?.firstOrNull()?.let {
                GeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    formattedAddress = it.formatLines()
                )
            }
        }.getOrNull()
    }

    /**
     * Reverse-geocode: lat/lng → human-readable address. Used after capturing a GPS
     * fix or when the user drops a pin on the map picker.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            runCatching {
                @Suppress("DEPRECATION")
                val results: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
                results?.firstOrNull()?.formatLines()?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    /**
     * Glue every populated address-line into a single comma-separated string. The
     * `getAddressLine` API only ever returns one line on most devices, so we
     * concatenate the structured fields ourselves to provide a fuller result.
     */
    private fun Address.formatLines(): String {
        val explicit = (0..maxAddressLineIndex)
            .mapNotNull { runCatching { getAddressLine(it) }.getOrNull() }
            .filter { it.isNotBlank() }
        if (explicit.isNotEmpty()) return explicit.joinToString(", ")

        // Fallback when no address line was filled in.
        return listOfNotNull(
            featureName?.takeIf { it.isNotBlank() },
            thoroughfare?.takeIf { it.isNotBlank() },
            subLocality?.takeIf { it.isNotBlank() },
            locality?.takeIf { it.isNotBlank() },
            adminArea?.takeIf { it.isNotBlank() },
            countryName?.takeIf { it.isNotBlank() }
        ).joinToString(", ")
    }
}

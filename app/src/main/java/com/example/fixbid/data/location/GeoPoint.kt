package com.example.fixbid.data.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SDK-neutral geographic point used across the data and presentation layers.
 *
 * The previous osmdroid-based UI leaked `org.osmdroid.util.GeoPoint` up into
 * ViewModels, which meant any future map swap (osmdroid → MapLibre, MapLibre →
 * Mapbox, etc.) cascaded through the whole codebase. This shared type lives in
 * the data layer so only the actual `MapView` host has to translate to and from
 * the rendering library's lat/lng class.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {

    /**
     * Great-circle distance to [other] in metres using the Haversine formula
     * (Earth radius ≈ 6 371 km). Accurate to within a few metres for the kind
     * of urban distances FixBid deals with — plenty for ETA badges and
     * "straight-line fallback" rendering.
     */
    fun distanceToMeters(other: GeoPoint): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLng = Math.toRadians(other.longitude - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val a = sin(dLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}

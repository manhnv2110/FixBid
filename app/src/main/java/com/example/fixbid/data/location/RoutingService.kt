package com.example.fixbid.data.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight client for the public OSRM demo server (router.project-osrm.org).
 *
 * OSRM is the de-facto OSM-based routing engine; the demo endpoint is rate limited
 * but free for low traffic and requires no API key. The response includes a Polyline6
 * encoded geometry (returned as GeoJSON when `geometries=geojson`) plus distance and
 * duration. We only use the `driving` profile because it's the most common scenario
 * for a worker travelling to a customer; other profiles (`cycling`, `foot`) are
 * available with the same endpoint shape if needed later.
 *
 * The implementation uses `HttpURLConnection` so we don't pull in additional Ktor
 * client transitive dependencies.
 */
@Singleton
class RoutingService @Inject constructor() {

    data class RouteResult(
        val points: List<GeoPoint>,
        val distanceMeters: Double,
        val durationSeconds: Double
    )

    suspend fun fetchRoute(start: GeoPoint, end: GeoPoint): RouteResult? =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("https://router.project-osrm.org/route/v1/driving/")
                append("${start.longitude},${start.latitude};")
                append("${end.longitude},${end.latitude}")
                append("?overview=full&geometries=geojson")
            }
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "FixBid-Android/1.0")
                }

                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return@runCatching null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@runCatching null

                val routes = json.optJSONArray("routes") ?: return@runCatching null
                if (routes.length() == 0) return@runCatching null

                val route = routes.getJSONObject(0)
                val distance = route.optDouble("distance", 0.0)
                val duration = route.optDouble("duration", 0.0)

                // GeoJSON LineString -> array of [lng, lat] tuples.
                val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                val points = ArrayList<GeoPoint>(coordinates.length())
                for (i in 0 until coordinates.length()) {
                    val tuple = coordinates.getJSONArray(i)
                    points.add(GeoPoint(tuple.getDouble(1), tuple.getDouble(0)))
                }

                RouteResult(
                    points = points,
                    distanceMeters = distance,
                    durationSeconds = duration
                )
            }.getOrNull()
        }
}

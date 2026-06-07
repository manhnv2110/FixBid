package com.example.fixbid.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * MapLibre style + asset helpers.
 *
 * We render OpenStreetMap data through [OpenFreeMap](https://openfreemap.org/), a
 * free public CDN that serves Mapbox/MapLibre-compatible **vector tile** styles
 * with no API key, no registration and no usage limits. Vector tiles look
 * dramatically better than raw raster (`tile.openstreetmap.org`) at every zoom
 * level — they're crisp on high-DPI screens, support smooth zoom interpolation,
 * and ship with proper road/POI/label hierarchies out of the box.
 *
 * We expose three ready-to-use styles:
 *   - [LIBERTY] (default): full-colour OSM Liberty look, similar to Google Maps
 *     light. Works great for the worker navigation screen and the address picker.
 *   - [POSITRON]: low-contrast neutral basemap — good when overlaying lots of
 *     custom data (heatmaps, dense markers, route polylines).
 *   - [BRIGHT]: punchier saturated palette, more "OSM Mapnik"-flavoured.
 *
 * Override the default at the call site by passing a different constant to
 * `Style.Builder().fromUri(...)`.
 */
object MapStyles {

    /** Default style — colourful OSM Liberty. */
    const val LIBERTY: String = "https://tiles.openfreemap.org/styles/liberty"

    /** Low-contrast neutral basemap, ideal for data overlays. */
    const val POSITRON: String = "https://tiles.openfreemap.org/styles/positron"

    /** High-saturation "Mapnik-flavoured" style. */
    const val BRIGHT: String = "https://tiles.openfreemap.org/styles/bright"

    /** Convenience alias the rest of the app uses. Swap here to retheme everything. */
    const val DEFAULT: String = LIBERTY

    // MapLibre layer / source IDs used across the worker navigation screen and
    // the address picker. Centralising them avoids accidental string typos.
    const val SOURCE_ROUTE = "fixbid-route-source"
    const val LAYER_ROUTE = "fixbid-route-layer"

    const val SOURCE_MARKERS = "fixbid-markers-source"
    const val LAYER_MARKERS = "fixbid-markers-layer"

    const val IMAGE_CUSTOMER = "fixbid-icon-customer"
    const val IMAGE_WORKER = "fixbid-icon-worker"
}

/**
 * Convert a vector / shape drawable resource to a tinted bitmap for use as a
 * MapLibre [SymbolLayer] icon (`Style.addImage(name, bitmap)`).
 */
fun drawableToBitmap(
    context: Context,
    @DrawableRes resId: Int,
    tintArgb: Int? = null,
    sizePx: Int = 96
): Bitmap {
    val drawable = ContextCompat.getDrawable(context, resId)
        ?: return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val mutable = drawable.mutate()
    if (tintArgb != null) {
        DrawableCompat.setTint(mutable, tintArgb)
    }
    val width = if (mutable.intrinsicWidth > 0) mutable.intrinsicWidth else sizePx
    val height = if (mutable.intrinsicHeight > 0) mutable.intrinsicHeight else sizePx
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    mutable.setBounds(0, 0, canvas.width, canvas.height)
    mutable.draw(canvas)
    return bitmap
}

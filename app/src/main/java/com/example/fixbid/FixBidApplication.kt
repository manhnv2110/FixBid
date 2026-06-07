package com.example.fixbid

import android.app.Application
import com.example.fixbid.core.notification.AppNotificationManager
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import javax.inject.Inject

@HiltAndroidApp
class FixBidApplication : Application() {

    @Inject
    lateinit var appNotificationManager: AppNotificationManager

    override fun onCreate() {
        super.onCreate()

        // MapLibre needs a single global init before any MapView is constructed.
        // We point at OpenFreeMap (vector tiles, no API key, no usage limits) so
        // the maps look as crisp as Google Maps — see `core.map.MapStyles`.
        // Passing `null` here is the documented "no API key" path.
        MapLibre.getInstance(this)

        // Register notification channels up front so they exist before the first
        // notification is posted.
        appNotificationManager.ensureChannels()
    }
}

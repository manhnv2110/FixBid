package com.example.fixbid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class FixBidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise osmdroid: a polite User-Agent is required by the OpenStreetMap
        // tile policy. Cache lives under the app's external files dir so we never
        // need WRITE_EXTERNAL_STORAGE on modern devices.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = getExternalFilesDir("osmdroid")
            osmdroidTileCache = getExternalFilesDir("osmdroid/tiles")
        }
    }
}

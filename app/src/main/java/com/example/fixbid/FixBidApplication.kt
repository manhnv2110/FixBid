package com.example.fixbid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class FixBidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = getExternalFilesDir("osmdroid")
            osmdroidTileCache = getExternalFilesDir("osmdroid/tiles")
        }
    }
}
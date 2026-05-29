package com.example.fixbid

import android.app.Application
import com.example.fixbid.core.notification.AppNotificationManager
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import javax.inject.Inject

@HiltAndroidApp
class FixBidApplication : Application() {

    @Inject
    lateinit var appNotificationManager: AppNotificationManager

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = getExternalFilesDir("osmdroid")
            osmdroidTileCache = getExternalFilesDir("osmdroid/tiles")
        }
        // Register notification channels up front so they exist before the first
        // notification is posted.
        appNotificationManager.ensureChannels()
    }
}

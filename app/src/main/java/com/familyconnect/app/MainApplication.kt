package com.familyconnect.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.familyconnect.app.util.Constants
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import timber.log.Timber.DebugTree

/**
 * Main Application class for Family Connect.
 * Implements defensive initialization to prevent startup crashes.
 */
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 1. Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        
        Timber.tag(TAG).d("Application onCreate started")

        // 2. Safely Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Timber.tag(TAG).i("Firebase initialized successfully")
            
            // Enable Crashlytics in non-debug builds
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Firebase initialization failed. Check google-services.json")
        }

        // 3. Create Notification Channels
        try {
            createNotificationChannels()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create notification channels")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_monitor),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_monitor_desc)
                setShowBadge(true)
            }

            val alertChannel = NotificationChannel(
                "family_connect_alerts",
                getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_alerts_desc)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(monitorChannel)
            manager?.createNotificationChannel(alertChannel)
            Timber.tag(TAG).d("Notification channels created")
        }
    }

    companion object {
        private const val TAG = "MainApplication"
    }
}

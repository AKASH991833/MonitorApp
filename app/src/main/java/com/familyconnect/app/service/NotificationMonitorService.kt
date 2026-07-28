package com.familyconnect.app.service

import android.annotation.SuppressLint
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.util.Constants
import com.google.firebase.database.ServerValue
import timber.log.Timber

@SuppressLint("OverrideAbstract")
class NotificationMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifMonitor"
    }

    private val firebaseSource = FirebaseSource()
    private val prefs by lazy {
        getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.i("Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Timber.w("Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.getBoolean(Constants.KEY_APP_USAGE_ENABLED, false)) return
        if (sbn.isOngoing) return

        val packageName = sbn.packageName
        val notification = sbn.notification
        val title = notification.extras?.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isEmpty() && text.isEmpty()) return

        val childId = prefs.getString("child_id", null) ?: return

        val notifData = mapOf(
            "packageName" to packageName,
            "appName" to getAppName(packageName),
            "title" to title,
            "text" to text,
            "timestamp" to ServerValue.TIMESTAMP,
            "postedAt" to sbn.postTime
        )

        firebaseSource.getDevicesRef()
            .child(childId)
            .child("notifications")
            .push()
            .setValue(notifData)
            .addOnSuccessListener {
                Timber.d("Notification saved: $packageName - $title")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to save notification")
            }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

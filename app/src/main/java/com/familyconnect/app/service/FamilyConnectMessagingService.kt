package com.familyconnect.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familyconnect.app.service.ForegroundMonitorService
import com.familyconnect.app.util.Constants
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

class FamilyConnectMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FamilyConnectFCM"
        private const val KEY_COMMAND = "command"
        private const val KEY_SESSION_ID = "sessionId"
        private const val KEY_PARENT_ID = "parentId"
        private const val KEY_SDP_OFFER = "sdpOffer"
        private const val KEY_SENDER_ROLE = "senderRole"

        private const val COMMAND_WAKE = "wake"
        private const val COMMAND_SLEEP = "sleep"
        private const val COMMAND_STOP = "stop"
        private const val COMMAND_PING = "ping"

        private const val ROLE_PARENT = "parent"
        private const val ROLE_CHILD = "child"
    }

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://studio-6135479340-ea1c2-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }
    private val prefs: android.content.SharedPreferences by lazy {
        getSharedPreferences("family_connect_prefs", Context.MODE_PRIVATE)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.i("FCM token refreshed: $token")
        updateFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("Message received from: ${message.from}")

        val data = message.data
        if (data.isEmpty()) {
            Timber.w("Empty data payload, ignoring notification message")
            showDefaultNotification(message)
            return
        }

        Timber.i("Received data: $data")
        handleDataMessage(data)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val command = data[KEY_COMMAND] ?: run {
            Timber.w("No command key in data payload")
            return
        }
        val sessionId = data[KEY_SESSION_ID]
        val parentId = data[KEY_PARENT_ID]
        val sdpOffer = data[KEY_SDP_OFFER]
        val senderRole = data[KEY_SENDER_ROLE]

        val myRole = prefs.getString("user_role", ROLE_CHILD) ?: ROLE_CHILD

        Timber.d("Command=$command, myRole=$myRole, sessionId=$sessionId, parentId=$parentId")

        when (command) {
            COMMAND_WAKE -> {
                if (myRole != ROLE_CHILD) {
                    Timber.i("Ignoring wake command - current role is $myRole")
                    return
                }
                Timber.i("Processing wake command")
                val sid = sessionId ?: "unknown"
                
                val autoStart = prefs.getBoolean(Constants.KEY_AUTO_START, true)
                if (autoStart) {
                    ForegroundMonitorService.startStream(this, sid)
                } else {
                    showAckNotification(sid)
                }
            }

            COMMAND_SLEEP -> {
                Timber.i("Processing sleep command")
                ForegroundMonitorService.stopStream(this)
            }

            COMMAND_STOP -> {
                if (myRole != ROLE_CHILD) {
                    Timber.i("Ignoring stop command - current role is $myRole")
                    return
                }
                Timber.w("Processing emergency stop command")
                ForegroundMonitorService.emergencyStop(this)
            }

            COMMAND_PING -> {
                Timber.i("Processing ping command")
                updateOnlineStatus()
            }

            else -> {
                Timber.w("Unknown command received: $command")
            }
        }
    }

    private fun showAckNotification(sessionId: String) {
        val channelId = "family_connect_ack"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Connection Requests",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val allowIntent = Intent(this, ForegroundMonitorService::class.java).apply {
            action = ForegroundMonitorService.ACTION_START_STREAM
            putExtra(ForegroundMonitorService.EXTRA_SESSION_ID, sessionId)
        }
        val allowPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getService(this, 1, allowIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 1, allowIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Connection Request")
            .setContentText("Your parent wants to start a monitoring session.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, "Allow", allowPendingIntent)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2001, notification)
    }

    private fun updateFcmToken(token: String) {
        val childId = prefs.getString("child_id", null)
        if (childId != null) {
            database.getReference("devices")
                .child(childId)
                .child("fcmToken")
                .setValue(token)
                .addOnSuccessListener {
                    Timber.i("FCM token updated in Realtime Database")
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to update FCM token")
                }
        } else {
            Timber.w("No child_id available, storing token locally")
            prefs.edit().putString("pending_fcm_token", token).apply()
        }
    }

    private fun updateOnlineStatus() {
        val childId = prefs.getString("child_id", null)
        if (childId != null) {
            val updates = mapOf(
                "online" to true,
                "lastPing" to System.currentTimeMillis(),
                "deviceInfo" to "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
            )
            database.getReference("devices")
                .child(childId)
                .updateChildren(updates)
                .addOnSuccessListener {
                    Timber.i("Online status updated")
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to update online status")
                }
        }
    }

    private fun showDefaultNotification(message: RemoteMessage) {
        val title = message.notification?.title ?: "Family Connect"
        val body = message.notification?.body ?: "New message"

        val channelId = "family_connect_general"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Family Connect Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Timber.w("Messages deleted on server - re-syncing status")
        updateOnlineStatus()
    }
}

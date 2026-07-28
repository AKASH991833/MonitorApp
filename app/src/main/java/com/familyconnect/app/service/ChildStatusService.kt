package com.familyconnect.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.util.Constants
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class ChildStatusService : Service() {

    companion object {
        const val CHANNEL_ID = "family_connect_status"
        const val NOTIFICATION_ID = 3001
        const val ACTION_START = "com.familyconnect.app.action.START_STATUS"
        const val ACTION_STOP = "com.familyconnect.app.action.STOP_STATUS"

        fun start(context: Context) {
            val intent = Intent(context, ChildStatusService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChildStatusService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statusJob: Job? = null
    private var locationJob: Job? = null
    private var locationProvider: com.google.android.gms.location.FusedLocationProviderClient? = null
    private val firebaseSource = FirebaseSource()

    override fun onCreate() {
        super.onCreate()
        Timber.d("ChildStatusService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("ChildStatusService onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startStatusUpdates()
            }
            ACTION_STOP -> {
                stopUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startStatusUpdates() {
        statusJob?.cancel()
        statusJob = serviceScope.launch {
            while (isActive) {
                try {
                    val userId = firebaseSource.getCurrentUserId()
                    sendStatusUpdate(userId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to send status update")
                }
                delay(Constants.STATUS_UPDATE_INTERVAL_MS)
            }
        }

        locationJob?.cancel()
        locationJob = serviceScope.launch {
            while (isActive) {
                try {
                    val userId = firebaseSource.getCurrentUserId()
                    sendLocationUpdate(userId)
                } catch (e: Exception) {
                    Timber.w("Location update failed: ${e.message}")
                }
                delay(Constants.LOCATION_UPDATE_INTERVAL_MS)
            }
        }

        Timber.i("Status updates started")
    }

    private suspend fun sendStatusUpdate(userId: String) {
        val batteryLevel = getBatteryLevel()
        val isCharging = isCharging()
        val networkType = getNetworkType()
        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"

        val updates = mapOf<String, Any>(
            "status/battery" to batteryLevel,
            "status/isCharging" to isCharging,
            "status/network" to networkType,
            "status/deviceInfo" to deviceInfo,
            "status/lastSeen" to ServerValue.TIMESTAMP,
            "status/isOnline" to true
        )

        firebaseSource.getDevicesRef().child(userId).updateChildren(updates)
    }

    private suspend fun sendLocationUpdate(userId: String) {
        try {
            val prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(Constants.KEY_LOCATION_ENABLED, false)) return

            if (locationProvider == null) {
                locationProvider = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
            }

            val task = locationProvider?.lastLocation
            if (task != null) {
                val location = com.google.android.gms.tasks.Tasks.await(task)
                if (location != null) {
                    val locationData = mapOf<String, Any>(
                        "location/lat" to location.latitude,
                        "location/lng" to location.longitude,
                        "location/accuracy" to location.accuracy.toDouble(),
                        "location/altitude" to location.altitude,
                        "location/timestamp" to location.time
                    )
                    firebaseSource.getDevicesRef().child(userId).updateChildren(locationData)
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to get location: ${e.message}")
        }
    }

    private fun getBatteryLevel(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) return -1
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    private fun isCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) return false
        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getNetworkType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "unknown"
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Family Connect Status",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background status service"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Family Connect")
            .setContentText("Status updates active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun stopUpdates() {
        statusJob?.cancel()
        locationJob?.cancel()
        Timber.i("Status updates stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUpdates()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

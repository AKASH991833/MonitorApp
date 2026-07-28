package com.familyconnect.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.familyconnect.app.data.remote.AnswerPayload
import com.familyconnect.app.data.remote.IceCandidatePayload
import com.familyconnect.app.data.remote.OfferPayload
import com.familyconnect.app.data.remote.SignalingService
import com.familyconnect.app.webrtc.WebRTCClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import timber.log.Timber
import java.util.UUID

class ForegroundMonitorService : Service() {

    companion object {
        const val ACTION_START_STREAM = "com.familyconnect.app.action.START_STREAM"
        const val ACTION_STOP_STREAM = "com.familyconnect.app.action.STOP_STREAM"
        const val ACTION_EMERGENCY_STOP = "com.familyconnect.app.action.EMERGENCY_STOP"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val CHANNEL_ID = "family_connect_monitor_channel"
        const val NOTIFICATION_ID = 1001

        fun startStream(context: Context, sessionId: String) {
            val intent = Intent(context, ForegroundMonitorService::class.java).apply {
                action = ACTION_START_STREAM
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopStream(context: Context) {
            val intent = Intent(context, ForegroundMonitorService::class.java).apply {
                action = ACTION_STOP_STREAM
            }
            context.startService(intent)
        }

        fun emergencyStop(context: Context) {
            val intent = Intent(context, ForegroundMonitorService::class.java).apply {
                action = ACTION_EMERGENCY_STOP
            }
            context.startService(intent)
        }
    }

    inner class EmergencyStopReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_EMERGENCY_STOP == intent.action) {
                Timber.w("Emergency stop triggered via notification action")
                handleEmergencyStop()
            }
        }
    }

    private lateinit var webRTCClient: WebRTCClient
    private var eglBase: EglBase? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isStreaming = false
    private var currentSessionId: String? = null
    private var emergencyReceiver: EmergencyStopReceiver? = null
    private val signalingService = SignalingService()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var signalingJob: Job? = null
    private var commandsJob: Job? = null

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("ForegroundMonitorService onCreate")
        eglBase = try { EglBase.create() } catch (e: Throwable) { Timber.e(e, "Failed to create EglBase"); null }
        webRTCClient = WebRTCClient(applicationContext, eglBase?.eglBaseContext ?: org.webrtc.EglBase.create().eglBaseContext)
        createNotificationChannel()
        registerEmergencyReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_START_STREAM -> {
                currentSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (currentSessionId.isNullOrEmpty()) {
                    currentSessionId = UUID.randomUUID().toString()
                }
                val notification = createNotification("Your parent is viewing and listening")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                acquireWakeLock()
                startStream()
            }
            ACTION_STOP_STREAM -> {
                stopStream()
            }
            ACTION_EMERGENCY_STOP -> {
                handleEmergencyStop()
            }
        }

        return START_STICKY
    }

    private fun startStream() {
        if (isStreaming) return
        isStreaming = true
        val sid = currentSessionId ?: return
        Timber.d("Starting stream for session $sid")

        try {
            webRTCClient.apply {
                setCallbacks(
                    onIceCandidate = { candidate ->
                        serviceScope.launch {
                            signalingService.sendIceCandidate(
                                sid,
                                IceCandidatePayload(candidate.sdp, candidate.sdpMLineIndex, candidate.sdpMid)
                            )
                        }
                    },
                    onConnectionChange = { state ->
                        Timber.d("Child connection state: $state")
                        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                            updateNotification("Streaming is active")
                        } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED ||
                                   state == PeerConnection.PeerConnectionState.FAILED) {
                            stopStream()
                        }
                    },
                    onAddStream = { stream ->
                        Timber.d("Remote stream added with ${stream.videoTracks.size} video tracks")
                    }
                )
                initializePeerConnection(defaultIceServers())
                startVideoCapture()
                startAudioCapture()
            }

            updateNotification("Connecting...")

            // Listen for SDP offer from parent
            signalingJob = serviceScope.launch {
                signalingService.listenForOffer(sid).collectLatest { offerPayload ->
                    Timber.d("Received offer for session $sid")
                    val sdp = SessionDescription(
                        SessionDescription.Type.OFFER,
                        offerPayload.sdp ?: return@collectLatest
                    )
                    webRTCClient.setRemoteDescription(sdp)

                    val answer = webRTCClient.createAnswer() ?: return@collectLatest
                    webRTCClient.setLocalDescription(answer)

                    // Send answer back to parent
                    webRTCClient.addTrackToPeerConnection()
                    signalingService.sendAnswer(
                        sid,
                        AnswerPayload(answer.description, answer.type.name)
                    )
                    Timber.i("Answer sent for session $sid")
                }
            }

            // Listen for ICE candidates from parent
            serviceScope.launch {
                signalingService.listenForIceCandidates(sid).collectLatest { payload ->
                    if (payload.candidate != null) {
                        webRTCClient.addIceCandidate(
                            IceCandidate(payload.sdpMid ?: "", payload.sdpMLineIndex ?: 0, payload.candidate)
                        )
                    }
                }
            }

            Timber.i("Stream started successfully for session $sid")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start stream")
            stopStream()
        }
    }

    private fun stopStream() {
        if (!isStreaming) return
        isStreaming = false
        Timber.d("Stopping stream")

        signalingJob?.cancel()
        commandsJob?.cancel()

        try {
            webRTCClient.stopVideoCapture()
            webRTCClient.stopAudioCapture()
            webRTCClient.dispose()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping stream")
        }

        releaseWakeLock()
        updateNotification("Streaming stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleEmergencyStop() {
        Timber.w("Emergency stop initiated")

        signalingJob?.cancel()
        commandsJob?.cancel()

        if (::webRTCClient.isInitialized) {
            try {
                webRTCClient.stopVideoCapture()
                webRTCClient.stopAudioCapture()
                webRTCClient.dispose()
            } catch (e: Exception) {
                Timber.e(e, "Error during emergency cleanup")
            }
        }

        releaseWakeLock()
        isStreaming = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Family Connect Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitor service notification for Family Connect"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val stopIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_EMERGENCY_STOP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Family Connect")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Emergency Stop",
                stopIntent
            )
            .setContentIntent(launchIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = createNotification(content)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FamilyConnect:MonitorWakeLock"
            )
        }
        wakeLock?.acquire(30 * 60 * 1000L)
        Timber.d("WakeLock acquired")
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Timber.d("WakeLock released")
        }
    }

    private fun registerEmergencyReceiver() {
        emergencyReceiver = EmergencyStopReceiver()
        val filter = IntentFilter(ACTION_EMERGENCY_STOP)
        ContextCompat.registerReceiver(
            this,
            emergencyReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun defaultIceServers(): List<org.webrtc.PeerConnection.IceServer> {
        return listOf(
            org.webrtc.PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer(),
            org.webrtc.PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302")
                .createIceServer()
        )
    }

    override fun onDestroy() {
        Timber.d("ForegroundMonitorService onDestroy")
        signalingJob?.cancel()
        commandsJob?.cancel()
        serviceScope.cancel()
        if (isStreaming) {
            stopStream()
        }
        emergencyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering emergency receiver")
            }
        }
        eglBase?.release()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

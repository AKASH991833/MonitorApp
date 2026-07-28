package com.familyconnect.app.ui.parent.liveview

import android.app.Application
import android.graphics.Bitmap
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.remote.AnswerPayload
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.remote.IceCandidatePayload
import com.familyconnect.app.data.remote.OfferPayload
import com.familyconnect.app.data.remote.SignalingService
import com.familyconnect.app.data.repository.AppRepository
import com.familyconnect.app.webrtc.WebRTCClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import timber.log.Timber
import java.util.UUID
import kotlin.math.pow

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

class LiveViewViewModel(application: Application) : AndroidViewModel(application) {

    lateinit var childId: String

    private val firebaseSource = FirebaseSource()
    private val signalingService = SignalingService()
    private val repository: AppRepository
    
    private val eglBase = EglBase.create()
    val eglContext: EglBase.Context = eglBase.eglBaseContext
    
    private val webRTCClient = WebRTCClient(application, eglContext)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isCameraFront = MutableStateFlow(true)
    val isCameraFront: StateFlow<Boolean> = _isCameraFront.asStateFlow()

    val sessionId: String = UUID.randomUUID().toString()

    private var reconnectionAttempts = 0
    private var autoTimeoutJob: Job? = null
    private val maxReconnectAttempts = 3

    init {
        val context = application.applicationContext
        val database = AppDatabase.getInstance(context)
        val prefs = context.getSharedPreferences("family_connect_prefs", Application.MODE_PRIVATE)
        repository = AppRepository(database, firebaseSource, prefs)
    }

    fun initialize(childId: String) {
        this.childId = childId
    }

    fun startStream() {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                
                webRTCClient.setCallbacks(
                    onIceCandidate = { candidate ->
                        viewModelScope.launch {
                            signalingService.sendIceCandidate(
                                sessionId,
                                IceCandidatePayload(candidate.sdp, candidate.sdpMLineIndex, candidate.sdpMid)
                            )
                        }
                    },
                    onConnectionChange = { state ->
                        _connectionState.value = when (state) {
                            PeerConnection.PeerConnectionState.CONNECTED -> ConnectionState.CONNECTED
                            PeerConnection.PeerConnectionState.DISCONNECTED, 
                            PeerConnection.PeerConnectionState.FAILED -> ConnectionState.ERROR
                            else -> ConnectionState.CONNECTING
                        }
                    },
                    onAddStream = { stream ->
                        _remoteVideoTrack.value = stream.videoTracks.firstOrNull()
                    }
                )

                webRTCClient.initializePeerConnection(defaultIceServers())
                
                // 1. Create Offer
                val offer = webRTCClient.createOffer() ?: throw Exception("Failed to create offer")
                webRTCClient.setLocalDescription(offer)
                
                // 2. Send Offer to Child via Firebase
                signalingService.sendOffer(sessionId, OfferPayload(offer.description, offer.type.name))
                firebaseSource.sendFcmCommand(childId, "start_stream", sessionId)

                // 3. Listen for Answer
                launch {
                    signalingService.listenForAnswer(sessionId).collectLatest { answerPayload ->
                        val sdp = SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(answerPayload.type?.lowercase() ?: "answer"),
                            answerPayload.sdp
                        )
                        webRTCClient.setRemoteDescription(sdp)
                    }
                }

                // 4. Listen for ICE Candidates
                launch {
                    signalingService.listenForIceCandidates(sessionId).collectLatest { payload ->
                        if (payload.candidate != null) {
                            webRTCClient.addIceCandidate(
                                IceCandidate(payload.sdpMid ?: "", payload.sdpMLineIndex ?: 0, payload.candidate)
                            )
                        }
                    }
                }

                startAutoTimeout()
            } catch (e: Exception) {
                Timber.e(e, "Failed to start stream")
                handleConnectionError()
            }
        }
    }

    private fun defaultIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    }

    fun stopStream() {
        viewModelScope.launch {
            try {
                firebaseSource.sendFcmCommand(childId, "stop")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop stream")
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
                autoTimeoutJob?.cancel()
            }
        }
    }

    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
    }

    fun switchCamera() {
        _isCameraFront.value = !_isCameraFront.value
    }

    fun captureScreenshot() {
        Timber.d("Screenshot capture requested")
        viewModelScope.launch {
            try {
                val view = webRTCClient.remoteSurfaceView
                if (view != null && view.width > 0 && view.height > 0) {
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    view.draw(canvas)
                    val file = java.io.File(
                        getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "screenshot_${System.currentTimeMillis()}.png"
                    )
                    file.parentFile?.mkdirs()
                    java.io.FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                    Timber.i("Screenshot saved to ${file.absolutePath}")
                } else {
                    Timber.w("Cannot capture screenshot: view not ready")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to capture screenshot")
            }
        }
    }

    private fun handleConnectionError() {
        if (reconnectionAttempts < maxReconnectAttempts) {
            reconnectionAttempts++
            val delayMs = (1000L * 2.0.pow(reconnectionAttempts.toDouble())).toLong()
            viewModelScope.launch {
                delay(delayMs)
                startStream()
            }
        } else {
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun startAutoTimeout() {
        autoTimeoutJob?.cancel()
        autoTimeoutJob = viewModelScope.launch {
            val timeoutMinutes = 15L
            delay(timeoutMinutes * 60 * 1000L)
            if (isActive) {
                stopStream()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webRTCClient.dispose()
        eglBase.release()
        autoTimeoutJob?.cancel()
    }
}

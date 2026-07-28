package com.familyconnect.app.webrtc

import android.content.Context
import android.util.Size
import android.view.View
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters
import org.webrtc.RtpSender
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoEncoderFactory
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import timber.log.Timber

enum class StreamQuality {
    LOW, SD, HD
}

class WebRTCClient(
    private val context: Context,
    private val eglBaseContext: EglBase.Context
) {

    companion object {
        private const val TAG = "WebRTCClient"
        private const val VIDEO_CODEC_VP8 = "VP8"
        private const val VIDEO_FPS = 15
        private const val SD_BITRATE_BPS = 500_000
        private const val HD_BITRATE_BPS = 1_500_000
        private const val LOW_BITRATE_BPS = 250_000

        private val VIDEO_RESOLUTIONS = mapOf(
            StreamQuality.LOW to Size(320, 240),
            StreamQuality.SD to Size(640, 480),
            StreamQuality.HD to Size(1280, 720)
        )
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null
    var videoSource: VideoSource? = null
    var audioSource: AudioSource? = null
    var videoTrack: VideoTrack? = null
    var audioTrack: AudioTrack? = null
    var localSurfaceView: SurfaceViewRenderer? = null
    var remoteSurfaceView: SurfaceViewRenderer? = null

    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var audioManager: org.webrtc.audio.AudioDeviceModule? = null
    private var streamQuality: StreamQuality = StreamQuality.SD
    private var isVideoCapturing = false
    private var isAudioCapturing = false
    private var isMicMuted = false
    private var remoteStream: MediaStream? = null

    private var onIceCandidateCallback: ((IceCandidate) -> Unit)? = null
    private var onIceConnectionChangeCallback: ((PeerConnection.IceConnectionState) -> Unit)? = null
    private var onConnectionChangeCallback: ((PeerConnection.PeerConnectionState) -> Unit)? = null
    private var onAddStreamCallback: ((MediaStream) -> Unit)? = null
    private var onRemoveStreamCallback: ((MediaStream) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    fun setCallbacks(
        onIceCandidate: ((IceCandidate) -> Unit)? = null,
        onIceConnectionChange: ((PeerConnection.IceConnectionState) -> Unit)? = null,
        onConnectionChange: ((PeerConnection.PeerConnectionState) -> Unit)? = null,
        onAddStream: ((MediaStream) -> Unit)? = null,
        onRemoveStream: ((MediaStream) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        onIceCandidateCallback = onIceCandidate
        onIceConnectionChangeCallback = onIceConnectionChange
        onConnectionChangeCallback = onConnectionChange
        onAddStreamCallback = onAddStream
        onRemoveStreamCallback = onRemoveStream
        onErrorCallback = onError
    }

    fun initializePeerConnection(iceServers: List<PeerConnection.IceServer>) {
        if (peerConnectionFactory != null) {
            Timber.d("PeerConnectionFactory already initialized, reusing")
        } else {
            Timber.d("Initializing PeerConnection")
        }

        val encoderFactory: VideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val decoderFactory: VideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        audioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)

        videoSource = peerConnectionFactory?.createVideoSource(false)
        val videoConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("minWidth", "320"))
            mandatory.add(MediaConstraints.KeyValuePair("minHeight", "240"))
            mandatory.add(MediaConstraints.KeyValuePair("maxWidth", "1280"))
            mandatory.add(MediaConstraints.KeyValuePair("maxHeight", "720"))
            mandatory.add(MediaConstraints.KeyValuePair("minFrameRate", "10"))
            mandatory.add(MediaConstraints.KeyValuePair("maxFrameRate", VIDEO_FPS.toString()))
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 10
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceConnectionReceivingTimeout = 15
            iceBackupCandidatePairPingInterval = 3000
            keyType = PeerConnection.KeyType.ECDSA
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Timber.d("onIceCandidate: $candidate")
                onIceCandidateCallback?.invoke(candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
                Timber.d("onIceCandidatesRemoved: ${candidates.size}")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Timber.d("onIceConnectionChange: $state")
                onIceConnectionChangeCallback?.invoke(state)
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Timber.d("onIceConnectionReceivingChange: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Timber.d("onIceGatheringChange: $state")
            }

            override fun onAddStream(stream: MediaStream) {
                Timber.d("onAddStream: ${stream.videoTracks.size} video tracks, ${stream.audioTracks.size} audio tracks")
                remoteStream = stream
                onAddStreamCallback?.invoke(stream)
            }

            override fun onRemoveStream(stream: MediaStream) {
                Timber.d("onRemoveStream")
                remoteStream = null
                onRemoveStreamCallback?.invoke(stream)
            }

            override fun onDataChannel(channel: DataChannel) {
                Timber.d("onDataChannel: ${channel.label()}")
            }

            override fun onRenegotiationNeeded() {
                Timber.d("onRenegotiationNeeded")
            }

            override fun onAddTrack(track: org.webrtc.RtpReceiver, streams: Array<out MediaStream>) {
                Timber.d("onAddTrack: ${track.track()?.kind()}")
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Timber.d("onConnectionChange: $state")
                onConnectionChangeCallback?.invoke(state)
            }

            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Timber.d("onStandardizedIceConnectionChange: $state")
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                Timber.d("onSignalingChange: $state")
            }
        })

        if (peerConnection == null) {
            val error = "Failed to create PeerConnection"
            Timber.e(error)
            onErrorCallback?.invoke(error)
            return
        }

        val stream = peerConnectionFactory?.createLocalMediaStream("local_stream")
        videoTrack?.let { stream?.addTrack(it) }
        audioTrack?.let { stream?.addTrack(it) }

        Timber.i("PeerConnection initialized successfully")
    }

    fun createOffer(): SessionDescription? {
        Timber.d("Creating SDP offer")

        val sdpMediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        }

        var result: SessionDescription? = null
        val semaphore = java.util.concurrent.Semaphore(0)

        peerConnection?.createOffer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Timber.d("SDP offer created successfully")
                result = sessionDescription
                semaphore.release()
            }

            override fun onCreateFailure(error: String) {
                Timber.e("SDP offer creation failed: $error")
                onErrorCallback?.invoke("Failed to create offer: $error")
                semaphore.release()
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, sdpMediaConstraints)

        semaphore.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)
        return result
    }

    fun createAnswer(): SessionDescription? {
        Timber.d("Creating SDP answer")

        val sdpMediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        }

        var result: SessionDescription? = null
        val semaphore = java.util.concurrent.Semaphore(0)

        peerConnection?.createAnswer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Timber.d("SDP answer created successfully")
                result = sessionDescription
                semaphore.release()
            }

            override fun onCreateFailure(error: String) {
                Timber.e("SDP answer creation failed: $error")
                onErrorCallback?.invoke("Failed to create answer: $error")
                semaphore.release()
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, sdpMediaConstraints)

        semaphore.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS)
        return result
    }

    fun setLocalDescription(sdp: SessionDescription) {
        Timber.d("Setting local description: ${sdp.type}")

        val adjustedSdp = applyBitrateConstraints(sdp.description)

        val modifiedSdp = SessionDescription(sdp.type, adjustedSdp)
        peerConnection?.setLocalDescription(object : org.webrtc.SdpObserver {
            override fun onSetSuccess() {
                Timber.d("Local description set successfully")
            }

            override fun onSetFailure(error: String) {
                Timber.e("Failed to set local description: $error")
                onErrorCallback?.invoke("Failed to set local description: $error")
            }

            override fun onCreateSuccess(sessionDescription: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, modifiedSdp)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        Timber.d("Setting remote description: ${sdp.type}")

        peerConnection?.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onSetSuccess() {
                Timber.d("Remote description set successfully")
            }

            override fun onSetFailure(error: String) {
                Timber.e("Failed to set remote description: $error")
                onErrorCallback?.invoke("Failed to set remote description: $error")
            }

            override fun onCreateSuccess(sessionDescription: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        Timber.d("Adding ICE candidate: ${candidate.sdpMid}:${candidate.sdpMLineIndex}")
        peerConnection?.addIceCandidate(candidate)
    }

    private fun applyBitrateConstraints(sdp: String): String {
        val bitrate = when (streamQuality) {
            StreamQuality.LOW -> LOW_BITRATE_BPS
            StreamQuality.SD -> SD_BITRATE_BPS
            StreamQuality.HD -> HD_BITRATE_BPS
        }

        var modifiedSdp = sdp

        val videoCodecPattern = Regex("a=rtpmap:\\d+ $VIDEO_CODEC_VP8/90000")
        val match = videoCodecPattern.find(modifiedSdp)
        if (match != null) {
            val ptLine = match.value
            val ptMatch = Regex("(\\d+)").find(ptLine)
            if (ptMatch != null) {
                val pt = ptMatch.groupValues[1]
                val bitrateLine = "a=fmtp:$pt x-google-max-bitrate=$bitrate;x-google-min-bitrate=${bitrate / 2};x-google-start-bitrate=${bitrate * 3 / 4}"
                modifiedSdp = modifiedSdp.replaceAfter(ptLine, "\n$bitrateLine" + modifiedSdp.substringAfter(ptLine))
            }
        }

        return modifiedSdp
    }

    fun setQuality(quality: StreamQuality) {
        streamQuality = quality
        val resolution = VIDEO_RESOLUTIONS[quality] ?: Size(640, 480)
        Timber.d("Stream quality set to $quality (${resolution.width}x${resolution.height})")

        if (isVideoCapturing) {
            videoCapturer?.let {
                if (it is CameraVideoCapturer) {
                    it.changeCaptureFormat(resolution.width, resolution.height, VIDEO_FPS)
                }
            }
        }
    }

    fun startVideoCapture(): Boolean {
        if (isVideoCapturing) return true
        Timber.d("Starting video capture")

        try {
            val capturer = createVideoCapturer()
            if (capturer == null) {
                val error = "Failed to create video capturer"
                Timber.e(error)
                onErrorCallback?.invoke(error)
                return false
            }

            videoCapturer = capturer
            val resolution = VIDEO_RESOLUTIONS[streamQuality] ?: Size(640, 480)

            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

            videoCapturer?.startCapture(resolution.width, resolution.height, VIDEO_FPS)

            videoTrack = peerConnectionFactory?.createVideoTrack("video_track", videoSource)
            isVideoCapturing = true

            Timber.i("Video capture started at ${resolution.width}x${resolution.height} @${VIDEO_FPS}fps")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start video capture")
            onErrorCallback?.invoke("Video capture error: ${e.message}")
            return false
        }
    }

    fun stopVideoCapture() {
        if (!isVideoCapturing) return
        Timber.d("Stopping video capture")

        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            isVideoCapturing = false
            Timber.i("Video capture stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping video capture")
        }
    }

    fun startAudioCapture(): Boolean {
        if (isAudioCapturing) return true
        Timber.d("Starting audio capture")

        try {
            audioTrack?.setEnabled(true)
            isAudioCapturing = true
            Timber.i("Audio capture started")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start audio capture")
            onErrorCallback?.invoke("Audio capture error: ${e.message}")
            return false
        }
    }

    fun stopAudioCapture() {
        if (!isAudioCapturing) return
        Timber.d("Stopping audio capture")

        try {
            audioTrack?.setEnabled(false)
            isAudioCapturing = false
            Timber.i("Audio capture stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio capture")
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        if (Camera2Enumerator.isSupported(context)) {
            val enumerator = Camera2Enumerator(context)
            return createCameraCapturer(enumerator)
        } else {
            val enumerator = Camera1Enumerator(false)
            return createCameraCapturer(enumerator)
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        val frontCamera = deviceNames.find { enumerator.isFrontFacing(it) }
        val backCamera = deviceNames.find { !enumerator.isFrontFacing(it) }

        val selectedCamera = frontCamera ?: backCamera ?: deviceNames.firstOrNull()

        if (selectedCamera == null) {
            Timber.e("No camera found")
            return null
        }

        Timber.i("Using camera: $selectedCamera (front=${enumerator.isFrontFacing(selectedCamera)})")
        return enumerator.createCapturer(selectedCamera, null)
    }

    fun switchCamera() {
        val capturer = videoCapturer as? CameraVideoCapturer
        if (capturer != null) {
            try {
                capturer.switchCamera(null)
                Timber.d("Camera switched")
            } catch (e: Exception) {
                Timber.e(e, "Failed to switch camera")
            }
        }
    }

    fun toggleMic(mute: Boolean) {
        isMicMuted = mute
        audioTrack?.setEnabled(!mute)
        Timber.d("Mic ${if (mute) "muted" else "unmuted"}")
    }

    fun isMicMuted(): Boolean = isMicMuted

    fun getRemoteVideoTrack(): VideoTrack? {
        return remoteStream?.videoTracks?.firstOrNull()
    }

    fun getRemoteAudioTrack(): AudioTrack? {
        return remoteStream?.audioTracks?.firstOrNull()
    }

    fun initSurfaceView(context: Context): SurfaceViewRenderer {
        val surfaceView = SurfaceViewRenderer(context).apply {
            setMirror(false)
            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
        remoteSurfaceView = surfaceView
        return surfaceView
    }

    fun initLocalSurfaceView(context: Context): SurfaceViewRenderer {
        val surfaceView = SurfaceViewRenderer(context).apply {
            setMirror(true)
            setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            init(eglBaseContext, null)
        }
        localSurfaceView = surfaceView
        return surfaceView
    }

    fun attachRemoteVideo() {
        val track = getRemoteVideoTrack()
        val view = remoteSurfaceView
        if (track != null && view != null) {
            track.addSink(view)
            Timber.d("Remote video track attached to surface view")
        }
    }

    fun attachLocalVideo() {
        val track = videoTrack
        val view = localSurfaceView
        if (track != null && view != null) {
            track.addSink(view)
            Timber.d("Local video track attached to surface view")
        }
    }

    fun detachRemoteVideo() {
        val track = getRemoteVideoTrack()
        val view = remoteSurfaceView
        if (track != null && view != null) {
            track.removeSink(view)
        }
    }

    fun detachLocalVideo() {
        val track = videoTrack
        val view = localSurfaceView
        if (track != null && view != null) {
            track.removeSink(view)
        }
    }

    fun addTrackToPeerConnection() {
        videoTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local_stream"))
        }
        audioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("local_stream"))
        }
    }

    fun setMaxBitrate(bitrateBps: Int) {
        val senders = peerConnection?.senders ?: return
        for (sender in senders) {
            val track = sender.track()
            if (track != null && track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                val parameters = sender.parameters
                if (parameters.encodings.isNotEmpty()) {
                    for (encoding in parameters.encodings) {
                        encoding.maxBitrateBps = bitrateBps
                        encoding.minBitrateBps = bitrateBps / 2
                    }
                    sender.parameters = parameters
                    Timber.d("Bitrate set to ${bitrateBps}bps for video sender")
                }
            }
        }
    }

    fun dispose() {
        Timber.d("Disposing WebRTCClient")

        try {
            stopVideoCapture()
            stopAudioCapture()

            detachRemoteVideo()
            detachLocalVideo()

            remoteSurfaceView?.release()
            localSurfaceView?.release()
            remoteSurfaceView = null
            localSurfaceView = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            videoTrack?.dispose()
            videoTrack = null
            audioTrack?.dispose()
            audioTrack = null

            videoSource?.dispose()
            videoSource = null
            audioSource?.dispose()
            audioSource = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            remoteStream = null
            isVideoCapturing = false
            isAudioCapturing = false

            Timber.i("WebRTCClient disposed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error during WebRTCClient disposal")
        }
    }

    fun isActive(): Boolean = peerConnection != null
}

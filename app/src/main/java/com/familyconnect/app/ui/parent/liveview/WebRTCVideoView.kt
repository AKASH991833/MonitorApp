package com.familyconnect.app.ui.parent.liveview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun WebRTCVideoView(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglContext, null)
                setScalingType(scalingType)
                setMirror(mirror)
                setEnableHardwareScaler(true)
            }
        },
        update = { view ->
            videoTrack?.addSink(view)
        },
        onRelease = { view ->
            videoTrack?.removeSink(view)
            view.release()
        }
    )
}

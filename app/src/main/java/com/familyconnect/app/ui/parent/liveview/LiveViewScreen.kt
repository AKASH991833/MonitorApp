package com.familyconnect.app.ui.parent.liveview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LiveViewScreen(
    childId: String,
    onStop: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val viewModel: LiveViewViewModel = viewModel()

    LaunchedEffect(childId) {
        viewModel.initialize(childId)
        viewModel.startStream()
    }

    val connectionState by viewModel.connectionState.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isCameraFront by viewModel.isCameraFront.collectAsState()
    var showStopDialog by remember { mutableStateOf(false) }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop Stream") },
            text = { Text("Are you sure you want to stop the live stream?") },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    viewModel.stopStream()
                    onStop()
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (connectionState) {
                ConnectionState.CONNECTING -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting...", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                ConnectionState.CONNECTED -> {
                    WebRTCVideoView(
                        videoTrack = remoteVideoTrack,
                        eglContext = viewModel.eglContext,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            "LIVE",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                ConnectionState.DISCONNECTED -> {
                    Text("Disconnected", color = Color.Gray, style = MaterialTheme.typography.titleLarge)
                }
                ConnectionState.ERROR -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Connection Error", color = Color.Red, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.startStream() }) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Child", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Live"
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.DISCONNECTED -> "Disconnected"
                        ConnectionState.ERROR -> "Error"
                    },
                    color = when (connectionState) {
                        ConnectionState.CONNECTED -> Color.Green
                        ConnectionState.ERROR -> Color.Red
                        else -> Color.Yellow
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.switchCamera() }) {
                    Icon(Icons.Filled.CameraAlt, "Switch Camera", tint = Color.White)
                }
                IconButton(onClick = { viewModel.toggleMic() }) {
                    Icon(
                        if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        "Mic",
                        tint = if (isMicMuted) Color.Red else Color.White
                    )
                }
                IconButton(onClick = { viewModel.captureScreenshot() }) {
                    Icon(Icons.Filled.PhotoCamera, "Screenshot", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showStopDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

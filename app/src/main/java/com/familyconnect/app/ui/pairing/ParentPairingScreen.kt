package com.familyconnect.app.ui.pairing

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale

@Composable
fun ParentPairingScreen(
    viewModel: PairingViewModel,
    onPaired: () -> Unit,
    onBack: () -> Unit
) {
    val code by viewModel.code.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isPaired by viewModel.isPaired.collectAsState()
    val countdownTime by viewModel.countdownTime.collectAsState()

    var parentName by remember { mutableStateOf("") }

    LaunchedEffect(isPaired) {
        if (isPaired) {
            onPaired()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pair with Your Child",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Generate a code so your child can connect to your device",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = parentName,
            onValueChange = { parentName = it },
            label = { Text("Your Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.generatePairingCode(parentName) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = parentName.isNotBlank() && !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Generate Code", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (code.isNotBlank()) {
            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pairing Code",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = code,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val clipboardManager = LocalClipboardManager.current
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Code")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val context = LocalContext.current
                    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.familyconnect.app&code=$code"
                    val qrBitmap = remember(playStoreUrl) {
                        generateQrCode(playStoreUrl)
                    }
                    qrBitmap?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Scan with Google Camera → Play Store opens\nThen open app & enter code below manually",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT,
                                    "Install Family Connect to pair with me!\n" +
                                    "Download: https://play.google.com/store/apps/details?id=com.familyconnect.app\n" +
                                    "Pairing Code: $code"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share with child"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share App Link")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Code expires in:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val minutes = countdownTime / 60
                    val seconds = countdownTime % 60
                    Text(
                        text = String.format(Locale.US, "%d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (countdownTime <= 60) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { countdownTime / 600f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = if (countdownTime <= 60) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        error?.let { errorMessage ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun generateQrCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300)
        val bw = bitMatrix.width
        val bh = bitMatrix.height
        val border = 20
        val newW = bw + border * 2
        val newH = bh + border * 2
        val bitmap = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        for (x in 0 until newW) {
            for (y in 0 until newH) {
                val inQr = x >= border && x < bw + border && y >= border && y < bh + border
                val qrX = x - border
                val qrY = y - border
                bitmap.setPixel(
                    x, y,
                    if (inQr && bitMatrix[qrX, qrY]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

package com.familyconnect.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.repository.AppRepository
import com.familyconnect.app.ui.navigation.AppNavGraph
import com.familyconnect.app.ui.theme.FamilyConnectTheme
import com.familyconnect.app.util.Constants
import org.webrtc.PeerConnectionFactory
import timber.log.Timber

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.tag(TAG).d("MainActivity onCreate")
        
        val repository = AppRepository(
            AppDatabase.getInstance(applicationContext),
            FirebaseSource(),
            getSharedPreferences(Constants.SHARED_PREFS_NAME, android.content.Context.MODE_PRIVATE)
        )
        
        try {
            enableEdgeToEdge()
            
            // Safe WebRTC Initialization
            initializeWebRTC()
            
            setContent {
                val themeMode by repository.themeMode.collectAsState(initial = "system")
                val isDark = when (themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                }

                FamilyConnectTheme(darkTheme = isDark) {
                    AppNavGraph()
                }
            }
            
            checkAndRequestPermissions()
            handleDeepLink(intent)
            
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Critical error in MainActivity onCreate")
            try {
                setContent {
                    FamilyConnectTheme {
                        androidx.compose.material3.Text("Failed to initialize app: ${e.message}")
                    }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun initializeWebRTC() {
        if (isWebRTCInitialized) return
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)
            isWebRTCInitialized = true
            Timber.tag(TAG).i("WebRTC initialized successfully")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to initialize WebRTC")
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Timber.tag(TAG).d("Requesting permissions: $missingPermissions")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result.forEach { (permission, isGranted) ->
            if (isGranted) {
                Timber.tag(TAG).d("Permission $permission granted")
            } else {
                Timber.tag(TAG).w("Permission $permission denied")
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        val code = when {
            uri.host == "github.com" && uri.path?.contains("/releases/download/") == true ->
                uri.getQueryParameter("code")
            uri.scheme == "familyconnect" && uri.host == "pair" ->
                uri.getQueryParameter("code")
            uri.scheme == "https" && uri.host == "familyconnect.app" && uri.path?.startsWith("/pair") == true ->
                uri.getQueryParameter("code")
            else -> null
        }
        if (code != null) {
            Timber.tag(TAG).i("Deep link received with code: $code")
            pendingPairingCode = code
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private var isWebRTCInitialized = false
        var pendingPairingCode: String? = null
    }
}

package com.familyconnect.app.ui.splash

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyconnect.app.data.model.UserRole
import com.familyconnect.app.ui.navigation.NavRoutes
import com.familyconnect.app.util.Constants
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun SplashScreen(
    onNavigation: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            com.google.firebase.FirebaseApp.initializeApp(context)
            Timber.i("Firebase initialized in splash")
        } catch (e: Exception) {
            Timber.w(e, "Firebase not available - app will work in local mode")
        }
        delay(1500L)
        val prefs = context.getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val roleName = prefs.getString(Constants.KEY_ROLE, null)
        val destination = when (roleName) {
            UserRole.PARENT.name -> NavRoutes.ParentDashboard.route
            UserRole.CHILD.name -> NavRoutes.ChildIdle.route
            else -> NavRoutes.Onboarding.route
        }
        Timber.d("SplashScreen navigating to $destination")
        onNavigation(destination)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "Family Connect",
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Family Connect",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "v2.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }
    }
}

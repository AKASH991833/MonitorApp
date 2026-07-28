package com.familyconnect.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.model.UserRole
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.repository.AppRepository
import com.familyconnect.app.service.ChildStatusService
import com.familyconnect.app.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs: android.content.SharedPreferences

    val streamQuality: StateFlow<String>
    val autoTimeout: StateFlow<Int>
    val locationEnabled: StateFlow<Boolean>
    val darkThemeEnabled: StateFlow<Boolean>
    val autoStartEnabled: StateFlow<Boolean>
    val currentRole: MutableStateFlow<UserRole?>

    // New settings
    private val _sosEnabled = MutableStateFlow(true)
    val sosEnabled: StateFlow<Boolean> = _sosEnabled.asStateFlow()

    private val _ambientAudio = MutableStateFlow(false)
    val ambientAudio: StateFlow<Boolean> = _ambientAudio.asStateFlow()

    private val _appUsageEnabled = MutableStateFlow(false)
    val appUsageEnabled: StateFlow<Boolean> = _appUsageEnabled.asStateFlow()

    init {
        val context = application.applicationContext
        val database = AppDatabase.getInstance(context)
        val firebaseSource = FirebaseSource()
        prefs = context.getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        repository = AppRepository(database, firebaseSource, prefs)

        streamQuality = repository.streamQuality.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), "SD"
        )
        autoTimeout = repository.autoTimeout.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), 15
        )
        locationEnabled = repository.locationEnabled.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), true
        )
        darkThemeEnabled = repository.themeMode.map { it == "dark" }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), false
        )
        autoStartEnabled = repository.autoStartEnabled.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), true
        )

        val roleName = prefs.getString(Constants.KEY_ROLE, null)
        currentRole = MutableStateFlow(
            when (roleName) {
                UserRole.PARENT.name -> UserRole.PARENT
                UserRole.CHILD.name -> UserRole.CHILD
                else -> null
            }
        )

        _sosEnabled.value = prefs.getBoolean(Constants.KEY_SOS_ENABLED, true)
        _ambientAudio.value = prefs.getBoolean(Constants.KEY_AMBIENT_AUDIO, false)
        _appUsageEnabled.value = prefs.getBoolean(Constants.KEY_APP_USAGE_ENABLED, false)
    }

    fun setStreamQuality(quality: String) {
        repository.setStreamQuality(quality)
    }

    fun setAutoTimeout(minutes: Int) {
        repository.setAutoTimeout(minutes)
    }

    fun setLocationEnabled(enabled: Boolean) {
        repository.setLocationEnabled(enabled)
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        repository.setThemeMode(if (enabled) "dark" else "light")
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        repository.setAutoStartEnabled(enabled)
    }

    fun setSosEnabled(enabled: Boolean) {
        _sosEnabled.value = enabled
        prefs.edit().putBoolean(Constants.KEY_SOS_ENABLED, enabled).apply()
    }

    fun setAmbientAudio(enabled: Boolean) {
        _ambientAudio.value = enabled
        prefs.edit().putBoolean(Constants.KEY_AMBIENT_AUDIO, enabled).apply()
    }

    fun setAppUsageEnabled(enabled: Boolean) {
        _appUsageEnabled.value = enabled
        prefs.edit().putBoolean(Constants.KEY_APP_USAGE_ENABLED, enabled).apply()
    }

    fun switchRole() {
        ChildStatusService.stop(getApplication())
        viewModelScope.launch {
            try {
                repository.clearLocalData()
                val newRole = when (currentRole.value) {
                    UserRole.PARENT -> UserRole.CHILD
                    UserRole.CHILD -> UserRole.PARENT
                    null -> UserRole.PARENT
                }
                repository.setUserRole(newRole)
                currentRole.value = newRole
            } catch (e: Exception) {
                Timber.e(e, "Failed to switch role")
            }
        }
    }

    fun logout() {
        ChildStatusService.stop(getApplication())
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (e: Exception) {
                Timber.e(e, "Failed to logout")
            }
        }
    }
}

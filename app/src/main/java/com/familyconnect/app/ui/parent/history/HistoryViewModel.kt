package com.familyconnect.app.ui.parent.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.model.SessionRecord
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessions: StateFlow<List<SessionRecord>> = _sessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())

    init {
        val context = application.applicationContext
        val database = AppDatabase.getInstance(context)
        val firebaseSource = FirebaseSource()
        val prefs = context.getSharedPreferences("family_connect_prefs", Application.MODE_PRIVATE)
        repository = AppRepository(database, firebaseSource, prefs)
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _sessions.value = repository.getSessionHistory().sortedByDescending { it.startTime }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load session history")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun formatDate(timestamp: Long): String = dateFormatter.format(Date(timestamp))

    fun formatTime(timestamp: Long): String = timeFormatter.format(Date(timestamp))

    fun formatDuration(millis: Long?): String {
        if (millis == null) return "--"
        val minutes = millis / 60000
        val hours = minutes / 60
        val remMinutes = minutes % 60
        return if (hours > 0) "${hours}h ${remMinutes}m" else "${remMinutes}m"
    }
}

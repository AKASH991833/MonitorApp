package com.familyconnect.app.ui.child

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.repository.AppRepository
import com.familyconnect.app.service.ChildStatusService
import com.familyconnect.app.service.ForegroundMonitorService
import com.familyconnect.app.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class ChildIdleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val firebaseSource = FirebaseSource()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _parentName = MutableStateFlow("")
    val parentName: StateFlow<String> = _parentName.asStateFlow()

    private var commandsListener: ChildEventListener? = null

    init {
        val context = application.applicationContext
        val database = AppDatabase.getInstance(context)
        val prefs = context.getSharedPreferences(Constants.SHARED_PREFS_NAME, Application.MODE_PRIVATE)
        repository = AppRepository(database, firebaseSource, prefs)

        viewModelScope.launch {
            try {
                val paired = repository.getPairedChildren()
                if (paired.isNotEmpty()) {
                    _parentName.value = paired.first().childName
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load paired info")
            }
        }

        startCommandListener()
        ChildStatusService.start(getApplication())
    }

    private fun startCommandListener() {
        viewModelScope.launch {
            try {
                val userId = firebaseSource.getCurrentUserId()
                val commandsRef = firebaseSource.getCommandsRef().child(userId)

                val listener = object : ChildEventListener {
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                        val command = snapshot.child("command").value as? String ?: return
                        val sessionId = snapshot.child("sessionId").value as? String
                        Timber.d("Child received command: $command sessionId=$sessionId")

                        viewModelScope.launch {
                            when (command) {
                                "start_stream" -> {
                                    val sid = sessionId ?: "unknown"
                                    _isStreaming.value = true
                                    ForegroundMonitorService.startStream(
                                        getApplication(),
                                        sid
                                    )
                                }
                                "stop" -> {
                                    _isStreaming.value = false
                                    ForegroundMonitorService.stopStream(getApplication())
                                }
                                "ping" -> {
                                    repository.updateChildStatus(userId, true)
                                }
                            }
                        }
                        // Remove processed command from RTDB
                        snapshot.ref.removeValue()
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {
                        Timber.e(error.toException(), "Commands listener cancelled")
                    }
                }
                commandsRef.addChildEventListener(listener)
                commandsListener = listener
                Timber.i("Commands listener started for user $userId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start commands listener")
            }
        }
    }

    fun emergencyStop() {
        val context = getApplication<Application>()
        ForegroundMonitorService.emergencyStop(context)

        viewModelScope.launch {
            try {
                val childId = FirebaseAuth.getInstance().currentUser?.uid
                if (childId != null) {
                    repository.sendStopCommand(childId)
                }
                _isStreaming.value = false
            } catch (e: Exception) {
                Timber.e(e, "Failed to send emergency stop")
            }
        }
    }

    fun sendSosAlert() {
        viewModelScope.launch {
            try {
                val childId = firebaseSource.getCurrentUserId()
                val paired = repository.getPairedChildren()
                if (paired.isNotEmpty()) {
                    val sosData = mapOf(
                        "childId" to childId,
                        "childName" to paired.first().childName,
                        "timestamp" to System.currentTimeMillis(),
                        "message" to "SOS! I need help!"
                    )
                    firebaseSource.getSosAlertsRef()
                        .child(paired.first().childId)
                        .push()
                        .setValue(sosData)
                    Timber.i("SOS alert sent to parent ${paired.first().childId}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to send SOS alert")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            commandsListener?.let {
                try {
                    val userId = firebaseSource.getCurrentUserId()
                    firebaseSource.getCommandsRef().child(userId).removeEventListener(it)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to remove commands listener")
                }
            }
        }
    }

    fun updateStreamingStatus(isStreaming: Boolean) {
        _isStreaming.value = isStreaming
    }

    override fun onCleared() {
        super.onCleared()
        ChildStatusService.stop(getApplication())
        viewModelScope.launch {
            commandsListener?.let {
                try {
                    val userId = firebaseSource.getCurrentUserId()
                    firebaseSource.getCommandsRef().child(userId).removeEventListener(it)
                } catch (e: Exception) {
                    Timber.e(e, "Error removing commands listener")
                }
            }
        }
    }
}

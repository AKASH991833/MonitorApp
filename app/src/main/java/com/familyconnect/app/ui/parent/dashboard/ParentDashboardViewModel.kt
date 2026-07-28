package com.familyconnect.app.ui.parent.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.model.PairedChild
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.repository.AppRepository
import com.familyconnect.app.util.Constants
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class ChildStatusInfo(
    val battery: Int = -1,
    val isCharging: Boolean = false,
    val network: String = "unknown",
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracy: Float? = null,
    val lastSeen: Long = 0L
)

data class SosAlert(
    val childId: String = "",
    val childName: String = "",
    val timestamp: Long = 0L,
    val message: String = ""
)

class ParentDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val firebaseSource = FirebaseSource()

    private val _pairedChildren = MutableStateFlow<List<PairedChild>>(emptyList())
    val pairedChildren: StateFlow<List<PairedChild>> = _pairedChildren.asStateFlow()

    private val _childStatuses = MutableStateFlow<Map<String, ChildStatusInfo>>(emptyMap())
    val childStatuses: StateFlow<Map<String, ChildStatusInfo>> = _childStatuses.asStateFlow()

    private val _sosAlerts = MutableStateFlow<List<SosAlert>>(emptyList())
    val sosAlerts: StateFlow<List<SosAlert>> = _sosAlerts.asStateFlow()

    private val _hasUnreadSos = MutableStateFlow(false)
    val hasUnreadSos: StateFlow<Boolean> = _hasUnreadSos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val statusListeners = mutableMapOf<String, ValueEventListener>()
    private var sosListener: ChildEventListener? = null

    init {
        val context = application.applicationContext
        val database = AppDatabase.getInstance(context)
        val prefs = context.getSharedPreferences(Constants.SHARED_PREFS_NAME, Application.MODE_PRIVATE)
        repository = AppRepository(database, firebaseSource, prefs)
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val children = repository.getPairedChildren()
                _pairedChildren.value = children
                children.forEach { child ->
                    startListeningForChildStatus(child.childId)
                }
                startListeningForSosAlerts()
                startListeningForNewPairedChildren()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load paired children")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startListeningForNewPairedChildren() {
        viewModelScope.launch {
            try {
                val parentId = firebaseSource.getCurrentUserId()
                firebaseSource.listenForParentChildren(parentId).collect { snapshot ->
                    snapshot.children.forEach { childSnapshot ->
                        val childId = childSnapshot.key ?: return@forEach
                        val childName = childSnapshot.child("childName").value as? String ?: return@forEach
                        val existing = _pairedChildren.value.find { it.childId == childId }
                        if (existing == null) {
                            repository.addPairedChildFromNotification(childId, childName)
                            val updated = repository.getPairedChildren()
                            _pairedChildren.value = updated
                            startListeningForChildStatus(childId)
                            Timber.i("New child paired: $childName ($childId)")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to listen for new paired children")
            }
        }
    }

    private fun startListeningForChildStatus(childId: String) {
        val ref = firebaseSource.getDevicesRef().child(childId).child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val battery = snapshot.child("battery").value as? Long ?: -1
                val isCharging = snapshot.child("isCharging").value as? Boolean ?: false
                val network = snapshot.child("network").value as? String ?: "unknown"
                val lastSeen = snapshot.child("lastSeen").value as? Long ?: 0L

                val current = _childStatuses.value.toMutableMap()
                current[childId] = ChildStatusInfo(
                    battery = battery.toInt(),
                    isCharging = isCharging,
                    network = network,
                    lastSeen = lastSeen
                )
                _childStatuses.value = current
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException(), "Status listener cancelled for $childId")
            }
        }
        ref.addValueEventListener(listener)
        statusListeners[childId] = listener

        // Also listen for location updates
        val locRef = firebaseSource.getDevicesRef().child(childId).child("location")
        val locListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").value as? Double
                val lng = snapshot.child("lng").value as? Double
                val accuracy = snapshot.child("accuracy").value as? Double
                if (lat != null && lng != null) {
                    val current = _childStatuses.value.toMutableMap()
                    val existing = current[childId] ?: ChildStatusInfo()
                    current[childId] = existing.copy(
                        lat = lat,
                        lng = lng,
                        accuracy = accuracy?.toFloat()
                    )
                    _childStatuses.value = current
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        locRef.addValueEventListener(locListener)
        statusListeners["${childId}_loc"] = locListener
    }

    private fun startListeningForSosAlerts() {
        sosListener?.let {
            try {
                firebaseSource.getSosAlertsRef().removeEventListener(it)
            } catch (e: Exception) {}
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.children.forEach { alertSnapshot ->
                    val childId = alertSnapshot.child("childId").value as? String ?: return@forEach
                    val childName = alertSnapshot.child("childName").value as? String ?: "Unknown"
                    val timestamp = alertSnapshot.child("timestamp").value as? Long ?: 0L
                    val message = alertSnapshot.child("message").value as? String ?: "SOS Alert!"
                    val alert = SosAlert(childId, childName, timestamp, message)
                    val current = _sosAlerts.value.toMutableList()
                    current.add(0, alert)
                    _sosAlerts.value = current
                    _hasUnreadSos.value = true
                    Timber.w("SOS alert from $childName ($childId): $message")
                }
                // Remove processed SOS alerts
                snapshot.ref.removeValue()
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException(), "SOS listener cancelled")
            }
        }
        firebaseSource.getSosAlertsRef().addChildEventListener(listener)
        sosListener = listener
    }

    fun dismissSosAlert(alert: SosAlert) {
        val current = _sosAlerts.value.toMutableList()
        current.remove(alert)
        _sosAlerts.value = current
        if (current.isEmpty()) {
            _hasUnreadSos.value = false
        }
    }

    fun clearSosAlerts() {
        _sosAlerts.value = emptyList()
        _hasUnreadSos.value = false
    }

    fun refreshChildren() {
        loadChildren()
    }

    fun startLiveView(childId: String) {
        viewModelScope.launch {
            try {
                repository.sendWakeCommand(childId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send wake command to child $childId")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statusListeners.forEach { (key, listener) ->
            val childId = key.removeSuffix("_loc")
            if (key.endsWith("_loc")) {
                firebaseSource.getDevicesRef().child(childId).child("location").removeEventListener(listener)
            } else {
                firebaseSource.getDevicesRef().child(childId).child("status").removeEventListener(listener)
            }
        }
        statusListeners.clear()
        sosListener?.let {
            try {
                firebaseSource.getSosAlertsRef().removeEventListener(it)
            } catch (e: Exception) {}
        }
    }
}

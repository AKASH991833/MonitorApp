package com.familyconnect.app.ui.pairing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.model.PairingCode
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.data.repository.AppRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val firebaseSource: FirebaseSource

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _childName = MutableStateFlow("")
    val childName: StateFlow<String> = _childName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _countdownTime = MutableStateFlow(600L)
    val countdownTime: StateFlow<Long> = _countdownTime.asStateFlow()

    private val _parentName = MutableStateFlow("")
    val parentName: StateFlow<String> = _parentName.asStateFlow()

    private var countdownJob: Job? = null
    private var generatedCode: PairingCode? = null

    init {
        val context = application.applicationContext
        val database = AppDatabase.getInstance(context)
        firebaseSource = FirebaseSource()
        val prefs = context.getSharedPreferences("family_connect_prefs", Application.MODE_PRIVATE)
        repository = AppRepository(database, firebaseSource, prefs)
    }

    fun generatePairingCode(parentName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val codeStr = repository.generatePairingCode(parentName)
                _code.value = codeStr
                _parentName.value = parentName
                _countdownTime.value = 600L
                startCountdown()
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate pairing code")
                _error.value = "Failed to generate code. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun validatePairingCode(code: String, childName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = repository.validatePairingCode(code)
                result.fold(
                    onSuccess = { pairingCode ->
                        generatedCode = pairingCode
                        val fcmToken = try {
                            FirebaseMessaging.getInstance().token.await()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to get FCM token")
                            ""
                        }
                        repository.pairChild(pairingCode, childName, fcmToken)
                        val childUserId = firebaseSource.getCurrentUserId()
                        getApplication<Application>()
                            .getSharedPreferences("family_connect_prefs", Context.MODE_PRIVATE)
                            .edit().putString("child_id", childUserId).apply()
                        _isPaired.value = true
                        _childName.value = childName
                    },
                    onFailure = { error ->
                        _error.value = error.message ?: "Invalid or expired code"
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to validate pairing code")
                _error.value = "Invalid or expired code"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_countdownTime.value > 0) {
                delay(1000L)
                _countdownTime.value -= 1
                if (_countdownTime.value <= 0) {
                    _error.value = "Code has expired. Generate a new one."
                }
            }
        }
    }

    fun updateChildName(name: String) {
        _childName.value = name
    }

    fun reset() {
        countdownJob?.cancel()
        _code.value = ""
        _childName.value = ""
        _isLoading.value = false
        _error.value = null
        _isPaired.value = false
        _countdownTime.value = 600L
        _parentName.value = ""
        generatedCode = null
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

package com.familyconnect.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.familyconnect.app.data.local.AppDatabase
import com.familyconnect.app.data.local.PairedChildEntity
import com.familyconnect.app.data.local.SessionEntity
import com.familyconnect.app.data.model.PairedChild
import com.familyconnect.app.data.model.PairingCode
import com.familyconnect.app.data.model.SessionRecord
import com.familyconnect.app.data.model.UserRole
import com.familyconnect.app.data.remote.FirebaseSource
import com.familyconnect.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.UUID

class AppRepository(
    private val database: AppDatabase,
    private val firebaseSource: FirebaseSource,
    private val prefs: SharedPreferences
) {
    private val pairingDao = database.pairingDao()
    private val sessionDao = database.sessionDao()

    private val _themeMode = MutableStateFlow(prefs.getString(Constants.KEY_THEME_MODE, "system") ?: "system")
    val themeMode: Flow<String> = _themeMode.asStateFlow()

    private val _streamQuality = MutableStateFlow(prefs.getString(Constants.KEY_STREAM_QUALITY, "auto") ?: "auto")
    val streamQuality: Flow<String> = _streamQuality.asStateFlow()

    private val _autoTimeout = MutableStateFlow(prefs.getInt(Constants.KEY_AUTO_TIMEOUT, 30))
    val autoTimeout: Flow<Int> = _autoTimeout.asStateFlow()

    private val _locationEnabled = MutableStateFlow(prefs.getBoolean(Constants.KEY_LOCATION_ENABLED, false))
    val locationEnabled: Flow<Boolean> = _locationEnabled.asStateFlow()

    private val _autoStartEnabled = MutableStateFlow(prefs.getBoolean(Constants.KEY_AUTO_START, true))
    val autoStartEnabled: Flow<Boolean> = _autoStartEnabled.asStateFlow()

    fun getUserRole(): UserRole? {
        val role = prefs.getString(Constants.KEY_ROLE, null) ?: return null
        return try {
            UserRole.valueOf(role)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun setUserRole(role: UserRole) {
        prefs.edit().putString(Constants.KEY_ROLE, role.name).apply()
    }

    suspend fun generatePairingCode(parentName: String): String {
        val userId = try {
            firebaseSource.getCurrentUserId()
        } catch (e: Exception) {
            Timber.w(e, "Firebase not available, using local ID")
            "local_${System.currentTimeMillis()}"
        }
        val code = PairingCode.generateCode()
        val now = System.currentTimeMillis()
        val expiresAt = now + 10 * 60 * 1000L

        try {
            firebaseSource.generatePairingCode(userId, parentName, code)
        } catch (e: Exception) {
            Timber.w(e, "Firebase generate failed, storing locally")
        }

        prefs.edit()
            .putString("local_code_$code", "$parentName|$userId|$now|$expiresAt|false")
            .apply()

        return code
    }

    suspend fun validatePairingCode(code: String): Result<PairingCode> {
        val localData = prefs.getString("local_code_$code", null)
        if (localData != null) {
            val parts = localData.split("|")
            if (parts.size >= 5) {
                val parentName = parts[0]
                val parentId = parts[1]
                val createdAt = parts[2].toLongOrNull() ?: 0L
                val expiresAt = parts[3].toLongOrNull() ?: 0L
                val isUsed = parts.getOrElse(4) { "false" }.toBoolean()

                if (isUsed) {
                    return Result.failure(Exception("Pairing code already used"))
                }
                if (System.currentTimeMillis() > expiresAt) {
                    return Result.failure(Exception("Pairing code has expired"))
                }
                return Result.success(
                    PairingCode(code, parentId, parentName, createdAt, expiresAt, isUsed)
                )
            }
        }

        return try {
            firebaseSource.ensureSignedIn()
            val pairingCode = firebaseSource.validatePairingCode(code)
                ?: return Result.failure(Exception("Pairing code not found"))
            if (pairingCode.isUsed) {
                return Result.failure(Exception("Pairing code already used"))
            }
            if (System.currentTimeMillis() > pairingCode.expiresAt) {
                return Result.failure(Exception("Pairing code has expired"))
            }
            Result.success(pairingCode)
        } catch (e: Exception) {
            Timber.e(e, "Failed to validate pairing code")
            Result.failure(Exception("Invalid or expired code. Please try again."))
        }
    }

    suspend fun pairChild(pairingCode: PairingCode, childName: String, fcmToken: String) {
        val childId = try {
            firebaseSource.getCurrentUserId()
        } catch (e: Exception) {
            Timber.w(e, "Firebase not available, using local child ID")
            "local_child_${System.currentTimeMillis()}"
        }

        prefs.edit().putString("local_code_${pairingCode.code}", null).apply()

        try {
            firebaseSource.markCodeAsUsed(pairingCode.code)
            firebaseSource.registerChildFcmToken(childId, fcmToken, pairingCode.parentId)
        } catch (e: Exception) {
            Timber.w(e, "Firebase pairing operations failed, continuing with local data")
        }

        val child = PairedChildEntity(
            childId = childId,
            childName = childName,
            fcmToken = fcmToken,
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            pairedAt = System.currentTimeMillis()
        )
        pairingDao.insert(child)
    }

    suspend fun getPairedChildren(): List<PairedChild> {
        return pairingDao.getAll().map { entity ->
            PairedChild(
                childId = entity.childId,
                childName = entity.childName,
                fcmToken = entity.fcmToken,
                isOnline = entity.isOnline,
                lastSeen = entity.lastSeen,
                pairedAt = entity.pairedAt
            )
        }
    }

    suspend fun getSessionHistory(): List<SessionRecord> {
        return sessionDao.getAllSessions().map { entity ->
            SessionRecord(
                sessionId = entity.sessionId,
                childId = entity.childId,
                parentId = entity.parentId,
                startTime = entity.startTime,
                endTime = entity.endTime,
                duration = entity.duration,
                locationLat = entity.locationLat,
                locationLng = entity.locationLng,
                status = entity.status
            )
        }
    }

    suspend fun startLiveSession(parentId: String, childId: String): String {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        try {
            firebaseSource.createSessionRecord(sessionId, childId, parentId)
        } catch (e: Exception) {
            Timber.w(e, "Firebase session create failed, saving locally only")
        }

        val session = SessionEntity(
            sessionId = sessionId,
            childId = childId,
            parentId = parentId,
            startTime = now,
            endTime = null,
            duration = null,
            locationLat = null,
            locationLng = null,
            status = "active"
        )
        sessionDao.insert(session)

        return sessionId
    }

    suspend fun stopLiveSession(sessionId: String) {
        val endTime = System.currentTimeMillis()

        try {
            firebaseSource.updateSessionEndTime(sessionId)
        } catch (e: Exception) {
            Timber.w(e, "Firebase session end update failed")
        }

        val existing = sessionDao.getAllSessions().find { it.sessionId == sessionId }
        if (existing != null) {
            val duration = endTime - existing.startTime
            val updated = existing.copy(
                endTime = endTime,
                duration = duration,
                status = "completed"
            )
            sessionDao.update(updated)
        }
    }

    suspend fun sendWakeCommand(childId: String) {
        try {
            firebaseSource.sendFcmCommand(childId, "wake")
        } catch (e: Exception) {
            Timber.w(e, "Failed to send wake command via Firebase")
        }
    }

    suspend fun sendStopCommand(childId: String) {
        try {
            firebaseSource.sendFcmCommand(childId, "stop")
        } catch (e: Exception) {
            Timber.w(e, "Failed to send stop command via Firebase")
        }
    }

    suspend fun notifyParentOnPair(parentId: String, childId: String, childName: String) {
        try {
            firebaseSource.notifyParentOfPairedChild(parentId, childId, childName)
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify parent of paired child")
        }
    }

    suspend fun addPairedChildFromNotification(childId: String, childName: String) {
        val existing = pairingDao.getByChildId(childId)
        if (existing == null) {
            val child = PairedChildEntity(
                childId = childId,
                childName = childName,
                fcmToken = "",
                isOnline = true,
                lastSeen = System.currentTimeMillis(),
                pairedAt = System.currentTimeMillis()
            )
            pairingDao.insert(child)
        }
    }

    suspend fun updateChildStatus(childId: String, isOnline: Boolean) {
        try {
            firebaseSource.updateChildOnlineStatus(childId, isOnline)
        } catch (e: Exception) {
            Timber.w(e, "Firebase status update failed, updating locally only")
        }
        val existing = pairingDao.getByChildId(childId)
        if (existing != null) {
            pairingDao.insert(
                existing.copy(isOnline = isOnline, lastSeen = System.currentTimeMillis())
            )
        }
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(Constants.KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun setStreamQuality(quality: String) {
        prefs.edit().putString(Constants.KEY_STREAM_QUALITY, quality).apply()
        _streamQuality.value = quality
    }

    fun setAutoTimeout(minutes: Int) {
        prefs.edit().putInt(Constants.KEY_AUTO_TIMEOUT, minutes).apply()
        _autoTimeout.value = minutes
    }

    fun setLocationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_LOCATION_ENABLED, enabled).apply()
        _locationEnabled.value = enabled
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_AUTO_START, enabled).apply()
        _autoStartEnabled.value = enabled
    }

    suspend fun clearLocalData() {
        pairingDao.getAll().forEach { pairingDao.delete(it) }
        sessionDao.getAllSessions().forEach { sessionDao.delete(it) }
    }

    suspend fun logout() {
        try {
            firebaseSource.signOut()
        } catch (e: Exception) {
            Timber.w(e, "Firebase sign out failed")
        }
        clearLocalData()
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "AppRepository"
    }
}

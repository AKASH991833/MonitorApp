package com.familyconnect.app.data.remote

import com.familyconnect.app.data.model.PairingCode
import com.familyconnect.app.util.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseSource {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://studio-6135479340-ea1c2-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }

    fun getCurrentUser(): FirebaseUser? = try { auth.currentUser } catch (e: Exception) { null }

    suspend fun getCurrentUserId(): String {
        val user = auth.currentUser ?: signInAnonymously()
        return user.uid
    }

    suspend fun ensureSignedIn(): FirebaseUser {
        return auth.currentUser ?: signInAnonymously()
    }

    suspend fun signInAnonymously(): FirebaseUser {
        val result = auth.signInAnonymously().await()
        return result.user ?: throw Exception("Anonymous sign-in returned null user")
    }

    suspend fun createUserWithEmailAndPassword(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: throw Exception("User creation returned null user")
    }

    suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: throw Exception("Sign-in returned null user")
    }

    fun signOut() {
        auth.signOut()
    }

    fun getPairingCodesRef() = database.getReference("pairingCodes")
    fun getDevicesRef() = database.getReference("devices")
    fun getSessionsRef() = database.getReference("sessions")
    fun getCommandsRef() = database.getReference("commands")
    fun getSignalingRef() = database.getReference("signaling")
    fun getSosAlertsRef() = database.getReference(Constants.SOS_ALERTS_REF)

    suspend fun generatePairingCode(parentId: String, parentName: String, code: String = PairingCode.generateCode()): PairingCode {
        val now = System.currentTimeMillis()
        val expiresAt = now + 10 * 60 * 1000L

        val codeData = mapOf<String, Any>(
            "code" to code,
            "parentId" to parentId,
            "parentName" to parentName,
            "createdAt" to ServerValue.TIMESTAMP,
            "expiresAt" to expiresAt,
            "isUsed" to false
        )

        database.getReference("pairingCodes/$code").setValue(codeData).await()

        return PairingCode(
            code = code,
            parentId = parentId,
            parentName = parentName,
            createdAt = now,
            expiresAt = expiresAt,
            isUsed = false
        )
    }

    suspend fun validatePairingCode(code: String): PairingCode? {
        return suspendCancellableCoroutine { continuation ->
            val ref = database.getReference("pairingCodes/$code")
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        continuation.resume(null)
                        return
                    }
                    val pairingCode = snapshot.getValue(PairingCode::class.java)
                    continuation.resume(pairingCode)
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
        }
    }

    suspend fun markCodeAsUsed(code: String) {
        database.getReference("pairingCodes/$code/isUsed").setValue(true).await()
    }

    suspend fun registerChildFcmToken(childId: String, token: String, parentId: String) {
        val deviceData = mapOf(
            "fcmToken" to token,
            "parentId" to parentId,
            "lastSeen" to ServerValue.TIMESTAMP
        )
        database.getReference("devices/$childId").updateChildren(deviceData).await()
    }

    suspend fun updateChildOnlineStatus(childId: String, isOnline: Boolean) {
        val statusData = mapOf<String, Any>(
            "isOnline" to isOnline,
            "lastSeen" to ServerValue.TIMESTAMP
        )
        database.getReference("devices/$childId").updateChildren(statusData).await()
    }

    suspend fun sendFcmCommand(childId: String, command: String, sessionId: String? = null) {
        val commandData = mutableMapOf<String, Any>(
            "command" to command,
            "timestamp" to ServerValue.TIMESTAMP
        )
        sessionId?.let { commandData["sessionId"] = it }
        database.getReference("commands/$childId").push().setValue(commandData).await()
    }

    suspend fun sendSignalingData(sessionId: String, type: String, data: Any) {
        database.getReference("signaling/$sessionId/$type").setValue(data).await()
    }

    fun listenForSignaling(sessionId: String): Flow<DataSnapshot> = callbackFlow {
        val ref = database.getReference("signaling/$sessionId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    suspend fun createSessionRecord(
        sessionId: String,
        childId: String,
        parentId: String,
        status: String = "active"
    ) {
        val sessionData = mapOf<String, Any>(
            "sessionId" to sessionId,
            "childId" to childId,
            "parentId" to parentId,
            "startTime" to ServerValue.TIMESTAMP,
            "status" to status
        )
        database.getReference("sessions/$sessionId").setValue(sessionData).await()
    }

    suspend fun updateSessionEndTime(sessionId: String, status: String = "completed") {
        val updateData = mapOf<String, Any>(
            "endTime" to ServerValue.TIMESTAMP,
            "status" to status
        )
        database.getReference("sessions/$sessionId").updateChildren(updateData).await()
    }
}

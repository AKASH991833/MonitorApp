package com.familyconnect.app.data.remote

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber

data class OfferPayload(
    val sdp: String? = null,
    val type: String? = null
)

data class AnswerPayload(
    val sdp: String? = null,
    val type: String? = null
)

data class IceCandidatePayload(
    val candidate: String? = null,
    val sdpMLineIndex: Int? = null,
    val sdpMid: String? = null
)

class SignalingService {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(
        "https://studio-6135479340-ea1c2-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )

    private fun getSignalingRef(sessionId: String) =
        database.getReference("signaling/$sessionId")

    suspend fun sendOffer(sessionId: String, offer: OfferPayload) {
        getSignalingRef(sessionId).child("offer").setValue(offer).await()
        Timber.d("Offer sent for session: $sessionId")
    }

    suspend fun sendAnswer(sessionId: String, answer: AnswerPayload) {
        getSignalingRef(sessionId).child("answer").setValue(answer).await()
        Timber.d("Answer sent for session: $sessionId")
    }

    suspend fun sendIceCandidate(sessionId: String, candidate: IceCandidatePayload) {
        getSignalingRef(sessionId).child("candidates").push().setValue(candidate).await()
        Timber.d("ICE candidate sent for session: $sessionId")
    }

    fun listenForOffer(sessionId: String): Flow<OfferPayload> = callbackFlow {
        val ref = getSignalingRef(sessionId).child("offer")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val offer = snapshot.getValue(OfferPayload::class.java)
                    if (offer != null) {
                        trySend(offer)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun listenForAnswer(sessionId: String): Flow<AnswerPayload> = callbackFlow {
        val ref = getSignalingRef(sessionId).child("answer")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val answer = snapshot.getValue(AnswerPayload::class.java)
                    if (answer != null) {
                        trySend(answer)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun listenForIceCandidates(sessionId: String): Flow<IceCandidatePayload> = callbackFlow {
        val ref = getSignalingRef(sessionId).child("candidates")
        val listener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val candidate = snapshot.getValue(IceCandidatePayload::class.java)
                if (candidate != null) trySend(candidate)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun cleanupSignalingData(sessionId: String) {
        getSignalingRef(sessionId).removeValue().await()
        Timber.d("Signaling data cleaned up for session: $sessionId")
    }
}

package com.example.firechat.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Date

/** Ephemeral presence lives outside messages and never creates a conversation summary. */
class TypingRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun document(chatId: String, uid: String) =
        db.collection("chats").document(chatId).collection("typing").document(uid)

    fun publish(chatId: String, uid: String) {
        // An expired offline write is rejected by the rules instead of announcing stale typing.
        document(chatId, uid).set(mapOf(
            "updatedAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(System.currentTimeMillis() + EXPIRY_MS))
        )).addOnFailureListener { /* Presence is best effort; message sending remains independent. */ }
    }

    fun clear(chatId: String, uid: String) {
        document(chatId, uid).delete().addOnFailureListener { /* Remote expiry also clears the bubble. */ }
    }

    fun observe(chatId: String, uid: String): Flow<Boolean> = callbackFlow {
        var expiry: Job? = null
        trySend(false)
        val registration = document(chatId, uid).addSnapshotListener { snapshot, error ->
            expiry?.cancel()
            val remaining = snapshot?.getTimestamp("expiresAt")?.toDate()?.time
                ?.minus(System.currentTimeMillis()) ?: 0L
            val active = error == null && snapshot?.exists() == true && remaining > 0
            trySend(active)
            if (active) expiry = launch {
                delay(remaining.coerceAtMost(MAX_REMOTE_EXPIRY_MS))
                trySend(false)
            }
        }
        awaitClose { expiry?.cancel(); registration.remove() }
    }.distinctUntilChanged()

    companion object {
        const val EXPIRY_MS = 8_000L
        private const val MAX_REMOTE_EXPIRY_MS = 15_000L
    }
}

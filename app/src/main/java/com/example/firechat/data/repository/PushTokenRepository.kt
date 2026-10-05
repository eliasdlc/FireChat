package com.example.firechat.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Keeps this device's FCM token in fcmTokens/{uid}, a document only its owner
 * can read. The Cloud Function reads it to deliver new-message pushes.
 */
class PushTokenRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    suspend fun register(uid: String) {
        save(uid, messaging.token.await())
    }

    suspend fun save(uid: String, token: String) {
        db.collection("fcmTokens").document(uid)
            .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    /** Invalidates the token so a signed-out device stops receiving pushes. */
    fun unregister() {
        messaging.deleteToken()
    }
}

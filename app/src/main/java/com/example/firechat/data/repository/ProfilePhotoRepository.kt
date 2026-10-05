package com.example.firechat.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ProfilePhotoRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun update(uid: String, jpeg: ByteArray): String {
        require(uid.isNotBlank() && jpeg.isNotEmpty() && jpeg.size <= MAX_BYTES)
        val profile = db.collection("users").document(uid)
        val previousUrl = profile.get().await().getString("photoUrl")
        val reference = storage.reference.child("avatars/$uid/${UUID.randomUUID()}.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        val upload = reference.putBytes(jpeg, metadata)
        try { upload.await() }
        catch (error: CancellationException) { upload.cancel(); throw error }
        try {
            // A gs:// reference keeps downloads behind Storage authentication and rules.
            val url = reference.toString()
            profile.update("photoUrl", url).await()
            previousUrl?.let {
                runCatching {
                    val previous = storage.getReferenceFromUrl(it)
                    if (previous.bucket == reference.bucket && previous.path.startsWith("/avatars/$uid/") && previous.path != reference.path) {
                        previous.delete().addOnFailureListener { /* Cleanup must not undo a confirmed profile change. */ }
                    }
                }
            }
            return url
        } catch (error: Exception) {
            // Never remove a previous avatar when the profile update fails.
            // The server may have applied an update whose acknowledgment was lost.
            // Keep the uploaded object so a delayed profile reference remains valid.
            throw error
        }
    }

    companion object { const val MAX_BYTES = 512 * 1024 }
}

package com.example.firechat.data.repository

import com.example.firechat.data.media.OutgoingMedia
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/** Stores chat photos and videos under chats/{chatId}/{senderUid}/ and reads them back. */
class ChatMediaRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    /** gs:// references of an uploaded message: the file and, for videos, its thumbnail. */
    data class Uploaded(val mediaUrl: String, val thumbUrl: String?)

    /** Uploads [media]; [onProgress] receives 0 to 100 over the whole upload. */
    suspend fun upload(chatId: String, senderUid: String, media: OutgoingMedia, onProgress: (Int) -> Unit): Uploaded {
        val folder = storage.reference.child("chats/$chatId/$senderUid")
        val name = UUID.randomUUID().toString()
        return when (media) {
            is OutgoingMedia.Image -> {
                val ref = folder.child("$name.jpg")
                await(ref.putBytes(media.jpeg, jpegMetadata), onProgress)
                Uploaded(ref.toString(), null)
            }
            is OutgoingMedia.Video -> {
                val thumb = folder.child("$name-thumb.jpg")
                thumb.putBytes(media.thumbnail, jpegMetadata).await()
                val ref = folder.child("$name.${extensionOf(media.contentType)}")
                val metadata = StorageMetadata.Builder().setContentType(media.contentType).build()
                await(ref.putFile(media.uri, metadata), onProgress)
                Uploaded(ref.toString(), thumb.toString())
            }
        }
    }

    /** Downloads a gs:// image, refusing anything above [maxBytes]. */
    suspend fun bytes(url: String, maxBytes: Long): ByteArray =
        storage.getReferenceFromUrl(url).getBytes(maxBytes).await()

    /** Downloads a gs:// video into [target] so it plays without a public link. */
    suspend fun download(url: String, target: File) {
        storage.getReferenceFromUrl(url).getFile(target).await()
    }

    private suspend fun await(task: UploadTask, onProgress: (Int) -> Unit) {
        task.addOnProgressListener { snapshot ->
            if (snapshot.totalByteCount > 0) onProgress((100 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt())
        }
        try { task.await() } catch (error: CancellationException) { task.cancel(); throw error }
    }

    private fun extensionOf(contentType: String) = when (contentType) {
        "video/mp4" -> "mp4"
        "video/3gpp" -> "3gp"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> "video"
    }

    private companion object {
        val jpegMetadata: StorageMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
    }

}

package com.example.firechat.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.google.firebase.firestore.Exclude
import com.google.firebase.Timestamp

/**
 * A chat message. Text messages carry [text]; image and video messages carry a
 * gs:// [mediaUrl] in Storage, their pixel size and, for videos, a JPEG [thumbUrl].
 */
data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = TYPE_TEXT,
    val text: String = "",
    val mediaUrl: String? = null,
    val thumbUrl: String? = null,
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val durationMs: Long = 0,
    @ServerTimestamp val createdAt: Date? = null,
    @get:Exclude val serverCreatedAt: Timestamp? = null
) {
    @get:Exclude val isMedia: Boolean get() = type == TYPE_IMAGE || type == TYPE_VIDEO

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
    }
}

package com.example.firechat.data.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import com.example.firechat.data.model.Message
import java.io.ByteArrayOutputStream

/** A picked photo or video, already prepared for upload. */
sealed class OutgoingMedia {
    abstract val width: Int
    abstract val height: Int
    abstract val type: String

    /** A photo re-encoded as JPEG, longest side at most [MAX_IMAGE_SIDE]. */
    class Image(val jpeg: ByteArray, override val width: Int, override val height: Int) : OutgoingMedia() {
        override val type = Message.TYPE_IMAGE
    }

    /** A video uploaded as picked, plus a JPEG frame shown in the bubble before playing it. */
    class Video(
        val uri: Uri,
        val contentType: String,
        val thumbnail: ByteArray,
        override val width: Int,
        override val height: Int,
        val durationMs: Long
    ) : OutgoingMedia() {
        override val type = Message.TYPE_VIDEO
    }

    /** The picked file is larger than what Storage rules accept. */
    class TooLargeException : IllegalArgumentException()

    companion object {
        const val MAX_IMAGE_SIDE = 1600
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        const val MAX_VIDEO_BYTES = 50L * 1024 * 1024

        /** Reads [uri] from the picker and prepares it; runs off the main thread. */
        fun from(resolver: ContentResolver, uri: Uri): OutgoingMedia {
            val mime = resolver.getType(uri).orEmpty()
            return if (mime.startsWith("video/")) video(resolver, uri, mime) else image(resolver, uri)
        }

        private fun image(resolver: ContentResolver, uri: Uri): Image {
            val bitmap = ImageDecoding.decodeOriented(resolver, uri, MAX_IMAGE_SIDE * 2)
            val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt()) else bitmap
            val bytes = jpeg(scaled, 85)
            val result = Image(bytes, scaled.width, scaled.height)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            if (bytes.size > MAX_IMAGE_BYTES) throw TooLargeException()
            return result
        }

        private fun video(resolver: ContentResolver, uri: Uri, mime: String): Video {
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
            if (size == null || size > MAX_VIDEO_BYTES) throw TooLargeException()
            val retriever = MediaMetadataRetriever()
            try {
                resolver.openFileDescriptor(uri, "r").use { retriever.setDataSource(requireNotNull(it).fileDescriptor) }
                fun number(key: Int) = retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
                val rotation = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
                var width = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
                var height = number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
                if (rotation == 90 || rotation == 270) width = height.also { height = width }
                val frame = requireNotNull(retriever.getFrameAtTime(0)) // already rotated by the platform
                val scale = minOf(1f, 720f / maxOf(frame.width, frame.height))
                val thumb = frame.scale((frame.width * scale).toInt(), (frame.height * scale).toInt())
                val thumbnail = jpeg(thumb, 80)
                if (thumb !== frame) thumb.recycle()
                frame.recycle()
                return Video(uri, mime, thumbnail, width, height, number(MediaMetadataRetriever.METADATA_KEY_DURATION))
            } finally {
                retriever.release()
            }
        }

        private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray = ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it))
            it.toByteArray()
        }
    }
}

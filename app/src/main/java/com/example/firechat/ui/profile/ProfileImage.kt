package com.example.firechat.ui.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.example.firechat.data.media.ImageDecoding
import com.example.firechat.data.repository.ProfilePhotoRepository
import java.io.ByteArrayOutputStream

object ProfileImage {
    fun jpeg(resolver: ContentResolver, uri: Uri): ByteArray {
        val oriented = ImageDecoding.decodeOriented(resolver, uri, 1024)
        val size = minOf(oriented.width, oriented.height)
        val cropped = Bitmap.createBitmap(oriented, (oriented.width-size)/2, (oriented.height-size)/2, size, size)
        val resized = Bitmap.createScaledBitmap(cropped, 512, 512, true)
        val bytes = ByteArrayOutputStream().use {
            check(resized.compress(Bitmap.CompressFormat.JPEG, 85, it))
            it.toByteArray()
        }
        setOf(oriented, cropped, resized).forEach { it.recycle() }
        require(bytes.size <= ProfilePhotoRepository.MAX_BYTES)
        return bytes
    }
}

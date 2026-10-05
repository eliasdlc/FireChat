package com.example.firechat.ui.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import com.example.firechat.data.repository.ProfilePhotoRepository
import java.io.ByteArrayOutputStream

object ProfileImage {
    fun jpeg(resolver: ContentResolver, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1024) {
            options.inSampleSize *= 2
        }
        val source = resolver.openInputStream(uri).use {
            requireNotNull(BitmapFactory.decodeStream(it, null, options))
        }
        val orientation = runCatching {
            resolver.openInputStream(uri).use {
                ExifInterface(requireNotNull(it)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        val size = minOf(oriented.width, oriented.height)
        val cropped = Bitmap.createBitmap(oriented, (oriented.width-size)/2, (oriented.height-size)/2, size, size)
        val resized = Bitmap.createScaledBitmap(cropped, 512, 512, true)
        val bytes = ByteArrayOutputStream().use {
            check(resized.compress(Bitmap.CompressFormat.JPEG, 85, it))
            it.toByteArray()
        }
        setOf(source, oriented, cropped, resized).forEach { it.recycle() }
        require(bytes.size <= ProfilePhotoRepository.MAX_BYTES)
        return bytes
    }
}

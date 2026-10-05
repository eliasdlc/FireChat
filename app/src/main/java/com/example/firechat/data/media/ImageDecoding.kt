package com.example.firechat.data.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ImageDecoding {
    /**
     * Decodes [uri] upright (EXIF orientation applied), downsampled by powers of two
     * until its longest side is at most [maxSide]. Used by profile photos and chat photos.
     */
    fun decodeOriented(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxSide) {
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
        if (matrix.isIdentity) return source
        val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (oriented !== source) source.recycle()
        return oriented
    }
}

package com.example.firechat.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded imports copied into app storage, with orientation applied and metadata removed. */
class WallpaperPhotoStore(context: Context) {
    private val context = context.applicationContext
    private fun accountFolder(root: File, ownerUid: String): File {
        require(ownerUid.isNotBlank())
        val scope = MessageDigest.getInstance("SHA-256").digest(ownerUid.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(root, "wallpaper-photos/$scope").apply { check(mkdirs() || isDirectory) }
    }
    private fun checkedFile(folder: File, name: String): File {
        require(Regex("[0-9a-f-]{36}[.]jpg").matches(name))
        return File(folder, name)
    }
    fun savedFile(ownerUid: String, name: String) = checkedFile(accountFolder(context.filesDir, ownerUid), name)
    fun draftFile(ownerUid: String, name: String) = checkedFile(accountFolder(context.cacheDir, ownerUid), name)
    fun loadFile(ownerUid: String, name: String): File = savedFile(ownerUid, name).takeIf { it.isFile } ?: draftFile(ownerUid, name)

    fun importImage(ownerUid: String, uri: Uri): String {
        require(uri.scheme == "content")
        val folder = accountFolder(context.cacheDir, ownerUid)
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val source = File.createTempFile("source-", ".image", folder)
        val name = "${UUID.randomUUID()}.jpg"
        val draft = draftFile(ownerUid, name)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output ->
                    val bytes = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(bytes)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw ImageTooLargeException()
                        output.write(bytes, 0, count)
                    }
                }
            } ?: throw IOException("Cannot read image")
            val bitmap = decode(source)
            try {
                draft.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) }
            } finally { bitmap.recycle() }
            return name
        } catch (error: OutOfMemoryError) {
            draft.delete()
            throw IOException("Cannot decode image within memory budget", error)
        } catch (error: Exception) {
            draft.delete()
            throw error
        } finally { source.delete() }
    }

    private fun decode(file: File): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val ratio = minOf(1.0, MAX_EDGE.toDouble() / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * ratio).roundToInt()), max(1, (info.size.height * ratio).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw IOException("Unsupported image")
        val orientation = try { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (_: IOException) { ExifInterface.ORIENTATION_NORMAL }
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        if (transform.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    fun promote(ownerUid: String, name: String) {
        val saved = savedFile(ownerUid, name)
        if (!saved.isFile && !draftFile(ownerUid, name).renameTo(saved)) throw IOException("Cannot save image")
    }

    class ImageTooLargeException : IOException()
    companion object { const val MAX_EDGE = 1600; const val MAX_BYTES = 25L * 1024 * 1024 }
}

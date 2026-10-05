package com.example.firechat.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.withClip
import kotlin.math.max

/** Center-cropped image with independent dimming underneath the chat's content. */
class PhotoWallpaperDrawable(val bitmap: Bitmap, val fileName: String, brightness: Int) : Drawable() {
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply { color = Color.BLACK }
    private val destination = RectF()
    var brightness: Int = brightness
        set(value) { field = value.coerceIn(0, 100); invalidateSelf() }

    override fun onBoundsChange(bounds: Rect) {
        val scale = max(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        destination.set(bounds.exactCenterX() - width / 2, bounds.exactCenterY() - height / 2,
            bounds.exactCenterX() + width / 2, bounds.exactCenterY() + height / 2)
    }
    override fun draw(canvas: Canvas) {
        canvas.withClip(bounds) {
            drawBitmap(bitmap, null, destination, photoPaint)
            dimPaint.alpha = ((100 - brightness) * 255 / 100)
            drawRect(bounds, dimPaint)
        }
    }
    override fun setAlpha(alpha: Int) { photoPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { photoPaint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

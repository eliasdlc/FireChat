package com.example.firechat.ui.wallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.firechat.R

/** Static wallpaper drawn at display density, without bitmap allocations or animation. */
class WallpaperDrawable(context: Context, val wallpaper: ChatWallpaper) : Drawable() {
    private val background = Paint().apply { color = ContextCompat.getColor(context, wallpaper.colorRes) }
    private val pattern = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wallpaper_pattern)
        strokeWidth = context.resources.displayMetrics.density
    }
    private val patternAlpha = pattern.alpha
    private val density = context.resources.displayMetrics.density

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, background)
        val step = 24 * density
        when (wallpaper) {
            ChatWallpaper.DOTS -> {
                var y = bounds.top + step / 2
                while (y < bounds.bottom) {
                    var x = bounds.left + step / 2
                    while (x < bounds.right) {
                        canvas.drawCircle(x, y, 1.2f * density, pattern)
                        x += step
                    }
                    y += step
                }
            }
            ChatWallpaper.GRID -> {
                var x = bounds.left + step
                while (x < bounds.right) { canvas.drawLine(x, bounds.top.toFloat(), x, bounds.bottom.toFloat(), pattern); x += step }
                var y = bounds.top + step
                while (y < bounds.bottom) { canvas.drawLine(bounds.left.toFloat(), y, bounds.right.toFloat(), y, pattern); y += step }
            }
            else -> Unit
        }
    }

    override fun setAlpha(alpha: Int) { background.alpha = alpha; pattern.alpha = patternAlpha * alpha / 255; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { background.colorFilter = colorFilter; pattern.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = if (background.alpha == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
}

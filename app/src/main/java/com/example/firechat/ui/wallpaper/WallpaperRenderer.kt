package com.example.firechat.ui.wallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperRenderer {
    suspend fun load(context: Context, ownerUid: String, selection: WallpaperSelection): Drawable = when (selection) {
        is WallpaperSelection.Preset -> WallpaperDrawable(context, selection.wallpaper)
        is WallpaperSelection.Photo -> withContext(Dispatchers.IO) {
            val file = WallpaperPhotoStore(context).loadFile(ownerUid, selection.fileName)
            val bitmap = try { BitmapFactory.decodeFile(file.path) } catch (_: OutOfMemoryError) { null }
            if (bitmap == null) WallpaperDrawable(context, ChatWallpaper.ORIGINAL)
            else PhotoWallpaperDrawable(bitmap, selection.fileName, selection.brightness)
        }
    }
}

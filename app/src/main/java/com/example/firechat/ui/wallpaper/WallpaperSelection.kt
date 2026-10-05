package com.example.firechat.ui.wallpaper

import androidx.annotation.StringRes
import com.example.firechat.R

sealed interface WallpaperSelection {
    @get:StringRes val labelRes: Int

    data class Preset(val wallpaper: ChatWallpaper) : WallpaperSelection {
        override val labelRes get() = wallpaper.labelRes
    }

    data class Photo(val fileName: String, val brightness: Int = 100) : WallpaperSelection {
        init { require(brightness in 0..100) }
        override val labelRes get() = R.string.wallpaper_own_image
    }
}

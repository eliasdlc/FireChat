package com.example.firechat.ui.wallpaper

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.firechat.R

enum class ChatWallpaper(@StringRes val labelRes: Int, @ColorRes val colorRes: Int) {
    ORIGINAL(R.string.wallpaper_original, R.color.bg),
    DOTS(R.string.wallpaper_dots, R.color.bg),
    GRID(R.string.wallpaper_grid, R.color.bg),
    SAGE(R.string.wallpaper_sage, R.color.wallpaper_sage),
    SAND(R.string.wallpaper_sand, R.color.wallpaper_sand),
    LAVENDER(R.string.wallpaper_lavender, R.color.wallpaper_lavender)
}

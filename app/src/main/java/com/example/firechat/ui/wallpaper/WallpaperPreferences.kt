package com.example.firechat.ui.wallpaper

import android.content.Context
import androidx.core.content.edit

/** Device-local choices partitioned by account. A missing chat choice inherits the default. */
class WallpaperPreferences(context: Context) {
    private val context = context.applicationContext

    private fun preferences(ownerUid: String) = context.getSharedPreferences("wallpapers_$ownerUid", Context.MODE_PRIVATE)
    private fun decode(name: String?) = ChatWallpaper.entries.firstOrNull { it.name == name }

    fun default(ownerUid: String): ChatWallpaper = if (ownerUid.isBlank()) ChatWallpaper.ORIGINAL
        else decode(preferences(ownerUid).getString("default", null)) ?: ChatWallpaper.ORIGINAL

    fun chatOverride(ownerUid: String, chatId: String): ChatWallpaper? =
        if (ownerUid.isBlank() || chatId.isBlank()) null
        else decode(preferences(ownerUid).getString("chat_$chatId", null))

    fun resolve(ownerUid: String, chatId: String): ChatWallpaper = chatOverride(ownerUid, chatId) ?: default(ownerUid)

    fun saveDefault(ownerUid: String, wallpaper: ChatWallpaper) {
        require(ownerUid.isNotBlank())
        preferences(ownerUid).edit { putString("default", wallpaper.name) }
    }

    fun saveOverride(ownerUid: String, chatId: String, wallpaper: ChatWallpaper?) {
        require(ownerUid.isNotBlank() && chatId.isNotBlank())
        preferences(ownerUid).edit {
            if (wallpaper == null) remove("chat_$chatId") else putString("chat_$chatId", wallpaper.name)
        }
    }
}

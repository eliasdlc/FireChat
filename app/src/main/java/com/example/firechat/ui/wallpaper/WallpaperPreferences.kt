package com.example.firechat.ui.wallpaper

import android.content.Context
import java.io.IOException
import org.json.JSONObject

/** Account-local choices. A missing chat choice inherits the current default. */
class WallpaperPreferences(context: Context) {
    private val context = context.applicationContext
    private val photos = WallpaperPhotoStore(context)
    private fun preferences(ownerUid: String) = context.getSharedPreferences("wallpapers_$ownerUid", Context.MODE_PRIVATE)

    private fun decode(ownerUid: String, value: String?): WallpaperSelection? {
        ChatWallpaper.entries.firstOrNull { it.name == value }?.let { return WallpaperSelection.Preset(it) }
        if (value == null) return null
        return try {
            val data = JSONObject(value)
            val name = data.getString("photo")
            if (!photos.savedFile(ownerUid, name).isFile) null
            else WallpaperSelection.Photo(name, data.optInt("brightness", 100).coerceIn(0, 100))
        } catch (_: Exception) { null }
    }

    private fun encode(selection: WallpaperSelection): String = when (selection) {
        is WallpaperSelection.Preset -> selection.wallpaper.name
        is WallpaperSelection.Photo -> JSONObject().put("photo", selection.fileName).put("brightness", selection.brightness).toString()
    }

    fun default(ownerUid: String): WallpaperSelection =
        if (ownerUid.isBlank()) WallpaperSelection.Preset(ChatWallpaper.ORIGINAL)
        else decode(ownerUid, preferences(ownerUid).getString("default", null)) ?: WallpaperSelection.Preset(ChatWallpaper.ORIGINAL)

    fun chatOverride(ownerUid: String, chatId: String): WallpaperSelection? =
        if (ownerUid.isBlank() || chatId.isBlank()) null
        else decode(ownerUid, preferences(ownerUid).getString("chat_$chatId", null))

    fun resolve(ownerUid: String, chatId: String): WallpaperSelection = chatOverride(ownerUid, chatId) ?: default(ownerUid)

    fun saveDefault(ownerUid: String, wallpaper: ChatWallpaper) = saveDefault(ownerUid, WallpaperSelection.Preset(wallpaper))
    fun saveDefault(ownerUid: String, selection: WallpaperSelection) = save(ownerUid, "default", selection)
    fun saveOverride(ownerUid: String, chatId: String, wallpaper: ChatWallpaper) = saveOverride(ownerUid, chatId, WallpaperSelection.Preset(wallpaper))
    fun saveOverride(ownerUid: String, chatId: String, selection: WallpaperSelection?) {
        require(chatId.isNotBlank())
        save(ownerUid, "chat_$chatId", selection)
    }

    private fun save(ownerUid: String, key: String, selection: WallpaperSelection?) {
        require(ownerUid.isNotBlank())
        if (selection is WallpaperSelection.Photo) require(photos.savedFile(ownerUid, selection.fileName).isFile)
        val prefs = preferences(ownerUid)
        val previous = decode(ownerUid, prefs.getString(key, null))
        val editor = prefs.edit()
        if (selection == null) editor.remove(key) else editor.putString(key, encode(selection))
        if (!editor.commit()) throw IOException("Cannot persist wallpaper selection")
        // A photo can be shared by the default and a chat with its own brightness.
        if (previous is WallpaperSelection.Photo && prefs.all.values.none {
            (decode(ownerUid, it as? String) as? WallpaperSelection.Photo)?.fileName == previous.fileName
        }) photos.savedFile(ownerUid, previous.fileName).delete()
    }
}

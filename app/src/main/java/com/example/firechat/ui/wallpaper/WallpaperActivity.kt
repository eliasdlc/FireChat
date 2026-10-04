package com.example.firechat.ui.wallpaper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import com.example.firechat.R
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.ui.theme.ThemedActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class WallpaperActivity : ThemedActivity() {
    private lateinit var ownerUid: String
    private lateinit var preferences: WallpaperPreferences
    private var chatId: String? = null
    private var selected: ChatWallpaper? = null
    private val choices = listOf(
        R.id.wallpaperOriginal to ChatWallpaper.ORIGINAL, R.id.wallpaperDots to ChatWallpaper.DOTS,
        R.id.wallpaperGrid to ChatWallpaper.GRID, R.id.wallpaperSage to ChatWallpaper.SAGE,
        R.id.wallpaperSand to ChatWallpaper.SAND, R.id.wallpaperLavender to ChatWallpaper.LAVENDER
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ownerUid = AuthRepository().currentUserId.orEmpty()
        if (ownerUid.isBlank()) { finish(); return }
        chatId = intent.getStringExtra(EXTRA_CHAT_ID)?.takeIf { it.isNotBlank() }
        preferences = WallpaperPreferences(this)
        selected = if (savedInstanceState?.containsKey(STATE_SELECTION) == true)
            ChatWallpaper.entries.firstOrNull { it.name == savedInstanceState.getString(STATE_SELECTION) }
        else chatId?.let { preferences.chatOverride(ownerUid, it) } ?: if (chatId == null) preferences.default(ownerUid) else null
        enableEdgeToEdge()
        setContentView(R.layout.activity_wallpaper)
        findViewById<View>(R.id.main).applySystemBarsPadding()
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(if (chatId == null) R.string.wallpaper_default_title else R.string.wallpaper_chat_title)
            setNavigationOnClickListener { finish() }
        }
        findViewById<TextView>(R.id.wallpaperDescription).setText(
            if (chatId == null) R.string.wallpaper_default_description else R.string.wallpaper_chat_description
        )
        choices.forEach { (id, wallpaper) ->
            findViewById<MaterialButton>(id).apply {
                isSaveEnabled = false
                isCheckable = true
                setToggleCheckedStateOnClick(false)
                setText(wallpaper.labelRes)
                setOnClickListener { selected = wallpaper; renderSelection() }
            }
        }
        findViewById<MaterialButton>(R.id.wallpaperResetButton).apply {
            setText(if (chatId == null) R.string.wallpaper_restore_original else R.string.wallpaper_use_default)
            setOnClickListener { selected = if (chatId == null) ChatWallpaper.ORIGINAL else null; renderSelection() }
        }
        findViewById<View>(R.id.applyWallpaperButton).setOnClickListener {
            if (AuthRepository().currentUserId != ownerUid) { finish(); return@setOnClickListener }
            val target = chatId
            if (target == null) preferences.saveDefault(ownerUid, checkNotNull(selected))
            else preferences.saveOverride(ownerUid, target, selected)
            finish()
        }
        renderSelection()
    }

    override fun onResume() {
        super.onResume()
        if (::ownerUid.isInitialized && AuthRepository().currentUserId != ownerUid) finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTION, selected?.name)
        super.onSaveInstanceState(outState)
    }

    private fun renderSelection() {
        choices.forEach { (id, wallpaper) ->
            findViewById<MaterialButton>(id).apply {
                isChecked = wallpaper == selected
                contentDescription = if (isChecked) getString(R.string.accent_selected, getString(wallpaper.labelRes)) else getString(wallpaper.labelRes)
            }
        }
        val effective = selected ?: preferences.default(ownerUid)
        findViewById<View>(R.id.wallpaperPreview).background = WallpaperDrawable(this, effective)
        findViewById<TextView>(R.id.wallpaperSelection).text = if (selected == null)
            getString(R.string.wallpaper_inherited, getString(effective.labelRes)) else getString(effective.labelRes)
    }

    companion object {
        private const val EXTRA_CHAT_ID = "wallpaper_chat_id"
        private const val STATE_SELECTION = "wallpaper_selection"
        fun newIntent(context: Context, chatId: String? = null) = Intent(context, WallpaperActivity::class.java).apply {
            if (chatId != null) putExtra(EXTRA_CHAT_ID, chatId)
        }
    }
}

package com.example.firechat.ui.wallpaper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.firechat.R
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.ui.theme.ThemedActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperActivity : ThemedActivity() {
    private lateinit var ownerUid: String
    private lateinit var preferences: WallpaperPreferences
    private lateinit var photos: WallpaperPhotoStore
    private var chatId: String? = null
    private var selected: WallpaperSelection? = null
    private var draftFileName: String? = null
    private var previewJob: Job? = null
    private var busy = false
    private val choices = listOf(
        R.id.wallpaperOriginal to ChatWallpaper.ORIGINAL, R.id.wallpaperDots to ChatWallpaper.DOTS,
        R.id.wallpaperGrid to ChatWallpaper.GRID, R.id.wallpaperSage to ChatWallpaper.SAGE,
        R.id.wallpaperSand to ChatWallpaper.SAND, R.id.wallpaperLavender to ChatWallpaper.LAVENDER
    )
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && AuthRepository().currentUserId == ownerUid) lifecycleScope.launch {
            setBusy(true)
            showStatus(R.string.wallpaper_image_loading)
            try {
                val name = withContext(Dispatchers.IO) { photos.importImage(ownerUid, uri) }
                draftFileName?.let { photos.draftFile(ownerUid, it).delete() }
                draftFileName = name
                selected = WallpaperSelection.Photo(name)
                showStatus(null)
                renderSelection()
                findViewById<ScrollView>(R.id.wallpaperScroll).post {
                    findViewById<ScrollView>(R.id.wallpaperScroll).smoothScrollTo(0, findViewById<View>(R.id.wallpaperBrightnessControls).top)
                }
            } catch (_: WallpaperPhotoStore.ImageTooLargeException) {
                showStatus(R.string.wallpaper_image_too_large)
            } catch (_: Exception) {
                showStatus(R.string.wallpaper_image_error)
            } finally { setBusy(false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ownerUid = AuthRepository().currentUserId.orEmpty()
        if (ownerUid.isBlank()) { finish(); return }
        chatId = intent.getStringExtra(EXTRA_CHAT_ID)?.takeIf { it.isNotBlank() }
        preferences = WallpaperPreferences(this)
        photos = WallpaperPhotoStore(this)
        draftFileName = savedInstanceState?.getString(STATE_DRAFT)
        selected = if (savedInstanceState?.containsKey(STATE_SELECTION) == true) {
            val name = savedInstanceState.getString(STATE_PHOTO)
            if (name != null && photos.loadFile(ownerUid, name).isFile)
                WallpaperSelection.Photo(name, savedInstanceState.getInt(STATE_BRIGHTNESS, 100).coerceIn(0, 100))
            else ChatWallpaper.entries.firstOrNull { it.name == savedInstanceState.getString(STATE_SELECTION) }?.let { WallpaperSelection.Preset(it) }
        } else if (chatId == null) preferences.default(ownerUid) else preferences.chatOverride(ownerUid, checkNotNull(chatId))
        if (chatId == null && selected == null) selected = preferences.default(ownerUid)
        enableEdgeToEdge()
        setContentView(R.layout.activity_wallpaper)
        findViewById<View>(R.id.main).applySystemBarsPadding()
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setTitle(if (chatId == null) R.string.wallpaper_default_title else R.string.wallpaper_chat_title)
            setNavigationOnClickListener { if (!busy) finish() }
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
                setOnClickListener { selected = WallpaperSelection.Preset(wallpaper); showStatus(null); renderSelection() }
            }
        }
        findViewById<View>(R.id.chooseWallpaperImageButton).setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        findViewById<Slider>(R.id.wallpaperBrightnessSlider).addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val photo = (selected ?: preferences.default(ownerUid)) as? WallpaperSelection.Photo
                if (photo != null) { selected = photo.copy(brightness = value.toInt()); renderSelection() }
            }
        }
        findViewById<MaterialButton>(R.id.wallpaperResetButton).apply {
            setText(if (chatId == null) R.string.wallpaper_restore_original else R.string.wallpaper_use_default)
            setOnClickListener {
                selected = if (chatId == null) WallpaperSelection.Preset(ChatWallpaper.ORIGINAL) else null
                showStatus(null); renderSelection()
            }
        }
        findViewById<View>(R.id.applyWallpaperButton).setOnClickListener { saveSelection() }
        renderSelection()
    }

    private fun saveSelection() {
        if (AuthRepository().currentUserId != ownerUid) { finish(); return }
        val selection = selected
        setBusy(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (selection is WallpaperSelection.Photo) photos.promote(ownerUid, selection.fileName)
                    if (chatId == null) preferences.saveDefault(ownerUid, checkNotNull(selection))
                    else preferences.saveOverride(ownerUid, checkNotNull(chatId), selection)
                }
                finish()
            } catch (_: Exception) {
                showStatus(R.string.wallpaper_save_error)
                setBusy(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::ownerUid.isInitialized && AuthRepository().currentUserId != ownerUid) finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTION, (selected as? WallpaperSelection.Preset)?.wallpaper?.name)
        outState.putString(STATE_PHOTO, (selected as? WallpaperSelection.Photo)?.fileName)
        outState.putInt(STATE_BRIGHTNESS, (selected as? WallpaperSelection.Photo)?.brightness ?: 100)
        outState.putString(STATE_DRAFT, draftFileName)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && ::photos.isInitialized) draftFileName?.let { photos.draftFile(ownerUid, it).delete() }
        super.onDestroy()
    }

    private fun renderSelection() {
        choices.forEach { (id, wallpaper) ->
            findViewById<MaterialButton>(id).apply {
                isChecked = (selected as? WallpaperSelection.Preset)?.wallpaper == wallpaper
                contentDescription = if (isChecked) getString(R.string.accent_selected, getString(wallpaper.labelRes)) else getString(wallpaper.labelRes)
            }
        }
        val effective = selected ?: preferences.default(ownerUid)
        val preview = findViewById<View>(R.id.wallpaperPreview)
        val photo = effective as? WallpaperSelection.Photo
        findViewById<View>(R.id.wallpaperBrightnessControls).isVisible = photo != null
        if (photo != null) {
            findViewById<Slider>(R.id.wallpaperBrightnessSlider).value = photo.brightness.toFloat()
            findViewById<TextView>(R.id.wallpaperBrightnessValue).text = getString(R.string.wallpaper_brightness_value, photo.brightness)
        }
        previewJob?.cancel()
        val drawable = preview.background as? PhotoWallpaperDrawable
        if (photo != null && drawable?.fileName == photo.fileName) drawable.brightness = photo.brightness
        else previewJob = lifecycleScope.launch { preview.background = WallpaperRenderer.load(this@WallpaperActivity, ownerUid, effective) }
        findViewById<TextView>(R.id.wallpaperSelection).text = if (selected == null)
            getString(R.string.wallpaper_inherited, getString(effective.labelRes)) else getString(effective.labelRes)
    }

    private fun showStatus(message: Int?) {
        findViewById<TextView>(R.id.wallpaperImageStatus).apply {
            text = message?.let(::getString).orEmpty()
            isVisible = message != null
        }
    }
    private fun setBusy(value: Boolean) {
        busy = value
        (choices.map { it.first } + listOf(R.id.chooseWallpaperImageButton, R.id.applyWallpaperButton, R.id.wallpaperResetButton, R.id.wallpaperBrightnessSlider))
            .forEach { findViewById<View>(it).isEnabled = !value }
    }

    companion object {
        private const val EXTRA_CHAT_ID = "wallpaper_chat_id"
        private const val STATE_SELECTION = "wallpaper_selection"
        private const val STATE_PHOTO = "wallpaper_photo"
        private const val STATE_BRIGHTNESS = "wallpaper_brightness"
        private const val STATE_DRAFT = "wallpaper_draft"
        fun newIntent(context: Context, chatId: String? = null) = Intent(context, WallpaperActivity::class.java).apply {
            if (chatId != null) putExtra(EXTRA_CHAT_ID, chatId)
        }
    }
}

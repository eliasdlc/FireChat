package com.example.firechat.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.firechat.ui.wallpaper.WallpaperActivity
import com.example.firechat.ui.wallpaper.WallpaperRenderer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.firechat.ui.wallpaper.WallpaperPreferences
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.firechat.notifications.ActiveChat
import com.example.firechat.notifications.NotificationHelper
import androidx.activity.viewModels
import com.example.firechat.ui.theme.ThemedActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.ui.common.AvatarView
import com.example.firechat.ui.profile.UserProfileActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar

class ChatActivity : ThemedActivity() {

    private var wallpaperJob: Job? = null
    private val viewModel: ChatViewModel by viewModels()

    // The system photo picker: no storage permission, photos and videos only.
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.sendMedia(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        findViewById<View>(R.id.main).applySystemBarsPadding()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val messageList = findViewById<RecyclerView>(R.id.messageList)
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val chatName = findViewById<TextView>(R.id.chatName)
        val typingBubble = findViewById<TypingBubbleView>(R.id.typingBubble)
        val chatAvatar = findViewById<AvatarView>(R.id.chatAvatar)

        toolbar.title = ""
        chatName.text = viewModel.recipientName
        chatAvatar.bind(viewModel.recipientName, null)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.menu.add(0, R.id.action_chat_wallpaper, 0, R.string.wallpaper_chat_title).apply {
            setIcon(R.drawable.ic_wallpaper)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            isEnabled = viewModel.myUid.isNotBlank() && viewModel.recipientId.isNotBlank()
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_chat_wallpaper) {
                startActivity(WallpaperActivity.newIntent(this, viewModel.chatId))
                true
            } else false
        }
        findViewById<View>(R.id.chatProfileHeader).apply {
            isEnabled = viewModel.recipientId.isNotBlank()
            contentDescription = getString(R.string.view_user_profile, viewModel.recipientName)
            setOnClickListener {
                startActivity(UserProfileActivity.newIntent(this@ChatActivity, viewModel.recipientId))
            }
        }
        viewModel.recipientDisplayName.observe(this) { displayName ->
            typingBubble.contentDescription = getString(R.string.contact_typing, displayName)
            chatName.text = displayName
            chatAvatar.bind(displayName, viewModel.recipientPhoto.value)
            findViewById<View>(R.id.chatProfileHeader).contentDescription = getString(R.string.view_user_profile, displayName)
        }

        viewModel.recipientPhoto.observe(this) { photo ->
            chatAvatar.bind(chatName.text.toString(), photo)
        }

        val adapter = MessageAdapter(viewModel.myUid) { message ->
            startActivity(MediaViewerActivity.newIntent(this, message))
        }
        messageList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messageList.adapter = adapter

        messageInput.doAfterTextChanged { text ->
            sendButton.isEnabled = !text.isNullOrBlank()
            viewModel.messageEdited(text)
        }
        sendButton.isEnabled = false
        sendButton.setOnClickListener {
            viewModel.sendText(messageInput.text.toString())
            messageInput.text?.clear()
        }

        val attachButton = findViewById<ImageButton>(R.id.attachButton)
        attachButton.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        val uploadStatus = findViewById<TextView>(R.id.uploadStatus)

        viewModel.recipientTyping.observe(this) { typingBubble.isVisible = it }

        viewModel.recipientRead.observe(this) { adapter.submitRecipientRead(it) }
        viewModel.messages.observe(this) { list ->
            adapter.submitList(list) {
                if (list.isNotEmpty()) messageList.scrollToPosition(list.lastIndex)
                viewModel.messagesShown(list)
            }
        }
        viewModel.uiState.observe(this) { state ->
            val progress = state.uploadProgress
            uploadStatus.isVisible = progress != null
            if (progress != null) uploadStatus.text = getString(R.string.media_uploading, progress)
            attachButton.isEnabled = progress == null
            state.error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.errorShown()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val wallpaper = WallpaperPreferences(this).resolve(viewModel.myUid, viewModel.chatId)
        wallpaperJob?.cancel()
        wallpaperJob = lifecycleScope.launch {
            findViewById<View>(R.id.chatWallpaper).background = WallpaperRenderer.load(this@ChatActivity, viewModel.myUid, wallpaper)
        }
        viewModel.chatResumed()
        ActiveChat.chatId = viewModel.chatId
        NotificationHelper.cancel(this, viewModel.chatId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        viewModel.windowFocusChanged(hasFocus)
    }

    override fun onPause() {
        viewModel.chatPaused()
        if (ActiveChat.chatId == viewModel.chatId) ActiveChat.chatId = null
        super.onPause()
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"

        fun newIntent(context: Context, userId: String, userName: String): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_USER_NAME, userName)
    }
}

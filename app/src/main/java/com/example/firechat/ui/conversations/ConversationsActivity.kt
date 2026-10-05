package com.example.firechat.ui.conversations


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.firechat.ui.theme.ThemedActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.profile.ProfileActivity
import com.example.firechat.ui.users.UsersActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton



class ConversationsActivity : ThemedActivity() {

    private val viewModel: ConversationsViewModel by viewModels()


    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_conversations)
        findViewById<View>(R.id.main).applySystemBarsPadding()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val conversationList = findViewById<RecyclerView>(R.id.conversationList)
        val emptyState = findViewById<TextView>(R.id.emptyState)
        val newChatButton = findViewById<ExtendedFloatingActionButton>(R.id.newChatButton)
        setSupportActionBar(toolbar)

        val adapter = ConversationAdapter(viewModel.myUid, onClick = { conversation ->
            val otherUid = conversation.otherUid(viewModel.myUid)
            startActivity(ChatActivity.newIntent(this, otherUid, conversation.otherName(viewModel.myUid)))
        }, onLongClick = { row ->
            val chat = row.conversation
            val name = viewModel.nicknames.value?.get(chat.otherUid(viewModel.myUid)) ?: chat.otherName(viewModel.myUid)
            MaterialAlertDialogBuilder(this).setTitle(name)
                .setItems(arrayOf(getString(if (row.archived) R.string.unarchive_chat else R.string.archive_chat))) { _, _ ->
                    viewModel.setArchived(chat.id, !row.archived)
                }.show()
        })
        conversationList.layoutManager = LinearLayoutManager(this)
        conversationList.adapter = adapter
        (conversationList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        val search = findViewById<EditText>(R.id.inboxSearch)
        search.doAfterTextChanged { viewModel.searchChanged(it.toString()) }
        val filters = findViewById<ChipGroup>(R.id.inboxFilters)
        filters.setOnCheckedStateChangeListener { _, checked ->
            viewModel.filterChanged(when (checked.firstOrNull()) {
                R.id.filterRead -> InboxFilter.READ
                R.id.filterUnread -> InboxFilter.UNREAD
                else -> InboxFilter.ALL
            })
        }
        val archivedEntry = findViewById<View>(R.id.archivedEntry)
        archivedEntry.setOnClickListener { viewModel.showArchived(true) }
        val back = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { viewModel.showArchived(false) }
        }
        onBackPressedDispatcher.addCallback(this, back)
        toolbar.setNavigationOnClickListener { viewModel.showArchived(false) }
        viewModel.nicknames.observe(this) { adapter.submitNicknames(it) }
        viewModel.photos.observe(this) { adapter.submitPhotos(it) }

        newChatButton.setOnClickListener {
            startActivity(Intent(this, UsersActivity::class.java))
        }

        viewModel.inbox.observe(this) { state ->
            adapter.submitList(state.rows)
            emptyState.isVisible = state.rows.isEmpty()
            emptyState.setText(when {
                state.query.isNotBlank() || state.filter != InboxFilter.ALL -> R.string.inbox_no_matches
                state.archived -> R.string.inbox_archived_empty
                else -> R.string.conversations_empty
            })
            toolbar.title = getString(if (state.archived) R.string.archived_title else R.string.app_name)
            toolbar.navigationIcon = if (state.archived) androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_arrow_back) else null
            back.isEnabled = state.archived
            archivedEntry.isVisible = !state.archived
            archivedEntry.contentDescription = resources.getQuantityString(R.plurals.archived_chats_count, state.archivedCount, state.archivedCount)
            findViewById<TextView>(R.id.archivedCount).text = getString(R.string.inbox_number, state.archivedCount)
            if (search.text.toString() != state.query) search.setText(state.query)
            filters.check(when (state.filter) {
                InboxFilter.ALL -> R.id.filterAll
                InboxFilter.READ -> R.id.filterRead
                InboxFilter.UNREAD -> R.id.filterUnread
            })
            findViewById<Chip>(R.id.filterUnread).text = if (state.unreadChats > 0)
                getString(R.string.filter_unread_count, state.unreadChats) else getString(R.string.filter_unread)
        }
        viewModel.error.observe(this) { error ->
            findViewById<TextView>(R.id.inboxError).apply {
                text = error?.let(::getString).orEmpty()
                isVisible = error != null
            }
        }

        askNotificationPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_conversations, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_profile -> {
            startActivity(Intent(this, ProfileActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.ui.auth.LoginActivity
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.users.UsersActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import androidx.appcompat.app.AppCompatDelegate
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.ThemePreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder



class ConversationsActivity : AppCompatActivity() {

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

        val adapter = ConversationAdapter(viewModel.myUid) { conversation ->
            val otherUid = conversation.otherUid(viewModel.myUid)
            startActivity(ChatActivity.newIntent(this, otherUid, conversation.otherName(viewModel.myUid)))
        }
        conversationList.layoutManager = LinearLayoutManager(this)
        conversationList.adapter = adapter

        newChatButton.setOnClickListener {
            startActivity(Intent(this, UsersActivity::class.java))
        }

        viewModel.conversations.observe(this) { list ->
            adapter.submitList(list)
            emptyState.isVisible = list.isEmpty()
        }
        viewModel.error.observe(this) { error ->
            if (error != null) {
                emptyState.setText(error)
                emptyState.isVisible = true
            }
        }

        askNotificationPermission()
    }

    private fun showThemeDialog() {
        val modes = listOf(AppTheme.SYSTEM, AppTheme.DARK, AppTheme.LIGHT)
        val selectedIndex = modes.indexOf(ThemePreferences.read(this))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_dialog_title)
            .setSingleChoiceItems(R.array.theme_options, selectedIndex) { dialog, which ->
                val selected = modes[which]

                ThemePreferences.save(this, selected)
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(selected.nightMode)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_conversations, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_logout -> {
            viewModel.logout()
            openLogin()
            true
        }
        R.id.action_theme -> {
            showThemeDialog()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun openLogin() {
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

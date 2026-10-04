package com.example.firechat.ui.profile

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.firechat.ui.theme.ThemedActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.firechat.R
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class UserProfileActivity : ThemedActivity() {
    private val viewModel: UserProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user_profile)
        findViewById<View>(R.id.main).applySystemBarsPadding()
        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val content = findViewById<View>(R.id.userProfileContent)
        val name = findViewById<TextView>(R.id.userProfileName)
        val email = findViewById<TextView>(R.id.userProfileEmail)
        val avatar = findViewById<TextView>(R.id.userProfileAvatar)
        val publicName = findViewById<TextView>(R.id.userProfilePublicName)
        val nicknameEditor = findViewById<View>(R.id.nicknameEditor)
        val nicknameInput = findViewById<TextInputEditText>(R.id.nicknameInput)
        val nicknameLayout = findViewById<TextInputLayout>(R.id.nicknameLayout)
        val saveNickname = findViewById<MaterialButton>(R.id.saveNicknameButton)
        val removeNickname = findViewById<View>(R.id.removeNicknameButton)
        val nicknameStatus = findViewById<TextView>(R.id.nicknameStatus)
        nicknameInput.doAfterTextChanged { viewModel.nicknameChanged(it?.toString().orEmpty()) }
        saveNickname.setOnClickListener { viewModel.saveNickname() }
        removeNickname.setOnClickListener { viewModel.removeNickname() }
        val status = findViewById<View>(R.id.userProfileStatus)
        val error = findViewById<TextView>(R.id.userProfileError)
        val retry = findViewById<View>(R.id.retryUserProfileButton)
        retry.setOnClickListener { viewModel.loadProfile() }
        viewModel.uiState.observe(this) { state ->
            content.isVisible = state.isLoaded
            name.text = state.displayName
            publicName.text = getString(R.string.nickname_public_name, state.name)
            publicName.isVisible = state.originalNickname.isNotBlank()
            email.text = state.email
            email.isVisible = state.email.isNotBlank()
            avatar.text = state.displayName.trim().take(1).uppercase().ifBlank { "?" }
            nicknameEditor.isVisible = state.canEditNickname
            nicknameInput.isEnabled = state.canEditNickname && !state.isSavingNickname
            if (nicknameInput.text.toString() != state.nickname) nicknameInput.setText(state.nickname)
            nicknameLayout.error = state.nicknameError?.let(::getString)
            saveNickname.isEnabled = state.canSaveNickname
            saveNickname.setText(if (state.isSavingNickname) R.string.profile_saving else R.string.nickname_save)
            removeNickname.isVisible = state.originalNickname.isNotBlank()
            removeNickname.isEnabled = !state.isSavingNickname
            nicknameStatus.isVisible = state.nicknameSaved
            nicknameStatus.setText(if (state.originalNickname.isEmpty()) R.string.nickname_removed else R.string.nickname_saved)
            status.isVisible = state.isLoading
            error.text = state.error?.let(::getString).orEmpty()
            error.isVisible = state.error != null
            retry.isVisible = state.canRetry
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
    }

    companion object {
        const val EXTRA_USER_ID = "user_profile_id"

        fun newIntent(context: Context, userId: String): Intent =
            Intent(context, UserProfileActivity::class.java).putExtra(EXTRA_USER_ID, userId)
    }
}

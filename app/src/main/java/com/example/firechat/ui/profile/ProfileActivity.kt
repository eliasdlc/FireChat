package com.example.firechat.ui.profile

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.firechat.ui.theme.ThemedActivity
import com.example.firechat.ui.theme.AppearanceActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.example.firechat.R
import com.example.firechat.ui.common.AvatarView
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.ui.auth.WelcomeActivity
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileActivity : ThemedActivity() {
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        findViewById<View>(R.id.main).applySystemBarsPadding()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val editRow = findViewById<View>(R.id.editProfileRow)
        editRow.setOnClickListener { startActivity(Intent(this, EditProfileActivity::class.java)) }
        findViewById<View>(R.id.appearanceRow).setOnClickListener { startActivity(Intent(this, AppearanceActivity::class.java)) }
        findViewById<View>(R.id.logoutProfileRow).setOnClickListener { confirmLogout() }
        val retry = findViewById<View>(R.id.retryProfileButton)
        retry.setOnClickListener { viewModel.loadProfile() }
        val status = findViewById<TextView>(R.id.profileStatus)
        val error = findViewById<TextView>(R.id.profileError)
        viewModel.uiState.observe(this) { state ->
            findViewById<TextView>(R.id.profileDisplayName).text = state.name
            findViewById<TextView>(R.id.profileEmail).text = state.email
            findViewById<AvatarView>(R.id.profileAvatar).bind(state.name, state.photoUrl)
            editRow.isEnabled = state.isLoaded && !state.isLoading
            status.setText(R.string.profile_loading)
            status.isVisible = state.isLoading
            error.text = state.formError?.let(::getString).orEmpty()
            error.isVisible = state.formError != null
            retry.isVisible = !state.isLoaded && !state.isLoading
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload the canonical profile after returning from its editor.
        viewModel.loadProfile()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_logout)
            .setMessage(R.string.profile_logout_question)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_logout) { _, _ ->
                AuthRepository().logout()
                startActivity(Intent(this, WelcomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                ))
                finish()
            }
            .show()
    }
}

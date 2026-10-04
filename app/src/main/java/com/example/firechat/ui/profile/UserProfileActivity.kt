package com.example.firechat.ui.profile

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.example.firechat.R
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar

class UserProfileActivity : AppCompatActivity() {
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
        val status = findViewById<View>(R.id.userProfileStatus)
        val error = findViewById<TextView>(R.id.userProfileError)
        val retry = findViewById<View>(R.id.retryUserProfileButton)
        retry.setOnClickListener { viewModel.loadProfile() }
        viewModel.uiState.observe(this) { state ->
            content.isVisible = state.isLoaded
            name.text = state.name
            email.text = state.email
            email.isVisible = state.email.isNotBlank()
            avatar.text = state.name.trim().take(1).uppercase().ifBlank { "?" }
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

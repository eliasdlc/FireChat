package com.example.firechat.ui.profile

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import com.example.firechat.R
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ProfileActivity : AppCompatActivity() {
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

        val nameInput = findViewById<TextInputEditText>(R.id.profileNameInput)
        val nameLayout = findViewById<TextInputLayout>(R.id.profileNameLayout)
        val email = findViewById<TextView>(R.id.profileEmail)
        val avatar = findViewById<TextView>(R.id.profileAvatar)
        val status = findViewById<TextView>(R.id.profileStatus)
        val error = findViewById<TextView>(R.id.profileError)
        val saveButton = findViewById<MaterialButton>(R.id.saveProfileButton)
        val retryButton = findViewById<MaterialButton>(R.id.retryProfileButton)

        nameInput.doAfterTextChanged { viewModel.nameChanged(it.toString()) }
        saveButton.setOnClickListener { viewModel.save() }
        retryButton.setOnClickListener { viewModel.loadProfile() }

        viewModel.uiState.observe(this) { state ->
            nameInput.isEnabled = state.isLoaded && !state.isSaving
            if (nameInput.text.toString() != state.name) nameInput.setText(state.name)
            nameLayout.error = state.nameError?.let(::getString)
            email.text = state.email
            avatar.text = state.name.trim().take(1).uppercase().ifBlank { "?" }
            error.text = state.formError?.let(::getString).orEmpty()
            error.isVisible = state.formError != null
            retryButton.isVisible = !state.isLoaded && !state.isLoading
            saveButton.isEnabled = state.canSave
            saveButton.setText(
                if (state.isSaving) R.string.profile_saving else R.string.profile_save
            )
            status.text = when {
                state.isLoading -> getString(R.string.profile_loading)
                state.isSaved -> getString(R.string.profile_saved)
                else -> ""
            }
            status.isVisible = state.isLoading || state.isSaved
        }
    }
}

package com.example.firechat.ui.profile

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.firechat.ui.common.AvatarView
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.firechat.ui.theme.ThemedActivity
import androidx.core.view.isVisible
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import com.example.firechat.R
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EditProfileActivity : ThemedActivity() {
    private val viewModel: ProfileViewModel by viewModels()
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadPhoto(contentResolver, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_profile)
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
        val avatar = findViewById<AvatarView>(R.id.profileAvatar)
        val changePhoto = findViewById<MaterialButton>(R.id.changePhotoButton)
        changePhoto.setOnClickListener {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        val status = findViewById<TextView>(R.id.profileStatus)
        val error = findViewById<TextView>(R.id.profileError)
        val saveButton = findViewById<MaterialButton>(R.id.saveProfileButton)
        val retryButton = findViewById<MaterialButton>(R.id.retryProfileButton)

        nameInput.doAfterTextChanged { viewModel.nameChanged(it.toString()) }
        saveButton.setOnClickListener { viewModel.save() }
        retryButton.setOnClickListener { viewModel.loadProfile() }

        viewModel.uiState.observe(this) { state ->
            nameInput.isEnabled = state.isLoaded && !state.isSaving && !state.isUploadingPhoto
            if (nameInput.text.toString() != state.name) nameInput.setText(state.name)
            nameLayout.error = state.nameError?.let(::getString)
            email.text = state.email
            avatar.bind(state.name, state.photoUrl)
            changePhoto.isEnabled = state.isLoaded && !state.isLoading && !state.isSaving && !state.isUploadingPhoto
            changePhoto.setText(if (state.isUploadingPhoto) R.string.profile_uploading_photo else R.string.profile_change_photo)
            error.text = state.formError?.let(::getString).orEmpty()
            error.isVisible = state.formError != null
            retryButton.isVisible = !state.isLoaded && !state.isLoading
            saveButton.isEnabled = state.canSave
            saveButton.setText(
                if (state.isSaving) R.string.profile_saving else R.string.profile_save
            )
            status.text = when {
                state.isUploadingPhoto -> getString(R.string.profile_uploading_photo)
                state.photoSaved -> getString(R.string.profile_photo_saved)
                state.isLoading -> getString(R.string.profile_loading)
                state.isSaved -> getString(R.string.profile_saved)
                else -> ""
            }
            status.isVisible = state.isLoading || state.isSaved || state.isUploadingPhoto || state.photoSaved
        }
    }
}

package com.example.firechat.ui.profile

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val originalName: String = "",
    val isLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val nameError: Int? = null,
    val formError: Int? = null,
    val isSaved: Boolean = false
) {
    val canSave: Boolean
        get() = isLoaded && !isLoading && !isSaving && name.trim() != originalName
}

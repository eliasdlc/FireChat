package com.example.firechat.ui.profile

data class UserProfileUiState(
    val name: String = "",
    val email: String = "",
    val isLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val error: Int? = null,
    val canRetry: Boolean = false
)

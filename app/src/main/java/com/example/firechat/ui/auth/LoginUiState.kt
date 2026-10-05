package com.example.firechat.ui.auth

import androidx.annotation.StringRes

data class LoginUiState(
    val isLoading: Boolean = false,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val formError: Int? = null,
    val isLoggedIn: Boolean = false
)

package com.example.firechat.ui.auth

import androidx.annotation.StringRes

data class RegisterUiState(
    val isLoading: Boolean = false,
    @StringRes val nameError: Int? = null,
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    @StringRes val confirmationError: Int? = null,
    @StringRes val formError: Int? = null,
    val isRegistered: Boolean = false
)

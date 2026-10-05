package com.example.firechat.util

import android.util.Patterns
import androidx.annotation.StringRes
import com.example.firechat.R

object Validators {

    const val MIN_PASSWORD_LENGTH = 6

    @StringRes
    fun name(value: String): Int? =
        if (value.isBlank()) R.string.error_name_required else null

    @StringRes
    fun email(value: String): Int? = when {
        value.isBlank() -> R.string.error_email_required
        !Patterns.EMAIL_ADDRESS.matcher(value).matches() -> R.string.error_email_format
        else -> null
    }

    @StringRes
    fun password(value: String): Int? = when {
        value.isBlank() -> R.string.error_password_required
        value.length < MIN_PASSWORD_LENGTH -> R.string.error_password_length
        else -> null
    }

    @StringRes
    fun passwordConfirmation(password: String, confirmation: String): Int? =
        if (password != confirmation) R.string.error_password_mismatch else null
}

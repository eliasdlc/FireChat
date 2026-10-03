package com.example.firechat.ui.auth

import androidx.annotation.StringRes
import com.example.firechat.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

object AuthErrorMapper {

    @StringRes
    fun toMessage(error: Throwable): Int = when (error) {
        is FirebaseAuthWeakPasswordException -> R.string.error_auth_weak_password
        is FirebaseAuthInvalidUserException -> R.string.error_auth_invalid_credentials
        is FirebaseAuthInvalidCredentialsException -> R.string.error_auth_invalid_credentials
        is FirebaseAuthUserCollisionException -> R.string.error_auth_email_in_use
        is FirebaseNetworkException -> R.string.error_network
        is FirebaseTooManyRequestsException -> R.string.error_auth_too_many_requests
        else -> R.string.error_unknown
    }
}

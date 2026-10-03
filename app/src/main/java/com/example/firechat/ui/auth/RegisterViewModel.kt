package com.example.firechat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.util.Validators
import kotlinx.coroutines.launch

class RegisterViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _uiState = MutableLiveData(RegisterUiState())
    val uiState: LiveData<RegisterUiState> = _uiState

    fun register(name: String, email: String, password: String, confirmation: String) {
        val errors = RegisterUiState(
            nameError = Validators.name(name),
            emailError = Validators.email(email),
            passwordError = Validators.password(password),
            confirmationError = Validators.passwordConfirmation(password, confirmation)
        )
        if (errors != RegisterUiState()) {
            _uiState.value = errors
            return
        }

        _uiState.value = RegisterUiState(isLoading = true)
        viewModelScope.launch {
            _uiState.value = try {
                authRepository.register(name.trim(), email, password)
                RegisterUiState(isRegistered = true)
            } catch (e: Exception) {
                RegisterUiState(formError = AuthErrorMapper.toMessage(e))
            }
        }
    }
}

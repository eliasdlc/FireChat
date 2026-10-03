package com.example.firechat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.util.Validators
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _uiState = MutableLiveData(LoginUiState(isLoggedIn = authRepository.isLoggedIn))
    val uiState: LiveData<LoginUiState> = _uiState

    fun login(email: String, password: String) {
        val emailError = Validators.email(email)
        val passwordError = Validators.password(password)
        if (emailError != null || passwordError != null) {
            _uiState.value = LoginUiState(emailError = emailError, passwordError = passwordError)
            return
        }

        _uiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            _uiState.value = try {
                authRepository.login(email, password)
                LoginUiState(isLoggedIn = true)
            } catch (e: Exception) {
                LoginUiState(formError = AuthErrorMapper.toMessage(e))
            }
        }
    }
}

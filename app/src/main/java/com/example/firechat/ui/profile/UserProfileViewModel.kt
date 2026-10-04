package com.example.firechat.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firechat.R
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class UserProfileViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val auth = AuthRepository()
    private val users = UserRepository()
    private val sessionId = auth.currentUserId
    private val userId = savedStateHandle.get<String>(UserProfileActivity.EXTRA_USER_ID).orEmpty()
    private var loadJob: Job? = null
    private val _uiState = MutableLiveData(UserProfileUiState())
    val uiState: LiveData<UserProfileUiState> = _uiState

    fun loadProfile() {
        if (sessionId == null || auth.currentUserId != sessionId) {
            loadJob?.cancel()
            _uiState.value = UserProfileUiState(isLoading = false, error = R.string.error_profile_session)
            return
        }
        if (userId.isBlank() || '/' in userId) {
            _uiState.value = UserProfileUiState(isLoading = false, error = R.string.error_user_profile_missing)
            return
        }
        if (loadJob?.isActive == true) return
        _uiState.value = UserProfileUiState()
        loadJob = viewModelScope.launch {
            try {
                val user = withTimeout(15_000) { users.getUser(userId) }
                _uiState.value = when {
                    auth.currentUserId != sessionId -> UserProfileUiState(
                        isLoading = false, error = R.string.error_profile_session
                    )
                    user == null -> UserProfileUiState(
                        isLoading = false, error = R.string.error_user_profile_missing, canRetry = true
                    )
                    else -> UserProfileUiState(name = user.name, email = user.email, isLoaded = true, isLoading = false)
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.value = UserProfileUiState(isLoading = false, error = R.string.error_loading_user_profile, canRetry = true)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = UserProfileUiState(isLoading = false, error = R.string.error_loading_user_profile, canRetry = true)
            }
        }
    }
}

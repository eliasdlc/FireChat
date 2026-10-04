package com.example.firechat.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.firechat.R
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.data.repository.NicknameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class UserProfileViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val auth = AuthRepository()
    private val users = UserRepository()
    private val nicknames = NicknameRepository(application)
    // Restored drafts belong to the account that opened this screen.
    private val sessionId = savedStateHandle.get<String>(SESSION_OWNER)
        ?: auth.currentUserId.also { savedStateHandle[SESSION_OWNER] = it }
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
        if (loadJob?.isActive == true || _uiState.value?.isSavingNickname == true) return
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
                    else -> {
                        val nickname = nicknames.read(sessionId, userId)
                        UserProfileUiState(
                            name = user.name, email = user.email, isLoaded = true, isLoading = false,
                            nickname = savedStateHandle[NICKNAME_DRAFT] ?: nickname,
                            originalNickname = nickname, canEditNickname = userId != sessionId
                        )
                    }
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

    fun nicknameChanged(nickname: String) {
        val current = checkNotNull(_uiState.value)
        if (!current.canEditNickname || current.isSavingNickname || current.nickname == nickname) return
        savedStateHandle[NICKNAME_DRAFT] = nickname
        _uiState.value = current.copy(nickname = nickname, nicknameError = null, nicknameSaved = false)
    }

    fun saveNickname() = saveNicknameValue(checkNotNull(_uiState.value).nickname)

    fun removeNickname() = saveNicknameValue("")

    private fun saveNicknameValue(value: String) {
        val current = checkNotNull(_uiState.value)
        if (!current.canEditNickname || current.isLoading || current.isSavingNickname) return
        if (sessionId == null || auth.currentUserId != sessionId) {
            _uiState.value = UserProfileUiState(isLoading = false, error = R.string.error_profile_session)
            return
        }
        val normalized = NicknameRepository.normalize(value)
        if (normalized.length > NicknameRepository.MAX_LENGTH) {
            _uiState.value = current.copy(nicknameError = R.string.error_nickname_length, nicknameSaved = false)
            return
        }
        if (normalized == current.originalNickname) return
        _uiState.value = current.copy(isSavingNickname = true, nicknameError = null, nicknameSaved = false)
        viewModelScope.launch {
            try {
                nicknames.save(sessionId, userId, normalized)
                if (auth.currentUserId != sessionId) {
                    _uiState.value = UserProfileUiState(isLoading = false, error = R.string.error_profile_session)
                    return@launch
                }
                savedStateHandle.remove<String>(NICKNAME_DRAFT)
                _uiState.value = current.copy(
                    nickname = normalized, originalNickname = normalized,
                    isSavingNickname = false, nicknameSaved = true, nicknameError = null
                )
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                _uiState.value = current.copy(isSavingNickname = false, nicknameError = R.string.error_saving_nickname)
            }
        }
    }

    private companion object {
        const val NICKNAME_DRAFT = "nickname_draft"
        const val SESSION_OWNER = "nickname_owner"
    }
}

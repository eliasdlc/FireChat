package com.example.firechat.ui.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firechat.R
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.data.repository.ProfilePhotoRepository
import com.example.firechat.util.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch

class ProfileViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val userId = authRepository.currentUserId
    private var loadJob: Job? = null

    private val _uiState = MutableLiveData(
        ProfileUiState(name = savedStateHandle[NAME_DRAFT] ?: "")
    )
    val uiState: LiveData<ProfileUiState> = _uiState

    init {
        loadProfile()
    }

    fun loadProfile() {
        if (loadJob?.isActive == true || _uiState.value?.isSaving == true || _uiState.value?.isUploadingPhoto == true) return
        val current = checkNotNull(_uiState.value)
        _uiState.value = current.copy(isLoading = true, formError = null)
        loadJob = viewModelScope.launch {
            if (userId == null || authRepository.currentUserId != userId) {
                _uiState.value = current.copy(
                    isLoading = false, formError = R.string.error_profile_session
                )
                return@launch
            }
            try {
                val user = userRepository.getUser(userId)
                _uiState.value = if (user == null) {
                    current.copy(isLoading = false, formError = R.string.error_profile_missing)
                } else {
                    val draft = savedStateHandle.get<String>(NAME_DRAFT) ?: user.name
                    ProfileUiState(
                        name = draft, email = user.email, originalName = user.name, photoUrl = user.photoUrl,
                        isLoaded = true, isLoading = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = current.copy(
                    isLoading = false, formError = R.string.error_loading_profile
                )
            }
        }
    }

    fun nameChanged(name: String) {
        val current = checkNotNull(_uiState.value)
        if (!current.isLoaded || current.isSaving || current.isUploadingPhoto || current.name == name) return
        savedStateHandle[NAME_DRAFT] = name
        _uiState.value = current.copy(
            name = name, nameError = null, formError = null, isSaved = false, photoSaved = false
        )
    }

    fun save() {
        val current = checkNotNull(_uiState.value)
        if (!current.canSave) return
        val normalizedName = current.name.trim()
        val nameError = Validators.name(normalizedName)
        if (nameError != null) {
            _uiState.value = current.copy(nameError = nameError, isSaved = false)
            return
        }
        if (userId == null || authRepository.currentUserId != userId) {
            _uiState.value = current.copy(formError = R.string.error_profile_session)
            return
        }
        _uiState.value = current.copy(isSaving = true, nameError = null, formError = null)
        viewModelScope.launch {
            try {
                userRepository.updateName(userId, normalizedName)
                savedStateHandle[NAME_DRAFT] = normalizedName
                _uiState.value = current.copy(
                    name = normalizedName, originalName = normalizedName,
                    isSaving = false, isSaved = true
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = current.copy(
                    isSaving = false, formError = R.string.error_saving_profile, isSaved = false
                )
            }
        }
    }

    fun uploadPhoto(resolver: ContentResolver, uri: Uri) {
        val current = checkNotNull(_uiState.value)
        if (!current.isLoaded || current.isLoading || current.isSaving || current.isUploadingPhoto) return
        if (userId == null || authRepository.currentUserId != userId) {
            _uiState.value = current.copy(formError = R.string.error_profile_session)
            return
        }
        _uiState.value = current.copy(isUploadingPhoto = true, formError = null, photoSaved = false, isSaved = false)
        viewModelScope.launch {
            try {
                val url = withTimeout(60_000) {
                    val bytes = withContext(Dispatchers.IO) { ProfileImage.jpeg(resolver, uri) }
                    check(authRepository.currentUserId == userId)
                    ProfilePhotoRepository().update(userId, bytes)
                }
                _uiState.value = current.copy(photoUrl = url, isUploadingPhoto = false, photoSaved = true, formError = null, isSaved = false)
            } catch (_: TimeoutCancellationException) {
                _uiState.value = current.copy(isUploadingPhoto = false, formError = R.string.error_profile_photo, photoSaved = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = current.copy(isUploadingPhoto = false, formError = R.string.error_profile_photo, photoSaved = false)
            }
        }
    }

    private companion object {
        const val NAME_DRAFT = "profile_name_draft"
    }
}

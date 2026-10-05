package com.example.firechat.ui.profile

import com.example.firechat.data.repository.NicknameRepository

data class UserProfileUiState(
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val isLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val error: Int? = null,
    val canRetry: Boolean = false,
    val nickname: String = "",
    val originalNickname: String = "",
    val canEditNickname: Boolean = false,
    val isSavingNickname: Boolean = false,
    val nicknameError: Int? = null,
    val nicknameSaved: Boolean = false
) {
    val displayName: String get() = originalNickname.ifBlank { name }
    val canSaveNickname: Boolean get() = canEditNickname && !isLoading && !isSavingNickname &&
        NicknameRepository.normalize(nickname) != originalNickname
}

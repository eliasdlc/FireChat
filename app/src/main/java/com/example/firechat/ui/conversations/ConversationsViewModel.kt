package com.example.firechat.ui.conversations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.example.firechat.R
import com.example.firechat.data.model.Conversation
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.NicknameRepository
import kotlinx.coroutines.flow.catch

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository = ChatRepository()
    private val authRepository = AuthRepository()

    val myUid: String = authRepository.currentUserId.orEmpty()
    val nicknames: LiveData<Map<String, String>> = NicknameRepository(application).observe(myUid).asLiveData()

    private val _error = MutableLiveData<Int?>()
    val error: LiveData<Int?> = _error

    val conversations: LiveData<List<Conversation>> =
        chatRepository.observeConversations(myUid)
            .catch { _error.value = R.string.error_loading_conversations }
            .asLiveData()

    fun logout() {
        authRepository.logout()
    }
}

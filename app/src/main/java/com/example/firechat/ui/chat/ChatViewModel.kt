package com.example.firechat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.firechat.R
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.UserRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ChatViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val userRepository = UserRepository()
    private val chatRepository = ChatRepository()

    val myUid: String = AuthRepository().currentUserId.orEmpty()

    private val recipient = User(
        uid = savedStateHandle.get<String>(ChatActivity.EXTRA_USER_ID).orEmpty(),
        name = savedStateHandle.get<String>(ChatActivity.EXTRA_USER_NAME).orEmpty()
    )

    val recipientName: String = recipient.name

    val chatId: String = ChatRepository.chatIdFor(myUid, recipient.uid)

    private var me: User? = null

    private val _uiState = MutableLiveData(ChatUiState())
    val uiState: LiveData<ChatUiState> = _uiState

    val messages: LiveData<List<Message>> = chatRepository.observeMessages(chatId)
        .catch { _uiState.value = ChatUiState(error = R.string.error_loading_messages) }
        .asLiveData()

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                chatRepository.sendText(chatId, sender(), recipient, trimmed)
            } catch (e: Exception) {
                _uiState.value = ChatUiState(error = R.string.error_sending_message)
            }
        }
    }

    fun errorShown() {
        _uiState.value = _uiState.value?.copy(error = null)
    }

    private suspend fun sender(): User {
        me?.let { return it }
        val name = userRepository.getUser(myUid)?.name.orEmpty()
        return User(uid = myUid, name = name).also { me = it }
    }
}

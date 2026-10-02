package com.example.firechat.ui.conversations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.example.firechat.R
import com.example.firechat.data.model.Conversation
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.UserRepository
import kotlinx.coroutines.flow.catch

class ConversationsViewModel : ViewModel() {

    private val chatRepository = ChatRepository()

    val myUid: String = UserRepository.LOCAL_USER_ID

    private val _error = MutableLiveData<Int?>()
    val error: LiveData<Int?> = _error

    val conversations: LiveData<List<Conversation>> =
        chatRepository.observeConversations(myUid)
            .catch { _error.value = R.string.error_loading_conversations }
            .asLiveData()
}

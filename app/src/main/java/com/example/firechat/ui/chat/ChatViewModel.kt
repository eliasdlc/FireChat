package com.example.firechat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.firechat.R
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.data.repository.NicknameRepository
import com.example.firechat.data.repository.TypingRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ChatViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val userRepository = UserRepository()
    private val chatRepository = ChatRepository()

    val myUid: String = AuthRepository().currentUserId.orEmpty()

    private val recipient = User(
        uid = savedStateHandle.get<String>(ChatActivity.EXTRA_USER_ID).orEmpty(),
        name = savedStateHandle.get<String>(ChatActivity.EXTRA_USER_NAME).orEmpty()
    )

    val recipientName: String = recipient.name
    val recipientId: String = recipient.uid
    // Personal display names never enter the public User used for sending messages.
    val recipientDisplayName: LiveData<String> = NicknameRepository(application).observe(myUid)
        .map { it[recipient.uid] ?: recipient.name }
        .asLiveData()

    val recipientPhoto: LiveData<String?> =
        if (recipientId.isNotBlank() && '/' !in recipientId)
            userRepository.observeUser(recipientId).map { it?.photoUrl }
                .catch { emit(null) }.asLiveData()
        else MutableLiveData(null)

    val chatId: String = ChatRepository.chatIdFor(myUid, recipient.uid)

    private val typing = TypingRepository()
    private var idleTypingJob: Job? = null
    private var lastTypingPublish = 0L
    private var publishedTyping = false
    private var chatActive = false
    val recipientTyping: LiveData<Boolean> =
        if (myUid.isNotBlank() && recipientId.isNotBlank() && myUid != recipientId)
            typing.observe(chatId, recipientId).asLiveData()
        else MutableLiveData(false)

    fun chatResumed() { chatActive = true }

    fun messageEdited(text: CharSequence?) {
        if (!chatActive) return
        if (text.isNullOrBlank()) { stopTyping(); return }
        if (myUid.isBlank() || recipientId.isBlank() || myUid == recipientId || AuthRepository().currentUserId != myUid) return
        val now = SystemClock.elapsedRealtime()
        if (!publishedTyping || now - lastTypingPublish >= TYPING_THROTTLE_MS) {
            typing.publish(chatId, myUid)
            lastTypingPublish = now
            publishedTyping = true
        }
        idleTypingJob?.cancel()
        idleTypingJob = viewModelScope.launch { delay(TYPING_IDLE_MS); stopTyping() }
    }

    fun chatPaused() { chatActive = false; stopTyping() }

    private fun stopTyping() {
        idleTypingJob?.cancel()
        idleTypingJob = null
        if (publishedTyping && AuthRepository().currentUserId == myUid) typing.clear(chatId, myUid)
        publishedTyping = false
    }

    override fun onCleared() { stopTyping() }

    private var me: User? = null

    private val _uiState = MutableLiveData(ChatUiState())
    val uiState: LiveData<ChatUiState> = _uiState

    val messages: LiveData<List<Message>> = chatRepository.observeMessages(chatId)
        .catch { _uiState.value = ChatUiState(error = R.string.error_loading_messages) }
        .asLiveData()

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        stopTyping()
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

    private companion object {
        const val TYPING_THROTTLE_MS = 2_000L
        const val TYPING_IDLE_MS = 3_000L
    }

    private suspend fun sender(): User {
        me?.let { return it }
        val name = userRepository.getUser(myUid)?.name.orEmpty()
        return User(uid = myUid, name = name).also { me = it }
    }
}

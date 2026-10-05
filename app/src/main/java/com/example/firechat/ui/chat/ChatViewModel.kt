package com.example.firechat.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import android.app.Application
import android.net.Uri
import com.example.firechat.data.media.OutgoingMedia
import com.example.firechat.data.repository.ChatMediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.example.firechat.data.repository.InboxRepository
import com.example.firechat.data.model.ReadMarker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ChatViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val userRepository = UserRepository()
    private val chatRepository = ChatRepository()
    private val mediaRepository = ChatMediaRepository()

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

    private val inboxRepository = InboxRepository()
    private var windowFocused = false
    private var displayedMessages: List<Message> = emptyList()
    private var readJob: Job? = null
    private var lastMarked = ReadMarker()
    val recipientRead: LiveData<ReadMarker> = inboxRepository.observeRead(chatId, recipientId)
        .catch { emit(ReadMarker()) }.asLiveData()

    fun chatResumed() { chatActive = true; publishRead() }
    fun windowFocusChanged(focused: Boolean) { windowFocused = focused; publishRead() }
    fun messagesShown(list: List<Message>) { displayedMessages = list; publishRead() }

    private fun publishRead() {
        if (!chatActive || !windowFocused || readJob?.isActive == true || AuthRepository().currentUserId != myUid) return
        val message = displayedMessages.lastOrNull { it.senderId == recipientId && it.serverCreatedAt != null } ?: return
        val marker = ReadMarker(message.serverCreatedAt, message.id)
        if (!marker.isAfter(lastMarked)) return
        readJob = viewModelScope.launch {
            var confirmed = false
            try {
                inboxRepository.markRead(chatId, myUid, message)
                lastMarked = marker
                confirmed = true
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _uiState.value = (_uiState.value ?: ChatUiState()).copy(error = R.string.error_read_receipt) }
            finally {
                readJob = null
                if (confirmed) publishRead()
            }
        }
    }

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
        .catch { _uiState.value = (_uiState.value ?: ChatUiState()).copy(error = R.string.error_loading_messages) }
        .asLiveData()

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        stopTyping()
        viewModelScope.launch {
            try {
                chatRepository.sendText(chatId, sender(), recipient, trimmed)
            } catch (e: Exception) {
                _uiState.value = (_uiState.value ?: ChatUiState()).copy(error = R.string.error_sending_message)
            }
        }
    }

    /** Prepares, uploads and sends a photo or video picked from the device. */
    fun sendMedia(uri: Uri) {
        if (_uiState.value?.uploadProgress != null) return
        _uiState.value = ChatUiState(uploadProgress = 0)
        viewModelScope.launch {
            try {
                val media = withContext(Dispatchers.IO) { OutgoingMedia.from(getApplication<Application>().contentResolver, uri) }
                val uploaded = mediaRepository.upload(chatId, myUid, media) { percent ->
                    _uiState.postValue(ChatUiState(uploadProgress = percent))
                }
                chatRepository.sendMedia(chatId, sender(), recipient, media, uploaded)
                _uiState.value = ChatUiState()
            } catch (error: CancellationException) { throw error }
            catch (_: OutgoingMedia.TooLargeException) { _uiState.value = ChatUiState(error = R.string.error_media_too_large) }
            catch (_: Exception) { _uiState.value = ChatUiState(error = R.string.error_sending_media) }
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

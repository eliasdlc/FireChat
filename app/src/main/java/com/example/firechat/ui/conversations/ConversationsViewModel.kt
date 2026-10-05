package com.example.firechat.ui.conversations

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.firechat.data.repository.InboxRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.MutableLiveData
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.map
import androidx.lifecycle.distinctUntilChanged
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.R
import com.example.firechat.data.model.Conversation
import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.NicknameRepository
import com.example.firechat.data.repository.PushTokenRepository
import kotlinx.coroutines.flow.catch

class ConversationsViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val chatRepository = ChatRepository()
    private val inboxRepository = InboxRepository()
    private val archiving = mutableSetOf<String>()
    private val authRepository = AuthRepository()

    val myUid: String = authRepository.currentUserId.orEmpty()
    val nicknames: LiveData<Map<String, String>> = NicknameRepository(application).observe(myUid).asLiveData()

    private val _error = MutableLiveData<Int?>()
    val error: LiveData<Int?> = _error

    init {
        // Publishes this device's FCM token so new messages reach it as notifications.
        if (myUid.isNotBlank()) viewModelScope.launch {
            try { PushTokenRepository().register(myUid) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Retried on the next inbox visit and on every token refresh. */ }
        }
    }

    val conversations: LiveData<List<Conversation>> =
        chatRepository.observeConversations(myUid)
            .catch { _error.value = R.string.error_loading_conversations }
            .asLiveData()

    val photos: LiveData<Map<String, String?>> = conversations
        .map { list -> list.map { it.otherUid(myUid) }.distinct().sorted() }
        .distinctUntilChanged()
        .switchMap { ids ->
            UserRepository().observePhotos(ids)
                .catch { emit(emptyMap()) }
                .asLiveData()
        }

    private val unreadCounts = conversations
        .map { list -> list.map { it.id to it.otherUid(myUid) }.sortedBy { it.first } }
        .distinctUntilChanged().switchMap { ids ->
            inboxRepository.observeUnreadCounts(ids, myUid)
                .catch { _error.value = R.string.error_loading_read_states; emit(emptyMap()) }.asLiveData()
        }
    private val archivedChats = inboxRepository.observeArchived(myUid)
        .catch { _error.value = R.string.error_loading_archived; emit(emptySet()) }.asLiveData()
    private val query = savedStateHandle.getLiveData("inbox_query", "")
    private val filter = savedStateHandle.getLiveData("inbox_filter", InboxFilter.ALL.name)
    private val showingArchived = savedStateHandle.getLiveData("inbox_archived", false)
    private val _inbox = MediatorLiveData<InboxUiState>()
    val inbox: LiveData<InboxUiState> = _inbox

    init {
        _inbox.addSource(conversations) { rebuild() }
        _inbox.addSource(unreadCounts) { rebuild() }
        _inbox.addSource(archivedChats) { rebuild() }
        _inbox.addSource(nicknames) { rebuild() }
        _inbox.addSource(query) { rebuild() }
        _inbox.addSource(filter) { rebuild() }
        _inbox.addSource(showingArchived) { rebuild() }
    }

    private fun rebuild() {
        val rows = conversations.value.orEmpty().map {
            InboxRow(it, unreadCounts.value?.get(it.id), it.id in archivedChats.value.orEmpty())
        }
        val selected = InboxFilter.valueOf(filter.value ?: InboxFilter.ALL.name)
        val archived = showingArchived.value == true
        _inbox.value = InboxUiState(
            rows = InboxSelection.filter(rows, myUid, nicknames.value.orEmpty(), query.value.orEmpty(), selected, archived),
            query = query.value.orEmpty(), filter = selected, archived = archived,
            archivedCount = rows.count { it.archived },
            unreadChats = rows.count { it.archived == archived && (it.unreadCount ?: 0) > 0 }
        )
    }

    fun searchChanged(value: String) { if (query.value != value) savedStateHandle["inbox_query"] = value }
    fun filterChanged(value: InboxFilter) { if (filter.value != value.name) savedStateHandle["inbox_filter"] = value.name }
    fun showArchived(value: Boolean) {
        savedStateHandle["inbox_query"] = ""
        savedStateHandle["inbox_filter"] = InboxFilter.ALL.name
        savedStateHandle["inbox_archived"] = value
    }

    fun setArchived(chatId: String, value: Boolean) {
        if (authRepository.currentUserId != myUid || !archiving.add(chatId)) return
        viewModelScope.launch {
            try { inboxRepository.setArchived(myUid, chatId, value) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { _error.value = R.string.error_archiving_chat }
            finally { archiving.remove(chatId) }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}

package com.example.firechat.data.repository

import com.example.firechat.data.model.Conversation
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Date
import java.util.UUID

class ChatRepository {

    fun observeConversations(uid: String): Flow<List<Conversation>> = flowOf(emptyList())

    fun observeMessages(chatId: String): Flow<List<Message>> =
        messages.map { it[chatId].orEmpty() }

    suspend fun sendText(chatId: String, sender: User, recipient: User, text: String) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            senderId = sender.uid,
            senderName = sender.name,
            text = text,
            createdAt = Date()
        )
        messages.update { it + (chatId to it[chatId].orEmpty() + message) }
    }

    companion object {
        private val messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())

        fun chatIdFor(uidA: String, uidB: String): String =
            listOf(uidA, uidB).sorted().joinToString("_")
    }
}

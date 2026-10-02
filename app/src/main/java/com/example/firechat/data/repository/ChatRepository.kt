package com.example.firechat.data.repository

import com.example.firechat.data.model.Conversation
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Date
import java.util.UUID

class ChatRepository {

    fun observeConversations(uid: String): Flow<List<Conversation>> =
        conversations.map { all ->
            all.values
                .filter { uid in it.participants }
                .sortedByDescending { it.lastMessageAt }
        }

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
        conversations.update {
            it + (chatId to Conversation(
                id = chatId,
                participants = listOf(sender.uid, recipient.uid),
                participantNames = mapOf(sender.uid to sender.name, recipient.uid to recipient.name),
                lastMessage = message.text,
                lastMessageAt = message.createdAt
            ))
        }
    }

    companion object {
        private val messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
        private val conversations = MutableStateFlow<Map<String, Conversation>>(emptyMap())

        fun chatIdFor(uidA: String, uidB: String): String =
            listOf(uidA, uidB).sorted().joinToString("_")
    }
}

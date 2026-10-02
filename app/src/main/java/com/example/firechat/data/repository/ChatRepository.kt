package com.example.firechat.data.repository

import com.example.firechat.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ChatRepository {

    fun observeConversations(uid: String): Flow<List<Conversation>> = flowOf(emptyList())
}

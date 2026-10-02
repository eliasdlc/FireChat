package com.example.firechat.data.model

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class Conversation(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),

    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",

    val lastMessageIsImage: Boolean = false,
    val lastMessageAt: Date? = null
) {

    fun otherUid(myUid: String): String = participants.firstOrNull { it != myUid } ?: myUid

    fun otherName(myUid: String): String = participantNames[otherUid(myUid)].orEmpty()
}

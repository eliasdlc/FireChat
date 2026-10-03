package com.example.firechat.data.repository

import com.example.firechat.data.model.Conversation
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.User
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val chats get() = db.collection(CHATS)

    fun observeConversations(uid: String): Flow<List<Conversation>> = callbackFlow {
        val registration = chats.whereArrayContains(FIELD_PARTICIPANTS, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toObject(Conversation::class.java, ESTIMATE) }
                    .sortedByDescending { it.lastMessageAt }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun observeMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val registration = chats.document(chatId).collection(MESSAGES)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toObject(Message::class.java, ESTIMATE) }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendText(chatId: String, sender: User, recipient: User, text: String) {
        val chatRef = chats.document(chatId)
        val messageRef = chatRef.collection(MESSAGES).document()
        val message = Message(senderId = sender.uid, senderName = sender.name, text = text)
        val summary = mapOf(
            FIELD_PARTICIPANTS to listOf(sender.uid, recipient.uid),
            "participantNames" to mapOf(sender.uid to sender.name, recipient.uid to recipient.name),
            "lastMessage" to text,
            "lastMessageAt" to FieldValue.serverTimestamp()
        )
        db.batch()
            .set(messageRef, message)
            .set(chatRef, summary, SetOptions.merge())
            .commit()
            .await()
    }

    companion object {
        private const val CHATS = "chats"
        private const val MESSAGES = "messages"
        private const val FIELD_PARTICIPANTS = "participants"
        private const val FIELD_CREATED_AT = "createdAt"
        private val ESTIMATE = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE

        fun chatIdFor(uidA: String, uidB: String): String =
            listOf(uidA, uidB).sorted().joinToString("_")
    }
}

package com.example.firechat.data.repository

import com.example.firechat.data.model.Message
import com.example.firechat.data.model.ReadMarker
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class InboxRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun marker(chatId: String, uid: String) =
        db.collection("chats").document(chatId).collection("reads").document(uid)

    fun observeRead(chatId: String, uid: String): Flow<ReadMarker> = callbackFlow {
        val listener = marker(chatId, uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
            if (snapshot?.metadata?.isFromCache == true && !snapshot.exists()) return@addSnapshotListener
            trySend(snapshot?.toObject(ReadMarker::class.java) ?: ReadMarker())
        }
        awaitClose { listener.remove() }
    }

    suspend fun markRead(chatId: String, uid: String, message: Message) {
        val timestamp = message.serverCreatedAt ?: return
        require(message.id.isNotBlank() && message.senderId != uid)
        val next = ReadMarker(timestamp, message.id)
        val ref = marker(chatId, uid)
        db.runTransaction { transaction ->
            val previous = transaction.get(ref).toObject(ReadMarker::class.java) ?: ReadMarker()
            if (next.isAfter(previous)) transaction.set(ref, mapOf(
                "through" to timestamp, "throughMessageId" to message.id,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUnread(chatId: String, uid: String, peer: String): Flow<Int> =
        observeRead(chatId, uid).flatMapLatest { read ->
            callbackFlow {
                var query = db.collection("chats").document(chatId).collection("messages")
                    .orderBy("createdAt").orderBy(FieldPath.documentId())
                read.through?.let { query = query.startAfter(it, read.throughMessageId) }
                val listener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) { close(error); return@addSnapshotListener }
                    trySend(snapshot?.documents.orEmpty().count {
                        !it.metadata.hasPendingWrites() && it.getTimestamp("createdAt") != null &&
                            it.getString("senderId") == peer
                    })
                }
                awaitClose { listener.remove() }
            }
        }

    fun observeUnreadCounts(chats: List<Pair<String, String>>, uid: String): Flow<Map<String, Int>> {
        if (chats.isEmpty()) return flowOf(emptyMap())
        return combine(chats.map { (id, peer) -> observeUnread(id, uid, peer) }) { counts ->
            chats.map { it.first }.zip(counts.toList()).toMap()
        }
    }

    fun observeArchived(uid: String): Flow<Set<String>> = callbackFlow {
        val listener = db.collection("users").document(uid).collection("chatSettings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents.orEmpty().filter { it.getBoolean("archived") == true }
                    .map { it.id }.toSet())
            }
        awaitClose { listener.remove() }
    }

    suspend fun setArchived(uid: String, chatId: String, archived: Boolean) {
        require(uid.isNotBlank() && uid in chatId.split('_'))
        db.collection("users").document(uid).collection("chatSettings").document(chatId)
            .set(mapOf("archived" to archived)).await()
    }
}

package com.example.firechat.data.repository

import com.example.firechat.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val users get() = db.collection(USERS)

    suspend fun createProfile(user: User) {
        users.document(user.uid).set(user).await()
    }

    suspend fun getUser(uid: String): User? =
        users.document(uid).get().await().toObject(User::class.java)

    suspend fun updateName(uid: String, name: String) {
        require(uid.isNotBlank())
        require(name.isNotBlank())
        users.document(uid)
            .update(FIELD_NAME, name.trim())
            .await()
    }

    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        require(uid.isNotBlank() && '/' !in uid)
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(User::class.java))
        }
        awaitClose { registration.remove() }
    }

    fun observePhotos(uids: List<String>): Flow<Map<String, String?>> {
        val ids = uids.filter { it.isNotBlank() && '/' !in it }.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())
        return combine(ids.map(::observeUser)) { users ->
            ids.zip(users.map { it?.photoUrl }).toMap()
        }
    }

    fun observeUsers(excludeUid: String): Flow<List<User>> = callbackFlow {
        val registration = users.orderBy(FIELD_NAME).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.toObjects(User::class.java).orEmpty()
                .filter { it.uid != excludeUid }
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val USERS = "users"
        const val FIELD_NAME = "name"
    }
}

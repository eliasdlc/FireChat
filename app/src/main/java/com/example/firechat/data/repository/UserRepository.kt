package com.example.firechat.data.repository

import com.example.firechat.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun createProfile(user: User) {
        db.collection(USERS).document(user.uid).set(user).await()
    }

    fun observeUsers(excludeUid: String): Flow<List<User>> =
        flowOf(sampleUsers.filter { it.uid != excludeUid }.sortedBy { it.name })

    suspend fun getUser(uid: String): User? = sampleUsers.find { it.uid == uid }

    companion object {
        const val LOCAL_USER_ID = "local-user"

        private const val USERS = "users"

        private val sampleUsers = listOf(
            User(uid = LOCAL_USER_ID, name = "Yo", email = "yo@firechat.app"),
            User(uid = "user1", name = "Elias De La Cruz", email = "elias@firechat.app"),
            User(uid = "user2", name = "Carlos Gómez", email = "carlos@firechat.app"),
            User(uid = "user3", name = "María Rodríguez", email = "maria@firechat.app"),
            User(uid = "user4", name = "Luis Martínez", email = "luis@firechat.app")
        )
    }
}

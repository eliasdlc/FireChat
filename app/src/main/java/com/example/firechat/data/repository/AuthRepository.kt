package com.example.firechat.data.repository

import com.example.firechat.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val userRepository: UserRepository = UserRepository()
) {

    val currentUserId: String? get() = auth.currentUser?.uid

    val isLoggedIn: Boolean get() = auth.currentUser != null

    suspend fun register(name: String, email: String, password: String) {
        val firebaseUser = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Firebase no devolvió el usuario creado")
        firebaseUser.updateProfile(userProfileChangeRequest { displayName = name }).await()
        userRepository.createProfile(User(uid = firebaseUser.uid, name = name, email = email))
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun logout() {
        auth.signOut()
    }
}

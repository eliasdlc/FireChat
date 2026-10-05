package com.example.firechat.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Device-local nicknames, stored separately for each signed-in account. */
class NicknameRepository(context: Context) {
    private val context = context.applicationContext

    fun read(ownerId: String, userId: String): String =
        if (ownerId.isBlank()) "" else preferences(ownerId).getString(userId, "").orEmpty()

    fun observe(ownerId: String): Flow<Map<String, String>> {
        if (ownerId.isBlank()) return flowOf(emptyMap())
        return callbackFlow {
            val preferences = preferences(ownerId)
            fun snapshot() = preferences.all.mapNotNull { (id, value) ->
                (value as? String)?.takeIf { it.isNotBlank() }?.let { id to it }
            }.toMap()
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            trySend(snapshot())
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()
    }

    @SuppressLint("UseKtx") // The Boolean commit result is needed before reporting success.
    suspend fun save(ownerId: String, userId: String, nickname: String) = withContext(Dispatchers.IO) {
        require(ownerId.isNotBlank() && userId.isNotBlank() && ownerId != userId)
        val normalized = normalize(nickname)
        require(normalized.length <= MAX_LENGTH)
        val editor = preferences(ownerId).edit()
        if (normalized.isEmpty()) editor.remove(userId) else editor.putString(userId, normalized)
        check(editor.commit()) { "Could not persist nickname" }
    }

    private fun preferences(ownerId: String): SharedPreferences {
        val key = Base64.encodeToString(ownerId.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        return context.getSharedPreferences("contact_nicknames_$key", Context.MODE_PRIVATE)
    }

    companion object {
        const val MAX_LENGTH = 40
        fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
    }
}

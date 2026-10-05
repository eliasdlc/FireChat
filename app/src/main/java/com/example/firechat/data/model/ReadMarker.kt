package com.example.firechat.data.model

import com.google.firebase.Timestamp

/** The same timestamp and document ID ordering used by the message query. */
data class ReadMarker(val through: Timestamp? = null, val throughMessageId: String = "") {
    fun includes(message: Message): Boolean {
        val sent = message.serverCreatedAt ?: return false
        val read = through ?: return false
        return sent < read || (sent == read && message.id <= throughMessageId)
    }

    fun isAfter(previous: ReadMarker): Boolean {
        val current = through ?: return false
        val before = previous.through ?: return true
        return current > before || (current == before && throughMessageId > previous.throughMessageId)
    }
}

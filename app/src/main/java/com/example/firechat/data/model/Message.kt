package com.example.firechat.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.google.firebase.firestore.Exclude
import com.google.firebase.Timestamp

data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    @get:Exclude val serverCreatedAt: Timestamp? = null
)

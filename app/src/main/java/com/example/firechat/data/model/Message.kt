package com.example.firechat.data.model

import java.util.Date

data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val createdAt: Date? = null
)

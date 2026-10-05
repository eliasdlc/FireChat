package com.example.firechat.notifications

/** The chat currently on screen; pushes for it are not shown as notifications. */
object ActiveChat {
    @Volatile var chatId: String? = null
}

package com.example.firechat.notifications

import com.example.firechat.data.repository.AuthRepository
import com.example.firechat.data.repository.PushTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the data-only pushes sent by the notifyNewMessage Cloud Function and
 * keeps the signed-in user's token up to date.
 */
class MyChatMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = AuthRepository().currentUserId ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { PushTokenRepository().save(uid, token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val chatId = data["chatId"] ?: return
        // A push for another account on this device, or for the chat on screen, is not shown.
        if (data["recipientId"] != AuthRepository().currentUserId) return
        if (ActiveChat.chatId == chatId) return
        NotificationHelper.showMessage(
            context = this,
            chatId = chatId,
            senderId = data["senderId"].orEmpty(),
            senderName = data["senderName"].orEmpty(),
            type = data["type"].orEmpty(),
            text = data["text"].orEmpty()
        )
    }
}

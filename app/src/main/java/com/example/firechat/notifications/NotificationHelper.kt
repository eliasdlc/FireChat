package com.example.firechat.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.example.firechat.R
import com.example.firechat.data.model.Message
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.conversations.ConversationsActivity

/** Builds and shows the new-message notification, one per chat. */
object NotificationHelper {
    private const val CHANNEL_ID = "messages"

    /** Shows or replaces the notification for [chatId]; tapping it opens that chat above the inbox. */
    fun showMessage(context: Context, chatId: String, senderId: String, senderName: String, type: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val body = when (type) {
            Message.TYPE_IMAGE -> context.getString(R.string.message_image_preview)
            Message.TYPE_VIDEO -> context.getString(R.string.message_video_preview)
            else -> text
        }
        val open = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, ConversationsActivity::class.java))
            .addNextIntent(ChatActivity.newIntent(context, senderId, senderName))
            .getPendingIntent(chatId.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(chatId, ID, notification)
    }

    /** Removes the notification of a chat the user just opened. */
    fun cancel(context: Context, chatId: String) {
        NotificationManagerCompat.from(context).cancel(chatId, ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_messages), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private const val ID = 1
}

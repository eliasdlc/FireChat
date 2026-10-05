package com.example.firechat.ui.conversations

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.ui.common.AvatarView
import com.example.firechat.util.DateFormatter
import com.google.android.material.color.MaterialColors

class ConversationAdapter(
    private val myUid: String,
    private val onClick: (com.example.firechat.data.model.Conversation) -> Unit,
    private val onLongClick: (InboxRow) -> Unit = {}
) : ListAdapter<InboxRow, ConversationAdapter.ViewHolder>(Diff) {
    private var nicknames: Map<String, String> = emptyMap()

    private var photos: Map<String, String?> = emptyMap()

    fun submitPhotos(value: Map<String, String?>) {
        if (photos == value) return
        photos = value
        notifyItemRangeChanged(0, itemCount)
    }

    fun submitNicknames(value: Map<String, String>) {
        if (nicknames == value) return
        nicknames = value
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val avatar: AvatarView = itemView.findViewById(R.id.avatar)
        private val badge: TextView = itemView.findViewById(R.id.unreadBadge)
        private val name: TextView = itemView.findViewById(R.id.name)
        private val time: TextView = itemView.findViewById(R.id.time)
        private val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)

        fun bind(row: InboxRow) {
            val conversation = row.conversation
            val context = itemView.context
            val otherName = nicknames[conversation.otherUid(myUid)] ?: conversation.otherName(myUid)
            name.text = otherName
            avatar.bind(otherName, photos[conversation.otherUid(myUid)])
            lastMessage.text = if (conversation.lastMessageIsImage) {
                context.getString(R.string.message_image_preview)
            } else {
                conversation.lastMessage
            }
            time.text = DateFormatter.conversationTime(context, conversation.lastMessageAt)
            val unread = row.unreadCount ?: 0
            badge.visibility = if (unread > 0) View.VISIBLE else View.GONE
            badge.text = if (unread > 99) "99+" else unread.toString()
            badge.contentDescription = context.resources.getQuantityString(R.plurals.unread_messages_count, unread, unread)
            // Un chat con mensajes sin leer destaca la hora con el acento y el último mensaje en tinta.
            val hasUnread = unread > 0
            time.setTextColor(
                if (hasUnread) MaterialColors.getColor(time, androidx.appcompat.R.attr.colorPrimary)
                else context.getColor(R.color.mute)
            )
            lastMessage.setTextColor(context.getColor(if (hasUnread) R.color.ink else R.color.mute))
            lastMessage.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)
            itemView.setOnClickListener { onClick(conversation) }
            itemView.setOnLongClickListener { onLongClick(row); true }
        }
    }

    private object Diff : DiffUtil.ItemCallback<InboxRow>() {
        override fun areItemsTheSame(old: InboxRow, new: InboxRow) = old.conversation.id == new.conversation.id
        override fun areContentsTheSame(old: InboxRow, new: InboxRow) = old == new
    }
}

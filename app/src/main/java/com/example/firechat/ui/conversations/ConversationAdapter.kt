package com.example.firechat.ui.conversations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.data.model.Conversation
import com.example.firechat.util.DateFormatter

class ConversationAdapter(
    private val myUid: String,
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val avatar: TextView = itemView.findViewById(R.id.avatar)
        private val name: TextView = itemView.findViewById(R.id.name)
        private val time: TextView = itemView.findViewById(R.id.time)
        private val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)

        fun bind(conversation: Conversation) {
            val context = itemView.context
            val otherName = conversation.otherName(myUid)
            name.text = otherName
            avatar.text = otherName.take(1).uppercase()
            lastMessage.text = if (conversation.lastMessageIsImage) {
                context.getString(R.string.message_image_preview)
            } else {
                conversation.lastMessage
            }
            time.text = DateFormatter.conversationTime(context, conversation.lastMessageAt)
            itemView.setOnClickListener { onClick(conversation) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(old: Conversation, new: Conversation) = old.id == new.id
        override fun areContentsTheSame(old: Conversation, new: Conversation) = old == new
    }
}

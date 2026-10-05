package com.example.firechat.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.ReadMarker
import com.example.firechat.util.DateFormatter

class MessageAdapter(
    private val myUid: String
) : ListAdapter<Message, RecyclerView.ViewHolder>(Diff) {

    private var recipientRead = ReadMarker()

    fun submitRecipientRead(value: ReadMarker) {
        if (recipientRead == value) return
        recipientRead = value
        currentList.forEachIndexed { index, message ->
            if (message.senderId == myUid) notifyItemChanged(index)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).senderId == myUid) TYPE_SENT else TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            SentHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
        } else {
            ReceivedHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentHolder -> holder.bind(message)
            is ReceivedHolder -> holder.bind(message)
        }
    }

    inner class SentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val status: TextView = itemView.findViewById(R.id.readStatus)

        private val text: TextView = itemView.findViewById(R.id.text)
        private val time: TextView = itemView.findViewById(R.id.time)

        fun bind(message: Message) {
            text.text = message.text
            status.setText(when {
                message.serverCreatedAt == null -> R.string.message_sending
                recipientRead.includes(message) -> R.string.message_read
                else -> R.string.message_sent
            })
            status.contentDescription = status.text
            time.text = DateFormatter.messageTime(itemView.context, message.createdAt)
        }
    }

    inner class ReceivedHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val text: TextView = itemView.findViewById(R.id.text)
        private val time: TextView = itemView.findViewById(R.id.time)

        fun bind(message: Message) {
            text.text = message.text
            time.text = DateFormatter.messageTime(itemView.context, message.createdAt)
        }
    }

    private object Diff : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(old: Message, new: Message) = old.id == new.id
        override fun areContentsTheSame(old: Message, new: Message) = old == new
    }

    private companion object {
        const val TYPE_SENT = 1
        const val TYPE_RECEIVED = 2
    }
}

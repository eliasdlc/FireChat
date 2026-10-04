package com.example.firechat.ui.users

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.firechat.R
import com.example.firechat.data.model.User

class UserAdapter(
    private val onClick: (User) -> Unit
) : ListAdapter<User, UserAdapter.ViewHolder>(Diff) {
    private var nicknames: Map<String, String> = emptyMap()

    fun submitNicknames(value: Map<String, String>) {
        if (nicknames == value) return
        nicknames = value
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val avatar: TextView = itemView.findViewById(R.id.avatar)
        private val name: TextView = itemView.findViewById(R.id.name)
        private val email: TextView = itemView.findViewById(R.id.email)

        fun bind(user: User) {
            val displayName = nicknames[user.uid] ?: user.name
            name.text = displayName
            email.text = user.email
            avatar.text = displayName.take(1).uppercase()
            itemView.setOnClickListener { onClick(user) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(old: User, new: User) = old.uid == new.uid
        override fun areContentsTheSame(old: User, new: User) = old == new
    }
}

package com.example.firechat.ui.conversations

import com.example.firechat.data.model.Conversation
import java.text.Normalizer
import java.util.Locale

enum class InboxFilter { ALL, READ, UNREAD }

data class InboxRow(val conversation: Conversation, val unreadCount: Int? = null, val archived: Boolean = false)

data class InboxUiState(
    val rows: List<InboxRow> = emptyList(), val query: String = "",
    val filter: InboxFilter = InboxFilter.ALL, val archived: Boolean = false,
    val archivedCount: Int = 0, val unreadChats: Int = 0
)

object InboxSelection {
    fun filter(rows: List<InboxRow>, myUid: String, nicknames: Map<String, String>,
               query: String, filter: InboxFilter, archived: Boolean): List<InboxRow> {
        val search = normalize(query.trim())
        return rows.filter { row ->
            val chat = row.conversation
            row.archived == archived && when (filter) {
                InboxFilter.ALL -> true
                InboxFilter.READ -> row.unreadCount == 0
                InboxFilter.UNREAD -> (row.unreadCount ?: 0) > 0
            } && (search.isEmpty() || normalize(listOf(
                nicknames[chat.otherUid(myUid)].orEmpty(), chat.otherName(myUid), chat.lastMessage
            ).joinToString(" ")).contains(search))
        }
    }

    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
}

package com.example.firechat

import com.example.firechat.data.model.Conversation
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.ReadMarker
import com.example.firechat.ui.conversations.InboxFilter
import com.example.firechat.ui.conversations.InboxRow
import com.example.firechat.ui.conversations.InboxSelection
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class InboxSelectionTest {
    private val chat = Conversation(id = "me_peer", participants = listOf("me", "peer"),
        participantNames = mapOf("peer" to "Camila Pérez"), lastMessage = "Nos vemos mañana")
    private fun select(rows: List<InboxRow>, filter: InboxFilter = InboxFilter.ALL,
                       query: String = "", archived: Boolean = false) =
        InboxSelection.filter(rows, "me", mapOf("peer" to "Mi amiga"), query, filter, archived)

    @Test fun searchMatchesNicknameCanonicalNameAndPreviewWithoutCaseOrAccent() {
        val rows = listOf(InboxRow(chat, 2))
        for (query in listOf(" MI AMIGA ", "camila perez", "MANANA")) assertEquals(rows, select(rows, query = query))
        assertTrue(select(rows, query = "sin coincidencias").isEmpty())
    }

    @Test fun unknownCountsAreNeverClassifiedAsReadAndArchivedStaySeparate() {
        val unknown = InboxRow(chat)
        val read = InboxRow(chat.copy(id = "read"), 0)
        val unread = InboxRow(chat.copy(id = "unread"), 5)
        val archive = InboxRow(chat.copy(id = "archive"), 7, true)
        val rows = listOf(unknown, read, unread, archive)
        assertEquals(listOf(read), select(rows, InboxFilter.READ))
        assertEquals(listOf(unread), select(rows, InboxFilter.UNREAD))
        assertEquals(listOf(archive), select(rows, archived = true))
        assertEquals(listOf(unknown, read, unread), select(rows))
    }

    @Test fun receiptUsesNanosecondsAndDocumentIdAndExcludesPendingMessages() {
        val marker = ReadMarker(Timestamp(10, 500), "b")
        fun message(time: Timestamp?, id: String) = Message(id = id, serverCreatedAt = time)
        assertTrue(marker.includes(message(Timestamp(10, 499), "z")))
        assertTrue(marker.includes(message(Timestamp(10, 500), "a")))
        assertTrue(marker.includes(message(Timestamp(10, 500), "b")))
        assertFalse(marker.includes(message(Timestamp(10, 500), "c")))
        assertFalse(marker.includes(message(Timestamp(10, 501), "a")))
        assertFalse(marker.includes(message(null, "a")))
        assertFalse(ReadMarker().includes(message(Timestamp(10, 500), "a")))
    }

    @Test fun staleReceiptCannotRegressTheCursor() {
        val current = ReadMarker(Timestamp(10, 500), "b")
        assertFalse(current.isAfter(current))
        assertFalse(ReadMarker(Timestamp(10, 499), "z").isAfter(current))
        assertFalse(ReadMarker(Timestamp(10, 500), "a").isAfter(current))
        assertTrue(ReadMarker(Timestamp(10, 500), "c").isAfter(current))
        assertTrue(ReadMarker(Timestamp(10, 501), "a").isAfter(current))
        assertTrue(current.isAfter(ReadMarker()))
    }
}

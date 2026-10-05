package com.example.firechat

import android.os.Build
import android.graphics.Bitmap
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.data.model.Message
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.InboxRepository
import com.example.firechat.data.repository.NicknameRepository
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.chat.ChatViewModel
import com.example.firechat.ui.conversations.ConversationsActivity
import com.example.firechat.ui.conversations.ConversationAdapter
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.ThemePreferences
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Two synthetic clients, restricted to a private emulator or the separate phone QA app. */
@RunWith(AndroidJUnit4::class)
class InboxFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var peerAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var peerDb: FirebaseFirestore
    private lateinit var me: User
    private lateinit var peer: User
    private lateinit var chatId: String
    private lateinit var otherChatId: String

    @Before fun seed() {
        val args = InstrumentationRegistry.getArguments()
        val privatePhone = context.packageName == "com.example.firechat.inboxqa" &&
            args.getString("privatePhoneTests") == "true"
        assumeTrue(privatePhone || (Build.HARDWARE in setOf("ranchu", "goldfish") &&
            args.getString("profileEmulatorTests") == "true"))
        val host = if (privatePhone) "127.0.0.1" else "10.0.2.2"
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        auth = FirebaseAuth.getInstance(); db = FirebaseFirestore.getInstance()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "inbox-peer" }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "inbox-peer")
        peerAuth = FirebaseAuth.getInstance(app); peerDb = FirebaseFirestore.getInstance(app)
        if (!configured) {
            auth.useEmulator(host, 19099); db.useEmulator(host, 18080)
            peerAuth.useEmulator(host, 19099); peerDb.useEmulator(host, 18080)
            configured = true
        }
        auth.signOut(); peerAuth.signOut()
        val suffix = UUID.randomUUID().toString().take(8)
        runBlocking {
            val email = "inbox-$suffix@firechat.test"
            val uid = checkNotNull(auth.createUserWithEmailAndPassword(email, PASSWORD).await().user).uid
            me = User(uid, "El usuario de prueba", email)
            db.collection("users").document(uid).set(me).await()
            val peerEmail = "contacto-$suffix@firechat.test"
            val peerUid = checkNotNull(peerAuth.createUserWithEmailAndPassword(peerEmail, PASSWORD).await().user).uid
            peer = User(peerUid, "Camila Pérez", peerEmail)
            peerDb.collection("users").document(peerUid).set(peer).await()
            chatId = ChatRepository.chatIdFor(me.uid, peer.uid)
            repeat(3) { ChatRepository(peerDb).sendText(chatId, peer, me, "Hola ${it + 1}") }
            NicknameRepository(context).save(me.uid, peer.uid, "Mi amiga")
            val other = User("fixture$suffix", "Bruno Prueba")
            otherChatId = ChatRepository.chatIdFor(me.uid, other.uid)
            ChatRepository(db).sendText(otherChatId, me, other, "Mensaje enviado")
        }
        theme(AppTheme.LIGHT)
    }

    @After fun cleanup() { if (::auth.isInitialized) auth.signOut(); if (::peerAuth.isInitialized) peerAuth.signOut() }

    @Test fun aSearchFiltersBadgesAndPersonalArchive() {
        ActivityScenario.launch(ConversationsActivity::class.java).use { scenario ->
            awaitRows(scenario, setOf(chatId, otherChatId), chatId to 3)
            scenario.onActivity { assertEquals("FireChat", it.findViewById<MaterialToolbar>(R.id.toolbar).title.toString()) }
            onView(org.hamcrest.Matchers.allOf(withId(R.id.unreadBadge), isDisplayed())).check(matches(withText("3")))
            capture("inbox-all-light")
            onView(withId(R.id.filterRead)).perform(click())
            awaitRows(scenario, setOf(otherChatId))
            capture("inbox-read")
            onView(withId(R.id.filterUnread)).perform(click())
            awaitRows(scenario, setOf(chatId))
            capture("inbox-unread")
            onView(withId(R.id.inboxSearch)).perform(replaceText("CAMILA PEREZ"), closeSoftKeyboard())
            awaitRows(scenario, setOf(chatId))
            scenario.recreate()
            awaitRows(scenario, setOf(chatId))
            onView(withId(R.id.inboxSearch)).check(matches(withText("CAMILA PEREZ")))
            capture("inbox-search")
            onView(withId(R.id.inboxSearch)).perform(replaceText("No existe"), closeSoftKeyboard())
            awaitRows(scenario, emptySet())
            onView(withId(R.id.emptyState)).check(matches(withText(R.string.inbox_no_matches)))
            capture("inbox-search-empty")
            onView(withId(R.id.inboxSearch)).perform(replaceText(""), closeSoftKeyboard())
            awaitRows(scenario, setOf(chatId))
            onView(withText("Mi amiga")).perform(longClick())
            onView(withText(R.string.archive_chat)).perform(click())
            awaitRows(scenario, emptySet())
            waitUntil { assertEquals("1", text(scenario, R.id.archivedCount)) }
            assertTrue(runBlocking { setting(db, me.uid, chatId).get(Source.SERVER).await().getBoolean("archived") } == true)
            assertFalse(runBlocking { setting(peerDb, peer.uid, chatId).get(Source.SERVER).await().exists() })
            runBlocking { ChatRepository(peerDb).sendText(chatId, peer, me, "Sigo archivado") }
            awaitRows(scenario, emptySet())
            onView(withId(R.id.archivedEntry)).perform(click())
            awaitRows(scenario, setOf(chatId), chatId to 4)
            scenario.onActivity { assertEquals("Archivados", it.findViewById<MaterialToolbar>(R.id.toolbar).title.toString()) }
            capture("inbox-archived")
            scenario.recreate()
            awaitRows(scenario, setOf(chatId), chatId to 4)
            onView(withText("Mi amiga")).perform(longClick())
            onView(withText(R.string.unarchive_chat)).perform(click())
            awaitRows(scenario, emptySet())
            onView(withId(R.id.emptyState)).check(matches(withText(R.string.inbox_archived_empty)))
            capture("inbox-archived-empty")
            androidx.test.espresso.Espresso.pressBack()
            awaitRows(scenario, setOf(chatId, otherChatId), chatId to 4)
            capture("inbox-unarchived")
        }
        theme(AppTheme.DARK)
        ActivityScenario.launch(ConversationsActivity::class.java).use { scenario ->
            awaitRows(scenario, setOf(chatId, otherChatId), chatId to 4)
            capture("inbox-all-dark")
        }
    }

    @Test fun bReadingClearsIncomingCountAndPeerReceiptUpdatesSentMessage() {
        runBlocking { ChatRepository(db).sendText(chatId, me, peer, "Mensaje para leer") }
        val outgoing = messages(db).last { it.senderId == me.uid }
        chat().use { scenario ->
            waitUntil { assertEquals(messages(db).last { it.senderId == peer.uid }.id, ownMarker().getString("throughMessageId")) }
            waitUntil { assertEquals(context.getString(R.string.message_sent), readStatus(scenario)) }
            capture("chat-sent")
            runBlocking { InboxRepository(peerDb).markRead(chatId, peer.uid, outgoing) }
            waitUntil { assertEquals(context.getString(R.string.message_read), readStatus(scenario)) }
            capture("chat-read")
            val seen = ownMarker().getString("throughMessageId")
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { ChatRepository(peerDb).sendText(chatId, peer, me, "Mientras está cerrado") }
            android.os.SystemClock.sleep(500)
            assertEquals(seen, ownMarker().getString("throughMessageId"))
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil { assertEquals(messages(db).last { it.senderId == peer.uid }.id, ownMarker().getString("throughMessageId")) }
        }
        ActivityScenario.launch(ConversationsActivity::class.java).use { scenario ->
            awaitRows(scenario, setOf(chatId, otherChatId), chatId to 0)
            onView(withId(R.id.filterUnread)).perform(click())
            awaitRows(scenario, emptySet())
            capture("inbox-read-cleared")
        }
    }

    @Test fun cWindowWithoutFocusDoesNotMarkNewMessagesRead() {
        chat().use { scenario ->
            waitUntil { assertTrue(ownMarker().exists()) }
            val seen = ownMarker().getString("throughMessageId")
            scenario.onActivity { ViewModelProvider(it)[ChatViewModel::class.java].windowFocusChanged(false) }
            runBlocking { ChatRepository(peerDb).sendText(chatId, peer, me, "Sin foco") }
            waitUntil { onView(withText("Sin foco")).check(matches(isDisplayed())) }
            assertEquals(seen, ownMarker().getString("throughMessageId"))
            scenario.onActivity { ViewModelProvider(it)[ChatViewModel::class.java].windowFocusChanged(true) }
            waitUntil { assertEquals(messages(db).last().id, ownMarker().getString("throughMessageId")) }
        }
    }

    @Test fun dRulesProtectReceiptAndArchiveOwnershipAndMonotonicity() {
        val incoming = messages(db).filter { it.senderId == peer.uid }
        val inbox = InboxRepository(db)
        runBlocking { inbox.markRead(chatId, me.uid, incoming.last()); inbox.markRead(chatId, me.uid, incoming.first()) }
        assertEquals(incoming.last().id, ownMarker().getString("throughMessageId"))
        fun record(message: Message) = mapOf("through" to message.serverCreatedAt,
            "throughMessageId" to message.id, "updatedAt" to FieldValue.serverTimestamp())
        denied { db.collection("chats").document(chatId).collection("reads").document(peer.uid).set(record(incoming.last())).await() }
        denied { db.collection("chats").document(chatId).collection("reads").document(me.uid).set(record(incoming.first())).await() }
        runBlocking { ChatRepository(db).sendText(chatId, me, peer, "No puedo leer mi propio mensaje") }
        denied { db.collection("chats").document(chatId).collection("reads").document(me.uid).set(record(messages(db).last())).await() }
        denied { setting(db, peer.uid, chatId).set(mapOf("archived" to true)).await() }
        denied { setting(db, me.uid, chatId).set(mapOf("archived" to "invalid")).await() }
        denied { setting(db, me.uid, "stranger_other").set(mapOf("archived" to true)).await() }
        runBlocking { inbox.setArchived(me.uid, chatId, true) }
        denied { setting(peerDb, me.uid, chatId).get(Source.SERVER).await() }
        runBlocking { InboxRepository(peerDb).setArchived(peer.uid, chatId, true); inbox.setArchived(me.uid, chatId, false) }
        assertTrue(runBlocking { setting(peerDb, peer.uid, chatId).get(Source.SERVER).await().getBoolean("archived") } == true)
        assertEquals(4, messages(db).size)
        assertEquals("Camila Pérez", runBlocking { peerDb.collection("users").document(peer.uid).get(Source.SERVER).await().getString("name") })
    }

    private fun setting(client: FirebaseFirestore, uid: String, id: String) =
        client.collection("users").document(uid).collection("chatSettings").document(id)
    private fun ownMarker() = runBlocking { peerDb.collection("chats").document(chatId).collection("reads").document(me.uid).get(Source.SERVER).await() }
    private fun messages(client: FirebaseFirestore) = runBlocking {
        client.collection("chats").document(chatId).collection("messages").orderBy("createdAt").orderBy(FieldPath.documentId())
            .get(Source.SERVER).await().documents.map { checkNotNull(it.toObject(Message::class.java)).copy(serverCreatedAt = it.getTimestamp("createdAt")) }
    }
    private fun readStatus(scenario: ActivityScenario<ChatActivity>): String {
        var value = ""
        scenario.onActivity { value = it.findViewById<TextView>(R.id.readStatus)?.text?.toString().orEmpty() }
        return value
    }
    private fun chat() = ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peer.uid, peer.name))
    private fun denied(action: suspend () -> Unit) {
        val error = runBlocking { runCatching { action() }.exceptionOrNull() }
        assertTrue("Expected permission denial, got $error", error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
    private fun awaitRows(scenario: ActivityScenario<ConversationsActivity>, ids: Set<String>, count: Pair<String, Int>? = null) = waitUntil {
        var rows: List<com.example.firechat.ui.conversations.InboxRow> = emptyList()
        scenario.onActivity {
            rows = (it.findViewById<RecyclerView>(R.id.conversationList).adapter as ConversationAdapter).currentList.toList()
        }
        assertEquals(ids, rows.map { row -> row.conversation.id }.toSet())
        if (count != null) assertEquals(count.second, rows.first { row -> row.conversation.id == count.first }.unreadCount)
    }
    private fun text(scenario: ActivityScenario<ConversationsActivity>, id: Int): String {
        var value = ""; scenario.onActivity { value = it.findViewById<TextView>(id).text.toString() }; return value
    }
    private fun theme(value: AppTheme) {
        ThemePreferences.save(context, value)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(value.nightMode) }
    }
    private fun waitUntil(assertion: () -> Unit) {
        val deadline = System.nanoTime() + 30_000_000_000L
        var last: Throwable? = null
        while (System.nanoTime() < deadline) {
            try { assertion(); return }
            catch (error: AssertionError) { last = error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { last = error }
            Thread.sleep(100)
        }
        throw AssertionError("Inbox state timeout", last)
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(1000)
        val viewport = InstrumentationRegistry.getArguments().getString("proofViewport", "default")
        val file = File(context.getExternalFilesDir("profile-proof"), "$name-$viewport.png")
        file.outputStream().use { checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    companion object { private const val PASSWORD = "SyntheticInbox123!"; private var configured = false }
}

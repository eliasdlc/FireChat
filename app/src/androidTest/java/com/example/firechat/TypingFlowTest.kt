package com.example.firechat

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.graphics.Canvas
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.NicknameRepository
import com.example.firechat.data.repository.TypingRepository
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.ThemePreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date
import java.util.UUID

/** Two independent Firebase clients exercise native presence and authorization locally. */
@RunWith(AndroidJUnit4::class)
class TypingFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var peerAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var peerDb: FirebaseFirestore
    private lateinit var myUid: String
    private lateinit var peerUid: String
    private lateinit var chatId: String
    private lateinit var peerTyping: TypingRepository

    @Before
    fun seedAccounts() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
        )
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val peerApp = FirebaseApp.getApps(context).firstOrNull { it.name == "typing-peer" }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "typing-peer")
        peerAuth = FirebaseAuth.getInstance(peerApp)
        peerDb = FirebaseFirestore.getInstance(peerApp)
        if (!configured) {
            auth.useEmulator("10.0.2.2", 19099); db.useEmulator("10.0.2.2", 18080)
            peerAuth.useEmulator("10.0.2.2", 19099); peerDb.useEmulator("10.0.2.2", 18080)
            configured = true
        }
        auth.signOut(); peerAuth.signOut()
        val suffix = UUID.randomUUID().toString().take(8)
        runBlocking {
            val email = "lector-$suffix@firechat.test"
            myUid = checkNotNull(auth.createUserWithEmailAndPassword(email, PASSWORD).await().user).uid
            db.collection("users").document(myUid).set(User(myUid, "Lector Prueba", email)).await()
            val peerEmail = "escribiendo-$suffix@firechat.test"
            peerUid = checkNotNull(peerAuth.createUserWithEmailAndPassword(peerEmail, PASSWORD).await().user).uid
            peerDb.collection("users").document(peerUid).set(User(peerUid, "Camila Prueba", peerEmail)).await()
        }
        chatId = ChatRepository.chatIdFor(myUid, peerUid)
        peerTyping = TypingRepository(peerDb)
        setTheme(AppTheme.LIGHT)
    }

    @After
    fun signOut() { if (::auth.isInitialized) { auth.signOut(); peerAuth.signOut() } }

    @Test
    fun aRemoteTypingShowsAnimatedBubbleWithPersonalName() {
        runBlocking { NicknameRepository(context).save(myUid, peerUid, "Mi Cami") }
        chat().use { scenario ->
            awaitBubble(scenario, false)
            peerTyping.publish(chatId, peerUid)
            awaitBubble(scenario, true)
            scenario.onActivity {
                assertEquals("Mi Cami está escribiendo", it.findViewById<View>(R.id.typingBubble).contentDescription)
            }
            val frames = mutableSetOf<Int>()
            repeat(6) {
                frames.add(bubbleFrame(scenario))
                Thread.sleep(160)
            }
            assertTrue("Dots must move across native frames", frames.size > 1)
            capture("typing-light")
            peerTyping.publish(chatId, peerUid)
            scenario.onActivity {
                val input = it.findViewById<EditText>(R.id.messageInput)
                input.requestFocus()
                (it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            waitUntil {
                var keyboardVisible = false
                scenario.onActivity {
                    keyboardVisible = ViewCompat.getRootWindowInsets(it.findViewById(R.id.main))
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                assertTrue(keyboardVisible)
            }
            peerTyping.publish(chatId, peerUid)
            awaitBubble(scenario, true)
            scenario.onActivity {
                val bubble = it.findViewById<View>(R.id.typingBubble)
                val input = it.findViewById<View>(R.id.messageInput)
                assertTrue("Typing bubble must remain above the composer", bubble.bottom <= input.top)
            }
            capture("typing-keyboard")
            onView(withId(R.id.messageInput)).perform(closeSoftKeyboard())
            peerTyping.clear(chatId, peerUid)
            awaitBubble(scenario, false)
            capture("typing-hidden")
        }
    }

    @Test
    fun bEditingIsThrottledAndStopsOnIdleOrBlankInput() {
        chat().use { scenario ->
            onView(withId(R.id.messageInput)).perform(replaceText("H"), closeSoftKeyboard())
            waitUntil { assertTrue(ownPresence().exists()) }
            val first = ownPresence().getTimestamp("updatedAt")
            scenario.onActivity { activity ->
                val input = activity.findViewById<EditText>(R.id.messageInput)
                repeat(20) { input.append("a") }
            }
            assertEquals(first, ownPresence().getTimestamp("updatedAt"))
            awaitBubble(scenario, false) // Never show the local user's own presence.
            waitUntil { assertFalse(ownPresence().exists()) }
            onView(withId(R.id.messageInput)).perform(replaceText("Nuevo"), closeSoftKeyboard())
            waitUntil { assertTrue(ownPresence().exists()) }
            onView(withId(R.id.messageInput)).perform(replaceText("   "), closeSoftKeyboard())
            waitUntil { assertFalse(ownPresence().exists()) }
            assertTrue(runBlocking { db.collection("chats").whereArrayContains("participants", myUid).get(Source.SERVER).await().isEmpty })
            assertTrue(runBlocking { db.collection("chats").document(chatId).collection("messages").get(Source.SERVER).await().isEmpty })
        }
    }

    @Test
    fun cLeavingAndSendingClearPresenceWithoutLosingDraft() {
        chat().use { scenario ->
            onView(withId(R.id.messageInput)).perform(replaceText("Borrador"), closeSoftKeyboard())
            waitUntil { assertTrue(ownPresence().exists()) }
            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil { assertFalse(ownPresence().exists()) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            onView(withId(R.id.messageInput)).check(matches(withText("Borrador")))
            assertFalse(ownPresence().exists())
            onView(withId(R.id.messageInput)).perform(replaceText("Mensaje enviado"), closeSoftKeyboard())
            waitUntil { assertTrue(ownPresence().exists()) }
            onView(withId(R.id.sendButton)).perform(click())
            waitUntil { assertFalse(ownPresence().exists()) }
            waitUntil {
                val messages = runBlocking { db.collection("chats").document(chatId).collection("messages").get(Source.SERVER).await() }
                assertEquals("Mensaje enviado", messages.documents.single().getString("text"))
            }
        }
    }

    @Test
    fun dDisconnectedPeerExpiresWithoutAnotherServerEvent() {
        chat().use { scenario ->
            runBlocking { peerDocument().set(mapOf(
                "updatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 4_000))
            )).await() }
            awaitBubble(scenario, true)
            awaitBubble(scenario, false)
            assertTrue(runBlocking { peerDocument().get(Source.SERVER).await().exists() })
            capture("typing-expired")
        }
    }

    @Test
    fun eDarkBubbleAndIncomingMessageKeepPublicSenderName() {
        setTheme(AppTheme.DARK)
        chat().use { scenario ->
            peerTyping.publish(chatId, peerUid)
            awaitBubble(scenario, true)
            capture("typing-dark")
            runBlocking { ChatRepository(peerDb).sendText(chatId,
                User(peerUid, "Camila Prueba"), User(myUid, "Lector Prueba"), "Ya terminé de escribir") }
            peerTyping.clear(chatId, peerUid)
            awaitBubble(scenario, false)
            waitUntil { onView(withId(R.id.text)).check(matches(withText("Ya terminé de escribir"))) }
            onView(withId(R.id.sender)).check(matches(withText("Camila Prueba")))
            capture("typing-message-received")
        }
    }

    @Test
    fun fRulesRejectImpersonationOutsidersAndInvalidPresence() {
        val valid = mapOf("updatedAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 8_000)))
        val peerRef = db.collection("chats").document(chatId).collection("typing").document(peerUid)
        denied { peerRef.set(valid).await() }
        denied { peerRef.delete().await() }
        val mine = db.collection("chats").document(chatId).collection("typing").document(myUid)
        denied { mine.set(valid + ("expiresAt" to Timestamp(Date(System.currentTimeMillis() - 1_000)))).await() }
        denied { mine.set(valid + ("expiresAt" to Timestamp(Date(System.currentTimeMillis() + 60_000)))).await() }
        denied { mine.set(valid + ("updatedAt" to Timestamp(Date(0)))).await() }
        denied { mine.set(valid + ("secret" to "invalid")).await() }
        runBlocking { peerDocument().set(valid + ("expiresAt" to Timestamp(Date(System.currentTimeMillis() + 8_000)))).await() }
        auth.signOut()
        denied { peerRef.get(Source.SERVER).await() }
        runBlocking { auth.createUserWithEmailAndPassword(
            "ajeno-${UUID.randomUUID().toString().take(8)}@firechat.test", PASSWORD).await() }
        denied { peerRef.get(Source.SERVER).await() }
        denied { peerRef.set(valid).await() }
    }

    @Test
    fun gReducedMotionKeepsVisibleDotsStill() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        try {
            chat().use { scenario ->
                animatorScale(0)
                peerTyping.publish(chatId, peerUid)
                awaitBubble(scenario, true)
                val first = bubbleFrame(scenario)
                repeat(4) { Thread.sleep(160); assertEquals(first, bubbleFrame(scenario)) }
                capture("typing-reduced-motion")
            }
        } finally { animatorScale(1) }
    }

    private fun animatorScale(value: Int) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "settings put global animator_duration_scale $value"
        )).use { it.readBytes() }
        waitUntil {
            var enabled = false
            instrumentation.runOnMainSync {
                if (Build.VERSION.SDK_INT >= 26) enabled = ValueAnimator.areAnimatorsEnabled()
            }
            assertEquals(value != 0, enabled)
        }
    }

    private fun bubbleFrame(scenario: ActivityScenario<ChatActivity>): Int {
        var hash = 0
        scenario.onActivity { activity ->
            val bubble = activity.findViewById<View>(R.id.typingBubble)
            val bitmap = Bitmap.createBitmap(bubble.width, bubble.height, Bitmap.Config.ARGB_8888)
            bubble.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            hash = pixels.contentHashCode()
        }
        return hash
    }

    private fun denied(action: suspend () -> Unit) {
        try { runBlocking { action() }; fail("Operation must be denied") }
        catch (error: FirebaseFirestoreException) { assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code) }
    }

    private fun ownPresence() = runBlocking {
        peerDb.collection("chats").document(chatId).collection("typing").document(myUid).get(Source.SERVER).await()
    }
    private fun peerDocument() = peerDb.collection("chats").document(chatId).collection("typing").document(peerUid)
    private fun chat() = ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peerUid, "Camila Prueba"))
    private fun awaitBubble(scenario: ActivityScenario<ChatActivity>, visible: Boolean) = waitUntil {
        var actual = !visible
        scenario.onActivity { actual = it.findViewById<View>(R.id.typingBubble).isVisible }
        assertEquals(visible, actual)
    }
    private fun setTheme(theme: AppTheme) {
        ThemePreferences.save(context, theme)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(theme.nightMode) }
    }
    private fun waitUntil(check: () -> Unit) {
        val end = System.nanoTime() + 20_000_000_000L
        while (true) {
            try { check(); return }
            catch (error: AssertionError) { if (System.nanoTime() >= end) throw error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { if (System.nanoTime() >= end) throw error }
            Thread.sleep(100)
        }
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val viewport = InstrumentationRegistry.getArguments().getString("proofViewport", "default")
        File(context.getExternalFilesDir("profile-proof"), "$name-$viewport.png").outputStream().use {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private companion object {
        const val PASSWORD = "SyntheticProfile123!"
        var configured = false
    }
}

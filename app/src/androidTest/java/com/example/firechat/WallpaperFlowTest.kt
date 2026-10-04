package com.example.firechat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.theme.AppAccent
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.AppearanceActivity
import com.example.firechat.ui.theme.ThemePreferences
import com.example.firechat.ui.wallpaper.ChatWallpaper
import com.example.firechat.ui.wallpaper.WallpaperActivity
import com.example.firechat.ui.wallpaper.WallpaperDrawable
import com.example.firechat.ui.wallpaper.WallpaperPreferences
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WallpaperFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var uid: String
    private lateinit var peer: String
    private lateinit var otherPeer: String
    private lateinit var email: String
    private lateinit var peerEmail: String
    private lateinit var preferences: WallpaperPreferences
    private val chatA get() = ChatRepository.chatIdFor(uid, peer)
    private val chatB get() = ChatRepository.chatIdFor(uid, otherPeer)

    @Before
    fun setup() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        auth = FirebaseAuth.getInstance(); db = FirebaseFirestore.getInstance()
        if (!configured) { auth.useEmulator("10.0.2.2", 19099); db.useEmulator("10.0.2.2", 18080); configured = true }
        auth.signOut()
        val suffix = UUID.randomUUID().toString().take(8)
        email = "fondo-$suffix@firechat.test"; peerEmail = "contacto-fondo-$suffix@firechat.test"
        runBlocking {
            peer = checkNotNull(auth.createUserWithEmailAndPassword(peerEmail, PASSWORD).await().user).uid
            UserRepository().createProfile(User(peer, "Camila Prueba", peerEmail))
            auth.signOut()
            val otherEmail = "otro-fondo-$suffix@firechat.test"
            otherPeer = checkNotNull(auth.createUserWithEmailAndPassword(otherEmail, PASSWORD).await().user).uid
            UserRepository().createProfile(User(otherPeer, "Luis Prueba", otherEmail))
            auth.signOut()
            uid = checkNotNull(auth.createUserWithEmailAndPassword(email, PASSWORD).await().user).uid
            UserRepository().createProfile(User(uid, "Fondo Prueba", email))
        }
        preferences = WallpaperPreferences(context)
        ThemePreferences.saveAccent(context, AppAccent.GREEN)
        setMode(AppTheme.LIGHT)
    }

    @After
    fun cleanup() { if (::auth.isInitialized) auth.signOut() }

    @Test
    fun aAppearanceDefaultAppliesToBothChatsAfterConfirmation() {
        ActivityScenario.launch(AppearanceActivity::class.java).use {
            onView(withId(R.id.defaultWallpaperRow)).perform(scrollTo(), click())
            choose(ChatWallpaper.SAGE)
            assertEquals(ChatWallpaper.ORIGINAL, preferences.default(uid))
            capture("wallpaper-default-preview")
            apply()
            onView(withId(R.id.defaultWallpaperValue)).check(matches(withText(R.string.wallpaper_sage)))
            capture("wallpaper-appearance")
        }
        assertEquals(ChatWallpaper.SAGE, preferences.default(uid))
        assertEquals(AppAccent.GREEN, ThemePreferences.readAccent(context))
        verifyChat(peer, ChatWallpaper.SAGE, "wallpaper-inherited-chat-a")
        verifyChat(otherPeer, ChatWallpaper.SAGE, "wallpaper-inherited-chat-b")
    }

    @Test
    fun bChatOverrideCancelAndApplyKeepDraftAndMessagesIntact() {
        preferences.saveDefault(uid, ChatWallpaper.DOTS)
        runBlocking { ChatRepository().sendText(chatA, User(uid, "Fondo Prueba"), User(peer, "Camila Prueba"), "Mensaje de prueba") }
        val before = runBlocking { db.collection("chats").document(chatA).collection("messages").get(Source.SERVER).await().documents.map { it.data } }
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peer, "Camila Prueba")).use { scenario ->
            onView(withId(R.id.messageInput)).perform(replaceText("Borrador conservado"), closeSoftKeyboard())
            openChatSelector()
            onView(withId(R.id.wallpaperSelection)).check(matches(withText(context.getString(R.string.wallpaper_inherited, context.getString(R.string.wallpaper_dots)))))
            choose(ChatWallpaper.SAND)
            onView(withContentDescription(R.string.navigate_back)).perform(click())
            assertNull(preferences.chatOverride(uid, chatA))
            scenario.onActivity { assertWallpaper(it.findViewById(R.id.chatWallpaper), ChatWallpaper.DOTS) }
            onView(withId(R.id.messageInput)).check(matches(withText("Borrador conservado")))
            openChatSelector()
            choose(ChatWallpaper.LAVENDER)
            capture("wallpaper-chat-preview")
            apply()
            scenario.onActivity { assertWallpaper(it.findViewById(R.id.chatWallpaper), ChatWallpaper.LAVENDER) }
            onView(withId(R.id.messageInput)).check(matches(withText("Borrador conservado")))
            capture("wallpaper-custom-chat-draft")
        }
        assertEquals(ChatWallpaper.LAVENDER, preferences.chatOverride(uid, chatA))
        assertNull(preferences.chatOverride(uid, chatB))
        verifyChat(otherPeer, ChatWallpaper.DOTS, "wallpaper-unaffected-chat")
        assertEquals(before, runBlocking { db.collection("chats").document(chatA).collection("messages").get(Source.SERVER).await().documents.map { it.data } })
    }

    @Test
    fun cChangingDefaultPreservesOverridesAndResetRestoresLiveInheritance() {
        preferences.saveDefault(uid, ChatWallpaper.SAGE)
        preferences.saveOverride(uid, chatA, ChatWallpaper.LAVENDER)
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use { choose(ChatWallpaper.SAND); apply() }
        verifyChat(peer, ChatWallpaper.LAVENDER, "wallpaper-preserved-override")
        verifyChat(otherPeer, ChatWallpaper.SAND, "wallpaper-updated-default")
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peer, "Camila Prueba")).use { scenario ->
            openChatSelector()
            onView(withId(R.id.wallpaperResetButton)).perform(scrollTo(), click())
            capture("wallpaper-use-default-preview")
            apply()
            assertNull(preferences.chatOverride(uid, chatA))
            scenario.onActivity { assertWallpaper(it.findViewById(R.id.chatWallpaper), ChatWallpaper.SAND) }
            scenario.onActivity { it.startActivity(WallpaperActivity.newIntent(it)) }
            choose(ChatWallpaper.GRID); apply()
            scenario.onActivity { assertWallpaper(it.findViewById(R.id.chatWallpaper), ChatWallpaper.GRID) }
            capture("wallpaper-reset-inherits-new-default")
        }
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use {
            onView(withId(R.id.wallpaperResetButton)).perform(scrollTo(), click()); apply()
        }
        assertEquals(ChatWallpaper.ORIGINAL, preferences.default(uid))
    }

    @Test
    fun dAllWallpapersRenderInBothModesAndPreviewSurvivesRecreationWithoutSaving() {
        for (mode in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
            setMode(mode)
            ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use { scenario ->
                for (wallpaper in ChatWallpaper.entries) {
                    choose(wallpaper)
                    scenario.onActivity { activity ->
                        assertWallpaper(activity.findViewById(R.id.wallpaperPreview), wallpaper)
                        ChatWallpaper.entries.forEach { choice ->
                            assertEquals(choice == wallpaper, activity.findViewById<MaterialButton>(buttonId(choice)).isChecked)
                        }
                    }
                    capture("wallpaper-preview-${mode.name.lowercase()}-${wallpaper.name.lowercase()}")
                }
                scenario.recreate()
                scenario.onActivity { assertWallpaper(it.findViewById(R.id.wallpaperPreview), ChatWallpaper.LAVENDER) }
                assertEquals(ChatWallpaper.ORIGINAL, preferences.default(uid))
            }
            preferences.saveDefault(uid, ChatWallpaper.SAGE)
            verifyChat(peer, ChatWallpaper.SAGE, "wallpaper-chat-${mode.name.lowercase()}")
            preferences.saveDefault(uid, ChatWallpaper.ORIGINAL)
        }
    }

    @Test
    fun eLargeFontAndKeyboardKeepControlsReachableAndWallpaperBelowHeader() {
        try {
            shell("settings put system font_scale 1.3")
            ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chatA)).use { scenario ->
                waitUntil { scenario.onActivity { assertEquals(1.3f, it.resources.configuration.fontScale, 0.01f) } }
                choose(ChatWallpaper.GRID)
                capture("wallpaper-large-font")
                apply()
            }
            ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peer, "Camila Prueba")).use { scenario ->
                onView(withId(R.id.messageInput)).perform(click(), replaceText("Probando el fondo"))
                scenario.onActivity {
                    val input = it.findViewById<EditText>(R.id.messageInput)
                    input.requestFocus()
                    (it.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .showSoftInput(input, 0)
                }
                waitUntil { scenario.onActivity { activity ->
                    assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(activity.findViewById(R.id.main))
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
                    val wallpaper = activity.findViewById<View>(R.id.chatWallpaper)
                    val toolbar = activity.findViewById<View>(R.id.toolbar)
                    val composer = activity.findViewById<EditText>(R.id.messageInput)
                    assertWallpaper(wallpaper, ChatWallpaper.GRID)
                    assertTrue(wallpaper.top >= toolbar.bottom)
                    assertTrue(composer.bottom <= wallpaper.bottom)
                    assertTrue(activity.findViewById<View>(R.id.sendButton).isEnabled)
                } }
                capture("wallpaper-chat-keyboard")
                onView(withId(R.id.messageInput)).perform(closeSoftKeyboard())
            }
        } finally { shell("settings put system font_scale 1.0") }
    }

    @Test
    fun fAccountsStayIsolatedAndInvalidValuesFallBackSafely() {
        preferences.saveDefault(uid, ChatWallpaper.SAND)
        preferences.saveOverride(uid, chatA, ChatWallpaper.LAVENDER)
        auth.signOut()
        runBlocking { auth.signInWithEmailAndPassword(peerEmail, PASSWORD).await() }
        verifyChat(uid, ChatWallpaper.ORIGINAL, "wallpaper-other-account-original")
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chatA)).use { choose(ChatWallpaper.GRID); apply() }
        assertEquals(ChatWallpaper.GRID, preferences.resolve(peer, chatA))
        auth.signOut()
        runBlocking { auth.signInWithEmailAndPassword(email, PASSWORD).await() }
        verifyChat(peer, ChatWallpaper.LAVENDER, "wallpaper-owner-selection-retained")
        context.getSharedPreferences("wallpapers_$uid", Context.MODE_PRIVATE).edit()
            .putString("default", "REMOVED_PRESET").putString("chat_$chatB", "REMOVED_PRESET").commit()
        assertEquals(ChatWallpaper.ORIGINAL, preferences.resolve(uid, chatB))
        assertEquals(ChatWallpaper.LAVENDER, preferences.resolve(uid, chatA))
        assertEquals(ChatWallpaper.ORIGINAL, preferences.resolve("", chatA))
    }

    private fun openChatSelector() = onView(withId(R.id.action_chat_wallpaper)).perform(click())
    private fun choose(wallpaper: ChatWallpaper) = onView(withId(buttonId(wallpaper))).perform(scrollTo(), click())
    private fun apply() = onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
    private fun buttonId(wallpaper: ChatWallpaper) = when (wallpaper) {
        ChatWallpaper.ORIGINAL -> R.id.wallpaperOriginal; ChatWallpaper.DOTS -> R.id.wallpaperDots
        ChatWallpaper.GRID -> R.id.wallpaperGrid; ChatWallpaper.SAGE -> R.id.wallpaperSage
        ChatWallpaper.SAND -> R.id.wallpaperSand; ChatWallpaper.LAVENDER -> R.id.wallpaperLavender
    }
    private fun verifyChat(recipient: String, wallpaper: ChatWallpaper, name: String) {
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, recipient, "Contacto Prueba")).use { scenario ->
            scenario.onActivity { assertWallpaper(it.findViewById(R.id.chatWallpaper), wallpaper) }
            capture(name)
        }
    }
    private fun assertWallpaper(view: View, wallpaper: ChatWallpaper) {
        val drawable = view.background as WallpaperDrawable
        assertEquals(wallpaper, drawable.wallpaper)
        val image = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val bounds = Rect(drawable.bounds)
        drawable.setBounds(0, 0, 96, 96); drawable.draw(Canvas(image)); drawable.bounds = bounds
        assertEquals(view.context.getColor(wallpaper.colorRes), image.getPixel(0, 0))
        val colors = mutableSetOf<Int>()
        for (x in 0 until 96) for (y in 0 until 96) colors.add(image.getPixel(x, y))
        assertEquals(wallpaper in listOf(ChatWallpaper.DOTS, ChatWallpaper.GRID), colors.size > 1)
    }
    private fun setMode(mode: AppTheme) {
        ThemePreferences.save(context, mode)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode.nightMode) }
    }
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun waitUntil(check: () -> Unit) {
        val end = System.nanoTime() + 20_000_000_000L
        while (true) {
            try { check(); return }
            catch (error: AssertionError) { if (System.nanoTime() >= end) throw error }
            Thread.sleep(100)
        }
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(500)
        val viewport = InstrumentationRegistry.getArguments().getString("proofViewport", "default")
        File(context.getExternalFilesDir("profile-proof"), "$name-$viewport.png").outputStream().use {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private companion object { const val PASSWORD = "SyntheticWallpaper123!"; var configured = false }
}

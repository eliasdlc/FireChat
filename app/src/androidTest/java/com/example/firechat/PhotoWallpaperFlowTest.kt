package com.example.firechat

import android.content.Context
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo
import com.example.firechat.ui.wallpaper.WallpaperPhotoStore
import com.example.firechat.ui.wallpaper.PhotoWallpaperDrawable
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
import com.example.firechat.ui.wallpaper.WallpaperSelection
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
class PhotoWallpaperFlowTest {
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
    fun aPhotoBrightnessChangesPixelsAndSavedCopySurvivesProviderLoss() {
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use { scenario ->
            PhotoTestSupport.pick("scene")
            val original = PhotoTestSupport.photo(scenario)
            val bright = PhotoTestSupport.sample(original)
            assertEquals(100, original.brightness)
            assertTrue(preferences.default(uid) is WallpaperSelection.Preset)
            PhotoTestSupport.brightness(50)
            val dim = PhotoTestSupport.photo(scenario)
            assertEquals(50, dim.brightness)
            val darker = PhotoTestSupport.sample(dim)
            for ((a, b) in listOf(Color.red(bright) to Color.red(darker), Color.green(bright) to Color.green(darker), Color.blue(bright) to Color.blue(darker))) {
                assertTrue(kotlin.math.abs(a / 2 - b) <= 3)
            }
            capture("photo-preview-50")
            PhotoTestSupport.brightness(0)
            assertEquals(Color.BLACK, PhotoTestSupport.sample(PhotoTestSupport.photo(scenario)))
            PhotoTestSupport.brightness(100)
            assertEquals(bright, PhotoTestSupport.sample(PhotoTestSupport.photo(scenario)))
            PhotoTestSupport.brightness(50)
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
            PhotoTestSupport.waitUntil { assertTrue(preferences.default(uid) is WallpaperSelection.Photo) }
        }
        val saved = preferences.default(uid) as WallpaperSelection.Photo
        assertEquals(50, saved.brightness)
        assertTrue(WallpaperPhotoStore(context).savedFile(uid, saved.fileName).isFile)
        // The saved bytes live under filesDir rather than a provider URI or a cache draft.
        assertFalse(WallpaperPhotoStore(context).draftFile(uid, saved.fileName).exists())
        assertFalse(context.getSharedPreferences("wallpapers_$uid", Context.MODE_PRIVATE).getString("default", "")!!.contains("content://"))
        assertEquals(1, context.contentResolver.delete(android.net.Uri.parse("content://com.example.firechat.test.wallpaperfixtures/scene"), null, null))
        verifyPhotoChat(peer, saved, "photo-default-chat")
        verifyPhotoChat(otherPeer, saved, "photo-inherited-chat")
    }

    @Test
    fun bOverrideImageAndInheritedBrightnessPreserveDraftAndPublicMessages() {
        val default = importSaved("scene")
        preferences.saveDefault(uid, default)
        runBlocking { ChatRepository().sendText(chatA, User(uid, "Fondo Prueba"), User(peer, "Camila Prueba"), "Mensaje sin cambios") }
        val before = runBlocking { db.collection("chats").document(chatA).collection("messages").get(Source.SERVER).await().documents.map { it.data } }
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peer, "Camila Prueba")).use { scenario ->
            PhotoTestSupport.photo(scenario, R.id.chatWallpaper)
            onView(withId(R.id.messageInput)).perform(replaceText("Borrador con mi foto"), closeSoftKeyboard())
            onView(withId(R.id.action_chat_wallpaper)).perform(click())
            // Dimming an inherited photo creates a chat override referencing the same owned file.
            PhotoTestSupport.brightness(30)
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
            val dim = PhotoTestSupport.photo(scenario, R.id.chatWallpaper) { it.brightness == 30 }
            assertEquals(30, dim.brightness); assertEquals(default.fileName, dim.fileName)
            onView(withId(R.id.messageInput)).check(matches(withText("Borrador con mi foto")))
            onView(withId(R.id.action_chat_wallpaper)).perform(click())
            PhotoTestSupport.pick("second")
            PhotoTestSupport.waitUntil { onView(withId(R.id.applyWallpaperButton)).check(matches(isEnabled())) }
            PhotoTestSupport.brightness(70)
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
            val custom = PhotoTestSupport.photo(scenario, R.id.chatWallpaper) { it.fileName != default.fileName && it.brightness == 70 }
            assertNotEquals(default.fileName, custom.fileName); assertEquals(70, custom.brightness)
            onView(withId(R.id.messageInput)).check(matches(withText("Borrador con mi foto")))
            capture("photo-own-chat-draft")
        }
        assertEquals(default, preferences.default(uid))
        verifyPhotoChat(otherPeer, default, "photo-other-chat-keeps-default")
        assertEquals(before, runBlocking { db.collection("chats").document(chatA).collection("messages").get(Source.SERVER).await().documents.map { it.data } })
    }

    @Test
    fun cCancelledSelectionAndInvalidOrOversizedInputLeaveSavedPhotoUntouched() {
        val saved = importSaved("scene")
        preferences.saveDefault(uid, saved)
        val photos = WallpaperPhotoStore(context)
        var draft: String? = null
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use { scenario ->
            PhotoTestSupport.photo(scenario)
            PhotoTestSupport.pick(null, cancelled = true)
            assertEquals(saved.fileName, PhotoTestSupport.photo(scenario).fileName)
            for ((kind, error) in listOf("invalid" to R.string.wallpaper_image_error, "oversize" to R.string.wallpaper_image_too_large)) {
                PhotoTestSupport.pick(kind)
                PhotoTestSupport.waitUntil { onView(withId(R.id.wallpaperImageStatus)).check(matches(withText(error))) }
                onView(withId(R.id.wallpaperImageStatus)).perform(scrollTo())
                capture("photo-error-$kind")
                assertEquals(saved, preferences.default(uid))
                assertEquals(saved.fileName, PhotoTestSupport.photo(scenario).fileName)
            }
            PhotoTestSupport.pick("second")
            PhotoTestSupport.waitUntil { onView(withId(R.id.applyWallpaperButton)).check(matches(isEnabled())) }
            draft = PhotoTestSupport.photo(scenario).fileName
            PhotoTestSupport.brightness(20)
            onView(withContentDescription(R.string.navigate_back)).perform(click())
        }
        assertEquals(saved, preferences.default(uid))
        assertFalse(photos.draftFile(uid, checkNotNull(draft)).exists())
        assertTrue(photos.savedFile(uid, saved.fileName).isFile)
    }

    @Test
    fun dImageBoundsOrientationAccountIsolationAndSharedFileCleanup() {
        val large = importSaved("large")
        val largeBitmap = android.graphics.BitmapFactory.decodeFile(WallpaperPhotoStore(context).savedFile(uid, large.fileName).path)
        assertTrue(maxOf(largeBitmap.width, largeBitmap.height) <= WallpaperPhotoStore.MAX_EDGE)
        largeBitmap.recycle()
        val oriented = importSaved("oriented")
        val rotated = android.graphics.BitmapFactory.decodeFile(WallpaperPhotoStore(context).savedFile(uid, oriented.fileName).path)
        assertTrue(rotated.height > rotated.width); rotated.recycle()
        preferences.saveDefault(uid, large)
        preferences.saveOverride(uid, chatA, large.copy(brightness = 40))
        preferences.saveDefault(uid, oriented)
        assertTrue(WallpaperPhotoStore(context).savedFile(uid, large.fileName).isFile)
        preferences.saveOverride(uid, chatA, null)
        assertFalse(WallpaperPhotoStore(context).savedFile(uid, large.fileName).exists())
        assertTrue(preferences.default(peer) is WallpaperSelection.Preset)
        val encoded = context.getSharedPreferences("wallpapers_$uid", Context.MODE_PRIVATE).getString("default", null)
        context.getSharedPreferences("wallpapers_$peer", Context.MODE_PRIVATE).edit().putString("default", encoded).commit()
        assertTrue(preferences.default(peer) is WallpaperSelection.Preset)
        context.getSharedPreferences("wallpapers_$uid", Context.MODE_PRIVATE).edit().putString("chat_$chatB", "{\"photo\":\"../../outside.jpg\"}").commit()
        assertEquals(oriented, preferences.resolve(uid, chatB))
        preferences.saveDefault(uid, ChatWallpaper.ORIGINAL)
        assertFalse(WallpaperPhotoStore(context).savedFile(uid, oriented.fileName).exists())
    }

    @Test
    fun eRecreationKeepsUnappliedPhotoAndBrightnessThenResetInheritsDefault() {
        val default = importSaved("scene")
        preferences.saveDefault(uid, default)
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chatA)).use { scenario ->
            PhotoTestSupport.pick("second")
            PhotoTestSupport.photo(scenario)
            PhotoTestSupport.brightness(45)
            val pending = PhotoTestSupport.photo(scenario).fileName
            scenario.recreate()
            val restored = PhotoTestSupport.photo(scenario)
            assertEquals(pending, restored.fileName); assertEquals(45, restored.brightness)
            assertNull(preferences.chatOverride(uid, chatA))
            capture("photo-restored-draft")
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
            PhotoTestSupport.waitUntil { assertTrue(preferences.chatOverride(uid, chatA) is WallpaperSelection.Photo) }
        }
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chatA)).use { scenario ->
            onView(withId(R.id.wallpaperResetButton)).perform(scrollTo(), click())
            assertEquals(default.fileName, PhotoTestSupport.photo(scenario) { it.fileName == default.fileName }.fileName)
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
            PhotoTestSupport.waitUntil { assertNull(preferences.chatOverride(uid, chatA)) }
        }
        assertEquals(default, preferences.resolve(uid, chatA))
    }

    @Test
    fun fBrightnessAndReadableContentInBothModesLargeFontAndKeyboard() {
        val photo = importSaved("scene").copy(brightness = 35)
        preferences.saveDefault(uid, photo)
        for (mode in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
            setMode(mode)
            ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use { scenario ->
                assertEquals(35, PhotoTestSupport.photo(scenario).brightness)
                onView(withId(R.id.wallpaperBrightnessSlider)).perform(scrollTo())
                capture("photo-preview-${mode.name.lowercase()}")
            }
        }
        try {
            shell("settings put system font_scale 1.3")
            ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chatA)).use { scenario ->
                PhotoTestSupport.waitUntil { scenario.onActivity { assertEquals(1.3f, it.resources.configuration.fontScale, 0.01f) } }
                PhotoTestSupport.photo(scenario)
                PhotoTestSupport.brightness(60)
                capture("photo-large-font")
                onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
            }
            ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peer, "Camila Prueba")).use { scenario ->
                assertEquals(60, PhotoTestSupport.photo(scenario, R.id.chatWallpaper).brightness)
                onView(withId(R.id.messageInput)).perform(replaceText("Foto con teclado"))
                scenario.onActivity {
                    val input = it.findViewById<EditText>(R.id.messageInput); input.requestFocus()
                    (it.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input, 0)
                }
                PhotoTestSupport.waitUntil { scenario.onActivity {
                    assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(it.findViewById(R.id.main))?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
                    assertTrue(it.findViewById<View>(R.id.messageInput).bottom <= it.findViewById<View>(R.id.chatWallpaper).bottom)
                    assertEquals(it.getColor(R.color.ink), it.findViewById<EditText>(R.id.messageInput).currentTextColor)
                } }
                capture("photo-chat-keyboard-dark")
                onView(withId(R.id.messageInput)).perform(closeSoftKeyboard())
            }
        } finally { shell("settings put system font_scale 1.0") }
    }

    @Test
    fun gActualSystemPickerReadsImageFromPrivateEmulatorDownloads() {
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use { scenario ->
            onView(withId(R.id.chooseWallpaperImageButton)).perform(scrollTo(), click())
            PhotoTestSupport.waitUntil {
                assertEquals("com.google.android.documentsui", instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
            }
            capture("photo-system-picker")
            val fileName = "firechat-photo-fixture.png"
            PhotoTestSupport.waitUntil {
                assertTrue(clickNode { it.text?.toString() == fileName || it.contentDescription?.toString()?.contains(fileName) == true })
            }
            PhotoTestSupport.photo(scenario)
            PhotoTestSupport.brightness(55)
            capture("photo-real-picker-preview")
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
            PhotoTestSupport.waitUntil { assertTrue(preferences.default(uid) is WallpaperSelection.Photo) }
        }
        verifyPhotoChat(peer, preferences.default(uid) as WallpaperSelection.Photo, "photo-real-picker-chat")
    }

    private fun clickNode(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return false
        val node = find(root) ?: return false
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        android.util.Log.i("PhotoPicker", "Tap ${node.contentDescription} at $bounds")
        shell("input tap ${bounds.left + bounds.width() / 3} ${bounds.top + bounds.height() * 2 / 3}")
        return true
    }
    private fun importSaved(kind: String): WallpaperSelection.Photo {
        val store = WallpaperPhotoStore(context)
        val name = store.importImage(uid, android.net.Uri.parse("content://com.example.firechat.test.wallpaperfixtures/$kind"))
        store.promote(uid, name)
        return WallpaperSelection.Photo(name)
    }
    private fun verifyPhotoChat(recipient: String, expected: WallpaperSelection.Photo, name: String) {
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, recipient, "Contacto Prueba")).use { scenario ->
            val photo = PhotoTestSupport.photo(scenario, R.id.chatWallpaper)
            assertEquals(expected.fileName, photo.fileName); assertEquals(expected.brightness, photo.brightness)
            capture(name)
        }
    }
    private fun setMode(mode: AppTheme) {
        ThemePreferences.save(context, mode)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode.nightMode) }
    }
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
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

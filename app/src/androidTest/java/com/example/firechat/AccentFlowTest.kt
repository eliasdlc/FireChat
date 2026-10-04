package com.example.firechat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.ChatRepository
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.ui.auth.LoginActivity
import com.example.firechat.ui.auth.RegisterActivity
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.conversations.ConversationsActivity
import com.example.firechat.ui.profile.EditProfileActivity
import com.example.firechat.ui.theme.AppAccent
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.AppearanceActivity
import com.example.firechat.ui.theme.ThemePreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputLayout
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

/** Real Views and synthetic Firebase accounts verify accent rendering and retained forms. */
@RunWith(AndroidJUnit4::class)
class AccentFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var uid: String
    private lateinit var peerUid: String

    @Before
    fun setup() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
        )
        auth = FirebaseAuth.getInstance(); db = FirebaseFirestore.getInstance()
        if (!configured) {
            auth.useEmulator("10.0.2.2", 19099); db.useEmulator("10.0.2.2", 18080); configured = true
        }
        auth.signOut()
        val suffix = UUID.randomUUID().toString().take(8)
        val email = "color-$suffix@firechat.test"
        runBlocking {
            peerUid = checkNotNull(auth.createUserWithEmailAndPassword("contacto-$suffix@firechat.test", PASSWORD).await().user).uid
            UserRepository().createProfile(User(peerUid, "Camila Prueba", "contacto-$suffix@firechat.test"))
            auth.signOut()
            uid = checkNotNull(auth.createUserWithEmailAndPassword(email, PASSWORD).await().user).uid
            UserRepository().createProfile(User(uid, "Color Prueba", email))
        }
        ThemePreferences.saveAccent(context, AppAccent.BLUE)
        setMode(AppTheme.LIGHT)
    }

    @After
    fun finish() { if (::auth.isInitialized) auth.signOut() }

    @Test
    fun aAllPalettesRenderInLightAndDarkAndRemainSelected() {
        for (mode in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
            setMode(mode)
            ActivityScenario.launch(AppearanceActivity::class.java).use { scenario ->
                for (accent in AppAccent.entries) {
                    choose(accent)
                    waitUntil { scenario.onActivity { activity ->
                        assertAccent(activity, accent, mode)
                        AppAccent.entries.forEach { option ->
                            assertEquals(option == accent, activity.findViewById<MaterialButton>(buttonId(option)).isChecked)
                        }
                        val preview = activity.findViewById<TextView>(R.id.accentPreviewMessage)
                        assertEquals(primary(activity, accent), backgroundColor(preview))
                        assertEquals(onPrimary(activity, accent), preview.currentTextColor)
                    } }
                    onView(withId(buttonId(accent))).perform(click())
                    scenario.onActivity { assertTrue(it.findViewById<MaterialButton>(buttonId(accent)).isChecked) }
                    capture("accent-${mode.name.lowercase()}-${accent.name.lowercase()}")
                }
            }
        }
    }

    @Test
    fun bProfileEntryUpdatesRetainedConversationsAndKeepsPublicData() {
        val before = runBlocking { db.collection("users").document(uid).get(Source.SERVER).await().data }
        ActivityScenario.launch(ConversationsActivity::class.java).use { scenario ->
            onView(withId(R.id.action_profile)).perform(click())
            onView(withId(R.id.appearanceRow)).perform(scrollTo(), click())
            choose(AppAccent.GREEN)
            onView(withContentDescription(R.string.navigate_back)).perform(click())
            waitUntil { onView(withId(R.id.profileDisplayName)).check(matches(withText("Color Prueba"))) }
            capture("accent-profile-return")
            onView(withContentDescription(R.string.navigate_back)).perform(click())
            waitUntil { scenario.onActivity { activity ->
                assertAccent(activity, AppAccent.GREEN, AppTheme.LIGHT)
                val button = activity.findViewById<ExtendedFloatingActionButton>(R.id.newChatButton)
                assertEquals(primary(activity, AppAccent.GREEN), button.backgroundTintList?.defaultColor)
                assertEquals(onPrimary(activity, AppAccent.GREEN), button.currentTextColor)
            } }
            capture("accent-conversations")
        }
        assertEquals(before, runBlocking { db.collection("users").document(uid).get(Source.SERVER).await().data })
    }

    @Test
    fun cChangingAccentPreservesChatDraftAndRecolorsActualSentMessage() {
        val chatId = ChatRepository.chatIdFor(uid, peerUid)
        runBlocking { ChatRepository().sendText(chatId, User(uid, "Color Prueba"), User(peerUid, "Camila Prueba"), "Mensaje con mi color") }
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, peerUid, "Camila Prueba")).use { scenario ->
            waitUntil { onView(withId(R.id.text)).check(matches(withText("Mensaje con mi color"))) }
            onView(withId(R.id.messageInput)).perform(replaceText("Borrador sin enviar"), closeSoftKeyboard())
            scenario.onActivity { it.startActivity(Intent(it, AppearanceActivity::class.java)) }
            choose(AppAccent.ROSE)
            onView(withContentDescription(R.string.navigate_back)).perform(click())
            waitUntil { onView(withId(R.id.messageInput)).check(matches(withText("Borrador sin enviar"))) }
            scenario.onActivity { activity ->
                assertAccent(activity, AppAccent.ROSE, AppTheme.LIGHT)
                assertEquals(primary(activity, AppAccent.ROSE), backgroundColor(activity.findViewById(R.id.bubble)))
                assertEquals(onPrimary(activity, AppAccent.ROSE), activity.findViewById<TextView>(R.id.text).currentTextColor)
                assertEquals(primary(activity, AppAccent.ROSE), backgroundColor(activity.findViewById<ImageButton>(R.id.sendButton)))
            }
            capture("accent-chat-draft")
            val messages = runBlocking { db.collection("chats").document(chatId).collection("messages").get(Source.SERVER).await() }
            assertEquals(1, messages.size())
            assertEquals("Color Prueba", messages.documents.single().getString("senderName"))
        }
    }

    @Test
    fun dChangingAccentPreservesProfileDraftAndFocusedFieldColor() {
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            waitUntil { scenario.onActivity { assertTrue(it.findViewById<EditText>(R.id.profileNameInput).isEnabled) } }
            onView(withId(R.id.profileNameInput)).perform(replaceText("Borrador del perfil"), closeSoftKeyboard())
            scenario.onActivity { it.startActivity(Intent(it, AppearanceActivity::class.java)) }
            choose(AppAccent.VIOLET)
            onView(withContentDescription(R.string.navigate_back)).perform(click())
            waitUntil { onView(withId(R.id.profileNameInput)).check(matches(withText("Borrador del perfil"))) }
            scenario.onActivity { activity ->
                assertAccent(activity, AppAccent.VIOLET, AppTheme.LIGHT)
                assertEquals(primary(activity, AppAccent.VIOLET), activity.findViewById<MaterialButton>(R.id.saveProfileButton).backgroundTintList?.defaultColor)
                assertEquals(primary(activity, AppAccent.VIOLET), activity.findViewById<TextInputLayout>(R.id.profileNameLayout).boxStrokeColor)
            }
            capture("accent-profile-draft")
        }
        assertEquals("Color Prueba", runBlocking { db.collection("users").document(uid).get(Source.SERVER).await().getString("name") })
    }

    @Test
    fun eAuthenticationUsesSavedColorAndResetKeepsDarkMode() {
        ThemePreferences.saveAccent(context, AppAccent.AMBER)
        setMode(AppTheme.DARK)
        auth.signOut()
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertAccent(activity, AppAccent.AMBER, AppTheme.DARK)
                assertEquals(primary(activity, AppAccent.AMBER), activity.findViewById<MaterialButton>(R.id.loginButton).backgroundTintList?.defaultColor)
            }
            capture("accent-login-dark")
        }
        ActivityScenario.launch(RegisterActivity::class.java).use { scenario ->
            scenario.onActivity { assertAccent(it, AppAccent.AMBER, AppTheme.DARK) }
            capture("accent-register-dark")
        }
        ActivityScenario.launch(AppearanceActivity::class.java).use { scenario ->
            onView(withId(R.id.restoreAccentButton)).perform(scrollTo(), click())
            waitUntil { scenario.onActivity { assertAccent(it, AppAccent.BLUE, AppTheme.DARK) } }
            assertEquals(AppTheme.DARK, ThemePreferences.read(context))
            capture("accent-restored")
        }
    }

    @Test
    fun fUnknownPreferenceFallsBackAndLargeFontControlsRemainReachable() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("accent", "UNKNOWN_FUTURE_COLOR").commit()
        try {
            shell("settings put system font_scale 1.3")
            ActivityScenario.launch(AppearanceActivity::class.java).use { scenario ->
                waitUntil { scenario.onActivity {
                    assertEquals(1.3f, it.resources.configuration.fontScale, 0.01f)
                    assertAccent(it, AppAccent.BLUE, AppTheme.LIGHT)
                } }
                choose(AppAccent.TEAL)
                scenario.onActivity { assertAccent(it, AppAccent.TEAL, AppTheme.LIGHT) }
                capture("accent-large-font")
                onView(withId(R.id.restoreAccentButton)).perform(scrollTo(), click())
                waitUntil { scenario.onActivity { assertAccent(it, AppAccent.BLUE, AppTheme.LIGHT) } }
            }
        } finally { shell("settings put system font_scale 1.0") }
    }

    private fun choose(accent: AppAccent) {
        onView(withId(buttonId(accent))).perform(scrollTo(), click())
        waitUntil { assertEquals(accent, ThemePreferences.readAccent(context)) }
    }
    private fun buttonId(accent: AppAccent) = when (accent) {
        AppAccent.BLUE -> R.id.accentBlue; AppAccent.GREEN -> R.id.accentGreen
        AppAccent.VIOLET -> R.id.accentViolet; AppAccent.ROSE -> R.id.accentRose
        AppAccent.AMBER -> R.id.accentAmber; AppAccent.TEAL -> R.id.accentTeal
    }
    private fun primary(activity: android.app.Activity, accent: AppAccent) = contextCompatColor(activity, when (accent) {
        AppAccent.BLUE -> R.color.ac; AppAccent.GREEN -> R.color.ac_green; AppAccent.VIOLET -> R.color.ac_violet
        AppAccent.ROSE -> R.color.ac_rose; AppAccent.AMBER -> R.color.ac_amber; AppAccent.TEAL -> R.color.ac_teal
    })
    private fun onPrimary(activity: android.app.Activity, accent: AppAccent) = contextCompatColor(activity, when (accent) {
        AppAccent.BLUE -> R.color.on; AppAccent.GREEN -> R.color.on_green; AppAccent.VIOLET -> R.color.on_violet
        AppAccent.ROSE -> R.color.on_rose; AppAccent.AMBER -> R.color.on_amber; AppAccent.TEAL -> R.color.on_teal
    })
    private fun contextCompatColor(context: Context, id: Int) = androidx.core.content.ContextCompat.getColor(context, id)
    private fun assertAccent(activity: android.app.Activity, accent: AppAccent, mode: AppTheme) {
        val bars = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        assertEquals(mode == AppTheme.LIGHT, bars.isAppearanceLightStatusBars)
        assertEquals(mode == AppTheme.LIGHT, bars.isAppearanceLightNavigationBars)
        assertEquals(accent, ThemePreferences.readAccent(context))
        assertEquals(mode, ThemePreferences.read(context))
        assertEquals(primary(activity, accent), MaterialColors.getColor(activity, androidx.appcompat.R.attr.colorPrimary, 0))
        assertEquals(onPrimary(activity, accent), MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnPrimary, 0))
    }
    private fun backgroundColor(view: View): Int {
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val background = view.background
        val bounds = Rect(background.bounds)
        background.setBounds(0, 0, 64, 64)
        background.draw(Canvas(image))
        background.bounds = bounds
        return image.getPixel(32, 32)
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
            catch (error: androidx.test.espresso.NoMatchingViewException) { if (System.nanoTime() >= end) throw error }
            Thread.sleep(100)
        }
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(500)
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

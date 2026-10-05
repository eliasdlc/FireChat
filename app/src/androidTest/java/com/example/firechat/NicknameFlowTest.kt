package com.example.firechat

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
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
import com.example.firechat.data.repository.NicknameRepository
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.conversations.ConversationsActivity
import com.example.firechat.ui.profile.UserProfileActivity
import com.example.firechat.ui.profile.UserProfileViewModel
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.ThemePreferences
import com.example.firechat.ui.users.UserAdapter
import com.example.firechat.ui.users.UsersActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Native nickname flows with synthetic accounts and local Firebase emulators only. */
@RunWith(AndroidJUnit4::class)
class NicknameFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var ownerId: String
    private lateinit var ownerEmail: String
    private lateinit var otherId: String
    private lateinit var otherEmail: String
    private lateinit var nicknames: NicknameRepository

    @Before
    fun seedAccounts() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
        )
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        nicknames = NicknameRepository(context)
        if (!configured) {
            auth.useEmulator("10.0.2.2", 19099)
            db.useEmulator("10.0.2.2", 18080)
            configured = true
        }
        auth.signOut()
        val suffix = UUID.randomUUID().toString().take(8)
        otherEmail = "contacto-$suffix@firechat.test"
        ownerEmail = "lector-$suffix@firechat.test"
        runBlocking {
            otherId = checkNotNull(auth.createUserWithEmailAndPassword(otherEmail, PASSWORD).await().user).uid
            UserRepository().createProfile(User(otherId, PUBLIC_NAME, otherEmail, "synthetic-token"))
            auth.signOut()
            ownerId = checkNotNull(auth.createUserWithEmailAndPassword(ownerEmail, PASSWORD).await().user).uid
            UserRepository().createProfile(User(ownerId, OWNER_NAME, ownerEmail))
        }
        setTheme(AppTheme.LIGHT)
    }

    @After
    fun signOut() { if (::auth.isInitialized) auth.signOut() }

    @Test
    fun aNicknameAppearsEverywhereAndNeverEntersPublicMessages() {
        val otherBefore = publicProfile(otherId)
        val ownBefore = publicProfile(ownerId)
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, otherId, PUBLIC_NAME)).use {
            onView(withId(R.id.messageInput)).perform(replaceText("Mensaje público"), closeSoftKeyboard())
            onView(withId(R.id.chatProfileHeader)).perform(click())
            awaitName(PUBLIC_NAME)
            saveViaUi("  Mi   Cami  ")
            awaitName("Mi Cami")
            onView(withId(R.id.userProfilePublicName)).check(matches(withText(context.getString(R.string.nickname_public_name, PUBLIC_NAME))))
            capture("nickname-profile-saved")
            onView(withContentDescription(R.string.navigate_back)).perform(click())
            waitUntil { onView(withId(R.id.chatName)).check(matches(withText("Mi Cami"))) }
            onView(withId(R.id.messageInput)).check(matches(withText("Mensaje público")))
            capture("nickname-chat-header")
            onView(withId(R.id.sendButton)).perform(click())
            val chatId = ChatRepository.chatIdFor(ownerId, otherId)
            waitUntil {
                val summary = runBlocking { db.collection("chats").document(chatId).get(Source.SERVER).await() }
                assertEquals("Mensaje público", summary.getString("lastMessage"))
            }
            val summary = runBlocking { db.collection("chats").document(chatId).get(Source.SERVER).await() }
            assertEquals(PUBLIC_NAME, (summary.get("participantNames") as Map<*, *>)[otherId])
            val messages = runBlocking { db.collection("chats").document(chatId).collection("messages").get(Source.SERVER).await() }
            assertEquals(OWNER_NAME, messages.documents.single().getString("senderName"))

            ActivityScenario.launch(ConversationsActivity::class.java).use {
                waitUntil { onView(allOf(withId(R.id.name), withText("Mi Cami"))).check(matches(withText("Mi Cami"))) }
                capture("nickname-conversation-list")
            }
            ActivityScenario.launch(UsersActivity::class.java).use { scenario ->
                waitUntil {
                    scenario.onActivity { activity ->
                        val list = activity.findViewById<RecyclerView>(R.id.userList)
                        val index = (list.adapter as UserAdapter).currentList.indexOfFirst { it.uid == otherId }
                        org.junit.Assert.assertTrue(index >= 0)
                        list.scrollToPosition(index)
                    }
                    onView(allOf(withId(R.id.name), withText("Mi Cami"))).check(matches(withText("Mi Cami")))
                }
                capture("nickname-contact-list")
            }
        }
        assertEquals("Mi Cami", NicknameRepository(context).read(ownerId, otherId))
        assertEquals(otherBefore, publicProfile(otherId))
        assertEquals(ownBefore, publicProfile(ownerId))
    }

    @Test
    fun bAccountsOnSameDeviceHaveIndependentNicknames() {
        runBlocking { nicknames.save(ownerId, otherId, "Mi Cami") }
        val secondEmail = "segundo-${UUID.randomUUID().toString().take(8)}@firechat.test"
        val secondId = runBlocking {
            val id = checkNotNull(auth.createUserWithEmailAndPassword(secondEmail, PASSWORD).await().user).uid
            UserRepository().createProfile(User(id, "Segundo Lector", secondEmail))
            id
        }
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use {
            awaitName(PUBLIC_NAME)
            onView(withId(R.id.nicknameInput)).check(matches(withText("")))
            saveViaUi("Compañera")
            awaitName("Compañera")
            capture("nickname-second-account")
        }
        assertEquals("Compañera", nicknames.read(secondId, otherId))
        assertEquals("Mi Cami", nicknames.read(ownerId, otherId))
        runBlocking { auth.signInWithEmailAndPassword(ownerEmail, PASSWORD).await() }
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use {
            awaitName("Mi Cami")
            onView(withId(R.id.nicknameInput)).check(matches(withText("Mi Cami")))
        }
        assertEquals(PUBLIC_NAME, publicProfile(otherId)?.get("name"))
    }

    @Test
    fun cRemoveNicknameRestoresPublicNameAndLeavesOtherContactsUntouched() {
        runBlocking {
            nicknames.save(ownerId, otherId, "Mi Cami")
            nicknames.save(ownerId, "different-contact", "Amigo")
        }
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use {
            awaitName("Mi Cami")
            onView(withId(R.id.removeNicknameButton)).perform(scrollTo(), click())
            waitUntil { onView(withId(R.id.nicknameStatus)).check(matches(withText(R.string.nickname_removed))) }
            awaitName(PUBLIC_NAME)
            onView(withId(R.id.nicknameInput)).check(matches(withText("")))
            capture("nickname-removed")
        }
        assertEquals("", nicknames.read(ownerId, otherId))
        assertEquals("Amigo", nicknames.read(ownerId, "different-contact"))
    }

    @Test
    fun dLongNicknameIsRejectedAndDraftSurvivesRecreation() {
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use { scenario ->
            awaitName(PUBLIC_NAME)
            val draft = "a".repeat(41)
            onView(withId(R.id.nicknameInput)).perform(scrollTo(), replaceText(draft), closeSoftKeyboard())
            scenario.recreate()
            awaitName(PUBLIC_NAME)
            onView(withId(R.id.nicknameInput)).check(matches(withText(draft)))
            onView(withId(R.id.saveNicknameButton)).perform(scrollTo(), click())
            scenario.onActivity {
                assertEquals(context.getString(R.string.error_nickname_length), it.findViewById<TextInputLayout>(R.id.nicknameLayout).error)
            }
            assertEquals("", nicknames.read(ownerId, otherId))
            capture("nickname-validation-error")
        }
    }

    @Test
    fun eDarkModeAndBlankNicknameRestorePublicName() {
        runBlocking { nicknames.save(ownerId, otherId, "Apodo personal de cuarenta caracteres") }
        setTheme(AppTheme.DARK)
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use {
            awaitName("Apodo personal de cuarenta caracteres")
            capture("nickname-dark")
            saveViaUi("   ")
            awaitName(PUBLIC_NAME)
            assertEquals("", nicknames.read(ownerId, otherId))
        }
    }

    @Test
    fun fLostSessionCannotSaveNickname() {
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use { scenario ->
            awaitName(PUBLIC_NAME)
            onView(withId(R.id.nicknameInput)).perform(scrollTo(), replaceText("No guardar"), closeSoftKeyboard())
            auth.signOut()
            onView(withId(R.id.saveNicknameButton)).perform(scrollTo(), click())
            waitUntil { onView(withId(R.id.userProfileError)).check(matches(withText(R.string.error_profile_session))) }
            scenario.onActivity { assertFalse(it.findViewById<android.view.View>(R.id.nicknameEditor).isVisible) }
            assertEquals("", nicknames.read(ownerId, otherId))
            capture("nickname-session-error")
        }
    }

    @Test
    fun gRestoredDraftCannotCrossAccounts() {
        val handle = SavedStateHandle(mapOf(UserProfileActivity.EXTRA_USER_ID to otherId))
        val store = ViewModelStore()
        lateinit var first: UserProfileViewModel
        instrumentation.runOnMainSync {
            first = UserProfileViewModel(context.applicationContext as Application, handle)
            store.put("profile", first)
            first.loadProfile()
        }
        waitUntil { instrumentation.runOnMainSync { org.junit.Assert.assertTrue(first.uiState.value?.isLoaded == true) } }
        instrumentation.runOnMainSync {
            first.nicknameChanged("Borrador privado")
            store.clear()
        }
        val secondId = runBlocking {
            checkNotNull(auth.createUserWithEmailAndPassword(
                "restaurado-${UUID.randomUUID().toString().take(8)}@firechat.test", PASSWORD
            ).await().user).uid
        }
        val restoredStore = ViewModelStore()
        try {
            instrumentation.runOnMainSync {
                val restored = UserProfileViewModel(context.applicationContext as Application, handle)
                restoredStore.put("profile", restored)
                restored.loadProfile()
                val state = checkNotNull(restored.uiState.value)
                assertEquals(R.string.error_profile_session, state.error)
                assertEquals("", state.nickname)
                assertEquals("", state.name)
                assertFalse(state.canEditNickname)
            }
            assertEquals("", nicknames.read(secondId, otherId))
        } finally {
            instrumentation.runOnMainSync { restoredStore.clear() }
        }
    }

    private fun saveViaUi(value: String) {
        onView(withId(R.id.nicknameInput)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
        onView(withId(R.id.saveNicknameButton)).perform(scrollTo(), click())
        waitUntil {
            onView(withId(R.id.nicknameStatus)).check(matches(withText(
                if (value.isBlank()) R.string.nickname_removed else R.string.nickname_saved
            )))
        }
    }

    private fun publicProfile(id: String) = runBlocking {
        db.collection("users").document(id).get(Source.SERVER).await().data
    }

    private fun setTheme(theme: AppTheme) {
        ThemePreferences.save(context, theme)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(theme.nightMode) }
    }

    private fun awaitName(name: String) = waitUntil {
        onView(withId(R.id.userProfileName)).check(matches(withText(name)))
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
        val file = File(context.getExternalFilesDir("profile-proof"), "$name-$viewport.png")
        file.outputStream().use {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val PASSWORD = "SyntheticProfile123!"
        const val PUBLIC_NAME = "Camila Prueba"
        const val OWNER_NAME = "Lector Prueba"
        var configured = false
    }
}

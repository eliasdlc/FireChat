package com.example.firechat

import android.graphics.Bitmap
import android.os.Build
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.ui.chat.ChatActivity
import com.example.firechat.ui.profile.UserProfileActivity
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.ThemePreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID

/** Runs only on a private Android emulator using synthetic Firebase emulator accounts. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UserProfileFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewerId: String
    private lateinit var viewerEmail: String
    private lateinit var otherId: String
    private lateinit var otherEmail: String

    @Before
    fun seedAccounts() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
        )
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        if (!configured) {
            auth.useEmulator("10.0.2.2", 19099)
            db.useEmulator("10.0.2.2", 18080)
            configured = true
        }
        auth.signOut()
        val suffix = UUID.randomUUID().toString().take(8)
        otherEmail = "contacto-$suffix@firechat.test"
        viewerEmail = "lector-$suffix@firechat.test"
        runBlocking {
            otherId = checkNotNull(auth.createUserWithEmailAndPassword(otherEmail, PASSWORD).await().user).uid
            UserRepository().createProfile(User(otherId, "Camila Prueba", otherEmail, "private-fixture-token"))
            auth.signOut()
            viewerId = checkNotNull(auth.createUserWithEmailAndPassword(viewerEmail, PASSWORD).await().user).uid
            UserRepository().createProfile(User(viewerId, "Lector Prueba", viewerEmail))
        }
        setTheme(AppTheme.LIGHT)
    }

    @After
    fun signOut() {
        if (::auth.isInitialized) auth.signOut()
    }

    @Test
    fun aChatHeaderOpensCanonicalProfileAndBackPreservesDraft() {
        val before = profileData(otherId)
        val ownBefore = profileData(viewerId)
        ActivityScenario.launch<ChatActivity>(ChatActivity.newIntent(context, otherId, "Nombre antiguo")).use {
            onView(withId(R.id.messageInput)).perform(replaceText("Mensaje sin enviar"), closeSoftKeyboard())
            capture("chat-profile-access")
            onView(withId(R.id.chatProfileHeader)).perform(click())
            awaitName("Camila Prueba")
            onView(withId(R.id.userProfileEmail)).check(matches(withText(otherEmail)))
            onView(org.hamcrest.Matchers.allOf(withText("C"), androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA(withId(R.id.userProfileAvatar)))).check(matches(isDisplayed()))
            capture("user-profile-light")
            onView(withContentDescription(R.string.navigate_back)).perform(click())
            onView(withId(R.id.messageInput)).check(matches(withText("Mensaje sin enviar")))
            assertEquals(viewerId, auth.currentUser?.uid)
        }
        assertEquals(before, profileData(otherId))
        assertEquals(ownBefore, profileData(viewerId))
    }

    @Test
    fun bDarkProfileAndRecreationRetainRecipient() {
        val longName = "Camila Prueba de un nombre largo para comprobar el perfil"
        updateOtherName(longName)
        setTheme(AppTheme.DARK)
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use { scenario ->
            awaitName(longName)
            scenario.onActivity {
                assertNull(it.findViewById<android.view.View>(R.id.editProfileRow))
                assertNull(it.findViewById<android.view.View>(R.id.appearanceRow))
                assertNull(it.findViewById<android.view.View>(R.id.logoutProfileRow))
                val bars = WindowInsetsControllerCompat(it.window, it.window.decorView)
                assertFalse(bars.isAppearanceLightStatusBars)
                assertFalse(bars.isAppearanceLightNavigationBars)
            }
            capture("user-profile-dark-long-name")
            scenario.recreate()
            awaitName(longName)
            onView(withId(R.id.userProfileEmail)).check(matches(withText(otherEmail)))
        }
    }

    @Test
    fun cMissingProfileCanRetryAfterItIsCreated() {
        // Create an Auth account without a Firestore profile, then recover it through retry.
        val missingEmail = "pendiente-${UUID.randomUUID().toString().take(8)}@firechat.test"
        val missingId = runBlocking {
            val id = checkNotNull(auth.createUserWithEmailAndPassword(missingEmail, PASSWORD).await().user).uid
            auth.signInWithEmailAndPassword(viewerEmail, PASSWORD).await()
            id
        }
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, missingId)).use {
            awaitError(R.string.error_user_profile_missing)
            onView(withId(R.id.retryUserProfileButton)).check(matches(isDisplayed()))
            runBlocking {
                auth.signInWithEmailAndPassword(missingEmail, PASSWORD).await()
                UserRepository().createProfile(User(missingId, "Camila Recuperada", missingEmail))
                auth.signInWithEmailAndPassword(viewerEmail, PASSWORD).await()
            }
            onView(withId(R.id.retryUserProfileButton)).perform(click())
            awaitName("Camila Recuperada")
            capture("user-profile-retry-recovered")
        }
    }

    @Test
    fun dMissingOrInvalidRecipientShowsError() {
        for (id in listOf("", "invalid/path")) {
            ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, id)).use {
                awaitError(R.string.error_user_profile_missing)
                capture("user-profile-invalid-${if (id.isEmpty()) "empty" else "path"}")
            }
        }
    }

    @Test
    fun eNoSessionDoesNotExposeContactData() {
        auth.signOut()
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use { scenario ->
            awaitError(R.string.error_profile_session)
            scenario.onActivity {
                assertEquals("", it.findViewById<TextView>(R.id.userProfileName).text.toString())
                assertFalse(it.findViewById<android.view.View>(R.id.userProfileContent).isShown)
            }
            capture("user-profile-session-error")
        }
    }

    @Test
    fun zBackendFailureHasRetryAndNoStaleDetails() {
        runBlocking { db.terminate().await() }
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, otherId)).use { scenario ->
            awaitError(R.string.error_loading_user_profile)
            onView(withId(R.id.retryUserProfileButton)).check(matches(isDisplayed()))
            scenario.onActivity {
                assertFalse(it.findViewById<android.view.View>(R.id.userProfileContent).isShown)
            }
            capture("user-profile-load-error")
        }
    }

    private fun profileData(id: String) = runBlocking {
        db.collection("users").document(id).get(Source.SERVER).await().data
    }

    private fun updateOtherName(name: String) = runBlocking {
        auth.signInWithEmailAndPassword(otherEmail, PASSWORD).await()
        UserRepository().updateName(otherId, name)
        auth.signInWithEmailAndPassword(viewerEmail, PASSWORD).await()
    }

    private fun setTheme(theme: AppTheme) {
        ThemePreferences.save(context, theme)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(theme.nightMode) }
    }

    private fun awaitName(name: String) = waitUntil {
        onView(withId(R.id.userProfileName)).check(matches(withText(name)))
        onView(withId(R.id.userProfileContent)).check(matches(isDisplayed()))
    }

    private fun awaitError(error: Int) = waitUntil {
        onView(withId(R.id.userProfileError)).check(matches(withText(error)))
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
        var configured = false
    }
}

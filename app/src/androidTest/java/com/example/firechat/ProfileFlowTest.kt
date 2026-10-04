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
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.data.model.User
import com.example.firechat.data.repository.UserRepository
import com.example.firechat.ui.conversations.ConversationsActivity
import com.example.firechat.ui.profile.ProfileActivity
import com.example.firechat.ui.profile.EditProfileActivity
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.ThemePreferences
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID

/** Opt-in tests using local Firebase emulators and synthetic accounts only. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProfileFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var uid: String
    private lateinit var email: String

    @Before
    fun createEmulatorAccount() {
        assumeTrue(
            "Requires a private Android emulator and profileEmulatorTests=true",
            Build.HARDWARE in setOf("ranchu", "goldfish") &&
                InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        if (!configured) {
            auth.useEmulator("10.0.2.2", 19099)
            db.useEmulator("10.0.2.2", 18080)
            configured = true
        }
        auth.signOut()
        email = "perfil-${UUID.randomUUID().toString().take(8)}@firechat.test"
        uid = runBlocking {
            checkNotNull(auth.createUserWithEmailAndPassword(email, PASSWORD).await().user).uid
        }
        ThemePreferences.save(context, AppTheme.LIGHT)
        instrumentation.runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppTheme.LIGHT.nightMode)
        }
    }

    @After
    fun signOut() {
        if (::auth.isInitialized) auth.signOut()
    }

    @Test
    fun aLoadsProfileInLightAndDark() {
        seedProfile()
        for (theme in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
            ThemePreferences.save(context, theme)
            instrumentation.runOnMainSync {
                AppCompatDelegate.setDefaultNightMode(theme.nightMode)
            }
            ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
                awaitLoaded(scenario)
                onView(withId(R.id.profileEmail)).check(matches(withText(email)))
                scenario.onActivity {
                    assertFalse(it.findViewById<MaterialButton>(R.id.saveProfileButton).isEnabled)
                    val bars = WindowInsetsControllerCompat(it.window, it.window.decorView)
                    assertEquals(theme != AppTheme.DARK, bars.isAppearanceLightStatusBars)
                    assertEquals(theme != AppTheme.DARK, bars.isAppearanceLightNavigationBars)
                }
                capture("profile-${theme.name.lowercase()}")
            }
        }
    }

    @Test
    fun bSavesTrimmedNamePreservesFieldsAndReopens() {
        seedProfile()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            onView(withId(R.id.profileNameInput)).perform(replaceText("  Nuevo Perfil  "), closeSoftKeyboard())
            onView(withId(R.id.saveProfileButton)).perform(scrollTo(), click())
            awaitState(scenario) {
                it.findViewById<TextView>(R.id.profileStatus).text == context.getString(R.string.profile_saved)
            }
            val stored = runBlocking { db.collection("users").document(uid).get(Source.SERVER).await() }
            assertEquals("Nuevo Perfil", stored.getString("name"))
            assertEquals(uid, stored.getString("uid"))
            assertEquals(email, stored.getString("email"))
            assertEquals("synthetic-token", stored.getString("fcmToken"))
            assertEquals("preserve-me", stored.getString("fixtureExtra"))
            capture("profile-saved")
        }
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario, "Nuevo Perfil")
            capture("profile-reopened")
        }
    }

    @Test
    fun cRejectsBlankNameWithoutWriting() {
        seedProfile()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            onView(withId(R.id.profileNameInput)).perform(replaceText("   "), closeSoftKeyboard())
            onView(withId(R.id.saveProfileButton)).perform(scrollTo(), click())
            awaitState(scenario) {
                it.findViewById<TextInputLayout>(R.id.profileNameLayout).error ==
                    context.getString(R.string.error_name_required)
            }
            assertEquals("Ana Prueba", serverName())
            capture("profile-validation")
        }
    }

    @Test
    fun dRecreationPreservesUnsavedDraft() {
        seedProfile()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            onView(withId(R.id.profileNameInput)).perform(replaceText("Borrador sin guardar"), closeSoftKeyboard())
            scenario.recreate()
            awaitLoaded(scenario, "Borrador sin guardar")
            assertEquals("Ana Prueba", serverName())
            capture("profile-draft")
        }
    }

    @Test
    fun eMissingProfileCanRetry() {
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitError(scenario, R.string.error_profile_missing)
            capture("profile-missing")
            seedProfile()
            onView(withId(R.id.retryProfileButton)).perform(scrollTo(), click())
            awaitLoaded(scenario)
        }
    }

    @Test
    fun fMissingSessionDisablesEditing() {
        auth.signOut()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitError(scenario, R.string.error_profile_session)
            scenario.onActivity {
                assertFalse(it.findViewById<TextView>(R.id.profileNameInput).isEnabled)
                assertFalse(it.findViewById<MaterialButton>(R.id.saveProfileButton).isEnabled)
            }
            capture("profile-no-session")
        }
    }

    @Test
    fun gExpiredSessionKeepsDraftWithoutWriting() {
        seedProfile()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            auth.signOut()
            onView(withId(R.id.profileNameInput)).perform(replaceText("Cambio pendiente"), closeSoftKeyboard())
            onView(withId(R.id.saveProfileButton)).perform(scrollTo(), click())
            awaitError(scenario, R.string.error_profile_session)
            onView(withId(R.id.profileNameInput)).check(matches(withText("Cambio pendiente")))
            runBlocking { auth.signInWithEmailAndPassword(email, PASSWORD).await() }
            assertEquals("Ana Prueba", serverName())
        }
    }

    @Test
    fun hMenuOpensProfileAndBackReturnsToConversations() {
        seedProfile()
        ActivityScenario.launch(ConversationsActivity::class.java).use {
            capture("conversations-profile-access")
            onView(withId(R.id.action_profile)).perform(click())
            waitUntil {
                try {
                    onView(withId(R.id.profileDisplayName)).check(matches(withText("Ana Prueba")))
                    true
                } catch (_: AssertionError) {
                    false
                } catch (_: androidx.test.espresso.NoMatchingViewException) {
                    false
                }
            }
            capture("profile-home")
            onView(withId(R.id.editProfileRow)).perform(scrollTo(), click())
            waitUntil {
                try {
                    onView(withId(R.id.profileNameInput)).check(matches(withText("Ana Prueba")))
                    true
                } catch (_: AssertionError) { false }
            }
            onView(withId(R.id.profileNameInput)).perform(replaceText("Nombre actualizado"), closeSoftKeyboard())
            onView(withId(R.id.saveProfileButton)).perform(scrollTo(), click())
            waitUntil {
                try {
                    onView(withId(R.id.profileStatus)).check(matches(withText(R.string.profile_saved)))
                    true
                } catch (_: AssertionError) { false }
            }
            onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(R.string.navigate_back))
                .perform(click())
            waitUntil {
                try {
                    onView(withId(R.id.profileDisplayName)).check(matches(withText("Nombre actualizado")))
                    true
                } catch (_: AssertionError) { false }
            }
            assertEquals("Nombre actualizado", serverName())
            capture("profile-home-updated")
            onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(R.string.navigate_back))
                .perform(click())
            onView(withId(R.id.newChatButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun iAppearanceChangesFromProfileAndPersists() {
        seedProfile()
        ActivityScenario.launch(ProfileActivity::class.java).use { scenario ->
            for (theme in listOf(AppTheme.DARK, AppTheme.LIGHT, AppTheme.SYSTEM)) {
                onView(withId(R.id.appearanceRow)).perform(scrollTo(), click())
                val label = when (theme) {
                    AppTheme.DARK -> R.string.theme_dark
                    AppTheme.LIGHT -> R.string.theme_light
                    AppTheme.SYSTEM -> R.string.theme_system
                }
                onView(withText(label)).perform(click())
                assertEquals(theme, ThemePreferences.read(context))
                scenario.recreate()
                waitUntil {
                    var ready = false
                    scenario.onActivity {
                        ready = it.findViewById<TextView>(R.id.profileDisplayName).text == "Ana Prueba"
                    }
                    ready
                }
                capture("profile-home-${theme.name.lowercase()}")
            }
        }
    }

    @Test
    fun jLogoutCanBeCancelledThenReturnsToLogin() {
        seedProfile()
        ActivityScenario.launch(ProfileActivity::class.java).use {
            onView(withId(R.id.logoutProfileRow)).perform(scrollTo(), click())
            onView(withId(android.R.id.button2)).perform(click())
            assertEquals(uid, auth.currentUser?.uid)
            onView(withId(R.id.logoutProfileRow)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).perform(click())
            assertEquals(null, auth.currentUser)
            onView(withId(R.id.loginButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun zBackendFailureKeepsDraftAndNeverShowsSuccess() {
        seedProfile()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            runBlocking { db.terminate().await() }
            onView(withId(R.id.profileNameInput)).perform(replaceText("No confirmado"), closeSoftKeyboard())
            onView(withId(R.id.saveProfileButton)).perform(scrollTo(), click())
            awaitError(scenario, R.string.error_saving_profile)
            onView(withId(R.id.profileNameInput)).check(matches(withText("No confirmado")))
            capture("profile-save-error")
        }
    }

    private fun seedProfile() = runBlocking {
        UserRepository().createProfile(User(uid, "Ana Prueba", email, "synthetic-token"))
        db.collection("users").document(uid).update("fixtureExtra", "preserve-me").await()
    }

    private fun serverName(): String? = runBlocking {
        db.collection("users").document(uid).get(Source.SERVER).await().getString("name")
    }

    private fun awaitLoaded(scenario: ActivityScenario<EditProfileActivity>, name: String = "Ana Prueba") =
        awaitState(scenario) {
            val input = it.findViewById<TextView>(R.id.profileNameInput)
            input.isEnabled && input.text.toString() == name
        }

    private fun awaitError(scenario: ActivityScenario<EditProfileActivity>, message: Int) =
        awaitState(scenario) {
            it.findViewById<TextView>(R.id.profileError).text == context.getString(message)
        }

    private fun awaitState(scenario: ActivityScenario<EditProfileActivity>, predicate: (EditProfileActivity) -> Boolean) {
        waitUntil {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            ready
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!predicate()) {
            check(System.nanoTime() < deadline) { "Profile state did not arrive within 20 seconds" }
            Thread.sleep(100)
        }
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        // UiAutomation can capture the launch transition before the compositor settles.
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

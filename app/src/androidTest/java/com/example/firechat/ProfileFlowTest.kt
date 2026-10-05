package com.example.firechat

import android.content.ContentValues
import android.graphics.Color
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import com.example.firechat.ui.profile.UserProfileActivity
import androidx.lifecycle.ViewModelProvider
import com.example.firechat.ui.profile.ProfileViewModel
import com.example.firechat.ui.common.AvatarView
import com.example.firechat.data.repository.ProfilePhotoRepository
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
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
    private var fixtureImage: Uri? = null

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
            FirebaseStorage.getInstance().useEmulator("10.0.2.2", 19199)
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
        fixtureImage?.let { context.contentResolver.delete(it, null, null) }
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
                onView(withId(R.id.appearanceThemeRow)).perform(scrollTo(), click())
                val label = when (theme) {
                    AppTheme.DARK -> R.string.theme_dark
                    AppTheme.LIGHT -> R.string.theme_light
                    AppTheme.SYSTEM -> R.string.theme_system
                }
                onView(withText(label)).perform(click())
                onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(R.string.navigate_back))
                    .perform(click())
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
    fun kPhotoUploadPreservesFieldsAndDisplaysAfterReopening() {
        seedProfile()
        val uri = createImage()
        var firstUrl: String? = null
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            onView(withId(R.id.profileNameInput)).perform(replaceText("Nombre sin guardar"), closeSoftKeyboard())
            scenario.onActivity {
                ViewModelProvider(it)[ProfileViewModel::class.java].uploadPhoto(it.contentResolver, uri)
            }
            awaitState(scenario) {
                it.findViewById<TextView>(R.id.profileStatus).text == context.getString(R.string.profile_photo_saved)
            }
            onView(withId(R.id.profileNameInput)).check(matches(withText("Nombre sin guardar")))
            val document = runBlocking { db.collection("users").document(uid).get(Source.SERVER).await() }
            assertEquals("Ana Prueba", document.getString("name"))
            assertEquals(email, document.getString("email"))
            assertEquals("synthetic-token", document.getString("fcmToken"))
            assertEquals("preserve-me", document.getString("fixtureExtra"))
            val url = checkNotNull(document.getString("photoUrl"))
            firstUrl = url
            val reference = FirebaseStorage.getInstance().getReferenceFromUrl(url)
            val bytes = runBlocking { reference.getBytes(524288).await() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(512, bitmap.width)
            assertEquals(512, bitmap.height)
            assertEquals("image/jpeg", runBlocking { reference.metadata.await().contentType })
            awaitState(scenario) { avatarHasPhoto(it.findViewById(R.id.profileAvatar)) }
            capture("profile-photo-editor")
        }
        val oldPhoto = FirebaseStorage.getInstance().getReferenceFromUrl(checkNotNull(firstUrl))
        val replacement = runBlocking { ProfilePhotoRepository().update(uid, oldPhoto.getBytes(524288).await()) }
        org.junit.Assert.assertNotEquals(firstUrl, replacement)
        waitUntil { runBlocking { runCatching { oldPhoto.metadata.await() }.isFailure } }
        ActivityScenario.launch(ProfileActivity::class.java).use { scenario ->
            waitUntil {
                var loaded = false
                scenario.onActivity { loaded = avatarHasPhoto(it.findViewById(R.id.profileAvatar)) }
                loaded
            }
            capture("profile-photo-home")
        }
    }

    @Test
    fun lPhotoPickerCanSelectImage() {
        seedProfile()
        createImage()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            onView(withId(R.id.changePhotoButton)).perform(click())
            waitUntil {
                val root = instrumentation.uiAutomation.rootInActiveWindow ?: return@waitUntil false
                val nodes = allNodes(root)
                val photo = nodes.firstOrNull {
                    it.viewIdResourceName?.endsWith(":id/icon_thumbnail") == true ||
                        it.contentDescription?.toString()?.startsWith("Photo taken") == true
                }
                if (photo == null) {
                    android.util.Log.d("ProfilePicker", nodes.joinToString(" | ") {
                        "${it.viewIdResourceName}: ${it.text}: ${it.contentDescription}"
                    })
                    false
                } else {
                    val bounds = Rect()
                    photo.getBoundsInScreen(bounds)
                    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                        "input tap ${bounds.centerX()} ${bounds.centerY()}"
                    )).use { it.readBytes() }
                    true
                }
            }
            // Some system picker versions ask to confirm the selected photo.
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.let { allNodes(it).firstOrNull { node -> node.text?.toString() == "Add" }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            awaitState(scenario) {
                it.findViewById<TextView>(R.id.profileStatus).text == context.getString(R.string.profile_photo_saved)
            }
            awaitState(scenario) { avatarHasPhoto(it.findViewById(R.id.profileAvatar)) }
            capture("profile-photo-picked")
        }
    }

    @Test
    fun mCancelledPickerNeverChangesProfile() {
        seedProfile()
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            onView(withId(R.id.changePhotoButton)).perform(click())
            waitUntil { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("providers.media") == true }
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            onView(withId(R.id.changePhotoButton)).check(matches(isDisplayed()))
            assertEquals(null, runBlocking { db.collection("users").document(uid).get(Source.SERVER).await().getString("photoUrl") })
        }
    }

    @Test
    fun nInvalidImageKeepsExistingPhotoAndNameDraft() {
        seedProfile()
        val uri = createImage()
        val bytes = com.example.firechat.ui.profile.ProfileImage.jpeg(context.contentResolver, uri)
        val previous = runBlocking { ProfilePhotoRepository().update(uid, bytes) }
        ActivityScenario.launch(EditProfileActivity::class.java).use { scenario ->
            awaitLoaded(scenario)
            onView(withId(R.id.profileNameInput)).perform(replaceText("Borrador"), closeSoftKeyboard())
            scenario.onActivity {
                ViewModelProvider(it)[ProfileViewModel::class.java].uploadPhoto(it.contentResolver, Uri.parse("content://firechat.test/missing"))
            }
            awaitError(scenario, R.string.error_profile_photo)
            onView(withId(R.id.profileNameInput)).check(matches(withText("Borrador")))
            assertEquals(previous, runBlocking { db.collection("users").document(uid).get(Source.SERVER).await().getString("photoUrl") })
            capture("profile-photo-error")
        }
    }

    @Test
    fun oStorageRejectsInvalidMimeAndAnotherUsersPath() {
        val storage = FirebaseStorage.getInstance()
        for ((path, type) in listOf("avatars/$uid/not-image.jpg" to "text/plain", "avatars/other-user/photo.jpg" to "image/jpeg")) {
            val result = runBlocking { runCatching { storage.reference.child(path)
                .putBytes(byteArrayOf(1,2,3), StorageMetadata.Builder().setContentType(type).build()).await() } }
            org.junit.Assert.assertTrue(result.isFailure)
        }
    }

    @Test
    fun pAnotherSignedInUserSeesUploadedAvatarWithoutChangingNickname() {
        seedProfile()
        val original = runBlocking { ProfilePhotoRepository().update(uid,
            com.example.firechat.ui.profile.ProfileImage.jpeg(context.contentResolver, createImage())) }
        val viewer = runBlocking { checkNotNull(auth.createUserWithEmailAndPassword(
            "viewer-${UUID.randomUUID().toString().take(8)}@firechat.test", PASSWORD).await().user).uid }
        runBlocking { com.example.firechat.data.repository.NicknameRepository(context).save(viewer, uid, "Mi amiga") }
        ActivityScenario.launch<UserProfileActivity>(UserProfileActivity.newIntent(context, uid)).use { scenario ->
            waitUntil {
                var loaded = false
                scenario.onActivity { loaded = avatarHasPhoto(it.findViewById(R.id.userProfileAvatar)) }
                loaded
            }
            onView(withId(R.id.userProfileName)).check(matches(withText("Mi amiga")))
            capture("profile-photo-other-user")
            assertEquals(original, runBlocking { db.collection("users").document(uid).get(Source.SERVER).await().getString("photoUrl") })
        }
        auth.signOut()
        val denied = runBlocking { runCatching { FirebaseStorage.getInstance().getReferenceFromUrl(original).getBytes(524288).await() } }
        org.junit.Assert.assertTrue(denied.isFailure)
    }

    private fun avatarHasPhoto(view: AvatarView): Boolean = (view.getChildAt(1) as ImageView).drawable != null

    private fun createImage(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "FireChat-test-${UUID.randomUUID()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        fixtureImage = uri
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30,120,180)) }
        context.contentResolver.openOutputStream(uri).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, checkNotNull(it)) }
        bitmap.recycle()
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun allNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
        listOf(node) + (0 until node.childCount).flatMap { index -> node.getChild(index)?.let(::allNodes).orEmpty() }

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

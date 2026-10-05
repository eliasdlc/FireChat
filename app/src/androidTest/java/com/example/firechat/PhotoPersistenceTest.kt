package com.example.firechat

import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.ui.wallpaper.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Run each method in its own process without uninstalling the app. */
@RunWith(AndroidJUnit4::class)
class PhotoPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth
    @Before fun configure() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") && InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
        auth = FirebaseAuth.getInstance()
        auth.useEmulator("10.0.2.2", 19099)
    }
    @Test fun persistSelection() {
        auth.signOut()
        runBlocking { auth.createUserWithEmailAndPassword("photo-restart-${UUID.randomUUID()}@firechat.test", "SyntheticWallpaper123!").await() }
        val uid = checkNotNull(auth.currentUser).uid
        val preferences = WallpaperPreferences(context)
        for ((chat, kind, brightness) in listOf(Triple(null, "scene", 40), Triple(CHAT, "second", 70))) {
            ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chat)).use { scenario ->
                PhotoTestSupport.pick(kind)
                PhotoTestSupport.photo(scenario)
                PhotoTestSupport.brightness(brightness)
                onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
                PhotoTestSupport.waitUntil {
                    val selection = if (chat == null) preferences.default(uid) else preferences.resolve(uid, chat)
                    assertTrue(selection is WallpaperSelection.Photo)
                    assertEquals(brightness, (selection as WallpaperSelection.Photo).brightness)
                }
            }
        }
        val proof = context.getSharedPreferences("photo-proof", 0).edit()
        for ((key, selection) in listOf("default" to preferences.default(uid), "chat" to preferences.resolve(uid, CHAT))) {
            val photo = selection as WallpaperSelection.Photo
            proof.putString(key, photo.fileName).putString("${key}_sha", digest(WallpaperPhotoStore(context).savedFile(uid, photo.fileName)))
        }
        assertTrue(proof.commit())
        Log.i("PhotoPersistence", "Saved in process ${Process.myPid()}")
    }
    @Test fun freshProcessRestoresSelection() {
        val uid = checkNotNull(auth.currentUser).uid
        val preferences = WallpaperPreferences(context)
        val proof = context.getSharedPreferences("photo-proof", 0)
        assertEquals(preferences.default(uid), preferences.resolve(uid, "another-chat"))
        for ((chat, key, brightness) in listOf(Triple(null, "default", 40), Triple(CHAT, "chat", 70))) {
            val photo = (if (chat == null) preferences.default(uid) else preferences.resolve(uid, chat)) as WallpaperSelection.Photo
            assertEquals(brightness, photo.brightness)
            assertEquals(proof.getString(key, null), photo.fileName)
            assertEquals(proof.getString("${key}_sha", null), digest(WallpaperPhotoStore(context).savedFile(uid, photo.fileName)))
            ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chat)).use { scenario ->
                assertEquals(brightness, PhotoTestSupport.photo(scenario).brightness)
                onView(withId(R.id.wallpaperBrightnessSlider)).perform(scrollTo())
                File(context.getExternalFilesDir("profile-proof"), "photo-fresh-process-$key.png").outputStream().use {
                    checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        Log.i("PhotoPersistence", "Restored in process ${Process.myPid()}")
    }
    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private companion object { const val CHAT = "synthetic-photo-persistence-chat" }
}

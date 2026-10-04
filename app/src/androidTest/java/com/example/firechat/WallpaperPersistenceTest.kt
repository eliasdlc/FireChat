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
import com.example.firechat.ui.wallpaper.ChatWallpaper
import com.example.firechat.ui.wallpaper.WallpaperActivity
import com.example.firechat.ui.wallpaper.WallpaperDrawable
import com.example.firechat.ui.wallpaper.WallpaperPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Invoke each method in a separate process without uninstalling between them. */
@RunWith(AndroidJUnit4::class)
class WallpaperPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var auth: FirebaseAuth

    @Before
    fun configure() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
        auth = FirebaseAuth.getInstance()
        auth.useEmulator("10.0.2.2", 19099)
    }

    @Test
    fun persistSelection() {
        auth.signOut()
        runBlocking { auth.createUserWithEmailAndPassword("fondo-reinicio-${UUID.randomUUID().toString().take(8)}@firechat.test", "SyntheticWallpaper123!").await() }
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context)).use {
            onView(withId(R.id.wallpaperSage)).perform(scrollTo(), click())
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
        }
        ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, CHAT)).use {
            onView(withId(R.id.wallpaperLavender)).perform(scrollTo(), click())
            onView(withId(R.id.applyWallpaperButton)).perform(scrollTo(), click())
        }
        val uid = checkNotNull(auth.currentUser).uid
        assertEquals(ChatWallpaper.SAGE, WallpaperPreferences(context).default(uid))
        assertEquals(ChatWallpaper.LAVENDER, WallpaperPreferences(context).chatOverride(uid, CHAT))
        Log.i("WallpaperPersistence", "Saved default and override in process ${Process.myPid()}")
    }

    @Test
    fun freshProcessRestoresSelection() {
        val uid = checkNotNull(auth.currentUser).uid
        val preferences = WallpaperPreferences(context)
        assertEquals(ChatWallpaper.SAGE, preferences.default(uid))
        assertEquals(ChatWallpaper.LAVENDER, preferences.resolve(uid, CHAT))
        assertEquals(ChatWallpaper.SAGE, preferences.resolve(uid, "another-chat"))
        for ((chatId, expected) in listOf(null to ChatWallpaper.SAGE, CHAT to ChatWallpaper.LAVENDER)) {
            ActivityScenario.launch<WallpaperActivity>(WallpaperActivity.newIntent(context, chatId)).use { scenario ->
                scenario.onActivity {
                    assertEquals(expected, (it.findViewById<android.view.View>(R.id.wallpaperPreview).background as WallpaperDrawable).wallpaper)
                }
                instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(500)
                File(context.getExternalFilesDir("profile-proof"), "wallpaper-fresh-process-${expected.name.lowercase()}.png").outputStream().use {
                    checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        Log.i("WallpaperPersistence", "Restored default and override in process ${Process.myPid()}")
    }
    private companion object { const val CHAT = "synthetic-wallpaper-persistence-chat" }
}

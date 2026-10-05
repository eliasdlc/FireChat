package com.example.firechat

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.ui.theme.AppAccent
import com.example.firechat.ui.theme.AppTheme
import com.example.firechat.ui.theme.AppearanceActivity
import com.example.firechat.ui.theme.ThemePreferences
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run these methods in separate instrumentation processes, with an intervening force-stop. */
@RunWith(AndroidJUnit4::class)
class AccentPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun requirePrivateEmulator() {
        assumeTrue(Build.HARDWARE in setOf("ranchu", "goldfish") &&
            InstrumentationRegistry.getArguments().getString("profileEmulatorTests") == "true")
    }

    @Test
    fun persistSelection() {
        ThemePreferences.save(context, AppTheme.LIGHT)
        ThemePreferences.saveAccent(context, AppAccent.BLUE)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(AppTheme.LIGHT.nightMode) }
        ActivityScenario.launch(AppearanceActivity::class.java).use {
            onView(withId(R.id.accentGreen)).perform(scrollTo(), click())
            onView(withId(R.id.appearanceThemeRow)).perform(scrollTo(), click())
            onView(withText(R.string.theme_dark)).perform(click())
            assertEquals(AppAccent.GREEN, ThemePreferences.readAccent(context))
            assertEquals(AppTheme.DARK, ThemePreferences.read(context))
        }
        Log.i("AccentPersistence", "Saved selection in process ${Process.myPid()}")
    }

    @Test
    fun freshProcessRestoresSelection() {
        assertEquals(AppAccent.GREEN, ThemePreferences.readAccent(context))
        assertEquals(AppTheme.DARK, ThemePreferences.read(context))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        ActivityScenario.launch(AppearanceActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(Configuration.UI_MODE_NIGHT_YES, it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                assertEquals(it.getColor(R.color.ac_green), MaterialColors.getColor(it, androidx.appcompat.R.attr.colorPrimary, 0))
            }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(500)
            File(context.getExternalFilesDir("profile-proof"), "accent-fresh-process.png").outputStream().use {
                checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        Log.i("AccentPersistence", "Restored selection in process ${Process.myPid()}")
    }
}

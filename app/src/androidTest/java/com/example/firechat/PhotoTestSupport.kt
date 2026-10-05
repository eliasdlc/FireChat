package com.example.firechat

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.example.firechat.ui.wallpaper.PhotoWallpaperDrawable
import com.google.android.material.slider.Slider
import org.junit.Assert.*

internal object PhotoTestSupport {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    fun pick(kind: String?, cancelled: Boolean = false) {
        var previousName: String? = null
        onView(withId(R.id.wallpaperPreview)).check { view, error ->
            if (error != null) throw error
            previousName = (view.background as? PhotoWallpaperDrawable)?.fileName
        }
        val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") }
        val result = Instrumentation.ActivityResult(if (cancelled) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            Intent().setData(kind?.let { Uri.parse("content://com.example.firechat.test.wallpaperfixtures/$it") }))
        val monitor = instrumentation.addMonitor(filter, result, true)
        try {
            onView(withId(R.id.chooseWallpaperImageButton)).perform(scrollTo(), click())
            assertEquals(1, monitor.hits)
        } finally { instrumentation.removeMonitor(monitor) }
        if (!cancelled && kind in setOf("scene", "second", "large", "oriented")) waitUntil {
            onView(withId(R.id.wallpaperPreview)).check { view, error ->
                if (error != null) throw error
                val photo = view.background as? PhotoWallpaperDrawable
                assertNotNull(photo)
                assertNotEquals(previousName, photo?.fileName)
            }
        }
    }
    fun brightness(value: Int) {
        onView(withId(R.id.wallpaperBrightnessSlider)).perform(scrollTo(), object : ViewAction {
            override fun getConstraints() = isAssignableFrom(Slider::class.java)
            override fun getDescription() = "Touch the native brightness slider at $value percent"
            override fun perform(uiController: UiController, view: View) {
                val slider = view as Slider
                val x = slider.trackSidePadding + (slider.width - slider.trackSidePadding * 2) * value / 100f
                val time = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 100 else 0, action, x, slider.height / 2f, 0).apply {
                        source = InputDevice.SOURCE_TOUCHSCREEN; slider.dispatchTouchEvent(this); recycle()
                    }
                }
                uiController.loopMainThreadForAtLeast(200)
                assertEquals(value.toFloat(), slider.value, 1f)
            }
        })
    }
    fun <T : Activity> photo(scenario: ActivityScenario<T>, id: Int = R.id.wallpaperPreview, accept: (PhotoWallpaperDrawable) -> Boolean = { true }): PhotoWallpaperDrawable {
        var photo: PhotoWallpaperDrawable? = null
        waitUntil {
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity {
            assertTrue(it.findViewById<View>(id).background is PhotoWallpaperDrawable)
            photo = it.findViewById<View>(id).background as PhotoWallpaperDrawable
            assertTrue(accept(checkNotNull(photo)))
        } }
        return checkNotNull(photo)
    }
    fun sample(photo: PhotoWallpaperDrawable): Int {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val bounds = Rect(photo.bounds)
        photo.setBounds(0, 0, 96, 96); photo.draw(Canvas(bitmap)); photo.bounds = bounds
        return bitmap.getPixel(48, 16)
    }
    fun waitUntil(check: () -> Unit) {
        val end = System.nanoTime() + 25_000_000_000L
        while (true) {
            try { check(); return }
            catch (error: AssertionError) { if (System.nanoTime() >= end) throw error }
            catch (error: androidx.test.espresso.NoMatchingViewException) { if (System.nanoTime() >= end) throw error }
            Thread.sleep(100)
        }
    }
}

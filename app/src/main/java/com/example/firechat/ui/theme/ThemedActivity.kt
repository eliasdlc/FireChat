package com.example.firechat.ui.theme

import android.os.Bundle
import android.content.res.Configuration
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity

/** Apply the saved accent before inflation and refresh retained screens when returning to them. */
abstract class ThemedActivity : AppCompatActivity() {
    private lateinit var appliedAccent: AppAccent

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedAccent = ThemePreferences.readAccent(this)
        setTheme(appliedAccent.themeRes)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        if (appliedAccent != ThemePreferences.readAccent(this)) recreate()
    }
}

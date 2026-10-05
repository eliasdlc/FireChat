package com.example.firechat

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.firechat.ui.theme.ThemePreferences

class FireChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val theme = ThemePreferences.read(this)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
    }
}

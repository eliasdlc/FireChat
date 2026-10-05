package com.example.firechat.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

enum class AppTheme(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

object ThemePreferences {
    private const val FILE_NAME = "appearance"
    private const val KEY_THEME = "theme"
    private const val KEY_ACCENT = "accent"

    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_THEME, theme.name)
            }
    }

    fun saveAccent(context: Context, accent: AppAccent) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_ACCENT, accent.name) }
    }

    fun readAccent(context: Context): AppAccent {
        val savedName = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCENT, null)
        return AppAccent.entries.firstOrNull { it.name == savedName } ?: AppAccent.BLUE
    }

    fun read(context: Context): AppTheme {
        val savedName = context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, null)

        return AppTheme.entries
            .firstOrNull { it.name == savedName }
            ?: AppTheme.SYSTEM
    }
}

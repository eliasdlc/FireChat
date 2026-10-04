package com.example.firechat.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.example.firechat.ui.theme.ThemePreferences.KEY_THEME

enum class AppTheme(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

object ThemePreferences {
    private const val FILE_NAME = "appearance"
    private const val KEY_THEME = "theme"

    fun save(context: Context, theme: AppTheme) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_THEME, theme.name)
            }
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




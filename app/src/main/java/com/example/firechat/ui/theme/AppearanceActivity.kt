package com.example.firechat.ui.theme

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.firechat.R
import com.example.firechat.util.applySystemBarsPadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppearanceActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_appearance)
        findViewById<View>(R.id.main).applySystemBarsPadding()
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.appearanceThemeRow).setOnClickListener { showModeDialog() }
        findViewById<TextView>(R.id.appearanceThemeValue).setText(when (ThemePreferences.read(this)) {
            AppTheme.SYSTEM -> R.string.theme_system
            AppTheme.LIGHT -> R.string.theme_light
            AppTheme.DARK -> R.string.theme_dark
        })
        val selected = ThemePreferences.readAccent(this)
        val buttons = listOf(
            R.id.accentBlue to AppAccent.BLUE, R.id.accentGreen to AppAccent.GREEN,
            R.id.accentViolet to AppAccent.VIOLET, R.id.accentRose to AppAccent.ROSE,
            R.id.accentAmber to AppAccent.AMBER, R.id.accentTeal to AppAccent.TEAL
        )
        buttons.forEach { (id, accent) ->
            findViewById<MaterialButton>(id).apply {
                setText(accent.labelRes)
                isSaveEnabled = false
                isCheckable = true
                setToggleCheckedStateOnClick(false)
                isChecked = accent == selected
                iconTint = null
                icon = accentIcon(accent, isChecked)
                contentDescription = if (isChecked) getString(R.string.accent_selected, getString(accent.labelRes))
                    else getString(accent.labelRes)
                setOnClickListener { selectAccent(accent) }
            }
        }
        findViewById<MaterialButton>(R.id.restoreAccentButton).apply {
            isEnabled = selected != AppAccent.BLUE
            setOnClickListener { selectAccent(AppAccent.BLUE) }
        }
    }

    private fun selectAccent(accent: AppAccent) {
        if (accent == ThemePreferences.readAccent(this)) return
        ThemePreferences.saveAccent(this, accent)
        recreate()
    }

    private fun accentIcon(accent: AppAccent, selected: Boolean): android.graphics.drawable.Drawable {
        val palette = ContextThemeWrapper(this, accent.themeRes)
        val color = MaterialColors.getColor(palette, androidx.appcompat.R.attr.colorPrimary, Color.TRANSPARENT)
        val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        if (!selected) return circle
        val check = DrawableCompat.wrap(checkNotNull(ContextCompat.getDrawable(this, R.drawable.ic_check))).mutate()
        DrawableCompat.setTint(check, MaterialColors.getColor(palette, com.google.android.material.R.attr.colorOnPrimary, Color.TRANSPARENT))
        return LayerDrawable(arrayOf(circle, check)).apply {
            val inset = (resources.displayMetrics.density * 3).toInt()
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun showModeDialog() {
        val modes = listOf(AppTheme.SYSTEM, AppTheme.DARK, AppTheme.LIGHT)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_dialog_title)
            .setSingleChoiceItems(R.array.theme_options, modes.indexOf(ThemePreferences.read(this))) { dialog, which ->
                val mode = modes[which]
                ThemePreferences.save(this, mode)
                dialog.dismiss()
                AppCompatDelegate.setDefaultNightMode(mode.nightMode)
                findViewById<TextView>(R.id.appearanceThemeValue).setText(when (mode) {
                    AppTheme.SYSTEM -> R.string.theme_system
                    AppTheme.LIGHT -> R.string.theme_light
                    AppTheme.DARK -> R.string.theme_dark
                })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

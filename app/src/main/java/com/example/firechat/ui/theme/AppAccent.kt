package com.example.firechat.ui.theme

import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.example.firechat.R

enum class AppAccent(@StyleRes val themeRes: Int, @StringRes val labelRes: Int) {
    BLUE(R.style.Theme_FireChat_Blue, R.string.accent_blue),
    GREEN(R.style.Theme_FireChat_Green, R.string.accent_green),
    VIOLET(R.style.Theme_FireChat_Violet, R.string.accent_violet),
    ROSE(R.style.Theme_FireChat_Rose, R.string.accent_rose),
    AMBER(R.style.Theme_FireChat_Amber, R.string.accent_amber),
    TEAL(R.style.Theme_FireChat_Teal, R.string.accent_teal)
}

package com.hiepnguyen.tripfinance.ui.locale

import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val displayName: String, val flag: String) {
    VI("vi", "Tiếng Việt", "🇻🇳"),
    EN("en", "English", "🇬🇧")
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.VI }

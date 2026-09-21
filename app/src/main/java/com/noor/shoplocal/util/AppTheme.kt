package com.noor.shoplocal.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.noor.shoplocal.data.Prefs

/**
 * Applies the user's light / dark / system theme choice using AndroidX's
 * night-mode delegate. Called at app start and whenever the setting changes.
 */
object AppTheme {
    fun apply(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun applyFromPrefs(ctx: Context) = apply(Prefs.theme(ctx))
}

package com.armanmaurya.archiv.ui.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

object ThemeManager {
    private const val PREF_NAME = "app_preferences"
    private const val THEME_KEY = "selected_theme"
    private const val DEFAULT_THEME = "System"

    private val _currentTheme = mutableStateOf(DEFAULT_THEME)
    val currentTheme: State<String> = _currentTheme

    fun initTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _currentTheme.value = prefs.getString(THEME_KEY, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(THEME_KEY, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    fun setTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(THEME_KEY, theme).apply()
        _currentTheme.value = theme
    }

    fun isDarkMode(context: Context): Boolean {
        val theme = _currentTheme.value
        return when (theme) {
            "Dark" -> true
            "Light" -> false
            else -> {
                val nightMode = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}

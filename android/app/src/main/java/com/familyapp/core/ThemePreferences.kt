package com.familyapp.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object ThemePreferences {
    private const val PREFS_NAME = "family_app_theme_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    private var prefs: SharedPreferences? = null
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        val saved = sharedPrefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        _themeMode.value = try {
            AppThemeMode.valueOf(saved)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun toggleLightDark(isCurrentDark: Boolean) {
        val next = if (isCurrentDark) AppThemeMode.LIGHT else AppThemeMode.DARK
        setThemeMode(next)
    }
}

package com.posemonitor2.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeStore by preferencesDataStore(name = "app_theme_settings")

enum class ThemeMode(val storageValue: String, val label: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: LIGHT
    }
}

object ThemePreferences {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    const val ACTION_THEME_CHANGED = "com.posemonitor2.action.THEME_CHANGED"

    fun observeThemeMode(context: Context): Flow<ThemeMode> =
        context.themeStore.data.map { prefs -> ThemeMode.fromStorage(prefs[themeModeKey]) }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.themeStore.edit { prefs ->
            prefs[themeModeKey] = mode.storageValue
        }
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK  -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        try {
            val intent = android.content.Intent(ACTION_THEME_CHANGED)
            context.applicationContext.sendBroadcast(intent)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun PoseMonitorAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode by ThemePreferences.observeThemeMode(context).collectAsStateWithLifecycle(initialValue = ThemeMode.LIGHT)

    LaunchedEffect(mode) {
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK  -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    PoseMonitor2Theme(darkTheme = mode == ThemeMode.DARK, dynamicColor = false, content = content)
}

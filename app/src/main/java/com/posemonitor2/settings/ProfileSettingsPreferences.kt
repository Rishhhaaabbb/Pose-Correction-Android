package com.posemonitor2.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileSettingsStore by preferencesDataStore(name = "profile_settings")

enum class AppLanguage(val storageValue: String, val label: String) {
    ENGLISH_UK("en_uk", "English"),
    HINDI("hi", "Hindi"),
    KANNADA("kn", "Kannada");

    companion object {
        fun fromStorage(value: String?): AppLanguage = entries.firstOrNull { it.storageValue == value } ?: ENGLISH_UK
    }
}

enum class UnitSystem(val storageValue: String, val label: String) {
    METRIC("metric", "Kilograms (kg), Centimeters (cm)"),
    IMPERIAL("imperial", "Pounds (lb), Feet/Inches (ft/in)");

    companion object {
        fun fromStorage(value: String?): UnitSystem = entries.firstOrNull { it.storageValue == value } ?: METRIC
    }
}

object ProfileSettingsPreferences {
    private val dailyRemindersKey = booleanPreferencesKey("daily_reminders")
    private val goalAchievementsKey = booleanPreferencesKey("goal_achievements")
    private val languageKey = stringPreferencesKey("profile_language")
    private val unitSystemKey = stringPreferencesKey("unit_system")
    private val autoRotateScreenKey = booleanPreferencesKey("auto_rotate_screen")

    fun observeDailyReminders(context: Context): Flow<Boolean> =
        context.profileSettingsStore.data.map { it[dailyRemindersKey] ?: true }

    suspend fun setDailyReminders(context: Context, enabled: Boolean) {
        context.profileSettingsStore.edit { prefs ->
            prefs[dailyRemindersKey] = enabled
        }
    }

    fun observeGoalAchievements(context: Context): Flow<Boolean> =
        context.profileSettingsStore.data.map { it[goalAchievementsKey] ?: true }

    suspend fun setGoalAchievements(context: Context, enabled: Boolean) {
        context.profileSettingsStore.edit { prefs ->
            prefs[goalAchievementsKey] = enabled
        }
    }

    fun observeLanguage(context: Context): Flow<AppLanguage> =
        context.profileSettingsStore.data.map { prefs ->
            AppLanguage.fromStorage(prefs[languageKey])
        }

    suspend fun setLanguage(context: Context, language: AppLanguage) {
        // 1. Update DataStore first and wait for completion
        context.profileSettingsStore.edit { prefs ->
            prefs[languageKey] = language.storageValue
        }
        
        // 2. Then apply the locale change (which may trigger activity recreation)
        AppLocaleManager.setLanguage(context, language)
    }

    fun observeUnitSystem(context: Context): Flow<UnitSystem> =
        context.profileSettingsStore.data.map { prefs ->
            UnitSystem.fromStorage(prefs[unitSystemKey])
        }

    suspend fun setUnitSystem(context: Context, system: UnitSystem) {
        context.profileSettingsStore.edit { prefs ->
            prefs[unitSystemKey] = system.storageValue
        }
    }

    /**
     * Auto-rotate: when true, orientation is locked to the value required by each
     * exercise (portrait or landscape) as soon as a session starts.
     * When false the device keeps its current orientation throughout.
     * Defaults to true so existing behaviour is preserved.
     */
    fun observeAutoRotateScreen(context: Context): Flow<Boolean> =
        context.profileSettingsStore.data.map { it[autoRotateScreenKey] ?: true }

    suspend fun setAutoRotateScreen(context: Context, enabled: Boolean) {
        context.profileSettingsStore.edit { prefs ->
            prefs[autoRotateScreenKey] = enabled
        }
    }

    // Recent emails (simple pipe-separated list stored in DataStore)
    private val recentEmailsKey = stringPreferencesKey("recent_emails")

    fun observeRecentEmails(context: Context): Flow<List<String>> =
        context.profileSettingsStore.data.map { prefs ->
            prefs[recentEmailsKey].orEmpty()
                .split('|')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

    suspend fun addRecentEmail(context: Context, email: String) {
        if (email.isBlank()) return
        context.profileSettingsStore.edit { prefs ->
            val existing = prefs[recentEmailsKey].orEmpty().split('|').map { it.trim() }.filter { it.isNotBlank() }
            val updated = (listOf(email.trim()) + existing).distinct().take(5)
            prefs[recentEmailsKey] = updated.joinToString("|")
        }
    }
}

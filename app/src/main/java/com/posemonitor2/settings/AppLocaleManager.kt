package com.posemonitor2.settings

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLocaleManager {
    private const val PREF_FILE = "app_locale_prefs"
    private const val KEY_LANGUAGE = "language"

    /**
     * Call this in attachBaseContext of your Activities.
     */
    fun wrapContext(base: Context): Context {
        val language = storedLanguage(base)
        val locale = language.toLocale()
        
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        
        return base.createConfigurationContext(config)
    }

    /**
     * Initial application of locale on app start (e.g. in Application.onCreate).
     */
    fun applyLocale(context: Context) {
        val language = storedLanguage(context)
        val locale = language.toLocale()
        
        // This ensures the Application context and future activities pick it up
        val appLocale: LocaleListCompat = LocaleListCompat.create(locale)
        AppCompatDelegate.setApplicationLocales(appLocale)
        
        // Global default for safety
        Locale.setDefault(locale)
    }

    /**
     * Change language and persist it.
     */
    fun setLanguage(context: Context, language: AppLanguage) {
        // 1. Persist for our own manual wrapping
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.storageValue)
            .commit()

        // 2. Set globally via AppCompatDelegate
        val locale = language.toLocale()
        val appLocale: LocaleListCompat = LocaleListCompat.create(locale)
        AppCompatDelegate.setApplicationLocales(appLocale)
        
        // 3. Update global default
        Locale.setDefault(locale)
    }

    private fun storedLanguage(context: Context): AppLanguage {
        val stored = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return AppLanguage.fromStorage(stored)
    }

    private fun AppLanguage.toLocale(): Locale = when (this) {
        // Map "en_uk" storage value to English (UK) locale
        AppLanguage.ENGLISH_UK -> Locale.UK
        AppLanguage.HINDI -> Locale("hi")
        AppLanguage.KANNADA -> Locale("kn")
    }
}

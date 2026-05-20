package com.posemonitor2.onboarding

import android.content.Context

class OnboardingManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun shouldShowOnboarding(): Boolean {
        return !prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING_V1, false)
    }

    fun markOnboardingComplete() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING_V1, true).apply()
    }

    fun resetOnboarding() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING_V1, false).apply()
    }

    companion object {
        private const val PREF_FILE = "app_prefs"
        private const val KEY_HAS_SEEN_ONBOARDING_V1 = "has_seen_onboarding_v1"
    }
}

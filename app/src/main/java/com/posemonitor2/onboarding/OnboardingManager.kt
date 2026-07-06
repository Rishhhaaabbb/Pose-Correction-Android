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

    /**
     * Returns true if [userId] has completed (or skipped) the questionnaire on this device.
     * The key is per-user so that a different account signing in on the same device
     * will still see the questionnaire.
     */
    fun hasCompletedQuestionnaire(userId: String): Boolean {
        return prefs.getBoolean(questionnaireKey(userId), false)
    }

    /**
     * Marks the questionnaire as done for [userId] on this device.
     * Also resets the onboarding tour so it shows on next launch of UserFlowActivity.
     */
    fun markQuestionnaireCompleted(userId: String) {
        prefs.edit().putBoolean(questionnaireKey(userId), true).apply()
    }

    /**
     * Clears both the questionnaire and tour flags for [userId].
     * Useful for testing or if the user wants to redo setup.
     */
    fun resetQuestionnaire(userId: String) {
        prefs.edit()
            .remove(questionnaireKey(userId))
            .putBoolean(KEY_HAS_SEEN_ONBOARDING_V1, false)
            .apply()
    }

    private fun questionnaireKey(userId: String): String =
        "${KEY_QUESTIONNAIRE_COMPLETED_PREFIX}${userId.take(32)}"

    companion object {
        private const val PREF_FILE = "app_prefs"
        private const val KEY_HAS_SEEN_ONBOARDING_V1 = "has_seen_onboarding_v1"
        private const val KEY_QUESTIONNAIRE_COMPLETED_PREFIX = "questionnaire_completed_v1_"
    }
}

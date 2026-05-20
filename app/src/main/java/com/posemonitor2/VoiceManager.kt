// app/src/main/java/com/posemonitor2/VoiceManager.kt

package com.posemonitor2

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Manages Text-to-Speech (TTS) for the tracking session.
 * 
 * Features:
 * - Flushing instructions (new instructions interrupt old ones).
 * - Debounced feedback (prevents repeating the same correction too frequently).
 */
class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false
    
    // Feedback debouncing state
    private var lastFeedbackTime: Long = 0
    private val globalFeedbackCooldownMs = 6000L // 6 seconds global silence between ANY two tips

    private fun isLanguageSupported(status: Int): Boolean {
        return status != TextToSpeech.LANG_MISSING_DATA && status != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun tryApplyLanguage(locale: Locale): Boolean {
        val status = tts?.setLanguage(locale) ?: return false
        return isLanguageSupported(status)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val preferredLocales = listOf(Locale.getDefault(), Locale.UK).distinct()
            val selectedLocale = preferredLocales.firstOrNull { tryApplyLanguage(it) }

            if (selectedLocale == null) {
                // Keep TTS usable with engine defaults even if explicit locale data is missing.
                Log.w("VoiceManager", "Preferred TTS language data unavailable; using engine default voice")
            } else {
                Log.d("VoiceManager", "TTS language set to ${selectedLocale.toLanguageTag()}")
            }

            isInitialized = true
            tts?.setSpeechRate(0.85f) // Slower for clarity at a distance
            Log.d("VoiceManager", "TTS Initialized successfully with speech rate 0.85")
        } else {
            Log.e("VoiceManager", "TTS Initialization failed")
        }
    }

    /**
     * Speaks the given text.
     * @param text The text to speak.
     * @param isInstruction If true, it flushes the queue to speak immediately.
     */
    fun speak(text: String, isInstruction: Boolean = false) {
        if (!isInitialized) {
            Log.w("VoiceManager", "TTS not initialized yet. Skipping: $text")
            return
        }

        val now = System.currentTimeMillis()

        if (isInstruction) {
            // For instructions, clear any pending speech to stay in sync with the current state.
            // This ensures "Goal" always takes priority over "Correction".
            tts?.stop()
        } else {
            // Global feedback cooldown: skip if we spoke ANY tip in the last 6 seconds.
            if ((now - lastFeedbackTime) < globalFeedbackCooldownMs) {
                Log.d("VoiceManager", "Skipping feedback due to global cooldown: $text")
                return 
            }
            lastFeedbackTime = now
        }

        Log.d("VoiceManager", "Speaking (${if (isInstruction) "GOAL" else "TIP"}): $text")
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

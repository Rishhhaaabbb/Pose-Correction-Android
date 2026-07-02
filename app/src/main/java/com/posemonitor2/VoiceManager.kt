// app/src/main/java/com/posemonitor2/VoiceManager.kt

package com.posemonitor2

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manages Text-to-Speech (TTS) for the tracking session.
 *
 * Features:
 * - Flushing instructions (new instructions interrupt old ones).
 * - Debounced feedback (prevents repeating the same correction too frequently).
 * - Optional Groq AI rephrasing for more natural feedback.
 */
class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private val lock = Any()
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    @Volatile
    private var isInitialized = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val groqProvider = GroqFeedbackProvider(BuildConfig.GROQ_API_KEY)

    // Feedback debouncing state
    private var lastFeedbackTime: Long = 0
    private var lastSpokenText: String = ""
    private val globalFeedbackCooldownMs = 4000L  // Wait 4s between any two phrases
    private val samePhraseCooldownMs = 8000L      // Wait 8s before repeating the same instruction

    private fun isLanguageSupported(status: Int): Boolean {
        return status != TextToSpeech.LANG_MISSING_DATA && status != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun tryApplyLanguage(locale: Locale): Boolean {
        val status = tts?.setLanguage(locale) ?: return false
        return isLanguageSupported(status)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val preferredLocales = listOf(Locale.getDefault(), Locale.UK, Locale.US).distinct()
            val selectedLocale = preferredLocales.firstOrNull { tryApplyLanguage(it) }

            if (selectedLocale == null) {
                // Keep TTS usable with engine defaults even if explicit locale data is missing.
                Log.w("VoiceManager", "Preferred TTS language data unavailable; using engine default voice")
            } else {
                Log.d("VoiceManager", "TTS language set to ${selectedLocale.toLanguageTag()}")
            }

            isInitialized = true
            tts?.setSpeechRate(0.9f) // Slower for clearer instructions
            tts?.setPitch(1.0f)
            Log.d("VoiceManager", "TTS Initialized successfully with speech rate 0.9")
        } else {
            Log.e("VoiceManager", "TTS Initialization failed")
        }
    }

    /**
     * Speaks the given text.
     * @param text The text to speak.
     * @param isInstruction If true, it flushes the queue to speak immediately.
     * @param useGroq If true, uses AI to rephrase the text.
     */
    fun speak(text: String, isInstruction: Boolean = false, useGroq: Boolean = false) {
        if (!isInitialized) {
            Log.w("VoiceManager", "TTS not initialized yet. Skipping: $text")
            return
        }

        val now = System.currentTimeMillis()

        val shouldProceed = synchronized(lock) {
            if (tts == null) return@synchronized false

            val isGlobalCooldownActive = (now - lastFeedbackTime) < globalFeedbackCooldownMs
            val isSamePhraseCooldownActive = (!isInstruction && text == lastSpokenText && (now - lastFeedbackTime) < samePhraseCooldownMs)

            if (isGlobalCooldownActive || isSamePhraseCooldownActive) {
                Log.d("VoiceManager", "Skipping feedback ($text). Global: $isGlobalCooldownActive, Same: $isSamePhraseCooldownActive")
                false
            } else {
                if (isInstruction) {
                    // Instructions (like state changes) still interrupt if the cooldown has passed
                    tts?.stop()
                }
                lastFeedbackTime = now
                lastSpokenText = text
                true
            }
        }

        if (shouldProceed) {
            scope.launch {
                try {
                    val spokenText = if (useGroq && text.isNotEmpty()) {
                        groqProvider.getDynamicFeedback(text)
                    } else {
                        text
                    }

                    synchronized(lock) {
                        // Use QUEUE_FLUSH for everything to ensure instructions are "sharp" and immediate
                        tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                    Log.d("VoiceManager", "Speaking: $spokenText (Original: $text)")
                } catch (e: Exception) {
                    Log.e("VoiceManager", "Groq error, falling back to direct TTS", e)
                    synchronized(lock) {
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }
            }
        }
    }

    fun shutdown() {
        synchronized(lock) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }
}

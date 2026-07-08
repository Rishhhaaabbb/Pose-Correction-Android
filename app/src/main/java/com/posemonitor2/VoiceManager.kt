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
 * Manages Text-to-Speech for the monitoring session.
 *
 * Three independent voice channels, each with its own cooldown:
 *
 *   PRESENCE    — "Step into the frame", "One person only"
 *                 Instant TTS, 3 s cooldown, always interrupts.
 *
 *   INSTRUCTION — State-change cue spoken once per pose transition.
 *                 Routed through Groq for natural phrasing.
 *                 8 s same-phrase cooldown, interrupts current speech.
 *                 Groq in-flight guard prevents concurrent API calls.
 *
 *   CORRECTION  — Joint-specific corrective cues during holding.
 *                 Direct Android TTS (zero latency, no Groq).
 *                 2.5 s global cooldown + 6 s same-phrase cooldown.
 *                 Never interrupts an instruction already speaking.
 */
class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private val lock = Any()
    private var tts: TextToSpeech? = TextToSpeech(context, this)

    @Volatile private var isInitialized = false
    @Volatile private var groqInFlight  = false

    private val scope        = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val groqProvider = GroqFeedbackProvider(BuildConfig.GROQ_API_KEY)

    // ── Per-channel cooldown state ────────────────────────────────────────────
    private var lastPresenceTime    = 0L
    private var lastInstructionTime = 0L
    private var lastInstructionText = ""
    private var lastCorrectionTime  = 0L
    private var lastCorrectionText  = ""

    private val presenceCooldownMs        = 3_000L
    private val instructionSameCooldownMs = 8_000L
    private val correctionGlobalCooldownMs = 2_500L
    private val correctionSameCooldownMs   = 6_000L

    // ── TTS init ──────────────────────────────────────────────────────────────

    private fun isLanguageSupported(status: Int) =
        status != TextToSpeech.LANG_MISSING_DATA && status != TextToSpeech.LANG_NOT_SUPPORTED

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locales = listOf(Locale.getDefault(), Locale.UK, Locale.US).distinct()
            val chosen  = locales.firstOrNull { loc ->
                val s = tts?.setLanguage(loc) ?: return@firstOrNull false
                isLanguageSupported(s)
            }
            if (chosen == null) {
                Log.w(TAG, "Preferred TTS locale unavailable; using engine default")
            } else {
                Log.d(TAG, "TTS locale: ${chosen.toLanguageTag()}")
            }
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            isInitialized = true
            Log.d(TAG, "TTS ready")
        } else {
            Log.e(TAG, "TTS init failed")
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Speak a PRESENCE message ("Step into the frame", "One person only").
     * Always interrupts, never uses Groq.
     */
    fun speakPresence(text: String) {
        if (!isInitialized) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastPresenceTime < presenceCooldownMs) return
            lastPresenceTime = now
            tts?.stop()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "presence")
        }
        Log.d(TAG, "Presence: $text")
    }

    /**
     * Speak an INSTRUCTION (state-change cue).
     * Routed through Groq for natural phrasing; falls back to direct TTS.
     * Groq in-flight guard prevents concurrent API calls.
     */
    fun speakInstruction(text: String, exerciseName: String = "") {
        if (!isInitialized || text.isBlank()) return
        val now = System.currentTimeMillis()

        val shouldProceed = synchronized(lock) {
            val isSame = text == lastInstructionText &&
                         now - lastInstructionTime < instructionSameCooldownMs
            if (isSame) return@synchronized false
            lastInstructionTime = now
            lastInstructionText = text
            tts?.stop()
            true
        }
        if (!shouldProceed) return

        scope.launch {
            val spoken = if (!groqInFlight && BuildConfig.GROQ_API_KEY.isNotBlank() &&
                            BuildConfig.GROQ_API_KEY != "your_groq_api_key_here") {
                groqInFlight = true
                try {
                    groqProvider.getInstruction(text, exerciseName)
                } catch (e: Exception) {
                    Log.e(TAG, "Groq instruction error", e)
                    text
                } finally {
                    groqInFlight = false
                }
            } else {
                text
            }
            synchronized(lock) {
                tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "instruction")
            }
            Log.d(TAG, "Instruction: $spoken")
        }
    }

    /**
     * Speak a CORRECTION (joint-specific cue during holding).
     * Direct Android TTS — zero latency, no Groq.
     * Will not interrupt an instruction that is currently speaking.
     */
    fun speakCorrection(text: String) {
        if (!isInitialized || text.isBlank()) return
        // Skip generic fallback strings that carry no useful info
        if (GENERIC_FALLBACKS.any { text.equals(it, ignoreCase = true) }) return

        val now = System.currentTimeMillis()
        synchronized(lock) {
            val globalOk = now - lastCorrectionTime >= correctionGlobalCooldownMs
            val sameOk   = text != lastCorrectionText ||
                           now - lastCorrectionTime >= correctionSameCooldownMs
            if (!globalOk || !sameOk) return
            lastCorrectionTime = now
            lastCorrectionText = text
            // QUEUE_ADD — doesn't cut off an instruction mid-sentence
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "correction")
        }
        Log.d(TAG, "Correction: $text")
    }

    /**
     * Immediately silence TTS. Called when the user taps Mute.
     */
    fun stop() {
        synchronized(lock) { tts?.stop() }
    }

    fun shutdown() {
        synchronized(lock) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }

    // ── Compat shim — keeps existing call sites compiling ─────────────────────
    // Old code called speak(text, isInstruction, useGroq). Route to the new
    // channel methods so nothing else needs to change immediately.
    fun speak(text: String, isInstruction: Boolean = false, useGroq: Boolean = false) {
        when {
            isInstruction -> speakInstruction(text)
            else          -> speakCorrection(text)
        }
    }

    companion object {
        private const val TAG = "VoiceManager"

        /** Short generic strings that should never be spoken aloud. */
        private val GENERIC_FALLBACKS = setOf(
            "Hold steady",
            "Focus on your pose",
            "Adjust your body position",
            "Adjust to align",
            "Your body position is incorrect. Adjust to align.",
        )
    }
}

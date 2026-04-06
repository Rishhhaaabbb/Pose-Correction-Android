package com.posemonitor2

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.camera.core.CameraSelector
import com.core.poseengine.monitor.FrameStatus
import com.core.poseengine.monitor.MonitorPhase
import java.util.Locale
import kotlin.math.ceil

/**
 * Speaks live coaching feedback so users can follow guidance from a distance.
 *
 * Design goals:
 * 1) Never interrupt messages in the middle.
 * 2) Keep speech calm and clear for all age groups.
 * 3) Offer simple language, language selection, and gentle motivation.
 */
class MonitorSpeechCoach(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var googleEngineFallbackTried = false

    private var preferences = MonitorSpeechPreferences()

    private var lastSpokenAtMs: Long = 0L
    private var lastSpokenBucket: String? = null
    private var lastSpokenStateId: Int? = null
    private var lastGuidanceStateId: Int? = null
    private var lastRepCount: Int = -1
    private var lastCameraFacing: Int? = null
    private var introSpoken = false
    private var sessionCompleteSpoken = false
    private var utteranceCounter = 0

    private var lastMotivationAtMs: Long = 0L
    private var motivationIndex: Int = 0

    init {
        initializeTts(preferGoogleEngine = true)
    }

    fun applyPreferences(newPreferences: MonitorSpeechPreferences) {
        preferences = newPreferences
        if (isReady) {
            applyVoiceSettings()
            lastSpokenBucket = null
        }
    }

    fun announceSessionStarted(cameraFacing: Int) {
        if (introSpoken) return

        introSpoken = true
        lastCameraFacing = cameraFacing
        val cameraLabel = if (cameraFacing == CameraSelector.LENS_FACING_BACK) "Back" else "Front"

        speak(
            text = localized(
                english = "Monitoring started. $cameraLabel camera active. Follow the voice cues.",
                hindi = "मॉनिटरिंग शुरू हो गई है। $cameraLabel कैमरा चालू है। अब आवाज़ वाले निर्देशों का पालन करें।",
                kannada = "ಮಾನಿಟರಿಂಗ್ ಪ್ರಾರಂಭವಾಗಿದೆ. $cameraLabel ಕ್ಯಾಮೆರಾ ಸಕ್ರಿಯವಾಗಿದೆ. ಈಗ ಧ್ವನಿ ಸೂಚನೆಗಳನ್ನು ಅನುಸರಿಸಿ.",
            ),
            minGapMs = 0L,
            force = true,
            clearBucket = false,
        )
    }

    fun announceCameraFacing(cameraFacing: Int) {
        if (lastCameraFacing == cameraFacing) return

        lastCameraFacing = cameraFacing
        val message = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
            localized(
                english = "Back camera selected",
                hindi = "बैक कैमरा चुना गया है",
                kannada = "ಹಿಂದಿನ ಕ್ಯಾಮೆರಾ ಆಯ್ಕೆ ಮಾಡಲಾಗಿದೆ",
            )
        } else {
            localized(
                english = "Front camera selected",
                hindi = "फ्रंट कैमरा चुना गया है",
                kannada = "ಮುಂಭಾಗದ ಕ್ಯಾಮೆರಾ ಆಯ್ಕೆ ಮಾಡಲಾಗಿದೆ",
            )
        }

        speak(
            text = message,
            minGapMs = adjustGapForSpeed(CAMERA_SWITCH_GAP_MS),
            force = false,
            clearBucket = false,
        )
    }

    fun onFrameStatus(status: FrameStatus, guidanceStateId: Int?) {
        if (!isReady) return

        if (status.stateId != lastSpokenStateId) {
            lastSpokenStateId = status.stateId
            lastSpokenBucket = null
        }

        if (status.sessionComplete && !sessionCompleteSpoken) {
            sessionCompleteSpoken = true
            speak(
                text = localized(
                    english = "Session complete. Great work.",
                    hindi = "सेशन पूरा हुआ। बहुत अच्छा किया।",
                    kannada = "ಸೆಷನ್ ಪೂರ್ಣಗೊಂಡಿದೆ. ತುಂಬಾ ಚೆನ್ನಾಗಿ ಮಾಡಿದ್ದೀರಿ.",
                ),
                minGapMs = 0L,
                force = true,
                clearBucket = true,
            )
            return
        }

        if (status.repsCompleted > lastRepCount) {
            if (lastRepCount >= 0) {
                speak(
                    text = localized(
                        english = "Rep ${status.repsCompleted} complete",
                        hindi = "रेप ${status.repsCompleted} पूरा हुआ",
                        kannada = "ರೆಪ್ ${status.repsCompleted} ಪೂರ್ಣಗೊಂಡಿದೆ",
                    ),
                    minGapMs = adjustGapForSpeed(REP_ANNOUNCEMENT_GAP_MS),
                    force = false,
                    clearBucket = false,
                )
            }
            lastRepCount = status.repsCompleted
        }

        if (status.phase == MonitorPhase.GUIDANCE) {
            val upcomingStateId = guidanceStateId ?: status.stateId
            if (lastGuidanceStateId != upcomingStateId) {
                lastGuidanceStateId = upcomingStateId
                speak(
                    text = localized(
                        english = "Next pose. State ${upcomingStateId + 1}.",
                        hindi = "अगला पोज़। स्टेट ${upcomingStateId + 1}।",
                        kannada = "ಮುಂದಿನ ಭಂಗಿ. ಸ್ಥಿತಿ ${upcomingStateId + 1}.",
                    ),
                    minGapMs = adjustGapForSpeed(NEXT_POSE_GAP_MS),
                    force = false,
                    clearBucket = true,
                )
            }
        }

        val feedback = status.feedback.trim()
        if (feedback.isEmpty()) {
            maybeSpeakMotivation(status)
            return
        }

        // Guidance countdown strings change very fast and can overwhelm speech.
        if (status.phase == MonitorPhase.GUIDANCE && feedback.startsWith("Get ready! Starting in")) {
            return
        }

        val bucket = feedbackBucket(feedback)
        if (bucket == lastSpokenBucket) {
            maybeSpeakMotivation(status)
            return
        }

        val minGapBase = if (bucket.startsWith(HOLD_PREFIX) || bucket.startsWith(READY_PREFIX)) {
            HOLD_GUIDANCE_GAP_MS
        } else {
            GENERAL_GAP_MS
        }

        val spoken = speak(
            text = localizeFeedback(toSpeechText(feedback)),
            minGapMs = adjustGapForSpeed(minGapBase),
            force = false,
            clearBucket = false,
        )

        if (spoken) {
            lastSpokenBucket = bucket
        } else {
            maybeSpeakMotivation(status)
        }
    }

    fun shutdown() {
        isReady = false
        googleEngineFallbackTried = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun initializeTts(preferGoogleEngine: Boolean) {
        isReady = false
        tts?.stop()
        tts?.shutdown()

        tts = if (preferGoogleEngine) {
            TextToSpeech(appContext, { status ->
                handleTtsInitStatus(status, preferredGoogleEngine = true)
            }, GOOGLE_TTS_ENGINE_PACKAGE)
        } else {
            TextToSpeech(appContext) { status ->
                handleTtsInitStatus(status, preferredGoogleEngine = false)
            }
        }
    }

    private fun handleTtsInitStatus(status: Int, preferredGoogleEngine: Boolean) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            applyVoiceSettings()
            return
        }

        if (preferredGoogleEngine && !googleEngineFallbackTried) {
            googleEngineFallbackTried = true
            initializeTts(preferGoogleEngine = false)
        }
    }

    private fun applyVoiceSettings() {
        val localTts = tts ?: return

        val rate = when (preferences.speedMode) {
            SpeechSpeedMode.NORMAL -> NORMAL_RATE
            SpeechSpeedMode.EXTRA_SLOW -> EXTRA_SLOW_RATE
        }

        localTts.setSpeechRate(rate)
        // Slightly sweeter/charming tone while staying natural.
        localTts.setPitch(CHARMING_PITCH)

        val selectedLocale = resolveSupportedLocale(localTts, preferences.language.localeCandidates)
            ?: Locale.US
        localTts.setLanguage(selectedLocale)

        val voiceCandidate = localTts.voices
            ?.asSequence()
            ?.filter { voice ->
                voice.locale.language == selectedLocale.language
            }
            ?.maxWithOrNull(bestVoiceComparator())

        if (voiceCandidate != null) {
            localTts.voice = voiceCandidate
        }
    }

    private fun bestVoiceComparator(): Comparator<Voice> {
        return compareByDescending<Voice> { it.quality }
            // For equally high quality voices, prefer lower latency.
            .thenBy { it.latency }
            // Prefer network-capable voices for richer modern neural voice options.
            .thenByDescending { it.isNetworkConnectionRequired }
    }

    private fun resolveSupportedLocale(
        localTts: TextToSpeech,
        candidates: List<Locale>,
    ): Locale? {
        for (candidate in candidates) {
            val result = localTts.isLanguageAvailable(candidate)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                return candidate
            }
        }
        return null
    }

    private fun speak(
        text: String,
        minGapMs: Long,
        force: Boolean,
        clearBucket: Boolean,
    ): Boolean {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return false

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSpokenAtMs < minGapMs) return false

        val localTts = tts ?: return false
        if (!isReady) return false
        if (!force && localTts.isSpeaking) return false

        if (clearBucket) {
            lastSpokenBucket = null
        }

        val utteranceId = "monitor-${utteranceCounter++}"
        localTts.speak(cleaned, TextToSpeech.QUEUE_ADD, null, utteranceId)
        lastSpokenAtMs = now
        return true
    }

    private fun maybeSpeakMotivation(status: FrameStatus) {
        if (!status.isCorrect || status.phase != MonitorPhase.HOLDING) return

        val now = SystemClock.elapsedRealtime()
        val minGap = adjustGapForSpeed(MOTIVATION_GAP_MS)
        if (now - lastMotivationAtMs < minGap) return

        val message = nextMotivationMessage()
        val spoken = speak(
            text = message,
            minGapMs = minGap,
            force = false,
            clearBucket = false,
        )

        if (spoken) {
            lastMotivationAtMs = now
        }
    }

    private fun nextMotivationMessage(): String {
        val englishMessages = listOf(
            "Great job. Keep going, you are doing very well.",
            "Excellent control. Keep breathing slowly.",
            "Nice effort. Stay calm and hold this position.",
            "Wonderful. Your form is improving step by step.",
        )
        val hindiMessages = listOf(
            "बहुत अच्छा। ऐसे ही जारी रखें, आप बहुत अच्छा कर रहे हैं।",
            "शानदार नियंत्रण। धीरे-धीरे सांस लेते रहें।",
            "अच्छी कोशिश। शांत रहें और यह स्थिति बनाए रखें।",
            "बहुत बढ़िया। आपका पोज़ हर कदम पर बेहतर हो रहा है।",
        )
        val kannadaMessages = listOf(
            "ಅದ್ಭುತ. ಇದೇ ರೀತಿ ಮುಂದುವರಿಸಿ, ನೀವು ತುಂಬಾ ಚೆನ್ನಾಗಿ ಮಾಡುತ್ತಿದ್ದೀರಿ.",
            "ಅತ್ಯುತ್ತಮ ನಿಯಂತ್ರಣ. ನಿಧಾನವಾಗಿ ಉಸಿರಾಡುತ್ತಿರಿ.",
            "ಚೆನ್ನಾದ ಪ್ರಯತ್ನ. ಶಾಂತವಾಗಿರಿ ಮತ್ತು ಈ ಭಂಗಿಯನ್ನು ಹಿಡಿದುಕೊಳ್ಳಿ.",
            "ಅತ್ಯುತ್ತಮ. ನಿಮ್ಮ ಭಂಗಿ ಹಂತ ಹಂತವಾಗಿ ಉತ್ತಮವಾಗುತ್ತಿದೆ.",
        )

        val idx = motivationIndex % englishMessages.size
        motivationIndex++

        return when (preferences.language) {
            SpeechLanguageOption.ENGLISH -> englishMessages[idx]
            SpeechLanguageOption.HINDI -> hindiMessages[idx]
            SpeechLanguageOption.KANNADA -> kannadaMessages[idx]
        }
    }

    private fun feedbackBucket(feedback: String): String {
        val lower = feedback.lowercase(Locale.US)

        if (lower.startsWith("hold") || feedback.startsWith("🔥") || feedback.startsWith("✨")) {
            val seconds = extractSeconds(feedback)
            return if (seconds != null) "$HOLD_PREFIX${ceil(seconds).toInt()}" else "$HOLD_PREFIX$lower"
        }

        if (lower.startsWith("get ready! starting in")) {
            val seconds = extractSeconds(feedback)
            return if (seconds != null) "$READY_PREFIX${ceil(seconds).toInt()}" else "$READY_PREFIX$lower"
        }

        return lower
            .replace("🔥", "")
            .replace("✨", "")
            .trim()
    }

    private fun toSpeechText(feedback: String): String {
        val trimmed = feedback.trim()

        if (trimmed == "Next!") return "Next pose"

        val holdSeconds = extractSeconds(trimmed)
        if (trimmed.startsWith("Hold") && holdSeconds != null) {
            val rounded = ceil(holdSeconds).toInt().coerceAtLeast(1)
            return "Hold for $rounded seconds"
        }

        if ((trimmed.startsWith("🔥") || trimmed.startsWith("✨")) && holdSeconds != null) {
            val rounded = ceil(holdSeconds).toInt().coerceAtLeast(1)
            return "Hold for $rounded seconds"
        }

        if (trimmed.startsWith("Get ready! Starting in") && holdSeconds != null) {
            val rounded = ceil(holdSeconds).toInt().coerceAtLeast(1)
            return "Get ready. Starting in $rounded seconds"
        }

        return trimmed
            .replace("🔥", "")
            .replace("✨", "")
            .replace("!", "")
            .replace("  ", " ")
            .trim()
    }

    private fun localizeFeedback(englishText: String): String {
        return when (preferences.language) {
            SpeechLanguageOption.ENGLISH -> englishText
            SpeechLanguageOption.HINDI -> {
                if (englishText.startsWith("Hold for ")) {
                    val seconds = extractSeconds(englishText.replace("seconds", "s"))?.let { ceil(it).toInt() }
                    if (seconds != null) return "$seconds सेकंड तक इसी पोज़ में रहें।"
                }
                if (englishText.startsWith("Get ready. Starting in ")) {
                    val seconds = extractSeconds(englishText.replace("seconds", "s"))?.let { ceil(it).toInt() }
                    if (seconds != null) return "$seconds सेकंड में अगला पोज़ शुरू होगा। तैयार हो जाएं।"
                }
                englishText
            }
            SpeechLanguageOption.KANNADA -> {
                if (englishText.startsWith("Hold for ")) {
                    val seconds = extractSeconds(englishText.replace("seconds", "s"))?.let { ceil(it).toInt() }
                    if (seconds != null) return "$seconds ಸೆಕೆಂಡ್ ಇದೇ ಭಂಗಿಯನ್ನು ಹಿಡಿದುಕೊಳ್ಳಿ."
                }
                if (englishText.startsWith("Get ready. Starting in ")) {
                    val seconds = extractSeconds(englishText.replace("seconds", "s"))?.let { ceil(it).toInt() }
                    if (seconds != null) return "$seconds ಸೆಕೆಂಡ್ ನಂತರ ಮುಂದಿನ ಭಂಗಿ ಪ್ರಾರಂಭವಾಗುತ್ತದೆ. ಸಿದ್ಧರಾಗಿ."
                }
                englishText
            }
        }
    }

    private fun localized(english: String, hindi: String, kannada: String): String = when (preferences.language) {
        SpeechLanguageOption.ENGLISH -> english
        SpeechLanguageOption.HINDI -> hindi
        SpeechLanguageOption.KANNADA -> kannada
    }

    private fun extractSeconds(text: String): Double? {
        val match = TIME_REGEX.find(text) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    private fun adjustGapForSpeed(baseGapMs: Long): Long = when (preferences.speedMode) {
        SpeechSpeedMode.NORMAL -> baseGapMs
        SpeechSpeedMode.EXTRA_SLOW -> (baseGapMs * 1.35).toLong()
    }

    companion object {
        private const val NORMAL_RATE = 0.82f
        private const val EXTRA_SLOW_RATE = 0.68f
        private const val CHARMING_PITCH = 1.08f

        private const val GENERAL_GAP_MS = 5600L
        private const val HOLD_GUIDANCE_GAP_MS = 4200L
        private const val CAMERA_SWITCH_GAP_MS = 2000L
        private const val REP_ANNOUNCEMENT_GAP_MS = 3000L
        private const val NEXT_POSE_GAP_MS = 3400L
        private const val MOTIVATION_GAP_MS = 18000L

        private const val GOOGLE_TTS_ENGINE_PACKAGE = "com.google.android.tts"

        private const val HOLD_PREFIX = "hold:"
        private const val READY_PREFIX = "ready:"

        private val TIME_REGEX = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*s")
    }
}

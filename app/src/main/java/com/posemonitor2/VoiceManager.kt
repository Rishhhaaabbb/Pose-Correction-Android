// app/src/main/java/com/posemonitor2/VoiceManager.kt

package com.posemonitor2

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * High-Reliability Sequential Voice Engine.
 * 
 * Uses a Conflated Actor model to ensure:
 * 1. Only the MOST RECENT instruction is spoken (no backlog).
 * 2. Every sentence is spoken to completion (no cutoffs).
 * 3. Next API call starts ONLY when the speakers are silent.
 */
class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private val lock = Any()
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    
    @Volatile
    private var isInitialized = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val groqProvider = GroqFeedbackProvider(BuildConfig.GROQ_API_KEY)
    
    // Channel size 1 with DROP_OLDEST ensures we only process the LATEST relevant coaching tip
    private val speechChannel = Channel<SpeechRequest>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    
    private var lastSpokenText: String = ""
    private var lastFeedbackTime: Long = 0
    private var msgCounter = 0

    // Sequential signal binding
    private var activeUtteranceId: String? = null
    private var utteranceDeferred: CompletableDeferred<Unit>? = null

    data class SpeechRequest(
        val text: String,
        val isInstruction: Boolean,
        val useGroq: Boolean
    )

    init {
        // The Background Worker (Actor)
        scope.launch {
            for (request in speechChannel) {
                processSequentially(request)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val preferredLocales = listOf(Locale.getDefault(), Locale.UK, Locale.US).distinct()
            tts?.setLanguage(preferredLocales.firstOrNull { 
                val s = tts?.setLanguage(it) ?: TextToSpeech.LANG_NOT_SUPPORTED
                s != TextToSpeech.LANG_MISSING_DATA && s != TextToSpeech.LANG_NOT_SUPPORTED
            } ?: Locale.US)

            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("VoiceManager", "Audio playback started: $utteranceId")
                }
                
                override fun onDone(utteranceId: String?) {
                    synchronized(lock) {
                        if (utteranceId == activeUtteranceId) {
                            utteranceDeferred?.complete(Unit)
                            Log.d("VoiceManager", "Audio playback finished: $utteranceId")
                        }
                    }
                }
                
                override fun onError(utteranceId: String?) {
                    synchronized(lock) {
                        if (utteranceId == activeUtteranceId) utteranceDeferred?.complete(Unit)
                        Log.e("VoiceManager", "TTS Hardware Error: $utteranceId")
                    }
                }
            })

            isInitialized = true
            tts?.setSpeechRate(0.85f)
            Log.d("VoiceManager", "Conflated Actor Engine Ready")
        }
    }

    /**
     * Enqueues a coaching instruction. The engine will skip old feedback
     * and only process the newest one once it finishes the current sentence.
     */
    fun speak(text: String, isInstruction: Boolean = false, useGroq: Boolean = false) {
        if (!isInitialized || text.isBlank()) return
        
        // Cooldown to prevent repeating the EXACT same string too fast
        val now = System.currentTimeMillis()
        val cooldown = if (isInstruction) 3000L else 12000L
        if (text == lastSpokenText && (now - lastFeedbackTime) < cooldown) {
            return
        }

        // Send to sequential processor
        scope.launch {
            speechChannel.send(SpeechRequest(text, isInstruction, useGroq))
        }
    }

    private suspend fun processSequentially(request: SpeechRequest) {
        try {
            val isSystemWarning = request.text.contains("one person", ignoreCase = true) || 
                                 request.text.contains("visible", ignoreCase = true) ||
                                 request.text.contains("to begin", ignoreCase = true)

            // Step 1: Fetch from AI
            // We now allow Groq AI to handle system warnings for more natural coaching,
            // unless the user has requested direct feedback or AI is disabled.
            var spokenText = if (request.useGroq) {
                groqProvider.getDynamicFeedback(request.text)
            } else {
                request.text
            }

            // Ensure sentence completes fully for TTS
            if (!spokenText.trim().endsWith(".") && !spokenText.contains("!") && !spokenText.contains("?")) {
                spokenText = spokenText.trim() + "."
            }

            // Step 2: Bind ID and start audio
            val deferred = CompletableDeferred<Unit>()
            val msgId = "v_${++msgCounter}"
            
            synchronized(lock) {
                activeUtteranceId = msgId
                utteranceDeferred = deferred
                
                // Use QUEUE_ADD as a safety net. The Actor handles the logic, 
                // QUEUE_ADD ensures the hardware never stutters.
                tts?.speak(spokenText, TextToSpeech.QUEUE_ADD, null, msgId)
            }

            lastSpokenText = request.text
            lastFeedbackTime = System.currentTimeMillis()

            // Step 3: Wait for VERIFIED completion from hardware
            withTimeoutOrNull(30000L) { // 30s safety timeout
                deferred.await()
            }
            
            // Step 4: POST-SPEECH HARDWARE FLUSH
            // Extra second to ensure physical speakers are clear before next API call
            delay(1000)

        } catch (e: Exception) {
            Log.e("VoiceManager", "Sequential processing error", e)
        } finally {
            synchronized(lock) { utteranceDeferred = null }
        }
    }

    fun shutdown() {
        speechChannel.close()
        scope.cancel()
        synchronized(lock) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }
}

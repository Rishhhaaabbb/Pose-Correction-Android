package com.posemonitor2

import java.util.Locale

enum class SpeechSpeedMode {
    NORMAL,
    EXTRA_SLOW,
}

enum class SpeechLanguageOption(
    val displayName: String,
    val localeCandidates: List<Locale>,
) {
    ENGLISH(
        displayName = "English",
        localeCandidates = listOf(Locale.forLanguageTag("en-IN"), Locale.US, Locale.UK),
    ),
    HINDI(
        displayName = "Hindi",
        localeCandidates = listOf(
            Locale.forLanguageTag("hi-IN"),
            Locale.forLanguageTag("hi"),
            Locale.forLanguageTag("en-IN"),
        ),
    ),
    KANNADA(
        displayName = "Kannada",
        localeCandidates = listOf(
            Locale.forLanguageTag("kn-IN"),
            Locale.forLanguageTag("kn"),
            Locale.forLanguageTag("hi-IN"),
            Locale.forLanguageTag("en-IN"),
        ),
    ),
}

data class MonitorSpeechPreferences(
    val language: SpeechLanguageOption = SpeechLanguageOption.ENGLISH,
    val speedMode: SpeechSpeedMode = SpeechSpeedMode.NORMAL,
)

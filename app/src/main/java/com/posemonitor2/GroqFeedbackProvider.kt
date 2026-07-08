package com.posemonitor2

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 20,
)

@Serializable
data class GroqMessage(val role: String, val content: String)

@Serializable
data class GroqResponse(val choices: List<GroqChoice>)

@Serializable
data class GroqChoice(val message: GroqMessage)

/**
 * Wraps the Groq chat-completions endpoint.
 *
 * Only used for STATE-CHANGE INSTRUCTIONS — one call per pose transition.
 * Corrections are spoken directly via Android TTS (zero latency).
 */
class GroqFeedbackProvider(private val apiKey: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 4_000
            connectTimeoutMillis = 2_000
        }
    }

    /**
     * Rephrase a pose state instruction into a natural coaching cue.
     *
     * @param instruction  The raw instruction from the PoseState (e.g. "Lower into squat position").
     * @param exerciseName The current exercise (e.g. "chair-dip", "squat"). Used for context.
     * @return A short, natural coaching sentence. Falls back to [instruction] on any error.
     */
    suspend fun getInstruction(instruction: String, exerciseName: String = ""): String {
        if (apiKey.isBlank() || apiKey == "your_groq_api_key_here") return instruction

        val exerciseContext = if (exerciseName.isNotBlank()) {
            val readable = exerciseName.removeSuffix(".json").replace("-", " ")
            "The user is doing a $readable. "
        } else ""

        return try {
            Log.d(TAG, "Groq instruction → \"$instruction\" (exercise: $exerciseName)")

            val response: GroqResponse = client.post(ENDPOINT) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    GroqRequest(
                        messages = listOf(
                            GroqMessage(
                                role = "system",
                                content = """
                                    You are a concise personal fitness coach guiding a user through a live workout.
                                    ${exerciseContext}The user can see a ghost skeleton on their screen showing the target pose.
                                    You will receive the target pose instruction.

                                    Your job: speak it as a natural, encouraging coaching cue in ONE sentence (max 12 words).
                                    - Start with an action verb (e.g. "Lower", "Keep", "Extend", "Bring").
                                    - Be specific to the exercise if possible.
                                    - Do NOT say "ghost skeleton", "screen", "align", or technical jargon.
                                    - Sound like a real coach speaking aloud.

                                    Examples:
                                      Input:  "Extend arms forward at shoulder height"
                                      Output: "Raise both arms straight out in front of you"

                                      Input:  "Lower hips to seated position"
                                      Output: "Sit back and lower your hips like you're sitting in a chair"
                                """.trimIndent(),
                            ),
                            GroqMessage(role = "user", content = instruction),
                        ),
                    ),
                )
            }.body()

            val result = response.choices.firstOrNull()?.message?.content
                ?.trim()?.removeSuffix(".") ?: instruction
            Log.d(TAG, "Groq response → \"$result\"")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Groq error, using raw instruction", e)
            instruction
        }
    }

    companion object {
        private const val TAG      = "GroqFeedback"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    }
}

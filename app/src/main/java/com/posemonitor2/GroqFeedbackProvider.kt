package com.posemonitor2

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
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
    val max_tokens: Int = 30
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>
)

@Serializable
data class GroqChoice(
    val message: GroqMessage
)

class GroqFeedbackProvider(private val apiKey: String) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 3000
            connectTimeoutMillis = 2000
        }
    }

    suspend fun getDynamicFeedback(basicFeedback: String): String {
        if (apiKey.isBlank() || apiKey == "your_groq_api_key_here") {
            return basicFeedback
        }

        return try {
            Log.d("GroqFeedback", "Sending to AI: $basicFeedback")
            val response: GroqResponse = client.post("https://api.groq.com/openai/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    GroqRequest(
                        messages = listOf(
                            GroqMessage(
                                role = "system",
                                content = """
                                    You are an elite fitness coach. 
                                    The user is trying to align their body with a 'ghost skeleton' guide on their screen.
                                    You will receive a descriptive observation about their current pose deviation.
                                    
                                    Your Task: 
                                    Provide a SHARP, SHORT (max 4 words), and ACTIONABLE command that tells them exactly how to adjust to match the ghost.
                                    
                                    RULES:
                                    1. Strictly 1 to 4 words.
                                    2. Focus only on the action (e.g., 'Bend your knee', 'Hips down', 'Arms higher').
                                    3. Do not use 'Status', 'Action', or technical jargon.
                                    4. Be direct and authoritative but encouraging.
                                    5. Preserve 'left' or 'right' if the input mentions them.
                                """.trimIndent()
                            ),
                            GroqMessage(
                                role = "user",
                                content = basicFeedback
                            )
                        )
                    )
                )
            }.body()

            val finalResult = response.choices.firstOrNull()?.message?.content?.trim()?.removeSuffix(".") ?: basicFeedback
            Log.d("GroqFeedback", "AI Response: $finalResult")
            finalResult
        } catch (e: Exception) {
            Log.e("GroqFeedback", "Error calling Groq API", e)
            basicFeedback
        }
    }
}

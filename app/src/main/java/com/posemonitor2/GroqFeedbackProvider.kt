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
    val max_tokens: Int = 50
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
            requestTimeoutMillis = 5000
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
                                    You will receive a descriptive observation about their current pose deviation, or a system status message.
                                    
                                    Your Task: 
                                    Provide a SHARP, ACTIONABLE coaching command (max 10 words).
                                    
                                    CATEGORIES & RULES:
                                    1. NO PERSON: If the system says "Step into the frame to begin", tell the user to enter the camera view so you can see them.
                                    2. PARTIAL BODY: If the system says "You are only partially visible", tell the user to step back or adjust so their full body (head to toe) is seen.
                                    3. MULTIPLE PEOPLE: If the system says "Multiple people detected", tell the user exactly: "Only one person should be in the frame".
                                    4. POSE CORRECTION: For descriptive deviations (e.g., 'left knee too straight'), give a coaching command (e.g., 'Bend your left knee more').
                                    
                                    STRICT RULES:
                                    - Strictly 1 to 10 words.
                                    - Be direct, authoritative, and encouraging.
                                    - Do not use technical jargon or 'Status/Action'.
                                    - Preserve 'left' or 'right' if mentioned.
                                    - Ensure the command is actionable.
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

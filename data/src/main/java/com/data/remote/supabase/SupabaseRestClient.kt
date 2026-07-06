package com.data.remote.supabase

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SupabaseRestClient(
    private val httpClient: HttpClient,
    private val supabaseUrl: String,
    private val anonKey: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val resolvedSupabaseUrl: String = supabaseUrl
        .trim()
        .trimEnd('/')
        .replace("http://localhost", "http://10.0.2.2")
        .replace("http://127.0.0.1", "http://10.0.2.2")

    private fun requireAuthConfig() {
        if (resolvedSupabaseUrl.isBlank()) {
            throw IllegalStateException(
                "Supabase URL is not configured. Add SUPABASE_URL_DEBUG and SUPABASE_ANON_KEY_DEBUG in local.properties.",
            )
        }
        if (anonKey.isBlank()) {
            throw IllegalStateException(
                "Supabase anon key is not configured. Add SUPABASE_ANON_KEY_DEBUG in local.properties.",
            )
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): SupabaseAuthResponse {
        requireAuthConfig()
        val response = httpClient.post("$resolvedSupabaseUrl/auth/v1/token") {
            parameter("grant_type", "id_token")
            header("apikey", anonKey)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(SupabaseIdTokenRequest(provider = "google", id_token = idToken))
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Supabase Google auth failed (${response.status.value}): $raw")
        }

        return try {
            json.decodeFromString<SupabaseAuthResponse>(raw)
        } catch (err: SerializationException) {
            throw IllegalStateException("Supabase Google auth returned unexpected payload: $raw", err)
        }
    }

    suspend fun requestPhoneOtp(phoneE164: String) {
        requireAuthConfig()
        val response = httpClient.post("$resolvedSupabaseUrl/auth/v1/otp") {
            header("apikey", anonKey)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(SupabasePhoneOtpRequest(phone = phoneE164))
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Supabase phone OTP request failed (${response.status.value}): $raw")
        }
    }

    suspend fun verifyPhoneOtp(phoneE164: String, otpCode: String): SupabaseAuthResponse {
        requireAuthConfig()
        val response = httpClient.post("$resolvedSupabaseUrl/auth/v1/verify") {
            header("apikey", anonKey)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(
                SupabaseVerifyPhoneOtpRequest(
                    phone = phoneE164,
                    token = otpCode,
                )
            )
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Supabase phone OTP verify failed (${response.status.value}): $raw")
        }

        return try {
            json.decodeFromString<SupabaseAuthResponse>(raw)
        } catch (err: SerializationException) {
            throw IllegalStateException("Supabase phone OTP verify returned unexpected payload: $raw", err)
        }
    }

    suspend fun refreshAccessToken(refreshToken: String): SupabaseAuthResponse {
        requireAuthConfig()
        return httpClient.post("$resolvedSupabaseUrl/auth/v1/token") {
            parameter("grant_type", "refresh_token")
            header("apikey", anonKey)
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(SupabaseRefreshRequest(refresh_token = refreshToken))
        }.body()
    }

    suspend fun getUser(accessToken: String): SupabaseUserDto {
        requireAuthConfig()
        return httpClient.get("$resolvedSupabaseUrl/auth/v1/user") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            accept(ContentType.Application.Json)
        }.body()
    }

    suspend fun fetchProfile(userId: String, accessToken: String): SupabaseProfileDto? {
        requireAuthConfig()
        val items = httpClient.get("$resolvedSupabaseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("id", "eq.$userId")
            parameter("select", "*")
            parameter("limit", "1")
            accept(ContentType.Application.Json)
        }.body<List<SupabaseProfileDto>>()

        return items.firstOrNull()
    }

    suspend fun upsertProfile(profileDto: SupabaseProfileDto, accessToken: String) {
        requireAuthConfig()
        httpClient.post("$resolvedSupabaseUrl/rest/v1/profiles") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates")
            parameter("on_conflict", "id")
            contentType(ContentType.Application.Json)
            setBody(listOf(profileDto))
        }
    }

    suspend fun insertSession(sessionDto: SupabaseSessionDto, accessToken: String) {
        requireAuthConfig()
        httpClient.post("$resolvedSupabaseUrl/rest/v1/sessions") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(listOf(sessionDto))
        }
    }

    suspend fun upsertSessionMetric(metricDto: SupabaseSessionMetricDto, accessToken: String) {
        requireAuthConfig()
        httpClient.post("$resolvedSupabaseUrl/rest/v1/session_metrics") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates")
            parameter("on_conflict", "id")
            contentType(ContentType.Application.Json)
            setBody(listOf(metricDto))
        }
    }

    suspend fun upsertPoseGraph(graphDto: SupabasePoseGraphDto, accessToken: String) {
        requireAuthConfig()
        httpClient.post("$resolvedSupabaseUrl/rest/v1/pose_graphs") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates")
            parameter("on_conflict", "id")
            contentType(ContentType.Application.Json)
            setBody(listOf(graphDto))
        }
    }

    suspend fun upsertDailyStat(statDto: SupabaseDailyStatDto, accessToken: String) {
        requireAuthConfig()
        httpClient.post("$resolvedSupabaseUrl/rest/v1/daily_stats") {
            header("apikey", anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header("Prefer", "resolution=merge-duplicates")
            parameter("on_conflict", "id")
            contentType(ContentType.Application.Json)
            setBody(listOf(statDto))
        }
    }
}

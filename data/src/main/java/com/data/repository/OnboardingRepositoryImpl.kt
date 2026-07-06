package com.data.repository

import com.data.local.dao.OnboardingResponseDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.SyncQueueEntity
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.repository.mapper.toDomain
import com.data.repository.mapper.toEntity
import com.data.repository.mapper.toFirestoreDto
import com.domain.model.OnboardingResponse
import com.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepositoryImpl @Inject constructor(
    private val onboardingResponseDao: OnboardingResponseDao,
    private val syncQueueDao: SyncQueueDao,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val json: Json,
) : OnboardingRepository {

    override suspend fun saveAnswers(answers: OnboardingResponse): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val answersWithTimestamp = answers.copy(updatedAtEpochMs = now)
        onboardingResponseDao.upsert(answersWithTimestamp.toEntity())

        runCatching {
            firebaseRemoteClient.upsertOnboardingResponse(answersWithTimestamp.toFirestoreDto())
        }.onFailure { err ->
            enqueueOnboardingUpsert(answersWithTimestamp, err.message ?: "Remote onboarding upsert failed")
        }
    }

    override suspend fun getAnswers(userId: String): OnboardingResponse? {
        return onboardingResponseDao.getByUserId(userId)?.toDomain()
    }

    override fun observeAnswers(userId: String): Flow<OnboardingResponse?> {
        return onboardingResponseDao.observeByUserId(userId).map { it?.toDomain() }
    }

    override suspend fun isCompleted(userId: String): Boolean {
        // Check local DB first
        val localResult = onboardingResponseDao.isCompleted(userId)
        if (localResult != null) {
            return localResult == 1
        }
        // No local record ΓÇö fall back to Firestore
        return runCatching {
            val remoteDto = firebaseRemoteClient.fetchOnboardingResponse(userId)
            if (remoteDto != null && remoteDto.is_completed) {
                // Save remote record locally for offline use
                onboardingResponseDao.upsert(remoteDto.toEntity())
                true
            } else {
                false
            }
        }.getOrElse { false }  // On any error, treat as not completed (fail-safe)
    }

    private suspend fun enqueueOnboardingUpsert(answers: OnboardingResponse, reason: String) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = "onboarding_responses",
                entityId = answers.userId,
                operation = "UPSERT",
                payload = json.encodeToString(answers.toFirestoreDto()),
                lastError = reason,
                createdAtEpochMs = answers.updatedAtEpochMs,
            )
        )
    }
}
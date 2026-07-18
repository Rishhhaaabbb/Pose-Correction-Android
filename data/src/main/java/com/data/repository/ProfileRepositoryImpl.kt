package com.data.repository

import com.data.local.dao.ProfileDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.SyncQueueEntity
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.repository.mapper.toDomain
import com.data.repository.mapper.toEntity
import com.data.repository.mapper.toFirestoreDto
import com.domain.model.UserProfile
import com.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val syncQueueDao: SyncQueueDao,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val json: Json,
) : ProfileRepository {

    override fun observeProfile(userId: String): Flow<UserProfile?> {
        return profileDao.observeProfile(userId).map { it?.toDomain() }
    }

    override suspend fun refreshProfile(userId: String): Result<Unit> = runCatching {
        val remoteProfile = firebaseRemoteClient.fetchProfile(userId)
        if (remoteProfile != null) {
            profileDao.upsert(remoteProfile.toEntity())
        }
    }

    override suspend fun upsertProfile(profile: UserProfile): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        profileDao.upsert(profile.toEntity(now))

        runCatching {
            firebaseRemoteClient.upsertProfile(profile.toFirestoreDto(now))
        }.onFailure { err ->
            enqueueProfileUpsert(profile, now, err.message ?: "Remote profile upsert failed")
        }
    }

    override suspend fun patchProfileFields(userId: String, fields: Map<String, Any?>): Result<Unit> =
        runCatching {
            val now = System.currentTimeMillis()

            val existing = profileDao.getProfile(userId)
            if (existing != null) {
                profileDao.patchQuestionnaireFields(
                    userId = userId,
                    heightCm = fields["height_cm"] as? Int ?: existing.heightCm,
                    weightKg = fields["weight_kg"] as? Double ?: existing.weightKg,
                    fitnessGoal = fields["fitness_goal"] as? String ?: existing.fitnessGoal,
                    ageGroup = fields["age_group"] as? String ?: existing.ageGroup,
                    gender = fields["gender"] as? String ?: existing.gender,
                    experienceLevel = fields["experience_level"] as? String ?: existing.experienceLevel,
                    focusAreas = fields["focus_areas"] as? String ?: existing.focusAreas,
                    updatedAt = now,
                )
            }

            runCatching {
                firebaseRemoteClient.patchProfileFields(userId, fields)
            }
        }

    private suspend fun enqueueProfileUpsert(profile: UserProfile, now: Long, reason: String) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = "profiles",
                entityId = profile.id,
                operation = "UPSERT",
                payload = json.encodeToString(profile.toFirestoreDto(now)),
                lastError = reason,
                createdAtEpochMs = now,
            )
        )
    }
}


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


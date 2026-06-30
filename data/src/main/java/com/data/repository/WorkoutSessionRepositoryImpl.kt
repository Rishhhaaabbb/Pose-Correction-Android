package com.data.repository

import com.data.local.dao.SessionDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.SyncQueueEntity
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.repository.mapper.toDomain
import com.data.repository.mapper.toEntity
import com.data.repository.mapper.toFirestoreDto
import com.domain.model.WorkoutSession
import com.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutSessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val syncQueueDao: SyncQueueDao,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val json: Json,
) : WorkoutSessionRepository {
    override fun observeSessions(userId: String, limit: Int): Flow<List<WorkoutSession>> {
        return sessionDao.observeSessions(userId, limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getSessionsInLast24Hours(userId: String): List<WorkoutSession> {
        val since = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        return sessionDao.getSessionsSince(userId, since).map { it.toDomain() }
    }

    override suspend fun saveSession(session: WorkoutSession): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        sessionDao.upsert(session.toEntity(now))

        runCatching {
            firebaseRemoteClient.insertSession(session.toFirestoreDto(now))
        }.onFailure { err ->
            enqueueSessionUpsert(session, now, err.message ?: "Remote session insert failed")
        }
    }

    private suspend fun enqueueSessionUpsert(session: WorkoutSession, now: Long, reason: String) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = "sessions",
                entityId = session.id,
                operation = "UPSERT",
                payload = json.encodeToString(session.toFirestoreDto(now)),
                lastError = reason,
                createdAtEpochMs = now,
            )
        )
    }
}


package com.data.repository

import com.data.local.dao.SessionMetricDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.SyncQueueEntity
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.repository.mapper.toDomain
import com.data.repository.mapper.toEntity
import com.data.repository.mapper.toFirestoreDto
import com.domain.model.SessionMetric
import com.domain.repository.SessionMetricsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionMetricsRepositoryImpl @Inject constructor(
    private val sessionMetricDao: SessionMetricDao,
    private val syncQueueDao: SyncQueueDao,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val json: Json,
) : SessionMetricsRepository {
    override fun observeSessionMetrics(userId: String, limit: Int): Flow<List<SessionMetric>> {
        return sessionMetricDao.observeMetrics(userId, limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveSessionMetric(metric: SessionMetric): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        sessionMetricDao.upsert(metric.toEntity(now))

        runCatching {
            firebaseRemoteClient.upsertSessionMetric(metric.toFirestoreDto(now))
        }.onFailure { err ->
            enqueue(metric, now, err.message ?: "Remote metric upsert failed")
        }
    }

    private suspend fun enqueue(metric: SessionMetric, now: Long, reason: String) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = "session_metrics",
                entityId = metric.id,
                operation = "UPSERT",
                payload = json.encodeToString(metric.toFirestoreDto(now)),
                lastError = reason,
                createdAtEpochMs = now,
            )
        )
    }
}

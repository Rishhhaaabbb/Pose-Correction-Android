package com.data.repository

import com.data.local.dao.DailyStatDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.SyncQueueEntity
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.repository.mapper.toDomain
import com.data.repository.mapper.toEntity
import com.data.repository.mapper.toFirestoreDto
import com.domain.model.DailyStat
import com.domain.repository.DailyStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyStatsRepositoryImpl @Inject constructor(
    private val dailyStatDao: DailyStatDao,
    private val syncQueueDao: SyncQueueDao,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val json: Json,
) : DailyStatsRepository {
    override fun observeDailyStats(userId: String, limit: Int): Flow<List<DailyStat>> {
        return dailyStatDao.observeDailyStats(userId, limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun upsertDailyStat(stat: DailyStat): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        dailyStatDao.upsert(stat.toEntity(now))

        runCatching {
            firebaseRemoteClient.upsertDailyStat(stat.toFirestoreDto(now))
        }.onFailure { err ->
            enqueue(stat, now, err.message ?: "Remote daily stat upsert failed")
        }
    }

    private suspend fun enqueue(stat: DailyStat, now: Long, reason: String) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = "daily_stats",
                entityId = stat.id,
                operation = "UPSERT",
                payload = json.encodeToString(stat.toFirestoreDto(now)),
                lastError = reason,
                createdAtEpochMs = now,
            )
        )
    }
}

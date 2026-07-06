package com.data.repository

import com.data.local.dao.PoseGraphDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.SyncQueueEntity
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.repository.mapper.toDomain
import com.data.repository.mapper.toEntity
import com.data.repository.mapper.toFirestoreDto
import com.domain.model.PoseGraphRecord
import com.domain.repository.PoseGraphRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoseGraphRepositoryImpl @Inject constructor(
    private val poseGraphDao: PoseGraphDao,
    private val syncQueueDao: SyncQueueDao,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val json: Json,
) : PoseGraphRepository {
    override fun observePoseGraphs(userId: String, limit: Int): Flow<List<PoseGraphRecord>> {
        return poseGraphDao.observePoseGraphs(userId, limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun savePoseGraph(graph: PoseGraphRecord): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        poseGraphDao.upsert(graph.toEntity(now))

        runCatching {
            firebaseRemoteClient.upsertPoseGraph(graph.toFirestoreDto(now))
        }.onFailure { err ->
            enqueue(graph, now, err.message ?: "Remote pose graph upsert failed")
        }
    }

    private suspend fun enqueue(graph: PoseGraphRecord, now: Long, reason: String) {
        syncQueueDao.insert(
            SyncQueueEntity(
                entityType = "pose_graphs",
                entityId = graph.id,
                operation = "UPSERT",
                payload = json.encodeToString(graph.toFirestoreDto(now)),
                lastError = reason,
                createdAtEpochMs = now,
            )
        )
    }
}


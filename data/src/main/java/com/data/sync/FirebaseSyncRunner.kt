package com.data.sync

import com.data.local.dao.SyncQueueDao
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.remote.firebase.FirestoreDailyStatDto
import com.data.remote.firebase.FirestorePoseGraphDto
import com.data.remote.firebase.FirestoreProfileDto
import com.data.remote.firebase.FirestoreSessionDto
import com.data.remote.firebase.FirestoreSessionMetricDto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncRunner @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val json: Json,
) {
    suspend fun syncPending() {
        // Only sync if a Firebase user is signed in
        if (FirebaseAuth.getInstance().currentUser == null) return

        val items = syncQueueDao.getAll()
        items.forEach { item ->
            runCatching {
                when (item.entityType) {
                    "profiles" -> {
                        val dto = json.decodeFromString<FirestoreProfileDto>(item.payload)
                        firebaseRemoteClient.upsertProfile(dto)
                    }
                    "sessions" -> {
                        val dto = json.decodeFromString<FirestoreSessionDto>(item.payload)
                        firebaseRemoteClient.insertSession(dto)
                    }
                    "session_metrics" -> {
                        val dto = json.decodeFromString<FirestoreSessionMetricDto>(item.payload)
                        firebaseRemoteClient.upsertSessionMetric(dto)
                    }
                    "pose_graphs" -> {
                        val dto = json.decodeFromString<FirestorePoseGraphDto>(item.payload)
                        firebaseRemoteClient.upsertPoseGraph(dto)
                    }
                    "daily_stats" -> {
                        val dto = json.decodeFromString<FirestoreDailyStatDto>(item.payload)
                        firebaseRemoteClient.upsertDailyStat(dto)
                    }
                }
                syncQueueDao.deleteById(item.id)
            }.onFailure { err ->
                syncQueueDao.markFailed(item.id, err.message ?: "Sync failed")
            }
        }
    }
}

package com.data.sync

import android.util.Log
import com.data.local.dao.SyncQueueDao
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.remote.firebase.FirestoreDailyStatDto
import com.data.remote.firebase.FirestoreOnboardingDto
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
        if (FirebaseAuth.getInstance().currentUser == null) {
            Log.d(TAG, "syncPending: skipping — no authenticated Firebase user")
            return
        }

        val items = syncQueueDao.getAll()
        Log.d(TAG, "syncPending: found ${items.size} pending item(s)")

        items.forEach { item ->
            Log.d(TAG, "syncPending: processing type=${item.entityType} id=${item.entityId}")
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
                    "onboarding_responses" -> {
                        val dto = json.decodeFromString<FirestoreOnboardingDto>(item.payload)
                        firebaseRemoteClient.upsertOnboardingResponse(dto)
                    }
                    else -> Log.w(TAG, "syncPending: unknown entityType=${item.entityType}, skipping")
                }
                syncQueueDao.deleteById(item.id)
                Log.i(TAG, "syncPending: synced and removed — type=${item.entityType} id=${item.entityId}")
            }.onFailure { err ->
                Log.e(TAG, "syncPending: failed type=${item.entityType} id=${item.entityId}: ${err.message}", err)
                syncQueueDao.markFailed(item.id, err.message ?: "Sync failed")
            }
        }

        Log.d(TAG, "syncPending: done")
    }

    companion object {
        private const val TAG = "FirebaseSyncRunner"
    }
}

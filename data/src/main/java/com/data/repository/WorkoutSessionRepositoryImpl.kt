package com.data.repository

import android.util.Log
import com.data.local.dao.SessionDao
import com.data.local.dao.SyncQueueDao
import com.data.local.entity.SyncQueueEntity
import com.data.remote.firebase.FirebaseRemoteClient
import com.data.remote.firebase.UnauthenticatedException
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

    override fun observeSessions(userId: String, limit: Int): Flow<List<WorkoutSession>> =
        sessionDao.observeSessions(userId, limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getSessionsInLast24Hours(userId: String): List<WorkoutSession> {
        val since = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        return sessionDao.getSessionsSince(userId, since).map { it.toDomain() }
    }

    override suspend fun saveSession(session: WorkoutSession): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        sessionDao.upsert(session.toEntity(now))
        Log.d(TAG, "saveSession: saved to Room — id=${session.id}")

        try {
            firebaseRemoteClient.insertSession(session.toFirestoreDto(now))
            Log.i(TAG, "saveSession: Firestore write succeeded — id=${session.id}")
        } catch (e: UnauthenticatedException) {
            Log.w(TAG, "saveSession: skipping sync — no Firebase auth (admin session). id=${session.id}")
        } catch (e: Exception) {
            val reason = e.message ?: "Remote session insert failed"
            Log.e(TAG, "saveSession: Firestore failed, queuing — id=${session.id} reason=$reason", e)
            syncQueueDao.insert(SyncQueueEntity(
                entityType = "sessions",
                entityId = session.id,
                operation = "UPSERT",
                payload = json.encodeToString(session.toFirestoreDto(now)),
                lastError = reason,
                createdAtEpochMs = now,
            ))
            scheduleSync()
        }
    }

    private fun scheduleSync() {
        try {
            // Get application context via ActivityThread — no direct dependency needed
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApp = activityThreadClass.getMethod("currentApplication").invoke(null)
                as? android.content.Context ?: return
            val cls = Class.forName("com.posemonitor2.sync.SyncWorker")
            val method = cls.getDeclaredMethod("scheduleNetworkConstrainedSync", android.content.Context::class.java)
            method.invoke(null, currentApp)
        } catch (e: Exception) {
            Log.w(TAG, "scheduleSync: could not schedule WorkManager job: ${e.message}")
        }
    }

    companion object { private const val TAG = "WorkoutSessionRepo" }
}

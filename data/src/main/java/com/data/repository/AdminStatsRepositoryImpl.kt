package com.data.repository

import com.domain.model.AdminStats
import com.domain.repository.AdminStatsRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminStatsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AdminStatsRepository {

    override suspend fun fetchStats(): AdminStats = try {
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)

        // 1. Total Users
        val profilesSnapshot = firestore.collection("profiles").get().await()
        val totalUsers = profilesSnapshot.size()

        // 2. Active users in last 7 days (unique user IDs)
        val weekSnapshot = firestore.collection("workout_sessions")
            .whereGreaterThan("ended_at", sevenDaysAgo)
            .get().await()
        val activeUserIds = weekSnapshot.documents.mapNotNull { doc ->
            doc.getString("user_id")
        }.toSet()
        val activeThisWeek = activeUserIds.size

        // 3. New members this week
        val newMembersSnapshot = firestore.collection("profiles")
            .whereGreaterThan("created_at", sevenDaysAgo)
            .get().await()
        val newMembers = newMembersSnapshot.size()

        // 4. Weekly growth data: sessions per day over last 7 days
        val dayMillis = 24L * 60 * 60 * 1000
        val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % dayMillis)
        val growthData = (6 downTo 0).map { daysAgo ->
            val dayStart = todayStart - (daysAgo * dayMillis)
            val dayEnd = dayStart + dayMillis
            val daySessions = weekSnapshot.documents.count { doc ->
                val endedAt = doc.getLong("ended_at") ?: 0L
                endedAt in dayStart until dayEnd
            }
            daySessions.toFloat()
        }
        val peak = growthData.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val normalizedGrowth = growthData.map { it / peak }

        AdminStats(
            totalUsers = totalUsers,
            newMembers = newMembers,
            liveUsers = activeThisWeek,
            growthData = normalizedGrowth,
        )
    } catch (_: Exception) {
        AdminStats()
    }
}

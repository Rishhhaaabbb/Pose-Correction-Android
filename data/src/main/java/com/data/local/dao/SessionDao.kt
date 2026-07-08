package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE user_id = :userId ORDER BY started_at DESC LIMIT :limit")
    fun observeSessions(userId: String, limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE user_id = :userId AND started_at >= :sinceEpochMs ORDER BY started_at DESC")
    suspend fun getSessionsSince(userId: String, sinceEpochMs: Long): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)
}
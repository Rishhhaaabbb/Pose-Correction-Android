package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.SessionMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionMetricDao {
    @Query("SELECT * FROM session_metrics WHERE user_id = :userId ORDER BY updated_at DESC LIMIT :limit")
    fun observeMetrics(userId: String, limit: Int): Flow<List<SessionMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: SessionMetricEntity)
}

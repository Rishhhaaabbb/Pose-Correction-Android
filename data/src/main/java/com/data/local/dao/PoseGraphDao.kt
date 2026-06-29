package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.PoseGraphEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoseGraphDao {
    @Query("SELECT * FROM pose_graphs WHERE user_id = :userId ORDER BY updated_at DESC LIMIT :limit")
    fun observePoseGraphs(userId: String, limit: Int): Flow<List<PoseGraphEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(graph: PoseGraphEntity)
}

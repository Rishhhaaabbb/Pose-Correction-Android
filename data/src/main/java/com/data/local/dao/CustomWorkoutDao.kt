package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.CustomWorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomWorkoutDao {
    @Query("SELECT * FROM custom_workouts ORDER BY is_default DESC, created_at DESC")
    fun observeAll(): Flow<List<CustomWorkoutEntity>>

    @Query("SELECT * FROM custom_workouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomWorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: CustomWorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(workouts: List<CustomWorkoutEntity>)

    @Query("DELETE FROM custom_workouts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM custom_workouts WHERE is_default = 0")
    suspend fun countNonDefault(): Int
}

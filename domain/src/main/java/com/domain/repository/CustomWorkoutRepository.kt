package com.domain.repository

import com.domain.model.CustomWorkoutRecord
import kotlinx.coroutines.flow.Flow

interface CustomWorkoutRepository {
    fun observeAll(): Flow<List<CustomWorkoutRecord>>
    suspend fun getById(id: String): CustomWorkoutRecord?
    suspend fun upsert(record: CustomWorkoutRecord)
    suspend fun deleteById(id: String)
    suspend fun countNonDefault(): Int
}

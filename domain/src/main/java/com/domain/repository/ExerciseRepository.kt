package com.domain.repository

import com.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeAll(): Flow<List<Exercise>>
    suspend fun getAll(): List<Exercise>
    suspend fun getById(id: String): Exercise?
    suspend fun getByName(name: String): Exercise?
    suspend fun getByCategory(category: String): List<Exercise>
    suspend fun getByDifficulty(difficulty: String): List<Exercise>
    suspend fun save(exercises: List<Exercise>)
    suspend fun getCount(): Int
}
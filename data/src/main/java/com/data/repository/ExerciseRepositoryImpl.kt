package com.data.repository

import com.data.local.dao.ExerciseDao
import com.data.repository.mapper.toDomain
import com.data.repository.mapper.toEntity
import com.domain.model.Exercise
import com.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {

    override fun observeAll(): Flow<List<Exercise>> {
        return exerciseDao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAll(): List<Exercise> {
        return exerciseDao.getAll().map { it.toDomain() }
    }

    override suspend fun getById(id: String): Exercise? {
        return exerciseDao.getById(id)?.toDomain()
    }

    override suspend fun getByName(name: String): Exercise? {
        return exerciseDao.getByName(name)?.toDomain()
    }

    override suspend fun getByCategory(category: String): List<Exercise> {
        return exerciseDao.getByCategory(category).map { it.toDomain() }
    }

    override suspend fun getByDifficulty(difficulty: String): List<Exercise> {
        return exerciseDao.getByDifficulty(difficulty).map { it.toDomain() }
    }

    override suspend fun save(exercises: List<Exercise>) {
        exerciseDao.insertAll(exercises.map { it.toEntity() })
    }

    override suspend fun deleteById(id: String) {
        exerciseDao.deleteById(id)
    }

    override suspend fun getCount(): Int {
        return exerciseDao.getCount()
    }
}
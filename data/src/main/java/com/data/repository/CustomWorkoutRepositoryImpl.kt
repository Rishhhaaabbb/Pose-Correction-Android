package com.data.repository

import com.data.local.dao.CustomWorkoutDao
import com.data.local.entity.CustomWorkoutEntity
import com.domain.model.CustomWorkoutRecord
import com.domain.repository.CustomWorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomWorkoutRepositoryImpl @Inject constructor(
    private val customWorkoutDao: CustomWorkoutDao,
) : CustomWorkoutRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAll(): Flow<List<CustomWorkoutRecord>> =
        customWorkoutDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String): CustomWorkoutRecord? =
        customWorkoutDao.getById(id)?.toDomain()

    override suspend fun upsert(record: CustomWorkoutRecord) =
        customWorkoutDao.upsert(record.toEntity())

    override suspend fun deleteById(id: String) =
        customWorkoutDao.deleteById(id)

    override suspend fun countNonDefault(): Int =
        customWorkoutDao.countNonDefault()

    private fun CustomWorkoutEntity.toDomain() = CustomWorkoutRecord(
        id = id,
        title = title,
        exerciseIds = json.decodeFromString(exerciseIds),
        exerciseTargetReps = json.decodeFromString(exerciseTargetReps),
        isDefault = isDefault,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun CustomWorkoutRecord.toEntity() = CustomWorkoutEntity(
        id = id,
        title = title,
        exerciseIds = json.encodeToString(exerciseIds),
        exerciseTargetReps = json.encodeToString(exerciseTargetReps),
        isDefault = isDefault,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

package com.data.repository.mapper

import com.data.local.entity.ExerciseEntity
import org.json.JSONArray

fun ExerciseEntity.toDomain() = com.domain.model.Exercise(
    id = id,
    name = name,
    category = category,
    difficulty = difficulty,
    description = description,
    caloriesPerRep = caloriesPerRep,
    thumbnailUrl = thumbnailUrl,
    safeForInjuries = parseStringList(safeForInjuries),
    contraindicatedFor = parseStringList(contraindicatedFor),
)

fun com.domain.model.Exercise.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    category = category,
    difficulty = difficulty,
    description = description,
    caloriesPerRep = caloriesPerRep,
    thumbnailUrl = thumbnailUrl,
    safeForInjuries = JSONArray(safeForInjuries).toString(),
    contraindicatedFor = JSONArray(contraindicatedFor).toString(),
)

private fun parseStringList(json: String): List<String> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}
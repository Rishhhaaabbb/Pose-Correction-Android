package com.data.repository.mapper

import com.data.local.entity.ExerciseEntity

fun ExerciseEntity.toDomain() = com.domain.model.Exercise(
    id = id,
    name = name,
    category = category,
    difficulty = difficulty,
    description = description,
    caloriesPerRep = caloriesPerRep,
    thumbnailUrl = thumbnailUrl,
)

fun com.domain.model.Exercise.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    category = category,
    difficulty = difficulty,
    description = description,
    caloriesPerRep = caloriesPerRep,
    thumbnailUrl = thumbnailUrl,
)
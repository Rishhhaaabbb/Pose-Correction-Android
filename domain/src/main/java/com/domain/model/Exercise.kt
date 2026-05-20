package com.domain.model

data class Exercise(
    val id: String,
    val name: String,
    val category: String?,
    val difficulty: String?,
    val description: String?,
    val caloriesPerRep: Double,
    val thumbnailUrl: String?,
)
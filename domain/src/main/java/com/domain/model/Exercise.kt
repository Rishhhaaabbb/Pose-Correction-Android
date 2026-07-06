package com.domain.model

data class Exercise(
    val id: String,
    val name: String,
    val category: String?,
    val difficulty: String?,
    val description: String?,
    val caloriesPerRep: Double,
    val thumbnailUrl: String?,
    // Personalization metadata ΓÇö not stored in Room; populated at the domain layer
    // or via ExerciseRepository enrichment. Defaults are empty so existing
    // call sites (mapper, seeder, GraphGenerationViewModel) require no changes.
    val safeForInjuries: List<String> = emptyList(),
    val contraindicatedFor: List<String> = emptyList(),
    val yogaStyle: String? = null,
)
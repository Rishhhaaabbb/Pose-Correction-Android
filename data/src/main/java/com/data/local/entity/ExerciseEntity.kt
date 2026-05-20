package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String?,
    val difficulty: String?,
    val description: String?,
    @ColumnInfo(name = "calories_per_rep")
    val caloriesPerRep: Double,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String?,
)
package com.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pose_graphs")
data class PoseGraphEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "graph_name")
    val graphName: String,
    @ColumnInfo(name = "storage_path")
    val storagePath: String,
    @ColumnInfo(name = "metadata_json")
    val metadataJson: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)

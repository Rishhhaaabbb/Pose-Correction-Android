package com.domain.model

data class PoseGraphRecord(
    val id: String,
    val userId: String,
    val graphName: String,
    val storagePath: String,
    val metadataJson: String? = null,
)

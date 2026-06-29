package com.domain.model

data class SessionMetric(
    val id: String,
    val sessionId: String,
    val userId: String,
    val metricName: String,
    val metricValue: Double,
)

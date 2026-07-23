package com.domain.model

data class AdminStats(
    val totalUsers: Int = 0,
    val newMembers: Int = 0,
    val liveUsers: Int = 0,
    val growthData: List<Float> = emptyList(),
)

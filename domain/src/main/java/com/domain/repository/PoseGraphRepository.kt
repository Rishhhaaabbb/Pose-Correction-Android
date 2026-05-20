package com.domain.repository

import com.domain.model.PoseGraphRecord
import kotlinx.coroutines.flow.Flow

interface PoseGraphRepository {
    fun observePoseGraphs(userId: String, limit: Int): Flow<List<PoseGraphRecord>>

    suspend fun savePoseGraph(graph: PoseGraphRecord): Result<Unit>
}

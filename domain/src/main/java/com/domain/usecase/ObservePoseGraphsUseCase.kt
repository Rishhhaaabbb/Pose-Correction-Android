package com.domain.usecase

import com.domain.model.PoseGraphRecord
import com.domain.repository.PoseGraphRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePoseGraphsUseCase @Inject constructor(
    private val poseGraphRepository: PoseGraphRepository,
) {
    operator fun invoke(userId: String, limit: Int = 20): Flow<List<PoseGraphRecord>> =
        poseGraphRepository.observePoseGraphs(userId, limit)
}

package com.domain.usecase

import com.domain.model.PoseGraphRecord
import com.domain.repository.PoseGraphRepository
import javax.inject.Inject

class SavePoseGraphUseCase @Inject constructor(
    private val poseGraphRepository: PoseGraphRepository,
) {
    suspend operator fun invoke(graph: PoseGraphRecord): Result<Unit> =
        poseGraphRepository.savePoseGraph(graph)
}

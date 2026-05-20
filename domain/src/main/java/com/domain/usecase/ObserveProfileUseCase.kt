package com.domain.usecase

import com.domain.model.UserProfile
import com.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    operator fun invoke(userId: String): Flow<UserProfile?> = profileRepository.observeProfile(userId)
}

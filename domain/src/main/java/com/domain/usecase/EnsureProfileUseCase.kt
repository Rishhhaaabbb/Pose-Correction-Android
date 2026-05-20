package com.domain.usecase

import com.domain.model.UserProfile
import com.domain.repository.ProfileRepository
import javax.inject.Inject

class EnsureProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(profile: UserProfile): Result<Unit> =
        profileRepository.upsertProfile(profile)
}

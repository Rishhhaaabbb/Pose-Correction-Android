package com.domain.usecase

import com.domain.repository.ProfileRepository
import javax.inject.Inject

class FetchUserRoleUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    /** Returns the user's role string (e.g. "admin") or null if none set. */
    suspend operator fun invoke(userId: String): String? =
        profileRepository.fetchUserRole(userId)
}

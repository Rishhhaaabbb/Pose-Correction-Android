package com.domain.repository

import com.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(userId: String): Flow<UserProfile?>

    suspend fun refreshProfile(userId: String): Result<Unit>

    suspend fun upsertProfile(profile: UserProfile): Result<Unit>

    /** Patch only specific fields in the remote profile without touching others. */
    suspend fun patchProfileFields(userId: String, fields: Map<String, Any?>): Result<Unit>
}

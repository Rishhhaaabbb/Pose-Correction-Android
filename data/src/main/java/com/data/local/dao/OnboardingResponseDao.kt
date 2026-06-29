package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.OnboardingResponseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OnboardingResponseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OnboardingResponseEntity)

    @Query("SELECT * FROM onboarding_responses WHERE user_id = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): OnboardingResponseEntity?

    @Query("SELECT * FROM onboarding_responses WHERE user_id = :userId LIMIT 1")
    fun observeByUserId(userId: String): Flow<OnboardingResponseEntity?>

    @Query("SELECT is_completed FROM onboarding_responses WHERE user_id = :userId LIMIT 1")
    suspend fun isCompleted(userId: String): Int?  // null means no record exists
}

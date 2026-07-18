package com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    suspend fun getProfile(userId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("""
        UPDATE profiles SET
            height_cm = :heightCm,
            weight_kg = :weightKg,
            fitness_goal = :fitnessGoal,
            age_group = :ageGroup,
            gender = :gender,
            experience_level = :experienceLevel,
            focus_areas = :focusAreas,
            updated_at = :updatedAt
        WHERE id = :userId
    """)
    suspend fun patchQuestionnaireFields(
        userId: String,
        heightCm: Int?,
        weightKg: Double?,
        fitnessGoal: String?,
        ageGroup: String?,
        gender: String?,
        experienceLevel: String?,
        focusAreas: String?,
        updatedAt: Long,
    )
}

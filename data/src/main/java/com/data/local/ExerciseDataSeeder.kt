package com.data.local

import android.content.Context
import com.data.local.dao.ExerciseDao
import com.data.local.entity.ExerciseEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseDataSeeder @Inject constructor(
    private val exerciseDao: ExerciseDao,
    @ApplicationContext private val context: Context,
) {
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (exerciseDao.getCount() > 0) return@withContext

        val exercises = buildExerciseList()
        exerciseDao.insertAll(exercises)
    }

    private fun buildExerciseList(): List<ExerciseEntity> {
        val caloriesMap = mapOf(
            "squat" to 3.5,
            "wide-squat" to 4.0,
            "squat-hold" to 2.5,
            "lunge" to 3.8,
            "high-knees" to 5.0,
            "side-leg-raise" to 2.8,
            "lying-leg-raise" to 2.5,
            "bridge" to 3.0,
            "pushup" to 3.8,
            "knee-pushup" to 2.5,
            "chair-dip" to 3.2,
            "plank" to 3.0,
            "side-plank" to 2.8,
            "side-plank-raise" to 3.2,
            "deadbug" to 2.5,
            "bird-dog" to 2.8,
            "toe-touch-crunch" to 2.5,
            "side-touch" to 2.2,
            "knee-hug" to 2.0,
            "torso-twist" to 2.5,
            "tree" to 1.5,
            "triangle" to 2.5,
            "child" to 1.5,
            "cobrapose" to 2.2,
            "tricep-stretch" to 1.2,
            "shoulder-stretch" to 1.2,
            "neck-stretch" to 1.0,
            "leg-cradle" to 1.8,
        )

        val categoryMap = mapOf(
            "squat" to "Legs",
            "wide-squat" to "Legs",
            "squat-hold" to "Legs",
            "lunge" to "Legs",
            "high-knees" to "Cardio",
            "side-leg-raise" to "Legs",
            "lying-leg-raise" to "Legs",
            "bridge" to "Core",
            "pushup" to "Chest",
            "knee-pushup" to "Chest",
            "chair-dip" to "Chest",
            "plank" to "Core",
            "side-plank" to "Core",
            "side-plank-raise" to "Core",
            "deadbug" to "Core",
            "bird-dog" to "Core",
            "toe-touch-crunch" to "Core",
            "side-touch" to "Core",
            "knee-hug" to "Core",
            "torso-twist" to "Core",
            "tree" to "Stretch",
            "triangle" to "Stretch",
            "child" to "Stretch",
            "cobrapose" to "Stretch",
            "tricep-stretch" to "Stretch",
            "shoulder-stretch" to "Stretch",
            "neck-stretch" to "Stretch",
            "leg-cradle" to "Stretch",
        )

        val difficultyMap = mapOf(
            "squat" to "Beginner",
            "wide-squat" to "Beginner",
            "squat-hold" to "Intermediate",
            "lunge" to "Intermediate",
            "high-knees" to "Intermediate",
            "side-leg-raise" to "Intermediate",
            "lying-leg-raise" to "Intermediate",
            "bridge" to "Beginner",
            "pushup" to "Beginner",
            "knee-pushup" to "Beginner",
            "chair-dip" to "Intermediate",
            "plank" to "Beginner",
            "side-plank" to "Intermediate",
            "side-plank-raise" to "Intermediate",
            "deadbug" to "Intermediate",
            "bird-dog" to "Beginner",
            "toe-touch-crunch" to "Beginner",
            "side-touch" to "Beginner",
            "knee-hug" to "Beginner",
            "torso-twist" to "Beginner",
            "tree" to "Beginner",
            "triangle" to "Intermediate",
            "child" to "Beginner",
            "cobrapose" to "Beginner",
            "tricep-stretch" to "Beginner",
            "shoulder-stretch" to "Beginner",
            "neck-stretch" to "Beginner",
            "leg-cradle" to "Beginner",
        )

        val descriptionMap = mapOf(
            "squat" to "Stand with feet shoulder-width apart, lower your body by bending knees and hips, then stand back up.",
            "wide-squat" to "Stand with feet wider than shoulder-width, lower body by bending knees, keeping chest up.",
            "squat-hold" to "Hold the squat position for a specified duration to build endurance.",
            "lunge" to "Step forward with one leg, lowering hips until both knees are bent at 90 degrees.",
            "high-knees" to "Run in place, lifting knees as high as possible toward chest.",
            "side-leg-raise" to "Lie on your side, lift the top leg toward the ceiling while keeping it straight.",
            "lying-leg-raise" to "Lie on your back, lift both legs toward the ceiling while keeping them straight.",
            "bridge" to "Lie on your back with knees bent, lift hips toward ceiling, squeezing glutes.",
            "pushup" to "Start in plank position, lower chest to ground by bending elbows, push back up.",
            "knee-pushup" to "Perform pushup with knees on ground to reduce difficulty.",
            "chair-dip" to "Sit on edge of chair, lower body by bending elbows, then push back up.",
            "plank" to "Hold push-up position with arms straight, body forming a straight line.",
            "side-plank" to "Balance on one arm and side of foot, body forming a straight line sideways.",
            "side-plank-raise" to "From side plank, raise top leg toward ceiling for added challenge.",
            "deadbug" to "Lie on back, alternate extending opposite arm and leg while keeping back flat.",
            "bird-dog" to "On hands and knees, extend opposite arm and leg simultaneously, hold, then switch.",
            "toe-touch-crunch" to "Lie on back, reach toward toes while lifting shoulders off ground.",
            "side-touch" to "Sit with knees bent, lean to sides touching hands to floor beside hips.",
            "knee-hug" to "Lie on back, hug knees to chest, gently rock side to side.",
            "torso-twist" to "Sit with knees bent, rotate torso left and right to engage obliques.",
            "tree" to "Stand on one leg, place other foot on inner thigh, hands in prayer position.",
            "triangle" to "Form an inverted V shape with body, hands and feet on ground, hips lifted high.",
            "child" to "Kneel, sit back on heels, extend arms forward on ground, rest forehead down.",
            "cobrapose" to "Lie on stomach, push up on hands, arching back while hips stay on ground.",
            "tricep-stretch" to "Raise arm overhead, bend elbow, use other hand to gently push elbow down.",
            "shoulder-stretch" to "Cross one arm across body, use other arm to press it toward chest.",
            "neck-stretch" to "Gently tilt head to one side, hold, then switch to other side.",
            "leg-cradle" to "Lie on back, bring one knee to chest, use hands to cradle and stretch.",
        )

        val result = mutableListOf<ExerciseEntity>()
        
        try {
            context.assets.list("")?.filter { it.endsWith(".json") }?.forEach { name ->
                val baseName = name.removeSuffix(".json")
                result.add(
                    ExerciseEntity(
                        id = baseName,
                        name = baseName,
                        category = categoryMap[baseName] ?: "Other",
                        difficulty = difficultyMap[baseName] ?: "Beginner",
                        description = descriptionMap[baseName] ?: "",
                        caloriesPerRep = caloriesMap[baseName] ?: 2.0,
                        thumbnailUrl = null,
                    )
                )
            }
        } catch (_: Exception) {}

        return result
    }
}
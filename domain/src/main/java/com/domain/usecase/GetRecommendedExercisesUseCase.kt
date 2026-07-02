package com.domain.usecase

import com.domain.model.Exercise
import com.domain.model.UserProfile
import javax.inject.Inject

/**
 * Pure Kotlin use case ΓÇö zero Android imports.
 *
 * Scores every exercise in [allExercises] against the user's [profile] and
 * returns the top [maxResults] exercises sorted by score descending.
 *
 * Scoring rules:
 *  +30  per focus-area match  (exercise.category in user's focusAreas)
 *  +20  difficulty match for "Complete Beginner" or "Regular Practitioner"
 *  +25  difficulty match for "Advanced"
 *  +15  difficulty match for "Tried a Few Times"
 *  +15  exercise is safe for one of the user's injuries
 *  -50  exercise is contraindicated for one of the user's injuries
 *  +10  yoga style match
 *  -20  exercise was already done in the last 24 hours (variety boost)
 */
class GetRecommendedExercisesUseCase @Inject constructor() {

    operator fun invoke(
        profile: UserProfile,
        allExercises: List<Exercise>,
        recentExerciseIds: Set<String> = emptySet(),
        maxResults: Int = 6,
    ): List<Exercise> {
        return allExercises
            .map { exercise -> exercise to scoreExercise(exercise, profile, recentExerciseIds) }
            .sortedByDescending { (_, score) -> score }
            .take(maxResults)
            .map { (exercise, _) -> exercise }
    }

    private fun scoreExercise(
        exercise: Exercise,
        profile: UserProfile,
        recentExerciseIds: Set<String>,
    ): Int {
        var score = 0

        // ΓöÇΓöÇ 1. Focus-area match (+30 per matching area) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        val focusAreas = profile.focusAreas
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val exerciseCategory = exercise.category?.trim() ?: ""
        focusAreas.forEach { area ->
            if (area.equals(exerciseCategory, ignoreCase = true)) {
                score += 30
            }
        }

        // ΓöÇΓöÇ 2. Difficulty / experience-level match ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        val difficulty = exercise.difficulty?.trim() ?: ""
        when (profile.experienceLevel?.trim()) {
            "Complete Beginner" -> {
                if (difficulty.equals("Beginner", ignoreCase = true)) score += 20
            }
            "Tried a Few Times" -> {
                if (difficulty.equals("Beginner", ignoreCase = true) ||
                    difficulty.equals("Intermediate", ignoreCase = true)
                ) score += 15
            }
            "Regular Practitioner" -> {
                if (difficulty.equals("Intermediate", ignoreCase = true)) score += 20
            }
            "Advanced" -> {
                if (difficulty.equals("Advanced", ignoreCase = true)) score += 25
            }
        }

        // ΓöÇΓöÇ 3. Injury safety / contraindication ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        val userInjuries = profile.injuries
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
            ?: emptyList()

        if (userInjuries.isNotEmpty()) {
            // Safe for injury ΓåÆ +15
            val safeMatch = exercise.safeForInjuries.any { safe ->
                userInjuries.any { injury -> injury.equals(safe.trim(), ignoreCase = true) }
            }
            if (safeMatch) score += 15

            // Contraindicated ΓåÆ -50 (applied per matching injury, but cap at one penalty)
            val contraindicatedMatch = exercise.contraindicatedFor.any { contra ->
                userInjuries.any { injury -> injury.equals(contra.trim(), ignoreCase = true) }
            }
            if (contraindicatedMatch) score -= 50
        }

        // ΓöÇΓöÇ 4. Yoga-style match ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        val userYogaStyle = profile.yogaStyle?.trim()
        val exerciseYogaStyle = exercise.yogaStyle?.trim()
        if (userYogaStyle != null && exerciseYogaStyle != null &&
            userYogaStyle.equals(exerciseYogaStyle, ignoreCase = true)
        ) {
            score += 10
        }

        // ΓöÇΓöÇ 5. Variety boost (already done in last 24 h ΓåÆ -20) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        if (exercise.id in recentExerciseIds) {
            score -= 20
        }

        return score
    }
}
package com.posemonitor2

import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import com.core.poseengine.PoseGraph

/**
 * Manages screen orientation based on exercise requirements.
 *
 * Reads exercise JSON files to determine if an exercise requires:
 * - PORTRAIT: Front-facing exercises (standing poses, most yoga asanas)
 * - LANDSCAPE: Side-facing exercises (plank variations, side poses)
 *
 * This ensures optimal camera framing for keypoint detection.
 */
object OrientationManager {
    private const val TAG = "OrientationManager"
    private val orientationCache = mutableMapOf<String, ExerciseOrientation>()

    /**
     * Exercise orientation types.
     */
    enum class ExerciseOrientation {
        PORTRAIT,   // Front-facing, vertical phone position
        LANDSCAPE   // Side-facing, horizontal phone position
    }

    /**
     * Get the required orientation for an exercise.
     *
     * @param fileName The exercise JSON filename (e.g., "squat.json")
     * @param context The application context
     * @return PORTRAIT or LANDSCAPE orientation
     */
    fun getRequiredOrientation(fileName: String, context: Context): ExerciseOrientation {
        orientationCache[fileName]?.let { return it }

        val graph = try {
            val inputStream = context.assets.open(fileName)
            val json = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            PoseGraph.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load graph for $fileName", e)
            return ExerciseOrientation.PORTRAIT // Default fallback
        }

        // Check for orientation metadata in graph
        val orientationStr = graph.metadata.orientation?.lowercase()
        val resolved = when (orientationStr) {
            "portrait", "front" -> ExerciseOrientation.PORTRAIT
            "landscape", "side" -> ExerciseOrientation.LANDSCAPE
            else -> {
                Log.w(TAG, "No orientation metadata for $fileName, defaulting to PORTRAIT")
                ExerciseOrientation.PORTRAIT
            }
        }

        orientationCache[fileName] = resolved
        return resolved
    }

    /**
     * Get Android screen orientation constant for an exercise.
     *
     * @param orientation The exercise orientation
     * @return ActivityInfo.SCREEN_ORIENTATION_* constant
     */
    fun getScreenOrientationConstant(orientation: ExerciseOrientation): Int {
        return when (orientation) {
            ExerciseOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // Allow both landscape sides so users can hold the phone either way.
            ExerciseOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    /**
     * Get the recommended phone placement for an exercise.
     *
     * @param orientation The exercise orientation
     * @return Human-readable placement instructions
     */
    fun getPlacementInstructions(orientation: ExerciseOrientation): String {
        return when (orientation) {
            ExerciseOrientation.PORTRAIT -> "Switch to portrait mode. Place phone 1.75-2m away, 10-20cm from ground, full body visible."
            ExerciseOrientation.LANDSCAPE -> "Switch to landscape mode. Place phone 1.75-2m away, 10-20cm from ground, full body visible."
        }
    }

    /**
     * Get the minimum distance recommendation.
     */
    fun getMinDistance(): Double = 1.75

    /**
     * Get the optimal distance recommendation.
     */
    fun getOptimalDistance(): Double = 2.0

    /**
     * Get the recommended height from ground.
     */
    fun getRecommendedHeightCm(): Int = 15 // 10-20cm range, middle value
}

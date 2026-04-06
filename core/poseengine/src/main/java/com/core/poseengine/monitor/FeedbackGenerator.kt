package com.core.poseengine.monitor

import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.RawFeatureMap
import kotlin.math.abs
import kotlin.math.sqrt

class FeedbackGenerator(
    private val historyLength: Int = 15,
    private val coachingProfile: CoachingProfile = CoachingProfile.YOGA_GENTLE,
    private val coachingMode: CoachingMode = CoachingMode.BALANCED,
) {
    private val featureHistory = ArrayDeque<RawFeatureMap>(historyLength)
    private var roundRobinIssueIndex = 0
    private var lastIssueSignature = ""

    fun updateHistory(features: RawFeatureMap) {
        if (featureHistory.size >= historyLength) featureHistory.removeFirst()
        featureHistory.addLast(features)
    }

    fun generateFeedback(
        deviations: Map<FeatureKey, Double>,
        currentFeatures: RawFeatureMap,
    ): String {
        if (isUnstable()) return "Please hold still for a moment and keep your body steady."
        if (deviations.isEmpty()) return "Good. Keep this pose and breathe slowly."

        val orderedIssues = deviations
            .filter { (key, deviation) -> isActionableDeviation(key, deviation) }
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<FeatureKey, Double>> { priorityTier(it.key) }
                    .thenByDescending { abs(it.value) }
            )

        if (orderedIssues.isEmpty()) return "Good. Keep this pose and breathe slowly."

        val issueSignature = orderedIssues.joinToString(separator = "|") { (key, deviation) ->
            "$key:${deviation.signBucket()}"
        }

        if (issueSignature != lastIssueSignature) {
            lastIssueSignature = issueSignature
            roundRobinIssueIndex = 0
        }

        val selected = orderedIssues[roundRobinIssueIndex % orderedIssues.size]
        roundRobinIssueIndex++

        val remainingIssueCount = (orderedIssues.size - 1).coerceAtLeast(0)
        val primaryInstruction = generateSpecificFeedback(
            key = selected.key,
            deviation = selected.value,
            remainingIssueCount = remainingIssueCount,
        )

        if (coachingMode != CoachingMode.FULL || orderedIssues.size < 2) {
            return primaryInstruction
        }

        val secondary = orderedIssues[roundRobinIssueIndex % orderedIssues.size]
        val secondaryHint = generateSecondaryHint(secondary.key, secondary.value)
        return "$primaryInstruction Also, $secondaryHint"
    }

    private fun generateSpecificFeedback(
        key: FeatureKey,
        deviation: Double,
        remainingIssueCount: Int,
    ): String {
        val baseInstruction = when (key) {
            // ── Angle features ─────────────────────────────────────────────────
            FeatureKey.LEFT_ELBOW_ANGLE ->
                if (deviation > 0) {
                    "Move your left elbow to the target angle. Straighten your left arm a little."
                } else {
                    "Move your left elbow to the target angle. Bend your left arm a little."
                }
            FeatureKey.RIGHT_ELBOW_ANGLE ->
                if (deviation > 0) {
                    "Move your right elbow to the target angle. Straighten your right arm a little."
                } else {
                    "Move your right elbow to the target angle. Bend your right arm a little."
                }

            FeatureKey.LEFT_KNEE_ANGLE ->
                if (deviation > 0) {
                    "Move your left knee to the target angle. Straighten your left leg a little."
                } else {
                    "Move your left knee to the target angle. Bend your left knee a little."
                }
            FeatureKey.RIGHT_KNEE_ANGLE ->
                if (deviation > 0) {
                    "Move your right knee to the target angle. Straighten your right leg a little."
                } else {
                    "Move your right knee to the target angle. Bend your right knee a little."
                }

            FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE ->
                if (abs(deviation) > 10.0) {
                    if (deviation > 0) {
                        "Bend a little more at your hips and gently push your hips back."
                    } else {
                        "Lift your hips a little and keep your back tall."
                    }
                } else {
                    "Adjust your hip position slowly until it matches the target pose."
                }

            FeatureKey.LEFT_SHOULDER_ANGLE ->
                if (deviation > 0) {
                    "Lift your left shoulder and left arm a little."
                } else {
                    "Lower your left shoulder a little and relax your neck."
                }
            FeatureKey.RIGHT_SHOULDER_ANGLE ->
                if (deviation > 0) {
                    "Lift your right shoulder and right arm a little."
                } else {
                    "Lower your right shoulder a little and relax your neck."
                }

            FeatureKey.TORSO_ANGLE ->
                if (abs(deviation) > 5.0) {
                    if (deviation > 0) {
                        "Bend your upper body a little more forward."
                    } else {
                        "Lift your chest and make your upper body more upright."
                    }
                } else {
                    "Adjust your upper body angle slowly."
                }

            // ── Positional features ────────────────────────────────────────────
            FeatureKey.LEFT_ARM_ELEVATION ->
                if (deviation > 0) {
                    "Raise your left arm a little higher."
                } else {
                    "Lower your left arm a little."
                }
            FeatureKey.RIGHT_ARM_ELEVATION ->
                if (deviation > 0) {
                    "Raise your right arm a little higher."
                } else {
                    "Lower your right arm a little."
                }

            FeatureKey.LEFT_LEG_SPREAD ->
                if (deviation > 0) {
                    "Move your left foot a little wider."
                } else {
                    "Bring your left foot a little closer."
                }
            FeatureKey.RIGHT_LEG_SPREAD ->
                if (deviation > 0) {
                    "Move your right foot a little wider."
                } else {
                    "Bring your right foot a little closer."
                }

            FeatureKey.BODY_CENTER_Y ->
                if (deviation > 0) {
                    "Take a small step back from the camera."
                } else {
                    "Take a small step closer to the camera."
                }

            else -> "Adjust this body part slowly to match the target pose."
        }

        val followUp = if (remainingIssueCount > 0) {
            " Then we will fix the next body part slowly."
        } else {
            " Great effort, keep breathing."
        }

        return baseInstruction + followUp
    }

    private fun isUnstable(): Boolean {
        if (featureHistory.size < 5) return false
        val torsoVals = featureHistory.mapNotNull { it[FeatureKey.TORSO_ANGLE] }
        if (torsoVals.size < 3) return false
        val mean = torsoVals.average()
        return populationStd(torsoVals, mean) > INSTABILITY_THRESHOLD
    }

    fun reset() {
        featureHistory.clear()
        roundRobinIssueIndex = 0
        lastIssueSignature = ""
    }

    private fun isActionableDeviation(key: FeatureKey, deviation: Double): Boolean {
        val magnitude = abs(deviation)
        val angleThreshold = if (coachingProfile == CoachingProfile.WORKOUT_STRICT) {
            STRICT_ANGLE_ERROR_THRESHOLD
        } else {
            GENTLE_ANGLE_ERROR_THRESHOLD
        }
        val positionThreshold = if (coachingProfile == CoachingProfile.WORKOUT_STRICT) {
            STRICT_POSITION_ERROR_THRESHOLD
        } else {
            GENTLE_POSITION_ERROR_THRESHOLD
        }

        return when {
            key.isAngle -> magnitude >= angleThreshold
            key.isPositional -> magnitude >= positionThreshold
            else -> magnitude >= DEFAULT_ERROR_THRESHOLD
        }
    }

    private fun generateSecondaryHint(key: FeatureKey, deviation: Double): String = when (key) {
        FeatureKey.LEFT_ELBOW_ANGLE ->
            if (deviation > 0) "keep your left arm a little straighter" else "bend your left arm a little more"
        FeatureKey.RIGHT_ELBOW_ANGLE ->
            if (deviation > 0) "keep your right arm a little straighter" else "bend your right arm a little more"
        FeatureKey.LEFT_KNEE_ANGLE ->
            if (deviation > 0) "straighten your left leg slightly" else "bend your left knee slightly"
        FeatureKey.RIGHT_KNEE_ANGLE ->
            if (deviation > 0) "straighten your right leg slightly" else "bend your right knee slightly"
        FeatureKey.LEFT_SHOULDER_ANGLE ->
            if (deviation > 0) "lift your left shoulder a little" else "lower your left shoulder a little"
        FeatureKey.RIGHT_SHOULDER_ANGLE ->
            if (deviation > 0) "lift your right shoulder a little" else "lower your right shoulder a little"
        FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE ->
            if (deviation > 0) "move your hips slightly back" else "lift your hips slightly"
        FeatureKey.TORSO_ANGLE ->
            if (deviation > 0) "tilt your upper body a little forward" else "lift your chest a little"
        FeatureKey.LEFT_ARM_ELEVATION ->
            if (deviation > 0) "raise your left arm slightly" else "lower your left arm slightly"
        FeatureKey.RIGHT_ARM_ELEVATION ->
            if (deviation > 0) "raise your right arm slightly" else "lower your right arm slightly"
        FeatureKey.LEFT_LEG_SPREAD ->
            if (deviation > 0) "move your left foot a little wider" else "bring your left foot in a little"
        FeatureKey.RIGHT_LEG_SPREAD ->
            if (deviation > 0) "move your right foot a little wider" else "bring your right foot in a little"
        FeatureKey.BODY_CENTER_Y ->
            if (deviation > 0) "take a tiny step back" else "take a tiny step forward"
        else -> "adjust this body part gently"
    }

    private fun priorityTier(key: FeatureKey): Int = when {
        key.isCoreJoint -> 4
        key == FeatureKey.TORSO_ANGLE -> 3
        key.isAngle -> 2
        key.isPositional -> 1
        else -> 0
    }

    companion object {
        private const val INSTABILITY_THRESHOLD = 8.0
        private const val GENTLE_ANGLE_ERROR_THRESHOLD = 6.5
        private const val STRICT_ANGLE_ERROR_THRESHOLD = 4.5
        private const val GENTLE_POSITION_ERROR_THRESHOLD = 0.10
        private const val STRICT_POSITION_ERROR_THRESHOLD = 0.07
        private const val DEFAULT_ERROR_THRESHOLD = 0.06

        private fun populationStd(values: List<Double>, mean: Double): Double {
            if (values.size <= 1) return 0.0
            var sumSq = 0.0
            for (v in values) { val d = v - mean; sumSq += d * d }
            return sqrt(sumSq / values.size)
        }
    }
}

private fun Double.signBucket(): String = when {
    this > 0.0 -> "+"
    this < 0.0 -> "-"
    else -> "0"
}

// ── FeatureKey extensions ─────────────────────────────────────────────────────

// isCoreJoint is defined as a member property on FeatureKey

val FeatureKey.isPositional: Boolean
    get() = this == FeatureKey.LEFT_ARM_ELEVATION  ||
            this == FeatureKey.RIGHT_ARM_ELEVATION ||
            this == FeatureKey.LEFT_LEG_SPREAD     ||
            this == FeatureKey.RIGHT_LEG_SPREAD    ||
            this == FeatureKey.BODY_CENTER_Y

// isAngle is defined as a member property on FeatureKey
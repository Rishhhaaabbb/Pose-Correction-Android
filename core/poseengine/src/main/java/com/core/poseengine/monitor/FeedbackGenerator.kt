package com.core.poseengine.monitor

import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.RawFeatureMap
import kotlin.math.abs
import kotlin.math.sqrt

class FeedbackGenerator(
    private val historyLength: Int = 15,
) {
    private val featureHistory = ArrayDeque<RawFeatureMap>(historyLength)
    private val lastFeedbackKeys = ArrayDeque<FeatureKey>(3)

    fun updateHistory(features: RawFeatureMap) {
        if (featureHistory.size >= historyLength) featureHistory.removeFirst()
        featureHistory.addLast(features)
    }

    fun generateFeedback(
        deviations: Map<FeatureKey, Double>,
        currentFeatures: RawFeatureMap,
    ): String {
        if (isUnstable()) return "Hold steady - minimize movement"
        if (deviations.isEmpty()) return "Adjust to match target pose"

        // Priority 1: angle/core joints
        // Priority 2: positional features (elevation, spread, body center, torso)
        val coreDevs       = deviations.filter { (key, _) -> key.isCoreJoint }
        val positionalDevs = deviations.filter { (key, _) -> key.isPositional }

        val candidatePool = when {
            coreDevs.isNotEmpty()       -> coreDevs
            positionalDevs.isNotEmpty() -> positionalDevs
            else                        -> deviations
        }

        var worstKey = candidatePool.maxByOrNull { (_, v) -> abs(v) }?.key
            ?: return "Adjust position to match target"

        if (worstKey in lastFeedbackKeys) {
            val others = candidatePool.filter { (k, _) -> k != worstKey }
            if (others.isNotEmpty()) {
                worstKey = others.maxByOrNull { (_, v) -> abs(v) }!!.key
            }
        }

        if (lastFeedbackKeys.size >= 3) lastFeedbackKeys.removeFirst()
        lastFeedbackKeys.addLast(worstKey)

        val deviation = deviations[worstKey] ?: 0.0
        return generateSpecificFeedback(worstKey, deviation)
    }

    private fun generateSpecificFeedback(key: FeatureKey, deviation: Double): String {
        return when (key) {
            // ── Angle features ─────────────────────────────────────────────────
            FeatureKey.LEFT_ELBOW_ANGLE ->
                if (deviation > 0) "Straighten left arm more" else "Bend left elbow deeper"
            FeatureKey.RIGHT_ELBOW_ANGLE ->
                if (deviation > 0) "Straighten right arm more" else "Bend right elbow deeper"

            FeatureKey.LEFT_KNEE_ANGLE ->
                if (deviation > 0) "Straighten left leg" else "Bend left knee more"
            FeatureKey.RIGHT_KNEE_ANGLE ->
                if (deviation > 0) "Straighten right leg" else "Bend right knee more"

            FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE ->
                if (abs(deviation) > 10.0) {
                    if (deviation > 0) "Hinge forward at hips more" else "Straighten hips, stand taller"
                } else "Adjust hip position"

            FeatureKey.LEFT_SHOULDER_ANGLE -> "Adjust left shoulder position"
            FeatureKey.RIGHT_SHOULDER_ANGLE -> "Adjust right shoulder position"

            FeatureKey.TORSO_ANGLE ->
                if (abs(deviation) > 5.0) {
                    if (deviation > 0) "Lean torso forward slightly" else "Bring torso more upright"
                } else "Adjust torso angle"

            // ── Positional features ────────────────────────────────────────────
            FeatureKey.LEFT_ARM_ELEVATION ->
                if (deviation > 0) "Raise left arm higher" else "Lower left arm"
            FeatureKey.RIGHT_ARM_ELEVATION ->
                if (deviation > 0) "Raise right arm higher" else "Lower right arm"

            FeatureKey.LEFT_LEG_SPREAD ->
                if (deviation > 0) "Spread left leg wider" else "Bring left leg closer in"
            FeatureKey.RIGHT_LEG_SPREAD ->
                if (deviation > 0) "Spread right leg wider" else "Bring right leg closer in"

            FeatureKey.BODY_CENTER_Y ->
                if (deviation > 0) "Step back from camera" else "Step closer to camera"

            else -> "Adjust position to match target"
        }
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
        lastFeedbackKeys.clear()
    }

    companion object {
        private const val INSTABILITY_THRESHOLD = 8.0

        private fun populationStd(values: List<Double>, mean: Double): Double {
            if (values.size <= 1) return 0.0
            var sumSq = 0.0
            for (v in values) { val d = v - mean; sumSq += d * d }
            return sqrt(sumSq / values.size)
        }
    }
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
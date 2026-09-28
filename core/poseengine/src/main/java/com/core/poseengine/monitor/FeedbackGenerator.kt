package com.core.poseengine.monitor

import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.RawFeatureMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Generates human-readable corrective feedback from pose deviations.
 *
 * Matches Python: EnhancedFeedbackGenerator
 *
 * ── Instability detection ─────────────────────────────────────────────────────
 * Python uses np.std(torso_vals) > 8.0 with ddof=0.
 * We use [populationStd] (same ddof=0) to match exactly.
 *
 * ── Feature prioritization ───────────────────────────────────────────────────
 * Core features (hip, knee, elbow, torso) are prioritized over peripheral
 * features. Within the same priority tier, the feature with the largest
 * deviation is selected.
 *
 * ── Joint rotation ───────────────────────────────────────────────────────────
 * After speaking about the same joint 3 times in a row, the generator rotates
 * to the next-worst joint. This ensures the user hears about ALL their
 * mistakes across different exercises, not just the single worst one.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * [FeedbackGenerator] is stateful (feature history, last-feedback rotation).
 * It must be owned by a single engine instance and called from one thread.
 *
 * @param historyLength  Number of frames to retain for stability analysis.
 *   Default 15 matches Python default.
 */
class FeedbackGenerator(
    private val historyLength: Int = 15,
) {
    // Rolling feature history for instability detection
    private val featureHistory = ArrayDeque<RawFeatureMap>(historyLength)

    // Current active mistake key that the user is being prompted to correct
    private var activeMistakeKey: FeatureKey? = null

    // How many consecutive times we've spoken about the same joint
    private var sameJointCount: Int = 0

    // Maximum times to repeat the same joint before rotating
    private val maxSameJointRepeat = 3

    /**
     * Add current frame features to the rolling history.
     */
    fun updateHistory(features: RawFeatureMap) {
        if (featureHistory.size >= historyLength) featureHistory.removeFirst()
        featureHistory.addLast(features)
    }

    /**
     * Generate a corrective feedback string for the current frame.
     *
     * @param deviations      Per-feature deviation from state mean (signed).
     * @param currentFeatures Current frame's extracted features.
     * @param tolerances      The tolerances for the current state, used to normalize deviations.
     */
    fun generateFeedback(
        deviations: Map<FeatureKey, Double>,
        currentFeatures: RawFeatureMap,
        tolerances: Map<FeatureKey, Double> = emptyMap()
    ): String {
        // Instability check
        if (isUnstable()) return "Hold steady"

        if (deviations.isEmpty()) return "Focus on your pose"

        // Build scored list of all deviating features
        val scored = deviations.mapNotNull { (key, dev) ->
            if (key == FeatureKey.BODY_CENTER_Y) return@mapNotNull null
            val tol = tolerances[key] ?: defaultTolerance(key)
            val ratio = abs(dev) / tol
            if (ratio > 1.0) Triple(key, dev, ratio * getPriority(key)) else null
        }.sortedByDescending { it.third }

        if (scored.isEmpty()) {
            return "Almost there, keep adjusting"
        }

        // If active mistake is still significant, keep it — but rotate after 3 repeats
        activeMistakeKey?.let { activeKey ->
            val activeEntry = scored.find { it.first == activeKey }
            if (activeEntry != null) {
                val activeRatio = abs(activeEntry.second) / (tolerances[activeKey] ?: defaultTolerance(activeKey))

                // Still a mistake?
                if (activeRatio > 1.0) {
                    // Force rotation after maxSameJointRepeat consecutive times
                    if (sameJointCount >= maxSameJointRepeat && scored.size > 1) {
                        // Pick the next-worst joint that's different
                        val next = scored.first { it.first != activeKey }
                        activeMistakeKey = next.first
                        sameJointCount = 1
                        return generateSpecificFeedback(next.first, next.second)
                    }

                    // Check if something else is now much worse (>15% worse)
                    val best = scored.first()
                    if (best.first != activeKey && best.third > activeEntry.third * 1.15) {
                        activeMistakeKey = best.first
                        sameJointCount = 1
                        return generateSpecificFeedback(best.first, best.second)
                    }

                    // Keep the active one
                    sameJointCount++
                    return generateSpecificFeedback(activeKey, activeEntry.second)
                }
            }
            // Active mistake is now fixed
            activeMistakeKey = null
            sameJointCount = 0
        }

        // Pick the worst scoring feature
        val worst = scored.first()
        activeMistakeKey = worst.first
        sameJointCount = 1
        return generateSpecificFeedback(worst.first, worst.second)
    }

    /**
     * Priority weights for different feature types.
     * Core structural joints get highest priority for feedback.
     */
    private fun getPriority(key: FeatureKey) = when (key) {
        FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE,
        FeatureKey.LEFT_KNEE_ANGLE, FeatureKey.RIGHT_KNEE_ANGLE,
        FeatureKey.LEFT_ELBOW_ANGLE, FeatureKey.RIGHT_ELBOW_ANGLE -> 1.8
        FeatureKey.LEFT_SHOULDER_ANGLE, FeatureKey.RIGHT_SHOULDER_ANGLE -> 1.6
        FeatureKey.TORSO_ANGLE -> 1.5
        FeatureKey.HIP_ASYMMETRY_DELTA, FeatureKey.KNEE_ASYMMETRY_DELTA -> 1.3
        FeatureKey.LEFT_ARM_ELEVATION, FeatureKey.RIGHT_ARM_ELEVATION -> 1.0
        FeatureKey.LEFT_LEG_SPREAD, FeatureKey.RIGHT_LEG_SPREAD -> 0.5
        else -> 0.8
    }

    /** Default tolerance when the tolerances map doesn't contain a key. */
    private fun defaultTolerance(key: FeatureKey): Double =
        if (key.isAngle) 15.0 else 0.20

    /**
     * Generate the specific instruction string for a feature+deviation pair.
     * All messages are short (<8 words) for quick TTS playback.
     * Covers ALL 16 FeatureKey values including asymmetry deltas.
     */
    private fun generateSpecificFeedback(key: FeatureKey, deviation: Double): String {
        val side = when {
            key.name.contains("LEFT") -> "left"
            key.name.contains("RIGHT") -> "right"
            else -> ""
        }

        return when (key) {
            FeatureKey.LEFT_ELBOW_ANGLE, FeatureKey.RIGHT_ELBOW_ANGLE ->
                if (deviation > 0) "Bend your $side elbow more"
                else "Straighten your $side arm"

            FeatureKey.LEFT_KNEE_ANGLE, FeatureKey.RIGHT_KNEE_ANGLE ->
                if (deviation > 0) "Bend your $side knee more"
                else "Straighten your $side leg"

            FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE ->
                if (deviation > 0) "Bend more at your $side hip"
                else "Extend your $side hip"

            FeatureKey.LEFT_SHOULDER_ANGLE, FeatureKey.RIGHT_SHOULDER_ANGLE ->
                if (deviation > 0) "Lower your $side arm"
                else "Lift your $side arm higher"

            FeatureKey.TORSO_ANGLE ->
                if (deviation > 0) "Straighten your back"
                else "Lean forward more"

            FeatureKey.LEFT_ARM_ELEVATION, FeatureKey.RIGHT_ARM_ELEVATION ->
                if (deviation > 0) "Lower your $side hand"
                else "Raise your $side hand"

            FeatureKey.LEFT_LEG_SPREAD, FeatureKey.RIGHT_LEG_SPREAD ->
                if (deviation > 0) "Bring your feet closer"
                else "Widen your stance"

            FeatureKey.BODY_CENTER_Y ->
                if (deviation > 0) "Stand a bit taller"
                else "Lower your body"

            FeatureKey.HIP_ASYMMETRY_DELTA ->
                if (deviation > 0) "Level your hips, right side higher"
                else "Level your hips, left side higher"

            FeatureKey.KNEE_ASYMMETRY_DELTA ->
                if (deviation > 0) "Match your knee angles"
                else "Match your knee angles"
        }
    }

    /**
     * Returns true if the torso is oscillating rapidly — indicates subject
     * is not yet settled into the pose.
     */
    private fun isUnstable(): Boolean {
        if (featureHistory.size < 5) return false

        val torsoVals = featureHistory.mapNotNull { it[FeatureKey.TORSO_ANGLE] }
        if (torsoVals.size < 3) return false

        val mean = torsoVals.average()
        val std = populationStd(torsoVals, mean)
        return std > INSTABILITY_THRESHOLD
    }

    fun reset() {
        featureHistory.clear()
        activeMistakeKey = null
        sameJointCount = 0
    }

    companion object {
        private const val INSTABILITY_THRESHOLD = 8.0

        /** Population std (ddof=0). Matches np.std(). */
        private fun populationStd(values: List<Double>, mean: Double): Double {
            if (values.size <= 1) return 0.0
            var sumSq = 0.0
            for (v in values) { val d = v - mean; sumSq += d * d }
            return sqrt(sumSq / values.size)
        }
    }
}

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
 * deviation is selected. The last-used feature is rotated away to avoid
 * repeating the same instruction every frame.
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
    // Matches Python: self.feature_history deque with pop(0) eviction
    private val featureHistory = ArrayDeque<RawFeatureMap>(historyLength)

    // Last three features used for feedback — rotated to avoid repetition
    // Matches Python: self.last_feedback_features (maxlen=3)
    private val lastFeedbackKeys = ArrayDeque<FeatureKey>(3)

    // Current active mistake key that the user is being prompted to correct
    private var activeMistakeKey: FeatureKey? = null

    /**
     * Add current frame features to the rolling history.
     *
     * Matches Python: update_history()
     */
    fun updateHistory(features: RawFeatureMap) {
        if (featureHistory.size >= historyLength) featureHistory.removeFirst()
        featureHistory.addLast(features)
    }

    /**
     * Generate a corrective feedback string for the current frame.
     *
     * Matches Python: generate_feedback()
     *
     * @param deviations      Per-feature absolute deviation from state mean.
     * @param currentFeatures Current frame's extracted features (unused in
     *   feedback string generation but kept for API symmetry with Python).
     */
    /**
     * Generate a corrective feedback string for the current frame.
     *
     * Matches Python: generate_feedback()
     *
     * @param deviations      Per-feature absolute deviation from state mean.
     * @param currentFeatures Current frame's extracted features (unused in
     *   feedback string generation but kept for API symmetry with Python).
     * @param tolerances      The tolerances for the current state, used to normalize deviations.
     */
    fun generateFeedback(
        deviations: Map<FeatureKey, Double>,
        currentFeatures: RawFeatureMap,
        tolerances: Map<FeatureKey, Double> = emptyMap()
    ): String {
        // Instability check — matches Python: if self._is_unstable()
        if (isUnstable()) return "Hold steady"

        if (deviations.isEmpty()) return "Focus on your pose"

        // Priority weights: 
        // 1. Core structural joints (Hip/Knee/Elbow) - 1.8x
        // 2. Torso (Posture) - 1.5x
        // 3. Peripheral (Elevation/Spread/Center) - 1.0x
        fun getPriority(key: FeatureKey) = when (key) {
            FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE,
            FeatureKey.LEFT_KNEE_ANGLE, FeatureKey.RIGHT_KNEE_ANGLE,
            FeatureKey.LEFT_ELBOW_ANGLE, FeatureKey.RIGHT_ELBOW_ANGLE -> 1.8
            FeatureKey.TORSO_ANGLE -> 1.5
            FeatureKey.LEFT_LEG_SPREAD, FeatureKey.RIGHT_LEG_SPREAD -> 0.5 // Deprioritize noisy spread
            else -> 1.0
        }

        // If we have an active mistake, check if it's still significant
        activeMistakeKey?.let { activeKey ->
            val dev = deviations[activeKey]
            if (dev != null) {
                val tol = tolerances[activeKey] ?: (if (activeKey.isAngle) 10.0 else 0.15)
                val ratio = abs(dev) / tol
                
                // If it's still a mistake, keep it UNLESS a core joint is now significantly worse
                if (ratio > 1.0) {
                    val activeScore = ratio * getPriority(activeKey)
                    
                    // Look for anything significantly worse (>30% worse)
                    val betterAlternative = deviations.entries.maxByOrNull { (k, d) ->
                        val t = tolerances[k] ?: (if (k.isAngle) 10.0 else 0.15)
                        (abs(d) / t) * getPriority(k)
                    }
                    
                    if (betterAlternative != null) {
                        val alternativeScore = (abs(betterAlternative.value) / (tolerances[betterAlternative.key] ?: (if (betterAlternative.key.isAngle) 10.0 else 0.15))) * getPriority(betterAlternative.key)
                        if (alternativeScore > activeScore * 1.3) {
                            activeMistakeKey = betterAlternative.key
                        } else {
                            return generateSpecificFeedback(activeKey, dev)
                        }
                    } else {
                        return generateSpecificFeedback(activeKey, dev)
                    }
                }
            }
            activeMistakeKey = null
        }

        var worstKey: FeatureKey? = null
        var maxScore = 0.0

        for ((key, dev) in deviations) {
            if (key == FeatureKey.BODY_CENTER_Y) continue
            
            val tol = tolerances[key] ?: (if (key.isAngle) 10.0 else 0.15)
            val ratio = abs(dev) / tol
            
            // Only consider it a mistake if it's 30% beyond the tolerance
            // This reduces "flickering" feedback for poses like pushups/cobra
            if (ratio > 1.3) {
                val score = ratio * getPriority(key)
                if (score > maxScore) {
                    maxScore = score
                    worstKey = key
                }
            }
        }

        if (worstKey == null) {
            // If no feature is strictly over 1.3, pick the one closest to failing
            // only if it's at least over the 1.0 threshold.
            worstKey = deviations.filter { it.key != FeatureKey.BODY_CENTER_Y }.maxByOrNull { (key, dev) ->
                val tol = tolerances[key] ?: (if (key.isAngle) 10.0 else 0.15)
                val ratio = abs(dev) / tol
                if (ratio > 1.0) ratio else -1.0
            }?.key
            
            // If even the "worst" is below 1.0, then it's actually correct or too close to tell
            if (worstKey != null) {
                val dev = deviations[worstKey]!!
                val tol = tolerances[worstKey] ?: (if (worstKey.isAngle) 10.0 else 0.15)
                if (abs(dev) / tol <= 1.0) worstKey = null
            }
        }

        if (worstKey == null) {
            return "Adjust your body position"
        }

        activeMistakeKey = worstKey
        val deviation = deviations[worstKey] ?: 0.0
        return generateSpecificFeedback(worstKey, deviation)
    }

    /**
     * Generate the specific instruction string for a feature+deviation pair.
     *
     * Matches Python: _generate_specific_feedback()
     *
     * Uses [FeatureKey] enum instead of string 'in' checks — compile-safe.
     */
    private fun generateSpecificFeedback(key: FeatureKey, deviation: Double): String {
        val side = if (key.name.contains("LEFT")) "left" else if (key.name.contains("RIGHT")) "right" else ""
        
        return when (key) {
            FeatureKey.LEFT_ELBOW_ANGLE, FeatureKey.RIGHT_ELBOW_ANGLE ->
                if (deviation > 0) "Your $side arm is too straight. Bend your elbow more." 
                else "Your $side arm is too bent. Straighten it out."

            FeatureKey.LEFT_KNEE_ANGLE, FeatureKey.RIGHT_KNEE_ANGLE ->
                if (deviation > 0) "Your $side leg is too straight. Bend your knee more." 
                else "Your $side leg is too bent. Straighten your leg more."

            FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE ->
                if (deviation > 0) "You need to bend more at your $side hip." 
                else "You are bending too much at the $side hip. Stand more upright."

            FeatureKey.LEFT_SHOULDER_ANGLE, FeatureKey.RIGHT_SHOULDER_ANGLE ->
                if (deviation > 0) "Your $side arm is too high relative to your torso. Lower it." 
                else "Your $side arm is too low relative to your torso. Lift it higher."

            FeatureKey.TORSO_ANGLE ->
                if (deviation > 0) "Your torso is leaning too far. Stand more upright." 
                else "You are too upright. Lean your torso forward more."

            FeatureKey.LEFT_ARM_ELEVATION, FeatureKey.RIGHT_ARM_ELEVATION ->
                if (deviation > 0) "Your $side hand is too high. Lower your hand." 
                else "Your $side hand is too low. Raise your hand higher."

            FeatureKey.LEFT_LEG_SPREAD, FeatureKey.RIGHT_LEG_SPREAD ->
                if (deviation > 0) "Your feet are too wide apart. Bring them closer." 
                else "Your feet are too close together. Widen your stance."

            FeatureKey.BODY_CENTER_Y ->
                if (deviation > 0) "Your whole body is too low in the frame. Stand taller." 
                else "Your whole body is too high in the frame. Crouch lower."

            else -> "Your body position is incorrect. Adjust to align."
        }
    }

    /**
     * Returns true if the torso is oscillating rapidly — indicates subject
     * is not yet settled into the pose.
     *
     * Matches Python: _is_unstable()
     *   - Requires >= 5 frames in history
     *   - Requires >= 3 torso_angle values
     *   - Returns True if population std > 8.0
     */
    private fun isUnstable(): Boolean {
        if (featureHistory.size < 5) return false

        val torsoVals = featureHistory.mapNotNull { it[FeatureKey.TORSO_ANGLE] }
        if (torsoVals.size < 3) return false

        // Population std — matches Python np.std() with ddof=0
        val mean = torsoVals.average()
        val std = populationStd(torsoVals, mean)
        return std > INSTABILITY_THRESHOLD
    }

    fun reset() {
        featureHistory.clear()
        lastFeedbackKeys.clear()
        activeMistakeKey = null
    }

    companion object {
        private const val INSTABILITY_THRESHOLD = 8.0

        /**
         * Population std (ddof=0). Matches np.std().
         * Replicates the same function from StateDiscovery to avoid cross-module dependency.
         */
        private fun populationStd(values: List<Double>, mean: Double): Double {
            if (values.size <= 1) return 0.0
            var sumSq = 0.0
            for (v in values) { val d = v - mean; sumSq += d * d }
            return sqrt(sumSq / values.size)
        }
    }
}


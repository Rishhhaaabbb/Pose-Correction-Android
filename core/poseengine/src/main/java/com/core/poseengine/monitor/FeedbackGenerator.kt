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
    fun generateFeedback(
        deviations: Map<FeatureKey, Double>,
        currentFeatures: RawFeatureMap,
    ): String {
        // Instability check — matches Python: if self._is_unstable()
        if (isUnstable()) return "Hold steady - minimize movement"

        if (deviations.isEmpty()) return "Adjust to match target pose"

        // Priority 1: core joints — matches Python: core_features = ['hip','knee','elbow','torso']
        val coreDevs = deviations.filter { (key, _) -> key.isCoreJoint }

        // Select worst deviation in priority tier
        // Matches Python: worst_feature, worst_dev = max(core_devs.items(), key=lambda x: abs(x[1]))
        val candidatePool = if (coreDevs.isNotEmpty()) coreDevs else deviations

        var worstKey = candidatePool.maxByOrNull { (_, v) -> abs(v) }?.key
            ?: return "Adjust position to match target"

        // Rotate away from recently used feedback key
        // Matches Python: if worst_feature in self.last_feedback_features: try other
        if (worstKey in lastFeedbackKeys) {
            val others = candidatePool.filter { (k, _) -> k != worstKey }
            if (others.isNotEmpty()) {
                worstKey = others.maxByOrNull { (_, v) -> abs(v) }!!.key
            }
        }

        // Record for rotation — matches Python: last_feedback_features.append(); pop(0) at 3
        if (lastFeedbackKeys.size >= 3) lastFeedbackKeys.removeFirst()
        lastFeedbackKeys.addLast(worstKey)

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
        return when (key) {
            FeatureKey.LEFT_ELBOW_ANGLE ->
                if (deviation > 0) "Straighten left arm more" else "Bend left arm more"
            FeatureKey.RIGHT_ELBOW_ANGLE ->
                if (deviation > 0) "Straighten right arm more" else "Bend right arm more"

            FeatureKey.LEFT_KNEE_ANGLE ->
                if (deviation > 0) "Straighten left leg" else "Bend left knee more"
            FeatureKey.RIGHT_KNEE_ANGLE ->
                if (deviation > 0) "Straighten right leg" else "Bend right knee more"

            FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE -> {
                // Matches Python: if abs(deviation) > 10
                if (abs(deviation) > 10.0) {
                    if (deviation > 0) "Bend forward from your hips more"
                    else "Stand up straighter, push hips forward"
                } else "Adjust your hip position"
            }

            FeatureKey.LEFT_SHOULDER_ANGLE -> "Adjust left shoulder position"
            FeatureKey.RIGHT_SHOULDER_ANGLE -> "Adjust right shoulder position"

            FeatureKey.TORSO_ANGLE -> {
                // Matches Python: if abs(deviation) > 5
                if (abs(deviation) > 5.0) {
                    if (deviation > 0) "Lean your upper body forward a little"
                    else "Straighten your back, stand more upright"
                } else "Adjust your back angle"
            }

            FeatureKey.LEFT_ARM_ELEVATION ->
                if (deviation > 0) "Raise left arm higher" else "Lower left arm"
            FeatureKey.RIGHT_ARM_ELEVATION ->
                if (deviation > 0) "Raise right arm higher" else "Lower right arm"

            else -> "Adjust position to match target"
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

// ── FeatureKey extensions used by FeedbackGenerator ──────────────────────────

/**
 * True for features corresponding to core joints that get priority in feedback.
 *
 * Matches Python: core_features = ['hip', 'knee', 'elbow', 'torso']
 */
val FeatureKey.isCoreJoint: Boolean
    get() = this == FeatureKey.LEFT_HIP_ANGLE ||
            this == FeatureKey.RIGHT_HIP_ANGLE ||
            this == FeatureKey.LEFT_KNEE_ANGLE ||
            this == FeatureKey.RIGHT_KNEE_ANGLE ||
            this == FeatureKey.LEFT_ELBOW_ANGLE ||
            this == FeatureKey.RIGHT_ELBOW_ANGLE ||
            this == FeatureKey.TORSO_ANGLE
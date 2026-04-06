package com.core.poseengine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.core.poseengine.model.RawFeatureMap.Companion.toStringMap
import kotlin.math.abs

data class PoseState(
    val stateId: Int,
    val meanFeatures: RawFeatureMap,
    val featureTolerances: RawFeatureMap,
    val minHoldDuration: Double,
    val meanKeypoints: List<Pair<Double, Double>>? = null,
) {

    // ── Matching ──────────────────────────────────────────────────────────────

    /**
     * Check whether [features] match this state within tolerance.
     *
     * Non-strict mode: uses an adaptive threshold based on how many features
     * are actually visible:
     *   - >= 6 features visible → 60% must match (normal front-facing)
     *   - 4-5 features visible  → 55% must match (partial occlusion)
     *   - < 4 features visible  → 50% must match (side-on pose)
     *
     * This prevents side-on exercises from being impossible to complete —
     * when only left-side joints are visible, a 70% threshold would require
     * ALL of them to be correct leaving zero margin for any deviation.
     *
     * Strict mode: ALL features present in [meanFeatures] must be within
     * tolerance.
     */
    fun matches(features: RawFeatureMap, strict: Boolean = false): MatchResult {
        var matchesCount = 0
        var totalCount = 0
        val deviations = mutableMapOf<FeatureKey, Double>()

        for ((featureKey, meanVal) in meanFeatures.map) {
            val current = features[featureKey]

            if (current == null) {
                // Feature not visible — skip entirely, don't penalise
                if (strict) return MatchResult.NO_MATCH
                continue
            }

            totalCount++
            val tolerance = featureTolerances[featureKey] ?: continue
            val rawDeviation = current - meanVal
            deviations[featureKey] = rawDeviation

            if (abs(rawDeviation) <= tolerance) matchesCount++
        }

        if (totalCount == 0) return MatchResult.NO_MATCH

        val matched = if (strict) {
            matchesCount == totalCount
        } else {
            // Adaptive threshold — fewer visible features = more lenient
            // This handles side-on poses where only one body side is visible
            val threshold = when {
                totalCount >= 6 -> MATCH_RATIO_FULL      // 0.60 — normal
                totalCount >= 4 -> MATCH_RATIO_PARTIAL   // 0.55 — partial occlusion
                else            -> MATCH_RATIO_MINIMAL   // 0.50 — side-on / heavy occlusion
            }
            matchesCount.toDouble() / totalCount >= threshold
        }

        return MatchResult(matched = matched, deviations = deviations)
    }

    // ── Tolerance scaling ─────────────────────────────────────────────────────

    fun withScaledTolerances(factor: Double): PoseState {
        if (factor == 1.0) return this
        val scaled = featureTolerances.map.mapValues { (_, v) -> v * factor }
        return copy(featureTolerances = RawFeatureMap(scaled))
    }

    // ── Serialization bridge ──────────────────────────────────────────────────

    fun toSerializable(): PoseStateDto = PoseStateDto(
        stateId = stateId,
        meanFeatures = meanFeatures.toStringMap(),
        featureTolerances = featureTolerances.toStringMap(),
        minHoldDuration = minHoldDuration,
        meanKeypoints = meanKeypoints?.map { (x, y) -> listOf(x, y) },
    )

    companion object {
        // Normal front-facing: 70% of visible features must match
        private const val MATCH_RATIO_FULL    = 0.70
        // Partial occlusion (4-5 features): 60%
        private const val MATCH_RATIO_PARTIAL = 0.60
        // Side-on / heavy occlusion (<4 features): 55%
        private const val MATCH_RATIO_MINIMAL = 0.55

        fun fromSerializable(dto: PoseStateDto): PoseState = PoseState(
            stateId = dto.stateId,
            meanFeatures = RawFeatureMap.fromStringMap(dto.meanFeatures),
            featureTolerances = RawFeatureMap.fromStringMap(dto.featureTolerances),
            minHoldDuration = dto.minHoldDuration,
            meanKeypoints = dto.meanKeypoints?.map { pair ->
                require(pair.size == 2) { "Each keypoint must have exactly 2 values [x, y]" }
                pair[0] to pair[1]
            },
        )
    }
}

// ── Match result ──────────────────────────────────────────────────────────────

data class MatchResult(
    val matched: Boolean,
    val deviations: Map<FeatureKey, Double>,
) {
    companion object {
        val NO_MATCH = MatchResult(matched = false, deviations = emptyMap())
    }
}

// ── Serialization DTO ─────────────────────────────────────────────────────────

@Serializable
data class PoseStateDto(
    @SerialName("state_id")
    val stateId: Int,

    @SerialName("mean_features")
    val meanFeatures: Map<String, Double>,

    @SerialName("feature_tolerances")
    val featureTolerances: Map<String, Double>,

    @SerialName("min_hold_duration")
    val minHoldDuration: Double,

    @SerialName("mean_keypoints")
    val meanKeypoints: List<List<Double>>? = null,
)
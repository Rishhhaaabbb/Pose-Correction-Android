package com.core.poseengine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.core.poseengine.model.RawFeatureMap.Companion.toStringMap
import kotlin.math.abs

/**
 * An immutable discovered pose state with mean features, tolerances, and
 * the minimum time a subject must hold this pose for it to count.
 *
 * ── Immutability contract ─────────────────────────────────────────────────────
 * Python mutates [featureTolerances] in-place inside the online monitor
 * during grace periods and adaptive scaling. This is a latent bug: if two
 * coroutines process the same state simultaneously, tolerance mutations race.
 *
 * Kotlin fix: [withScaledTolerances] returns a NEW PoseState with scaled
 * tolerances. The original is never mutated. The monitor holds its own
 * locally-scoped copy for the duration of a single frame's evaluation.
 *
 * ── Serialization ─────────────────────────────────────────────────────────────
 * [toSerializable] / [fromSerializable] bridge to the @Serializable DTO.
 * This keeps kotlinx.serialization annotations off the domain object and
 * lets the domain layer remain a pure-Kotlin JVM module with no serialization
 * plugin requirement.
 *
 * @param stateId            Unique integer ID. Stable across save/load cycles.
 * @param meanFeatures       Mean value of each feature across all frames
 *                           assigned to this state.
 * @param featureTolerances  Per-feature acceptable deviation band.
 *                           Must contain exactly the same keys as [meanFeatures].
 * @param minHoldDuration    Seconds the subject must hold this pose.
 * @param meanKeypoints      Optional mean 2-D keypoint positions for visualization.
 *                           List of exactly 17 (x, y) pairs, or null.
 */
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
     * Matches Python: PoseState.matches(features, strict)
     *
     * Non-strict mode (default): at least 60% of shared features must be within
     * tolerance. Matches Python's `match_ratio >= 0.6` check.
     *
     * Strict mode: ALL features present in [meanFeatures] must be within
     * tolerance. If any expected feature is missing from [features], returns
     * immediately with no match.
     *
     * @return [MatchResult] containing the match decision and per-feature deviations.
     */
    fun matches(features: RawFeatureMap, strict: Boolean = false): MatchResult {
        var matchesCount = 0
        var totalCount = 0
        val deviations = mutableMapOf<FeatureKey, Double>()

        for ((featureKey, meanVal) in meanFeatures.map) {
            val current = features[featureKey]

            if (current == null) {
                // Matches Python: if feature_name not in features: if strict: return False, {}
                if (strict) return MatchResult.NO_MATCH
                continue
            }

            totalCount++
            val tolerance = featureTolerances[featureKey] ?: continue
            val rawDeviation = current - meanVal
            deviations[featureKey] = rawDeviation

            if (abs(rawDeviation) <= tolerance) matchesCount++
        }

        // Matches Python: if total_count == 0: return False, {}
        if (totalCount == 0) return MatchResult.NO_MATCH

        val matched = if (strict) {
            matchesCount == totalCount
        } else {
            // Matches Python: match_ratio >= 0.6
            matchesCount.toDouble() / totalCount >= MATCH_RATIO_THRESHOLD
        }

        return MatchResult(matched = matched, deviations = deviations)
    }

    // ── Tolerance scaling ─────────────────────────────────────────────────────

    /**
     * Returns a new [PoseState] with all tolerances scaled by [factor].
     *
     * Replaces Python's in-place mutation:
     *   for k in self.current_state.feature_tolerances:
     *       self.current_state.feature_tolerances[k] *= progressive_mult
     *
     * The original [PoseState] is never mutated. Callers hold a local copy
     * for the duration of one frame evaluation only.
     */
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

        private const val MATCH_RATIO_THRESHOLD = 0.6

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

/**
 * Result of a [PoseState.matches] check.
 *
 * Replaces Python's Tuple[bool, Dict[str, float]] return type.
 *
 * @param matched    True if the pose satisfies the match criteria.
 * @param deviations Per-feature absolute deviation from the state mean.
 *                   Only populated for features present in both the state
 *                   and the observed frame. Empty when [matched] is false
 *                   due to total_count == 0.
 */
data class MatchResult(
    val matched: Boolean,
    val deviations: Map<FeatureKey, Double>,
) {
    companion object {
        /** Sentinel for immediate rejection (strict mode missing key, or empty features). */
        val NO_MATCH = MatchResult(matched = false, deviations = emptyMap())
    }
}

// ── Serialization DTO ─────────────────────────────────────────────────────────

/**
 * kotlinx.serialization DTO for [PoseState].
 *
 * Uses string-keyed maps to match the Python JSON format exactly.
 * The domain object [PoseState] itself has no serialization annotations,
 * keeping the core module free of the serialization plugin.
 *
 * JSON shape (matches Python to_dict() / from_dict()):
 * {
 *   "state_id": 0,
 *   "mean_features": { "left_knee_angle": 165.3, ... },
 *   "feature_tolerances": { "left_knee_angle": 12.5, ... },
 *   "min_hold_duration": 0.8,
 *   "mean_keypoints": [[x0,y0], [x1,y1], ...]   // optional
 * }
 */
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
package com.core.poseengine

import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.RawFeatureMap
import kotlin.math.abs
/**
 * Computes normalized feature-space distances between pose feature maps.
 *
 * Extracted from Python's StateDiscovery._compute_feature_distance() into its
 * own object so it can be unit-tested independently and reused by both
 * StateDiscovery (offline) and OnlineMonitor (online feedback generation).
 *
 * ── Distance formula ─────────────────────────────────────────────────────────
 * For each feature present in BOTH maps:
 *
 *   Angle features:
 *     diff = |val1 - val2|
 *     if diff > 180 → diff = 360 - diff    (wraparound correction)
 *     normalized = diff / 180.0            (maps [0°, 180°] → [0.0, 1.0])
 *
 *   Asymmetry features:
 *     normalized = |val1 - val2| / 180.0   (same scale as angles)
 *
 *   All other features (body-scale-normalized distances):
 *     normalized = |val1 - val2|           (already in [0, ~2] range)
 *
 * Final distance = arithmetic mean of all per-feature normalized distances.
 *
 * Returns [Double.POSITIVE_INFINITY] when the two maps share no common keys.
 * Matches Python: return float('inf')
 */
object DistanceCalculator {

    /**
     * Compute mean normalized distance between two feature maps.
     *
     * Matches Python: StateDiscovery._compute_feature_distance()
     */
    fun compute(features1: RawFeatureMap, features2: RawFeatureMap): Double {
        val commonKeys = features1.commonKeysWith(features2)
        if (commonKeys.isEmpty()) return Double.POSITIVE_INFINITY

        var sum = 0.0
        var count = 0

        for (key in commonKeys) {
            val val1 = features1[key] ?: continue
            val val2 = features2[key] ?: continue

            sum += normalizedDistance(key, val1, val2)
            count++
        }

        if (count == 0) return Double.POSITIVE_INFINITY

        // Matches Python: return np.mean(distances)
        return sum / count
    }

    /**
     * Per-feature normalized distance.
     *
     * Inlined from Python's per-feature branching inside _compute_feature_distance().
     */
    fun normalizedDistance(key: FeatureKey, val1: Double, val2: Double): Double {
        return when {
            key.isAngle -> {
                // Matches Python:
                //   diff = abs(val1 - val2)
                //   if diff > 180: diff = 360 - diff
                //   distances.append(diff / 180.0)
                var diff = abs(val1 - val2)
                if (diff > 180.0) diff = 360.0 - diff
                diff / 180.0
            }

            else -> {
                // Matches Python:
                //   distances.append(abs(val1 - val2))
                abs(val1 - val2)
            }
        }
    }
}
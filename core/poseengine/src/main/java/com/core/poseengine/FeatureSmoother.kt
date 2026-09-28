package com.core.poseengine

import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.RawFeatureMap

/**
 * Smooths extracted pose features over a rolling window of frames.
 *
 * Raw angle calculations from keypoint positions amplify small positional
 * noise — a 2px jitter at the wrist can produce a 5° elbow angle jump.
 * By averaging feature values over [windowSize] frames, these spikes are
 * dampened without adding perceptible latency.
 *
 * Design:
 * - Only features present in the CURRENT frame are returned (no hallucination
 *   of missing features from history).
 * - Angle features are averaged directly (all joint angles are in 0–180° so
 *   no wrap-around handling needed).
 * - If the window has fewer than [windowSize] frames, the average uses
 *   whatever history is available.
 *
 * Thread safety: NOT thread-safe. Must be called from one thread (same as
 * [PoseMonitorEngine] and [FeedbackGenerator]).
 *
 * @param windowSize Number of frames to average over. Default 3.
 */
class FeatureSmoother(
    private val windowSize: Int = 3,
) {
    private val history = ArrayDeque<RawFeatureMap>(windowSize)

    /**
     * Add the current frame's features to the window and return smoothed values.
     *
     * @param features Raw features extracted from the current frame.
     * @return Smoothed feature map with the same keys as [features].
     */
    fun smooth(features: RawFeatureMap): RawFeatureMap {
        // Add to history, evict oldest if full
        if (history.size >= windowSize) history.removeFirst()
        history.addLast(features)

        // If only 1 frame in history, return as-is (no smoothing possible)
        if (history.size == 1) return features

        // Average each feature present in the current frame across the window
        val smoothed = buildMap<FeatureKey, Double>(features.size) {
            for ((key, currentValue) in features.map) {
                var sum = 0.0
                var count = 0
                for (frame in history) {
                    val value = frame[key]
                    if (value != null) {
                        sum += value
                        count++
                    }
                }
                // Always at least 1 (the current frame)
                put(key, if (count > 0) sum / count else currentValue)
            }
        }

        return RawFeatureMap(smoothed)
    }

    /** Clear history (e.g., on exercise change or state reset). */
    fun reset() {
        history.clear()
    }
}

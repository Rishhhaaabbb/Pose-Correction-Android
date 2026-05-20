package com.domain.generator

/**
 * Configuration for the offline graph generator.
 *
 * Replaces Python's OfflineGraphGenerator constructor parameter list.
 * All defaults match Python defaults exactly.
 *
 * @param confidenceThreshold  Keypoint confidence required for a landmark to
 *   be used in feature extraction. Default 0.6 matches Python default.
 * @param stabilityThreshold   Max normalized feature distance for a frame to
 *   remain in the same stable segment. Default 0.12 matches Python default.
 * @param mergeThreshold       Max normalized feature distance for two segments
 *   to be merged into the same canonical state.
 *
 *   MUST DEFAULT TO stabilityThreshold — matches Python exactly:
 *     Python._find_matching_state: distance <= self.stability_threshold
 *   Python uses the SAME threshold for both detection and merging.
 *
 *   Previous Kotlin default was stabilityThreshold × 0.5 — this caused
 *   states that Python merges (distance ≤ 0.12) to remain separate in Kotlin
 *   (distance > 0.06), requiring artificially inflated hyperparameters to
 *   compensate and producing different graphs from the same video.
 * @param minHoldFrames        A segment shorter than this (after skip_frames)
 *   is discarded. Default 18 matches Python default.
 * @param smoothingWindow      Rolling average window for temporal smoothing.
 *   Default 7 matches Python default.
 * @param transitionBuffer     Frames skipped at segment boundaries to ignore
 *   transition motion. Default 3 matches Python default.
 * @param toleranceMultiplier  Per-feature tolerance = std × this value.
 *   Default 2.0 matches Python default.
 * @param skipFrames           Process every Nth frame. Default 1 (all frames).
 * @param maxFrames            Hard cap on frames processed. Null = no cap.
 */
data class GeneratorConfig(
    val confidenceThreshold: Double = 0.6,
    val stabilityThreshold: Double = 0.12,
    val minHoldFrames: Int = 18,
    val smoothingWindow: Int = 7,
    val transitionBuffer: Int = 3,
    val toleranceMultiplier: Double = 2.0,
    val skipFrames: Int = 1,
    val maxFrames: Int? = null,
) {
    /**
     * Merge threshold — always equals stabilityThreshold to match Python.
     *
     * Defined as a val (not a constructor param) so it cannot be accidentally
     * set to a different value, preserving the Python invariant that detection
     * and merging use the same threshold.
     *
     * If you ever need asymmetric thresholds, promote this to a constructor
     * param with default = stabilityThreshold.
     */
    val mergeThreshold: Double get() = stabilityThreshold

    init {
        require(skipFrames >= 1) { "skipFrames must be ≥ 1" }
        require(minHoldFrames >= 1) { "minHoldFrames must be ≥ 1" }
        require(confidenceThreshold in 0.0..1.0) { "confidenceThreshold must be in [0, 1]" }
        require(stabilityThreshold > 0.0) { "stabilityThreshold must be positive" }
    }

    /**
     * Effective FPS seen by the state discovery algorithm.
     * Matches Python: effective_fps = fps / skip_frames
     */
    fun effectiveFps(originalFps: Double): Double = originalFps / skipFrames

    companion object {
        /** Matches all Python defaults. */
        val DEFAULT = GeneratorConfig()
    }
}
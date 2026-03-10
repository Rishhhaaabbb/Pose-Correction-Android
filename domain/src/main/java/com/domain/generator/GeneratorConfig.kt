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
        val DEFAULT = `GeneratorConfig`()
    }
}
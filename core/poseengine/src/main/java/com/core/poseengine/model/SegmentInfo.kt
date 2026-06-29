package com.core.poseengine.model

/**
 * Represents a temporally contiguous block of frames where the pose was stable.
 *
 * This is an intermediate representation produced during segment detection and
 * consumed during state merging. It is never persisted to disk.
 *
 * Matches Python: class SegmentInfo
 *
 * @param startFrame      First frame index of this segment (inclusive).
 * @param endFrame        Last frame index of this segment (inclusive).
 * @param meanFeatures    Mean features computed across all frames in startFrame..endFrame.
 * @param segmentIndex    Temporal order of this segment among all discovered segments
 *                        (0 = first segment in video, 1 = second, etc.).
 */
data class SegmentInfo(
    val startFrame: Int,
    val endFrame: Int,
    val meanFeatures: RawFeatureMap,
    val segmentIndex: Int,
) {
    /**
     * Matches Python: duration_frames()
     * Inclusive range: endFrame - startFrame + 1
     */
    val durationFrames: Int get() = endFrame - startFrame + 1
}
package com.core.poseengine.model

/**
 * COCO-17 skeleton bone connections for visualization.
 *
 * Each pair is (startKeypointIndex, endKeypointIndex) using [KeypointIndex].
 *
 * Matches Python: _SKELETON list of (int, int) tuples.
 * Stored as a flat IntArray (pairs of indices) to minimize allocations
 * when iterating during rendering.
 *
 * Access via [bones] which produces [Pair<KeypointIndex, KeypointIndex>] lazily,
 * or iterate [boneIndices] directly for performance-sensitive rendering.
 */
object SkeletonConnections {

    /**
     * Flat array of bone connections: [start0, end0, start1, end1, ...]
     *
     * Matches Python _SKELETON exactly — same 17 pairs in the same order.
     */
    val boneIndices: IntArray = intArrayOf(
        0, 1,   // nose → left_eye
        0, 2,   // nose → right_eye
        1, 3,   // left_eye → left_ear
        2, 4,   // right_eye → right_ear
        0, 5,   // nose → left_shoulder
        0, 6,   // nose → right_shoulder
        5, 6,   // left_shoulder → right_shoulder
        5, 7,   // left_shoulder → left_elbow
        7, 9,   // left_elbow → left_wrist
        6, 8,   // right_shoulder → right_elbow
        8, 10,  // right_elbow → right_wrist
        5, 11,  // left_shoulder → left_hip
        6, 12,  // right_shoulder → right_hip
        11, 12, // left_hip → right_hip
        11, 13, // left_hip → left_knee
        13, 15, // left_knee → left_ankle
        12, 14, // right_hip → right_knee
        14, 16, // right_knee → right_ankle
    )

    /** Number of bones. */
    val boneCount: Int get() = boneIndices.size / 2

    /**
     * Iterate bone connections as index pairs.
     * Allocates — use [boneIndices] directly in hot rendering paths.
     */
    val bones: List<Pair<Int, Int>> by lazy {
        (0 until boneCount).map { i ->
            boneIndices[i * 2] to boneIndices[i * 2 + 1]
        }
    }

    /** Confidence threshold below which a landmark is considered invisible. */
    const val DRAW_CONFIDENCE_THRESHOLD: Float = 0.3f
}
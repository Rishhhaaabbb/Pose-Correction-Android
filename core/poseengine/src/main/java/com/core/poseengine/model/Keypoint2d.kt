package com.core.poseengine.model

/**
 * Single 2-D keypoint in pixel space.
 *
 * Replaces one row of Python's np.ndarray shape (17, 3).
 * All fields are Double to match numpy float64 precision throughout.
 *
 * @param x          Pixel x-coordinate
 * @param y          Pixel y-coordinate (increases downward, matching image convention)
 * @param confidence Detection confidence in [0.0, 1.0]
 */
data class Keypoint2d(
    val x: Double,
    val y: Double,
    val confidence: Double,
)

/**
 * Full 17-keypoint skeleton in 2-D pixel space.
 *
 * Wraps List<Keypoint2d> with a type alias for clarity at call sites.
 * Size is validated at construction time to prevent silent truncation bugs.
 */
@JvmInline
value class Skeleton2d(val keypoints: List<Keypoint2d>) {

    init {
        require(keypoints.size == KeypointIndex.COUNT) {
            "Skeleton2d requires exactly ${KeypointIndex.COUNT} keypoints, " +
                    "got ${keypoints.size}"
        }
    }

    operator fun get(index: KeypointIndex): Keypoint2d = keypoints[index.idx]
}
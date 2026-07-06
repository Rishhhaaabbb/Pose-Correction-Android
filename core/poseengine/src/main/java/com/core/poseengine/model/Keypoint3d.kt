package com.core.poseengine.model

/**
 * Single 3-D keypoint in metric or normalized space.
 *
 * Replaces one row of Python's np.ndarray shape (17, 4).
 *
 * VP3D / MediaPipe 3-D world coordinate convention:
 *   X = left/right
 *   Y = up          (positive = upward — opposite of 2-D image Y!)
 *   Z = forward/back
 *
 * @param x          Left/right position
 * @param y          Up/down position (positive = up)
 * @param z          Forward/back position
 * @param confidence Detection confidence in [0.0, 1.0]
 */
data class Keypoint3d(
    val x: Double,
    val y: Double,
    val z: Double,
    val confidence: Double,
)

/**
 * Full 17-keypoint skeleton in 3-D world space.
 */
@JvmInline
value class Skeleton3d(val keypoints: List<Keypoint3d>) {

    init {
        require(keypoints.size == KeypointIndex.COUNT) {
            "Skeleton3d requires exactly ${KeypointIndex.COUNT} keypoints, " +
                    "got ${keypoints.size}"
        }
    }

    operator fun get(index: KeypointIndex): Keypoint3d = keypoints[index.idx]
}
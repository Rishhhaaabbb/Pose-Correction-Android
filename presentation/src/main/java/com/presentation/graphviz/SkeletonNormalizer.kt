package com.presentation.graphviz

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max

/**
 * Normalizes raw keypoint positions for display inside a fixed-size node circle.
 *
 * Matches Python: PoseGraphVisualizer._normalize_skeleton()
 *
 * ── Algorithm (identical to Python) ──────────────────────────────────────────
 * 1. Filter out invalid keypoints (all-zero or NaN).
 * 2. Compute centroid of valid keypoints.
 * 3. Subtract centroid from all keypoints (center at origin).
 * 4. FLIP Y-axis: image coords have Y increasing downward;
 *    screen canvas has Y increasing downward too — BUT the stored keypoints
 *    were captured in image space, so flipping makes the figure right-side up
 *    when rendered in Compose (which also has Y-down). We match Python exactly:
 *    keypoints_centered[:, 1] = -keypoints_centered[:, 1]
 * 5. Scale so max extent fits in [-0.5, 0.5] with a 2.5× padding factor.
 *
 * Output range: approximately [-0.5, 0.5] in both axes.
 * Caller multiplies by nodeRadius to position within the node circle.
 *
 * @param rawKeypoints  List of 17 (x, y) pairs in image pixel coordinates.
 *   Matches the [PoseState.meanKeypoints] format.
 */
object SkeletonNormalizer {

    /**
     * Sentinel: a keypoint is "invalid" if both x and y are exactly 0.
     * Matches Python: np.all(keypoints == 0, axis=1)
     *
     * We also treat NaN as invalid.
     * Matches Python: np.any(np.isnan(keypoints), axis=1)
     */
    private fun Pair<Double, Double>.isInvalid(): Boolean =
        (first == 0.0 && second == 0.0) ||
                first.isNaN() || second.isNaN()

    /**
     * Normalize keypoints for rendering inside a node circle.
     *
     * @return List of 17 [Offset] values in approximately [-0.5, 0.5] range,
     *   or null if all keypoints are invalid.
     */
    fun normalize(rawKeypoints: List<Pair<Double, Double>>): List<Offset>? {
        require(rawKeypoints.size == 17) {
            "Expected 17 keypoints, got ${rawKeypoints.size}"
        }

        val validIndices = rawKeypoints.indices.filter { !rawKeypoints[it].isInvalid() }
        if (validIndices.isEmpty()) return null

        // Step 2: Centroid of valid keypoints
        // Matches Python: center = valid_kpts.mean(axis=0)
        val centerX = validIndices.sumOf { rawKeypoints[it].first } / validIndices.size
        val centerY = validIndices.sumOf { rawKeypoints[it].second } / validIndices.size

        // Step 3: Center all keypoints (invalid ones stay at their raw value — they
        // will be skipped during rendering, matching Python's behavior)
        // Step 4: Flip Y-axis
        // Matches Python:
        //   keypoints_centered = keypoints - center
        //   keypoints_centered[:, 1] = -keypoints_centered[:, 1]
        val centered = rawKeypoints.map { (x, y) ->
            val cx = x - centerX
            val cy = -(y - centerY)   // Y flip
            cx to cy
        }

        // Step 5: Compute max extent across valid keypoints
        // Matches Python:
        //   max_extent_x = np.abs(valid_kpts[:, 0] - center[0]).max()
        //   max_extent_y = np.abs(valid_kpts[:, 1] - center[1]).max()
        //   max_extent = max(max_extent_x, max_extent_y)
        var maxExtent = 0.0
        for (i in validIndices) {
            maxExtent = max(maxExtent, abs(centered[i].first))
            maxExtent = max(maxExtent, abs(centered[i].second))
        }

        // Scale with 2.5× padding factor
        // Matches Python: keypoints_scaled = keypoints_centered / (max_extent * 2.5)
        val scale = if (maxExtent > 1e-6) 1.0 / (maxExtent * 2.5) else 1.0

        return centered.map { (cx, cy) ->
            Offset((cx * scale).toFloat(), (cy * scale).toFloat())
        }
    }

    /**
     * Check whether a normalized keypoint is valid for rendering.
     *
     * Matches Python:
     *   if np.allclose(keypoints[i], 0): continue
     *   if np.any(np.isnan(start_pos)): continue
     */
    fun Offset.isValidForDrawing(): Boolean =
        !(x == 0f && y == 0f) && !x.isNaN() && !y.isNaN()
}
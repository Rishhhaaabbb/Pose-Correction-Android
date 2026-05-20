package com.data.mediapipe

import android.graphics.Bitmap
import com.core.poseengine.model.Skeleton2d

/**
 * Contract for a component that detects a human pose in a single image frame
 * and returns it as a [Skeleton2d] in COCO-17 format.
 *
 * Replaces Python: MediaPipePoseAdapter.detect(frame) -> Optional[np.ndarray]
 *
 * ── Return semantics ─────────────────────────────────────────────────────────
 * Returns null when:
 *   - No person is detected in the frame
 *   - Detection confidence is below the configured threshold
 *
 * Matches Python: return None if no pose detected.
 *
 * ── Threading ────────────────────────────────────────────────────────────────
 * Implementations must be safe to call from a background dispatcher.
 * The offline generator calls this synchronously inside a Flow on
 * [Dispatchers.Default]; the online monitor calls it from the camera
 * callback thread.
 *
 * ── Lifecycle ────────────────────────────────────────────────────────────────
 * Implementations hold native MediaPipe resources. Call [close] when done
 * to release them. In practice this is managed by the DI scope.
 */
interface MediaPipePoseProvider : AutoCloseable {

    /**
     * Detect a pose in [bitmap] and return COCO-17 keypoints.
     *
     * @param bitmap  ARGB_8888 or RGB_565 bitmap. The caller retains ownership
     *   and must recycle it after this call returns.
     * @return [Skeleton2d] with 17 keypoints, or null if no pose detected.
     */
    fun detect(bitmap: Bitmap): Skeleton2d?
}
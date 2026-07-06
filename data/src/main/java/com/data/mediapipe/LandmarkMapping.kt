package com.data.mediapipe

/**
 * Maps MediaPipe BlazePose 33-landmark indices to COCO-17 landmark indices.
 *
 * Matches Python: _MP_TO_COCO dict exactly — same 17 entries, same values.
 *
 * ── Storage format ────────────────────────────────────────────────────────────
 * Stored as a sparse IntArray of size 33 (max MP index + 1).
 * [mpToCoco[mpIdx]] returns the COCO index, or [UNMAPPED] (-1) if the MP
 * landmark has no COCO equivalent.
 *
 * This gives O(1) array access with no boxing — critical for per-frame use.
 *
 * ── Mapping rationale ─────────────────────────────────────────────────────────
 * MediaPipe provides 33 landmarks including fine-grained face points
 * (lips, irises, ears inner/outer). COCO-17 uses a coarser set.
 * Unmapped MP landmarks (e.g. left/right pinky, index, thumb) are dropped.
 *
 * Landmark index reference:
 *   MP  0 = nose              → COCO  0
 *   MP  2 = left_eye_inner    → COCO  1  (left_eye)
 *   MP  5 = right_eye_inner   → COCO  2  (right_eye)
 *   MP  7 = left_ear          → COCO  3
 *   MP  8 = right_ear         → COCO  4
 *   MP 11 = left_shoulder     → COCO  5
 *   MP 12 = right_shoulder    → COCO  6
 *   MP 13 = left_elbow        → COCO  7
 *   MP 14 = right_elbow       → COCO  8
 *   MP 15 = left_wrist        → COCO  9
 *   MP 16 = right_wrist       → COCO 10
 *   MP 23 = left_hip          → COCO 11
 *   MP 24 = right_hip         → COCO 12
 *   MP 25 = left_knee         → COCO 13
 *   MP 26 = right_knee        → COCO 14
 *   MP 27 = left_ankle        → COCO 15
 *   MP 28 = right_ankle       → COCO 16
 */
object LandmarkMapping {

    /** Sentinel value for unmapped MP landmark indices. */
    const val UNMAPPED = -1

    /** MediaPipe has 33 landmarks (indices 0–32). */
    private const val MP_LANDMARK_COUNT = 33

    /**
     * Sparse lookup array: mpToCoco[mpIndex] = cocoIndex or UNMAPPED.
     *
     * Matches Python _MP_TO_COCO dict exactly.
     */
    val mpToCoco: IntArray = IntArray(MP_LANDMARK_COUNT) { UNMAPPED }.also { arr ->
        arr[0]  = 0   // nose
        arr[2]  = 1   // left_eye (inner)
        arr[5]  = 2   // right_eye (inner)
        arr[7]  = 3   // left_ear
        arr[8]  = 4   // right_ear
        arr[11] = 5   // left_shoulder
        arr[12] = 6   // right_shoulder
        arr[13] = 7   // left_elbow
        arr[14] = 8   // right_elbow
        arr[15] = 9   // left_wrist
        arr[16] = 10  // right_wrist
        arr[23] = 11  // left_hip
        arr[24] = 12  // right_hip
        arr[25] = 13  // left_knee
        arr[26] = 14  // right_knee
        arr[27] = 15  // left_ankle
        arr[28] = 16  // right_ankle
    }

    /**
     * Number of COCO-17 landmarks produced by this mapping.
     * Matches Python: keypoints = np.zeros((17, 3), dtype=np.float32)
     */
    const val COCO_LANDMARK_COUNT = 17
}
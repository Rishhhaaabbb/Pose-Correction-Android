// core/poseengine/src/test/kotlin/FeatureExtractorTest.kt

package com.core.poseengine

import com.core.poseengine.model.*
import kotlin.math.abs
import kotlin.test.*

class FeatureExtractorTest {

    private val extractor = FeatureExtractor(confidenceThreshold = 0.5)

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a flat standing skeleton (all keypoints valid) with:
     *  - shoulder at y=100, hip at y=200 → bodyScale ≈ 100 px
     *  - all joints vertically aligned → elbow/knee angles ≈ 180°
     *  - torso vertical → torso_angle ≈ 0°
     */
    private fun straightStandingSkeleton(conf: Double = 0.9): Skeleton2d {
        val kpts = listOf(
            // 0 nose
            Keypoint2d(100.0, 50.0, conf),
            // 1 left_eye, 2 right_eye, 3 left_ear, 4 right_ear
            Keypoint2d(95.0, 45.0, conf), Keypoint2d(105.0, 45.0, conf),
            Keypoint2d(90.0, 48.0, conf), Keypoint2d(110.0, 48.0, conf),
            // 5 left_shoulder (x=90, y=100), 6 right_shoulder (x=110, y=100)
            Keypoint2d(90.0, 100.0, conf), Keypoint2d(110.0, 100.0, conf),
            // 7 left_elbow, 8 right_elbow (y=150 → arm straight down)
            Keypoint2d(90.0, 150.0, conf), Keypoint2d(110.0, 150.0, conf),
            // 9 left_wrist, 10 right_wrist
            Keypoint2d(90.0, 200.0, conf), Keypoint2d(110.0, 200.0, conf),
            // 11 left_hip (x=90, y=200), 12 right_hip (x=110, y=200)
            Keypoint2d(90.0, 200.0, conf), Keypoint2d(110.0, 200.0, conf),
            // 13 left_knee, 14 right_knee
            Keypoint2d(90.0, 280.0, conf), Keypoint2d(110.0, 280.0, conf),
            // 15 left_ankle, 16 right_ankle
            Keypoint2d(90.0, 360.0, conf), Keypoint2d(110.0, 360.0, conf),
        )
        return Skeleton2d(kpts)
    }

    private fun assertNearlyEqual(expected: Double, actual: Double, tolerance: Double = 0.01) {
        assertTrue(abs(actual - expected) <= tolerance,
            "Expected $expected ± $tolerance but got $actual")
    }

    // ── Validation tests ──────────────────────────────────────────────────────

    @Test
    fun `returns null when fewer than 4 critical keypoints are confident`() {
        // Set all knees and one hip to low confidence
        val kpts = straightStandingSkeleton().keypoints.toMutableList()
        kpts[KeypointIndex.LEFT_KNEE.idx] = kpts[KeypointIndex.LEFT_KNEE.idx].copy(confidence = 0.1)
        kpts[KeypointIndex.RIGHT_KNEE.idx] = kpts[KeypointIndex.RIGHT_KNEE.idx].copy(confidence = 0.1)
        kpts[KeypointIndex.LEFT_HIP.idx] = kpts[KeypointIndex.LEFT_HIP.idx].copy(confidence = 0.1)
        kpts[KeypointIndex.RIGHT_HIP.idx] = kpts[KeypointIndex.RIGHT_HIP.idx].copy(confidence = 0.1)
        val result = extractor.extractFeatures(Skeleton2d(kpts))
        assertNull(result, "Should return null when validation fails")
    }

    @Test
    fun `returns null when body scale is too small`() {
        // Collapse all keypoints to the same point → bodyScale = 0
        val kpts = List(17) { Keypoint2d(5.0, 5.0, 0.9) }
        val result = extractor.extractFeatures(Skeleton2d(kpts))
        assertNull(result, "Should return null for degenerate skeleton")
    }

    // ── Angle tests ───────────────────────────────────────────────────────────

    @Test
    fun `straight standing skeleton produces near-180 degree knee angles`() {
        val features = extractor.extractFeatures(straightStandingSkeleton())
        assertNotNull(features)
        val leftKnee = features[FeatureKey.LEFT_KNEE_ANGLE]
        assertNotNull(leftKnee)
        // Hip(y=200) → Knee(y=280) → Ankle(y=360): perfectly straight → 180°
        assertNearlyEqual(180.0, leftKnee, tolerance = 1.0)
    }

    @Test
    fun `torso angle is near zero for upright skeleton`() {
        val features = extractor.extractFeatures(straightStandingSkeleton())
        assertNotNull(features)
        val torso = features[FeatureKey.TORSO_ANGLE]
        assertNotNull(torso)
        // Shoulder midpoint (x=100, y=100), hip midpoint (x=100, y=200)
        // torso_vec = (0, -100) → atan2(0, 100) = 0°
        assertNearlyEqual(0.0, torso, tolerance = 0.1)
    }

    @Test
    fun `arm elevation is negative when wrists are below shoulders`() {
        // In straight standing, wrists at y=200, shoulders at y=100
        // elevation = -(200 - 100) / 100 = -1.0
        val features = extractor.extractFeatures(straightStandingSkeleton())
        assertNotNull(features)
        val elev = features[FeatureKey.LEFT_ARM_ELEVATION]
        assertNotNull(elev)
        assertNearlyEqual(-1.0, elev, tolerance = 0.01)
    }

    @Test
    fun `arm elevation is positive when wrists are raised above shoulders`() {
        val kpts = straightStandingSkeleton().keypoints.toMutableList()
        // Move wrists above shoulders (smaller y = higher in image)
        kpts[KeypointIndex.LEFT_WRIST.idx] = Keypoint2d(90.0, 50.0, 0.9)   // y=50 < shoulder y=100
        kpts[KeypointIndex.RIGHT_WRIST.idx] = Keypoint2d(110.0, 50.0, 0.9)
        val features = extractor.extractFeatures(Skeleton2d(kpts))
        assertNotNull(features)
        val elev = features[FeatureKey.LEFT_ARM_ELEVATION]
        assertNotNull(elev)
        assertTrue(elev > 0.0, "Wrist above shoulder should give positive elevation")
    }

    // ── Confidence threshold tests ────────────────────────────────────────────

    @Test
    fun `angle returns null when one keypoint is below confidence threshold`() {
        val kpts = straightStandingSkeleton().keypoints.toMutableList()
        kpts[KeypointIndex.LEFT_ELBOW.idx] = kpts[KeypointIndex.LEFT_ELBOW.idx].copy(confidence = 0.3)
        val features = extractor.extractFeatures(Skeleton2d(kpts))
        // Left elbow angle should be absent; right elbow should still be present
        assertNotNull(features)
        assertNull(features[FeatureKey.LEFT_ELBOW_ANGLE])
        assertNotNull(features[FeatureKey.RIGHT_ELBOW_ANGLE])
    }

    // ── Fallback merge tests ──────────────────────────────────────────────────

    @Test
    fun `extractFeaturesWithFallback returns 2D when no 3D provided`() {
        val skeleton = straightStandingSkeleton()
        val result = extractor.extractFeaturesWithFallback(skeleton, skeleton3d = null)
        assertNotNull(result)
        assertNotNull(result[FeatureKey.TORSO_ANGLE])
    }

    @Test
    fun `3D features override 2D features for matching keys`() {
        val skeleton2d = straightStandingSkeleton()
        // Build a minimal 3D skeleton that only provides TORSO_ANGLE
        val kpts3d = List(17) {
            Keypoint3d(0.0, 0.0, 0.0, 0.1) // low conf — no 3D angles will be computed
        }.toMutableList()
        // Give high confidence to all four torso keypoints in 3D
        kpts3d[5] = Keypoint3d(0.0, 0.5, 0.0, 0.9)   // left shoulder  (Y=up)
        kpts3d[6] = Keypoint3d(0.0, 0.5, 0.0, 0.9)   // right shoulder
        kpts3d[11] = Keypoint3d(0.0, 0.0, 0.0, 0.9)  // left hip
        kpts3d[12] = Keypoint3d(0.0, 0.0, 0.0, 0.9)  // right hip
        // spine_forward = 0, spine_vertical = 0.5 → atan2(0, 0.5) = 0°
        val skeleton3d = Skeleton3d(kpts3d)

        val result = extractor.extractFeaturesWithFallback(skeleton2d, skeleton3d)
        assertNotNull(result)
        // 3D torso angle should override 2D
        val torso = result[FeatureKey.TORSO_ANGLE]
        assertNotNull(torso)
        assertNearlyEqual(0.0, torso, tolerance = 0.1)
    }

    @Test
    fun `body center Y is positive for normal standing pose`() {
        // hipCy = 200, bodyScale = 100 → body_center_y = 2.0
        val features = extractor.extractFeatures(straightStandingSkeleton())
        assertNotNull(features)
        val bcy = features[FeatureKey.BODY_CENTER_Y]
        assertNotNull(bcy)
        assertNearlyEqual(2.0, bcy, tolerance = 0.01)
    }

    // ── RawFeatureMap operator tests ──────────────────────────────────────────

    @Test
    fun `RawFeatureMap plus operator merges with right side winning`() {
        val a = RawFeatureMap.of(
            FeatureKey.TORSO_ANGLE to 10.0,
            FeatureKey.LEFT_KNEE_ANGLE to 90.0,
        )
        val b = RawFeatureMap.of(
            FeatureKey.TORSO_ANGLE to 5.0,  // should override
        )
        val merged = a + b
        assertEquals(5.0, merged[FeatureKey.TORSO_ANGLE])
        assertEquals(90.0, merged[FeatureKey.LEFT_KNEE_ANGLE]) // preserved from a
    }

    @Test
    fun `commonKeysWith returns only shared keys`() {
        val a = RawFeatureMap.of(FeatureKey.TORSO_ANGLE to 10.0, FeatureKey.LEFT_KNEE_ANGLE to 90.0)
        val b = RawFeatureMap.of(FeatureKey.TORSO_ANGLE to 5.0, FeatureKey.RIGHT_KNEE_ANGLE to 45.0)
        val common = a.commonKeysWith(b)
        assertEquals(setOf(FeatureKey.TORSO_ANGLE), common)
    }
}
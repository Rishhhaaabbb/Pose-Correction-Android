// core/poseengine/src/test/kotlin/StateDiscoveryTest.kt

package com.core.poseengine

import com.core.poseengine.model.*
import kotlin.math.abs
import kotlin.test.*

class StateDiscoveryTest {

    private val discovery = StateDiscovery(
        stabilityThreshold = 0.15,
        minHoldFrames = 5,
        smoothingWindow = 3,
        transitionBuffer = 2,
        toleranceMultiplier = 2.0,
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun frameOf(vararg pairs: Pair<FeatureKey, Double>) =
        RawFeatureMap.of(*pairs)

    private fun emptyFrame() = RawFeatureMap.EMPTY

    /** Builds N identical frames with the given feature value. */
    private fun repeatedFrames(n: Int, kneeAngle: Double): List<RawFeatureMap> =
        List(n) { frameOf(FeatureKey.LEFT_KNEE_ANGLE to kneeAngle) }

    // ── populationStd ─────────────────────────────────────────────────────────

    @Test
    fun `populationStd returns 0 for single value`() {
        assertEquals(0.0, discovery.populationStd(listOf(45.0), 45.0))
    }

    @Test
    fun `populationStd matches numpy ddof=0 for known values`() {
        // np.std([2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0]) = 2.0
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val mean = values.average()
        val std = discovery.populationStd(values, mean)
        assertTrue(abs(std - 2.0) < 1e-9, "Expected 2.0 but got $std")
    }

    @Test
    fun `populationStd differs from sample std for small N`() {
        // For N=2, population std = |a-b|/2, sample std = |a-b|/sqrt(1) = |a-b|
        val values = listOf(0.0, 10.0)
        val popStd = discovery.populationStd(values, 5.0)
        assertTrue(abs(popStd - 5.0) < 1e-9, "Population std should be 5.0, got $popStd")
        // Confirms we're NOT using ddof=1 (which would give 7.07...)
    }

    // ── median ────────────────────────────────────────────────────────────────

    @Test
    fun `median of odd list returns middle element`() {
        assertEquals(3.0, discovery.median(listOf(1.0, 3.0, 5.0)))
    }

    @Test
    fun `median of even list returns average of two middle elements`() {
        // np.median([1.0, 2.0, 3.0, 4.0]) = 2.5
        assertEquals(2.5, discovery.median(listOf(1.0, 2.0, 3.0, 4.0)))
    }

    @Test
    fun `median of single value returns that value`() {
        assertEquals(7.0, discovery.median(listOf(7.0)))
    }

    // ── smoothFeatures ────────────────────────────────────────────────────────

    @Test
    fun `smooth passes through empty frames unchanged`() {
        val seq = listOf(
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 90.0),
            emptyFrame(),
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 90.0),
        )
        val smoothed = discovery.smoothFeatures(seq)
        assertEquals(3, smoothed.size)
        assertTrue(smoothed[1].isEmpty)
    }

    @Test
    fun `smooth computes rolling average over window`() {
        // Window = 3; values 10, 20, 30 → at index 2, avg = (10+20+30)/3 = 20
        val discovery3 = StateDiscovery(smoothingWindow = 3, minHoldFrames = 2)
        val seq = listOf(
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 10.0),
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 20.0),
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 30.0),
        )
        val smoothed = discovery3.smoothFeatures(seq)
        val avg = smoothed[2][FeatureKey.LEFT_KNEE_ANGLE]
        assertNotNull(avg)
        assertTrue(abs(avg - 20.0) < 1e-9, "Expected 20.0 but got $avg")
    }

    // ── detectStableSegments ──────────────────────────────────────────────────

    @Test
    fun `single long stable block produces one segment`() {
        val seq = repeatedFrames(20, kneeAngle = 90.0)
        val segments = discovery.detectStableSegments(seq)
        assertEquals(1, segments.size)
        assertEquals(0, segments[0].segmentIndex)
    }

    @Test
    fun `two distinct poses produce two segments`() {
        // 10 frames at 90°, then 10 frames at 160° (distance = 70/180 ≈ 0.39 > threshold)
        val seq = repeatedFrames(10, 90.0) + repeatedFrames(10, 160.0)
        val segments = discovery.detectStableSegments(seq)
        assertEquals(2, segments.size)
        assertEquals(0, segments[0].segmentIndex)
        assertEquals(1, segments[1].segmentIndex)
    }

    @Test
    fun `block shorter than minHoldFrames is not emitted`() {
        // Only 3 frames — below minHoldFrames=5
        val seq = repeatedFrames(3, 90.0)
        val segments = discovery.detectStableSegments(seq)
        assertTrue(segments.isEmpty())
    }

    // ── asymmetryVeto ─────────────────────────────────────────────────────────


    // ── computeMeanFeatures ───────────────────────────────────────────────────

    @Test
    fun `mean features averages values across frames`() {
        val frames = listOf(
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 80.0),
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 100.0),
        )
        val mean = discovery.computeMeanFeatures(frames)
        val v = mean[FeatureKey.LEFT_KNEE_ANGLE]
        assertNotNull(v)
        assertTrue(abs(v - 90.0) < 1e-9)
    }

    @Test
    fun `mean features skips absent keys per frame`() {
        val frames = listOf(
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 90.0),
            frameOf(FeatureKey.LEFT_KNEE_ANGLE to 90.0, FeatureKey.TORSO_ANGLE to 5.0),
        )
        val mean = discovery.computeMeanFeatures(frames)
        // LEFT_KNEE_ANGLE present in both → mean = 90
        // TORSO_ANGLE present in 1 of 2   → mean = 5.0 (only one contributor)
        assertEquals(90.0, mean[FeatureKey.LEFT_KNEE_ANGLE])
        assertEquals(5.0, mean[FeatureKey.TORSO_ANGLE])
    }

    // ── discoverStates end-to-end ─────────────────────────────────────────────

    @Test
    fun `two distinct repeated states produce correct temporal sequence`() {
        // Pattern: pose A (10 frames) → pose B (10 frames) → pose A again (10 frames)
        // Expected: 2 states, temporal sequence [0, 1, 0]
        val poseA = repeatedFrames(10, kneeAngle = 90.0)
        val poseB = repeatedFrames(10, kneeAngle = 160.0)
        val seq = poseA + poseB + poseA

        val result = discovery.discoverStates(seq, fps = 30.0)
        assertFalse(result.isEmpty)
        assertEquals(2, result.states.size)
        assertEquals(listOf(0, 1, 0), result.temporalSequence)
    }

    @Test
    fun `returns EMPTY for sequence shorter than minHoldFrames`() {
        val seq = repeatedFrames(3, 90.0)
        val result = discovery.discoverStates(seq, fps = 30.0)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `min hold duration is 60 percent of median segment duration`() {
        // 30 fps, 30 frames → duration = 1.0s → minHold = 0.6s
        val seq = repeatedFrames(30, kneeAngle = 90.0)
        val result = discovery.discoverStates(seq, fps = 30.0)
        assertFalse(result.isEmpty)
        val state = result.states.first()
        assertTrue(
            abs(state.minHoldDuration - 0.6) < 0.1,
            "Expected ~0.6s but got ${state.minHoldDuration}"
        )
    }
}

// ── DistanceCalculator tests ──────────────────────────────────────────────────

class DistanceCalculatorTest {

    @Test
    fun `returns infinity for disjoint feature maps`() {
        val a = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 90.0)
        val b = RawFeatureMap.of(FeatureKey.TORSO_ANGLE to 5.0)
        assertEquals(Double.POSITIVE_INFINITY, DistanceCalculator.compute(a, b))
    }

    @Test
    fun `angle distance handles wraparound correctly`() {
        // |350 - 10| = 340 → > 180 → 360 - 340 = 20 → 20/180 ≈ 0.111
        val dist = DistanceCalculator.normalizedDistance(FeatureKey.LEFT_KNEE_ANGLE, 350.0, 10.0)
        assertTrue(abs(dist - 20.0 / 180.0) < 1e-9, "Got $dist")
    }

    @Test
    fun `angle distance without wraparound is diff divided by 180`() {
        // |90 - 45| = 45 → 45/180 = 0.25
        val dist = DistanceCalculator.normalizedDistance(FeatureKey.LEFT_KNEE_ANGLE, 90.0, 45.0)
        assertTrue(abs(dist - 0.25) < 1e-9, "Got $dist")
    }

    @Test
    fun `non-angle distance is absolute difference`() {
        val dist = DistanceCalculator.normalizedDistance(FeatureKey.LEFT_ARM_ELEVATION, 0.5, 0.2)
        assertTrue(abs(dist - 0.3) < 1e-9, "Got $dist")
    }

    @Test
    fun `mean distance averages all common key distances`() {
        // Both maps share LEFT_KNEE_ANGLE and TORSO_ANGLE
        // knee: |90-90| / 180 = 0.0
        // torso: |10-0| / 180 = 0.0556
        // mean = 0.0278
        val a = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 90.0, FeatureKey.TORSO_ANGLE to 10.0)
        val b = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 90.0, FeatureKey.TORSO_ANGLE to 0.0)
        val dist = DistanceCalculator.compute(a, b)
        val expected = (0.0 + 10.0 / 180.0) / 2.0
        assertTrue(abs(dist - expected) < 1e-9, "Got $dist, expected $expected")
    }
}
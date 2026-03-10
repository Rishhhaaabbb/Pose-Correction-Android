// app/src/test/java/com/posemonitor/PoseMonitorIntegrationTest.kt

package com.posemonitor2

import com.core.poseengine.DiscoveryResult
import com.core.poseengine.PoseGraph
import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.PoseState
import com.core.poseengine.model.RawFeatureMap
import com.core.poseengine.monitor.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*

/**
 * App-level integration test for the full pose monitoring pipeline.
 *
 * Tests the complete flow:
 *   MockFeatureGenerator → PoseMonitorEngine → FrameStatus
 *
 * Uses randomly generated features within per-state tolerance bands so
 * tests are realistic but deterministic (fixed Random seed).
 *
 * ── What is tested ────────────────────────────────────────────────────────────
 *  1. Full pipeline: features flow through engine and produce valid FrameStatus
 *  2. State transitions: engine advances when hold is satisfied
 *  3. Rep counting: reps increment after completing the full sequence
 *  4. Session completion: engine halts after maxReps is reached
 *  5. Incorrect frames: out-of-tolerance features are rejected
 *  6. Null features: missing skeleton is handled gracefully
 *  7. Multi-state exercise: 3-state squat-like sequence end-to-end
 */
class PoseMonitorIntegrationTest {

    // ── Test exercise definition ───────────────────────────────────────────────
    //
    // Simulates a simple 2-state exercise:
    //   State 0 — standing  (knee ~170°, torso ~0°)
    //   State 1 — squat     (knee ~90°,  torso ~20°)
    //
    // Temporal sequence: [0, 1] → one rep = stand → squat

    private val standingMean = mapOf(
        FeatureKey.LEFT_KNEE_ANGLE  to 170.0,
        FeatureKey.RIGHT_KNEE_ANGLE to 170.0,
        FeatureKey.TORSO_ANGLE      to 0.0,
        FeatureKey.LEFT_ARM_ELEVATION  to -0.8,
        FeatureKey.RIGHT_ARM_ELEVATION to -0.8,
    )
    private val standingTolerances = mapOf(
        FeatureKey.LEFT_KNEE_ANGLE  to 15.0,
        FeatureKey.RIGHT_KNEE_ANGLE to 15.0,
        FeatureKey.TORSO_ANGLE      to 10.0,
        FeatureKey.LEFT_ARM_ELEVATION  to 0.2,
        FeatureKey.RIGHT_ARM_ELEVATION to 0.2,
    )

    private val squatMean = mapOf(
        FeatureKey.LEFT_KNEE_ANGLE  to 90.0,
        FeatureKey.RIGHT_KNEE_ANGLE to 90.0,
        FeatureKey.TORSO_ANGLE      to 20.0,
        FeatureKey.LEFT_ARM_ELEVATION  to -0.3,
        FeatureKey.RIGHT_ARM_ELEVATION to -0.3,
    )
    private val squatTolerances = mapOf(
        FeatureKey.LEFT_KNEE_ANGLE  to 15.0,
        FeatureKey.RIGHT_KNEE_ANGLE to 15.0,
        FeatureKey.TORSO_ANGLE      to 12.0,
        FeatureKey.LEFT_ARM_ELEVATION  to 0.2,
        FeatureKey.RIGHT_ARM_ELEVATION to 0.2,
    )

    private val state0 = PoseState(
        stateId = 0,
        meanFeatures = RawFeatureMap.of(*standingMean.entries.map { it.key to it.value }.toTypedArray()),
        featureTolerances = RawFeatureMap.of(*standingTolerances.entries.map { it.key to it.value }.toTypedArray()),
        minHoldDuration = 0.5,
    )
    private val state1 = PoseState(
        stateId = 1,
        meanFeatures = RawFeatureMap.of(*squatMean.entries.map { it.key to it.value }.toTypedArray()),
        featureTolerances = RawFeatureMap.of(*squatTolerances.entries.map { it.key to it.value }.toTypedArray()),
        minHoldDuration = 0.5,
    )

    private fun buildGraph(): PoseGraph =
        PoseGraph.buildFromDiscoveryResult(
            DiscoveryResult(listOf(state0, state1), listOf(0, 1))
        )

    private fun makeEngine(
        graph: PoseGraph = buildGraph(),
        time: FakeTimeSource = FakeTimeSource(0.0),
        maxReps: Int? = null,
    ) = PoseMonitorEngine(
        graph = graph,
        config = MonitorConfig(
            guidanceDurationSeconds = 0.0,
            enableAdaptive = false,
            progressiveTolerance = false,
            maxReps = maxReps,
        ),
        time = time,
    )

    // ── MockFeatureGenerator ──────────────────────────────────────────────────

    /**
     * Generates [RawFeatureMap] instances with values randomly sampled within
     * [fraction] × tolerance of each feature's mean value.
     *
     * [seed] makes generation deterministic across runs.
     *
     * @param mean        Target feature means (from a [PoseState]).
     * @param tolerances  Per-feature tolerance bands.
     * @param fraction    How far within the tolerance band to sample [0.0, 1.0].
     *                    Default 0.7 — well within tolerance, reliably matches.
     * @param seed        Random seed for reproducibility.
     */
    private fun generateFeaturesWithinTolerance(
        mean: Map<FeatureKey, Double>,
        tolerances: Map<FeatureKey, Double>,
        fraction: Double = 0.7,
        seed: Long = 42L,
    ): RawFeatureMap {
        val rng = Random(seed)
        val features = mean.entries.associate { (key, meanVal) ->
            val tol = tolerances[key] ?: 10.0
            val offset = (rng.nextDouble() * 2.0 - 1.0) * tol * fraction
            key to (meanVal + offset)
        }
        return RawFeatureMap(features)
    }

    /**
     * Generates features that are OUTSIDE tolerance for [targetState] —
     * used to simulate incorrect pose frames.
     *
     * Adds 2× tolerance to each value, guaranteeing a mismatch.
     */
    private fun generateFeaturesOutsideTolerance(
        mean: Map<FeatureKey, Double>,
        tolerances: Map<FeatureKey, Double>,
    ): RawFeatureMap {
        val features = mean.entries.associate { (key, meanVal) ->
            val tol = tolerances[key] ?: 10.0
            key to (meanVal + tol * 2.5)   // 2.5× outside tolerance
        }
        return RawFeatureMap(features)
    }

    /**
     * Feeds [n] correct frames for [stateIndex] (0=standing, 1=squat)
     * advancing time by [deltaSeconds] per frame.
     *
     * Returns the last [FrameStatus] produced.
     */
    private fun holdState(
        engine: PoseMonitorEngine,
        time: FakeTimeSource,
        stateIndex: Int,
        n: Int,
        deltaSeconds: Double = 0.1,
        seed: Long = 42L,
    ): FrameStatus {
        val mean = if (stateIndex == 0) standingMean else squatMean
        val tols = if (stateIndex == 0) standingTolerances else squatTolerances
        var last: FrameStatus? = null
        repeat(n) { i ->
            val features = generateFeaturesWithinTolerance(mean, tols, seed = seed + i)
            last = engine.processFeatures(features)
            time.advanceBy(deltaSeconds)
        }
        return last!!
    }

    // ── Pipeline tests ────────────────────────────────────────────────────────

    @Test
    fun `pipeline produces valid FrameStatus for correct features`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        val features = generateFeaturesWithinTolerance(standingMean, standingTolerances)
        val status = engine.processFeatures(features)

        assertNotNull(status)
        assertTrue(status.isCorrect, "Expected correct match for within-tolerance features")
        assertEquals(0, status.stateId)
        assertEquals(MonitorPhase.HOLDING, status.phase)
        assertFalse(status.sessionComplete)
    }

    @Test
    fun `pipeline rejects out-of-tolerance features`() {
        val engine = makeEngine()
        val features = generateFeaturesOutsideTolerance(standingMean, standingTolerances)
        val status = engine.processFeatures(features)
        assertFalse(status.isCorrect, "Expected rejection for out-of-tolerance features")
        assertEquals(0.0, status.holdProgress)
    }

    @Test
    fun `pipeline handles null features gracefully`() {
        val engine = makeEngine()
        val status = engine.processFeatures(null)
        assertFalse(status.isCorrect)
        assertTrue(status.feedback.contains("visible", ignoreCase = true))
        assertEquals(0.0, status.holdProgress)
    }

    @Test
    fun `hold progress increases with correct frames over time`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        val f = generateFeaturesWithinTolerance(standingMean, standingTolerances)
        engine.processFeatures(f)
        time.advanceBy(0.25)
        val status = engine.processFeatures(f)

        assertTrue(
            status.holdProgress > 0.3,
            "Expected progress ~0.5 after 0.25s, got ${status.holdProgress}"
        )
    }

    @Test
    fun `hold progress resets after incorrect frame`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        val correct = generateFeaturesWithinTolerance(standingMean, standingTolerances)
        val wrong = generateFeaturesOutsideTolerance(standingMean, standingTolerances)

        engine.processFeatures(correct)
        time.advanceBy(0.3)
        engine.processFeatures(correct)

        // Incorrect frame — resets hold
        engine.processFeatures(wrong)
        time.advanceBy(0.05)

        val status = engine.processFeatures(correct)
        assertTrue(
            status.holdProgress < 0.2,
            "Progress should have reset after incorrect frame, got ${status.holdProgress}"
        )
    }

    // ── State transition tests ────────────────────────────────────────────────

    @Test
    fun `engine transitions from state 0 to state 1 after hold`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // Hold state 0 for > 0.5s (6 frames × 0.1s = 0.6s)
        holdState(engine, time, stateIndex = 0, n = 6)

        // Now feed squat features — should be on state 1
        val squatFeatures = generateFeaturesWithinTolerance(squatMean, squatTolerances)
        val status = engine.processFeatures(squatFeatures)

        assertEquals(1, status.stateId, "Expected transition to state 1 after hold")
    }

    @Test
    fun `engine does not transition before hold duration is met`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // Only 2 frames × 0.1s = 0.2s < 0.5s required
        holdState(engine, time, stateIndex = 0, n = 2)

        val squatFeatures = generateFeaturesWithinTolerance(squatMean, squatTolerances)
        val status = engine.processFeatures(squatFeatures)

        // Still on state 0 — squat features don't match state 0
        assertEquals(0, status.stateId, "Should not transition before hold is complete")
        assertFalse(status.isCorrect)
    }

    @Test
    fun `state transitions produce phase TRANSITIONING during grace period`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        holdState(engine, time, stateIndex = 0, n = 6)

        val squatFeatures = generateFeaturesWithinTolerance(squatMean, squatTolerances)
        val status = engine.processFeatures(squatFeatures)

        // After transition, first frame is in grace
        assertTrue(
            status.phase == MonitorPhase.TRANSITIONING || status.phase == MonitorPhase.HOLDING,
            "Expected TRANSITIONING or HOLDING after state change, got ${status.phase}"
        )
    }

    // ── Rep counting tests ────────────────────────────────────────────────────

    @Test
    fun `completing full sequence increments rep count to 1`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // Complete state 0
        holdState(engine, time, stateIndex = 0, n = 6)

        // Complete state 1
        val lastStatus = holdState(engine, time, stateIndex = 1, n = 6)

        assertEquals(1, lastStatus.repsCompleted, "Expected 1 rep after full sequence")
    }

    @Test
    fun `two full sequences produce 2 reps`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // Rep 1
        holdState(engine, time, stateIndex = 0, n = 6)
        holdState(engine, time, stateIndex = 1, n = 6)

        // Rep 2
        holdState(engine, time, stateIndex = 0, n = 6)
        val lastStatus = holdState(engine, time, stateIndex = 1, n = 6)

        assertEquals(2, lastStatus.repsCompleted, "Expected 2 reps after two full sequences")
    }

    @Test
    fun `rep count does not increment on partial sequence`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // Only complete state 0, not state 1
        val lastStatus = holdState(engine, time, stateIndex = 0, n = 6)

        assertEquals(0, lastStatus.repsCompleted, "Rep count should be 0 for partial sequence")
    }

    // ── Session completion tests ──────────────────────────────────────────────

    @Test
    fun `session completes after maxReps=1`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time, maxReps = 1)

        holdState(engine, time, stateIndex = 0, n = 6)
        holdState(engine, time, stateIndex = 1, n = 6)

        // Next frame after session complete
        val features = generateFeaturesWithinTolerance(standingMean, standingTolerances)
        val status = engine.processFeatures(features)

        assertTrue(status.sessionComplete, "Session should be complete after maxReps=1")
        assertEquals(MonitorPhase.SESSION_COMPLETE, status.phase)
    }

    @Test
    fun `session continues when maxReps is null`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time, maxReps = null)

        // Complete 3 full reps
        repeat(3) {
            holdState(engine, time, stateIndex = 0, n = 6)
            holdState(engine, time, stateIndex = 1, n = 6)
        }

        val features = generateFeaturesWithinTolerance(standingMean, standingTolerances)
        val status = engine.processFeatures(features)

        assertFalse(status.sessionComplete, "Session should not complete when maxReps is null")
    }

    @Test
    fun `session summary is accurate after completion`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time, maxReps = 2)

        repeat(2) {
            holdState(engine, time, stateIndex = 0, n = 6)
            holdState(engine, time, stateIndex = 1, n = 6)
        }

        val summary = engine.getSessionSummary()
        assertEquals(2, summary.totalReps)
        assertTrue(summary.skippedStateIds.isEmpty())
        assertTrue(summary.overallAccuracy >= 0.0)
    }

    // ── 3-state exercise integration ──────────────────────────────────────────

    @Test
    fun `three-state exercise completes one rep end-to-end`() {
        // State 0: standing, State 1: squat, State 2: arms raised
        val armsRaisedMean = mapOf(
            FeatureKey.LEFT_KNEE_ANGLE  to 170.0,
            FeatureKey.RIGHT_KNEE_ANGLE to 170.0,
            FeatureKey.LEFT_ARM_ELEVATION  to 0.8,
            FeatureKey.RIGHT_ARM_ELEVATION to 0.8,
        )
        val armsRaisedTols = mapOf(
            FeatureKey.LEFT_KNEE_ANGLE  to 15.0,
            FeatureKey.RIGHT_KNEE_ANGLE to 15.0,
            FeatureKey.LEFT_ARM_ELEVATION  to 0.2,
            FeatureKey.RIGHT_ARM_ELEVATION to 0.2,
        )
        val state2 = PoseState(
            stateId = 2,
            meanFeatures = RawFeatureMap.of(*armsRaisedMean.entries.map { it.key to it.value }.toTypedArray()),
            featureTolerances = RawFeatureMap.of(*armsRaisedTols.entries.map { it.key to it.value }.toTypedArray()),
            minHoldDuration = 0.5,
        )

        val graph = PoseGraph.buildFromDiscoveryResult(
            DiscoveryResult(listOf(state0, state1, state2), listOf(0, 1, 2))
        )
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(graph = graph, time = time)

        // Complete all 3 states
        holdState(engine, time, stateIndex = 0, n = 6)
        holdState(engine, time, stateIndex = 1, n = 6)

        // State 2 — arms raised
        var lastStatus: FrameStatus? = null
        repeat(6) { i ->
            val f = generateFeaturesWithinTolerance(armsRaisedMean, armsRaisedTols, seed = 99L + i)
            lastStatus = engine.processFeatures(f)
            time.advanceBy(0.1)
        }

        assertEquals(1, lastStatus!!.repsCompleted, "Expected 1 rep after 3-state sequence")
    }

    // ── Streak tests ──────────────────────────────────────────────────────────

    @Test
    fun `streak builds on consecutive correct frames`() {
        val engine = makeEngine()
        val f = generateFeaturesWithinTolerance(standingMean, standingTolerances)
        repeat(5) { engine.processFeatures(f) }
        val status = engine.processFeatures(f)
        assertTrue(status.streak >= 5, "Expected streak >= 5, got ${status.streak}")
    }

    @Test
    fun `streak resets after incorrect frame`() {
        val engine = makeEngine()
        val correct = generateFeaturesWithinTolerance(standingMean, standingTolerances)
        val wrong = generateFeaturesOutsideTolerance(standingMean, standingTolerances)

        repeat(5) { engine.processFeatures(correct) }
        engine.processFeatures(wrong)
        val status = engine.processFeatures(correct)

        assertEquals(1, status.streak, "Streak should reset to 1 after incorrect frame")
    }

    // ── Randomized stress test ────────────────────────────────────────────────

    @Test
    fun `randomized correct feature sequence always produces isCorrect=true`() {
        val engine = makeEngine()
        val rng = Random(1234L)

        // 50 randomly seeded correct frames — all must match
        var allCorrect = true
        repeat(50) { i ->
            val f = generateFeaturesWithinTolerance(
                standingMean, standingTolerances,
                fraction = 0.6,
                seed = rng.nextLong()
            )
            val status = engine.processFeatures(f)
            if (!status.isCorrect) allCorrect = false
        }
        assertTrue(allCorrect, "All within-tolerance frames should be correct")
    }

    @Test
    fun `randomized out-of-tolerance sequence always produces isCorrect=false`() {
        val engine = makeEngine()

        // 20 out-of-tolerance frames — all must be rejected
        var allRejected = true
        repeat(20) {
            val f = generateFeaturesOutsideTolerance(standingMean, standingTolerances)
            val status = engine.processFeatures(f)
            if (status.isCorrect) allRejected = false
        }
        assertTrue(allRejected, "All out-of-tolerance frames should be rejected")
    }
}
// core/poseengine/src/test/kotlin/monitor/PoseMonitorEngineTest.kt

package com.core.poseengine.monitor

import com.core.poseengine.DiscoveryResult
import com.core.poseengine.PoseGraph
import com.core.poseengine.model.*
import kotlin.test.*

class PoseMonitorEngineTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeState(id: Int, kneeAngle: Double = 90.0, hold: Double = 0.5) = PoseState(
        stateId = id,
        meanFeatures = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to kneeAngle),
        featureTolerances = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 15.0),
        minHoldDuration = hold,
    )

    private fun twoStateGraph(): PoseGraph {
        val s0 = makeState(0, 90.0, hold = 0.5)
        val s1 = makeState(1, 160.0, hold = 0.5)
        return PoseGraph.buildFromDiscoveryResult(
            DiscoveryResult(listOf(s0, s1), listOf(0, 1))
        )
    }

    private fun featuresFor(kneeAngle: Double) =
        RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to kneeAngle)

    private fun makeEngine(
        graph: PoseGraph = twoStateGraph(),
        time: FakeTimeSource = FakeTimeSource(0.0),
        config: MonitorConfig = MonitorConfig(
            guidanceDurationSeconds = 0.0,   // disable guidance for most tests
            maxReps = null,
            enableAdaptive = false,
        ),
    ) = PoseMonitorEngine(graph = graph, config = config, time = time)

    // ── Basic matching ────────────────────────────────────────────────────────

    @Test
    fun `correct features for state 0 returns isCorrect=true`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)
        val status = engine.processFeatures(featuresFor(90.0))
        assertTrue(status.isCorrect)
        assertEquals(0, status.stateId)
    }

    @Test
    fun `incorrect features returns isCorrect=false`() {
        val engine = makeEngine()
        val status = engine.processFeatures(featuresFor(160.0)) // wrong for state 0
        assertFalse(status.isCorrect)
    }

    @Test
    fun `null features returns isCorrect=false with visibility feedback`() {
        val engine = makeEngine()
        val status = engine.processFeatures(null)
        assertFalse(status.isCorrect)
        assertTrue(status.feedback.contains("visible", ignoreCase = true))
    }

    // ── Hold progress ─────────────────────────────────────────────────────────

    @Test
    fun `hold progress accumulates over time`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // First frame — starts accumulation
        engine.processFeatures(featuresFor(90.0))
        time.advanceBy(0.25)

        val status = engine.processFeatures(featuresFor(90.0))
        // 0.25s held out of 0.5s required → 50% progress
        assertTrue(status.holdProgress >= 0.45, "Expected ~0.5, got ${status.holdProgress}")
    }

    @Test
    fun `hold progress resets on incorrect frame`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        engine.processFeatures(featuresFor(90.0))
        time.advanceBy(0.25)
        engine.processFeatures(featuresFor(90.0))

        // Now an incorrect frame
        engine.processFeatures(featuresFor(160.0))
        time.advanceBy(0.1)

        // Next correct frame should start from 0 because timer reset
        val status = engine.processFeatures(featuresFor(90.0))
        assertTrue(status.holdProgress < 0.3, "Progress should have reset, got ${status.holdProgress}")
    }

    // ── State transition ──────────────────────────────────────────────────────

    @Test
    fun `state transitions to 1 after hold completes`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // Hold state 0 long enough
        engine.processFeatures(featuresFor(90.0))
        time.advanceBy(0.6)  // > 0.5s required hold
        engine.processFeatures(featuresFor(90.0))

        // Now should be on state 1
        val status = engine.processFeatures(featuresFor(160.0))
        assertEquals(1, status.stateId)
    }

    // ── Rep counting ──────────────────────────────────────────────────────────

    @Test
    fun `completing full sequence increments rep counter`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(time = time)

        // Complete state 0
        engine.processFeatures(featuresFor(90.0))
        time.advanceBy(0.6)
        engine.processFeatures(featuresFor(90.0))

        // Complete state 1
        engine.processFeatures(featuresFor(160.0))
        time.advanceBy(0.6)
        val status = engine.processFeatures(featuresFor(160.0))

        assertEquals(1, status.repsCompleted)
    }

    @Test
    fun `session completes when maxReps reached`() {
        val time = FakeTimeSource(0.0)
        val engine = makeEngine(
            time = time,
            config = MonitorConfig(maxReps = 1, guidanceDurationSeconds = 0.0, enableAdaptive = false),
        )

        // Complete one rep
        engine.processFeatures(featuresFor(90.0))
        time.advanceBy(0.6)
        engine.processFeatures(featuresFor(90.0))
        engine.processFeatures(featuresFor(160.0))
        time.advanceBy(0.6)
        engine.processFeatures(featuresFor(160.0))

        val status = engine.processFeatures(featuresFor(90.0))
        assertTrue(status.sessionComplete)
        assertEquals(MonitorPhase.SESSION_COMPLETE, status.phase)
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    @Test
    fun `streak increments on consecutive correct frames`() {
        val engine = makeEngine()
        engine.processFeatures(featuresFor(90.0))
        engine.processFeatures(featuresFor(90.0))
        val status = engine.processFeatures(featuresFor(90.0))
        assertTrue(status.streak >= 2)
    }

    @Test
    fun `streak resets on incorrect frame`() {
        val engine = makeEngine()
        engine.processFeatures(featuresFor(90.0))
        engine.processFeatures(featuresFor(90.0))
        engine.processFeatures(featuresFor(160.0)) // wrong
        val status = engine.processFeatures(featuresFor(90.0))
        assertEquals(1, status.streak)
    }

    // ── Skip ─────────────────────────────────────────────────────────────────

    @Test
    fun `skipCurrentState advances to next state`() {
        val engine = makeEngine()
        engine.processFeatures(featuresFor(90.0))
        engine.skipCurrentState()
        val status = engine.processFeatures(featuresFor(160.0))
        assertEquals(1, status.stateId)
    }

    @Test
    fun `skipped state is recorded in session summary`() {
        val engine = makeEngine()
        engine.skipCurrentState()
        val summary = engine.getSessionSummary()
        assertTrue(0 in summary.skippedStateIds)
    }

    // ── Progressive tolerance ─────────────────────────────────────────────────

    @Test
    fun `progressive tolerance is 1_0 for fewer than 5 attempts`() {
        val config = MonitorConfig(enableAdaptive = false, guidanceDurationSeconds = 0.0)
        val engine = makeEngine(config = config)
        // Send 4 incorrect frames
        repeat(4) { engine.processFeatures(featuresFor(160.0)) } // wrong
        // 5th frame — still < 5 attempts, tolerance should not be scaled
        val status = engine.processFeatures(featuresFor(90.0))
        assertTrue(status.isCorrect, "Should match at unscaled tolerance")
    }
}

// ── SequenceNavigator tests ───────────────────────────────────────────────────

class SequenceNavigatorTest {

    @Test
    fun `advances to next index normally`() {
        val nav = SequenceNavigator(listOf(0, 1, 2))
        val result = nav.nextIndex(currentIndex = 0, currentStateId = 0)
        assertEquals(1, result.nextSequenceIndex)
        assertEquals(1, result.nextStateId)
        assertFalse(result.repCompleted)
    }

    @Test
    fun `wraps around at end of sequence and signals repCompleted`() {
        val nav = SequenceNavigator(listOf(0, 1, 2))
        val result = nav.nextIndex(currentIndex = 2, currentStateId = 2)
        assertEquals(0, result.nextSequenceIndex)
        assertTrue(result.repCompleted)
    }

    @Test
    fun `first==last special case jumps to index 1`() {
        // Sequence: [0, 1, 0] — first and last are both state 0
        val nav = SequenceNavigator(listOf(0, 1, 0))
        // When current is state 0 (the shared state) and we just wrapped:
        val result = nav.nextIndex(currentIndex = 2, currentStateId = 0)
        assertEquals(1, result.nextSequenceIndex)
        assertEquals(1, result.nextStateId)
    }

    @Test
    fun `first==last special case does not apply when current state differs`() {
        val nav = SequenceNavigator(listOf(0, 1, 0))
        // At index 0 (state 0), currentStateId=0, next should be index 1 normally
        val result = nav.nextIndex(currentIndex = 0, currentStateId = 0)
        assertEquals(1, result.nextSequenceIndex)
    }
}

// ── FeedbackGenerator tests ───────────────────────────────────────────────────

class FeedbackGeneratorTest {

    private val generator = FeedbackGenerator(historyLength = 15)

    @Test
    fun `unstable detection returns hold-steady message`() {
        // Feed 15 frames with wildly varying torso angle
        repeat(15) { i ->
            generator.updateHistory(
                RawFeatureMap.of(FeatureKey.TORSO_ANGLE to if (i % 2 == 0) 0.0 else 20.0)
            )
        }
        // std([0,20,0,20,...]) >> 8.0
        val feedback = generator.generateFeedback(
            deviations = mapOf(FeatureKey.LEFT_KNEE_ANGLE to 15.0),
            currentFeatures = RawFeatureMap.EMPTY,
        )
        assertEquals("Hold steady - minimize movement", feedback)
    }

    @Test
    fun `core joint prioritized over peripheral`() {
        val deviations = mapOf(
            FeatureKey.BODY_CENTER_Y to 100.0,       // large but not core
            FeatureKey.LEFT_KNEE_ANGLE to 20.0,      // core
        )
        val feedback = generator.generateFeedback(deviations, RawFeatureMap.EMPTY)
        assertTrue("knee" in feedback.lowercase(), "Expected knee feedback, got: $feedback")
    }

    @Test
    fun `positive knee deviation produces straighten feedback`() {
        val deviations = mapOf(FeatureKey.LEFT_KNEE_ANGLE to 15.0)
        val feedback = generator.generateFeedback(deviations, RawFeatureMap.EMPTY)
        assertTrue("Straighten" in feedback, "Got: $feedback")
    }

    @Test
    fun `negative knee deviation produces bend feedback`() {
        val deviations = mapOf(FeatureKey.LEFT_KNEE_ANGLE to -15.0)
        val feedback = generator.generateFeedback(deviations, RawFeatureMap.EMPTY)
        assertTrue("Bend" in feedback || "more" in feedback, "Got: $feedback")
    }
}
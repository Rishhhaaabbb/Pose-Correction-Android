// core/poseengine/src/test/kotlin/PoseGraphTest.kt

package com.core.poseengine

import com.core.poseengine.model.*
import kotlin.test.*

class PoseGraphTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun minimalState(id: Int): PoseState = PoseState(
        stateId = id,
        meanFeatures = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 90.0),
        featureTolerances = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 10.0),
        minHoldDuration = 0.5,
        meanKeypoints = null,
    )

    private fun threeStateLinearGraph(): PoseGraph =
        PoseGraph.Builder()
            .addStates(listOf(minimalState(0), minimalState(1), minimalState(2)))
            .buildTransitionsFromTemporalSequence(listOf(0, 1, 2))
            .build()

    // ── Builder / transition construction ────────────────────────────────────

    @Test
    fun `temporal sequence builds correct edges`() {
        val graph = threeStateLinearGraph()
        assertTrue(graph.isValidTransition(0, 1))
        assertTrue(graph.isValidTransition(1, 2))
        // Wrap-around: last(2) != first(0), so 2→0 is added
        assertTrue(graph.isValidTransition(2, 0))
        // No other edges
        assertFalse(graph.isValidTransition(0, 2))
    }

    @Test
    fun `duplicate transitions are deduplicated`() {
        val graph = PoseGraph.Builder()
            .addStates(listOf(minimalState(0), minimalState(1)))
            .apply {
                // Add 0→1 three times
                addTransition(0, 1)
                addTransition(0, 1)
                addTransition(0, 1)
            }
            .build()
        // Should appear only once
        assertEquals(1, graph.getNextStates(0).size)
    }

    @Test
    fun `no wrap-around when last state equals first state`() {
        // Temporal sequence where last == first: [0, 1, 0]
        // Last=0, First=0 → no wrap-around edge should be added for 0→0 (self-loop)
        val graph = PoseGraph.Builder()
            .addStates(listOf(minimalState(0), minimalState(1)))
            .buildTransitionsFromTemporalSequence(listOf(0, 1, 0))
            .build()
        // Edges from temporal pairs: 0→1, 1→0
        assertTrue(graph.isValidTransition(0, 1))
        assertTrue(graph.isValidTransition(1, 0))
        // No self-loop 0→0
        assertFalse(graph.isValidTransition(0, 0))
    }

    @Test
    fun `buildFromDiscoveryResult attaches temporal sequence to metadata`() {
        val states = listOf(minimalState(0), minimalState(1))
        val seq = listOf(0, 1, 0, 1)
        val result = DiscoveryResult(states = states, temporalSequence = seq)
        val graph = PoseGraph.buildFromDiscoveryResult(result)
        assertEquals(seq, graph.temporalSequence)
    }

    // ── State lookup ──────────────────────────────────────────────────────────

    @Test
    fun `getState returns correct state by ID`() {
        val graph = threeStateLinearGraph()
        assertEquals(1, graph.getState(1)?.stateId)
    }

    @Test
    fun `getState returns null for unknown ID`() {
        val graph = threeStateLinearGraph()
        assertNull(graph.getState(99))
    }

    @Test
    fun `initialState returns first state`() {
        val graph = threeStateLinearGraph()
        assertEquals(0, graph.initialState?.stateId)
    }

    // ── Incoming transitions ──────────────────────────────────────────────────

    @Test
    fun `getIncomingTransitions finds all sources`() {
        // 0→2 and 1→2 both target state 2
        val graph = PoseGraph.Builder()
            .addStates(listOf(minimalState(0), minimalState(1), minimalState(2)))
            .addTransition(0, 2)
            .addTransition(1, 2)
            .addTransition(2, 0)  // so state 2 isn't isolated
            .build()
        val incoming = graph.getIncomingTransitions(2).toSet()
        assertEquals(setOf(0, 1), incoming)
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    @Test
    fun `linear graph is detected as linear`() {
        val stats = threeStateLinearGraph().getStatistics()
        assertTrue(stats.isLinear)
    }

    @Test
    fun `cycle is detected when edge points to lower ID`() {
        val stats = threeStateLinearGraph().getStatistics()
        // 2→0 is a back edge (0 < 2), so hasCycles = true
        assertTrue(stats.hasCycles)
    }

    @Test
    fun `statistics counts match graph structure`() {
        val stats = threeStateLinearGraph().getStatistics()
        assertEquals(3, stats.numStates)
        // Edges: 0→1, 1→2, 2→0 (wrap-around)
        assertEquals(3, stats.numTransitions)
        assertEquals(1, stats.maxOutgoing)
        assertEquals(1, stats.maxIncoming)
    }

    @Test
    fun `empty graph returns EMPTY statistics`() {
        val stats = GraphStatistics.EMPTY
        assertEquals(0, stats.numStates)
        assertFalse(stats.hasCycles)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `valid graph passes validation`() {
        assertTrue(threeStateLinearGraph().validate().isValid)
    }

    @Test
    fun `graph with no states fails validation`() {
        val graph = PoseGraph.Builder().build()
        val result = graph.validate()
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { "no states" in it })
    }

    @Test
    fun `isolated state fails validation`() {
        val graph = PoseGraph.Builder()
            .addStates(listOf(minimalState(0), minimalState(1)))
            .addTransition(0, 1)
            // State 1 has no outgoing transition — isolated
            .build()
        val result = graph.validate()
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { "isolated" in it })
    }

    @Test
    fun `state with empty features fails validation`() {
        val emptyFeaturesState = PoseState(
            stateId = 0,
            meanFeatures = RawFeatureMap.EMPTY,   // empty!
            featureTolerances = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 10.0),
            minHoldDuration = 0.5,
        )
        val graph = PoseGraph.Builder()
            .addState(emptyFeaturesState)
            .addTransition(0, 0)  // self-loop to avoid isolated-state error
            .build()
        val result = graph.validate()
        assertIs<ValidationResult.Invalid>(result)
        assertTrue(result.errors.any { "no features" in it })
    }

    // ── JSON serialization round-trip ─────────────────────────────────────────

    @Test
    fun `graph survives JSON round-trip`() {
        val original = threeStateLinearGraph()
        val json = original.toJson()
        val restored = PoseGraph.fromJson(json)

        assertEquals(original.states.size, restored.states.size)
        assertEquals(original.transitions, restored.transitions)
        assertEquals(
            original.states.map { it.stateId },
            restored.states.map { it.stateId }
        )
    }

    @Test
    fun `transition string keys are preserved in JSON`() {
        val json = threeStateLinearGraph().toJson()
        // JSON must contain string keys like "0", "1", "2"
        assertTrue(json.contains("\"0\""), "Expected string key \"0\" in: $json")
        assertTrue(json.contains("\"1\""), "Expected string key \"1\" in: $json")
    }

    @Test
    fun `temporal sequence survives JSON round-trip via metadata`() {
        val states = listOf(minimalState(0), minimalState(1))
        val seq = listOf(0, 1, 0, 1)
        val result = DiscoveryResult(states = states, temporalSequence = seq)
        val original = PoseGraph.buildFromDiscoveryResult(result)

        val restored = PoseGraph.fromJson(original.toJson())
        assertEquals(seq, restored.temporalSequence)
    }

    @Test
    fun `fromJson is compatible with Python-generated JSON shape`() {
        // Minimal JSON matching Python's to_dict() output format
        val pythonJson = """
            {
              "version": "1.1",
              "metadata": {
                "temporal_sequence": [0, 1, 0],
                "fps": 30.0
              },
              "states": [
                {
                  "state_id": 0,
                  "mean_features": { "left_knee_angle": 90.0 },
                  "feature_tolerances": { "left_knee_angle": 10.0 },
                  "min_hold_duration": 0.5
                },
                {
                  "state_id": 1,
                  "mean_features": { "left_knee_angle": 160.0 },
                  "feature_tolerances": { "left_knee_angle": 10.0 },
                  "min_hold_duration": 0.5
                }
              ],
              "transitions": {
                "0": [1],
                "1": [0]
              }
            }
        """.trimIndent()

        val graph = PoseGraph.fromJson(pythonJson)
        assertEquals(2, graph.states.size)
        assertTrue(graph.isValidTransition(0, 1))
        assertTrue(graph.isValidTransition(1, 0))
        assertEquals(listOf(0, 1, 0), graph.temporalSequence)
        assertEquals(30.0, graph.metadata.fps)
    }

    // ── PoseState matching ────────────────────────────────────────────────────

    @Test
    fun `withScaledTolerances does not mutate original`() {
        val state = minimalState(0)
        val originalTolerance = state.featureTolerances[FeatureKey.LEFT_KNEE_ANGLE]!!
        val scaled = state.withScaledTolerances(2.0)
        // Original unchanged
        assertEquals(originalTolerance, state.featureTolerances[FeatureKey.LEFT_KNEE_ANGLE])
        // Scaled copy has doubled tolerance
        assertEquals(originalTolerance * 2.0, scaled.featureTolerances[FeatureKey.LEFT_KNEE_ANGLE])
    }

    @Test
    fun `matches returns true when all features within tolerance`() {
        val state = minimalState(0)  // left_knee_angle mean=90, tol=10
        val features = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 95.0)  // deviation=5 < 10
        val result = state.matches(features)
        assertTrue(result.matched)
    }

    @Test
    fun `matches returns false when feature outside tolerance`() {
        val state = minimalState(0)  // left_knee_angle mean=90, tol=10
        val features = RawFeatureMap.of(FeatureKey.LEFT_KNEE_ANGLE to 120.0)  // deviation=30 > 10
        val result = state.matches(features)
        assertFalse(result.matched)
    }

    @Test
    fun `strict match returns NO_MATCH when expected feature is missing`() {
        val state = minimalState(0)  // expects LEFT_KNEE_ANGLE
        val features = RawFeatureMap.of(FeatureKey.TORSO_ANGLE to 5.0)  // different key
        val result = state.matches(features, strict = true)
        assertEquals(MatchResult.NO_MATCH, result)
    }
}
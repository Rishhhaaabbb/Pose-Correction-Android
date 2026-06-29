// core/poseengine/src/test/kotlin/generator/GeneratorConfigTest.kt

package com.domain.generator

import kotlin.test.*

class GeneratorConfigTest {

    @Test
    fun `effectiveFps divides originalFps by skipFrames`() {
        val config = GeneratorConfig(skipFrames = 2)
        assertEquals(15.0, config.effectiveFps(30.0))
    }

    @Test
    fun `effectiveFps with skipFrames=1 returns originalFps unchanged`() {
        val config = GeneratorConfig(skipFrames = 1)
        assertEquals(30.0, config.effectiveFps(30.0))
    }

    @Test
    fun `skipFrames less than 1 fails at construction`() {
        assertFailsWith<IllegalArgumentException> {
            GeneratorConfig(skipFrames = 0)
        }
    }

    @Test
    fun `confidenceThreshold out of range fails at construction`() {
        assertFailsWith<IllegalArgumentException> {
            GeneratorConfig(confidenceThreshold = 1.5)
        }
    }

    @Test
    fun `default config matches Python defaults`() {
        val config = GeneratorConfig.DEFAULT
        assertEquals(0.6, config.confidenceThreshold)
        assertEquals(0.10, config.stabilityThreshold)
        assertEquals(12, config.minHoldFrames)
        assertEquals(7, config.smoothingWindow)
        assertEquals(3, config.transitionBuffer)
        assertEquals(2.0, config.toleranceMultiplier)
        assertEquals(1, config.skipFrames)
        assertNull(config.maxFrames)
    }
}

class GenerationResultTest {

    private fun makeResult(total: Int, valid: Int, segments: Int, states: Int) =
        GenerationResult(
            graph = com.core.poseengine.PoseGraph.Builder().build(),
            numFramesTotal = total,
            numFramesValid = valid,
            numStates = states,
            numSegments = segments,
            isLinear = true,
            hasCycles = false,
            numTransitions = states,
            outputPath = "/tmp/test.json",
        )

    @Test
    fun `validFrameRatio is correct`() {
        val result = makeResult(total = 100, valid = 75, segments = 5, states = 3)
        assertEquals(0.75, result.validFrameRatio)
    }

    @Test
    fun `validFrameRatio returns 0 for empty sequence`() {
        val result = makeResult(total = 0, valid = 0, segments = 0, states = 0)
        assertEquals(0.0, result.validFrameRatio)
    }

    @Test
    fun `hadMerging is true when segments exceed states`() {
        val result = makeResult(total = 100, valid = 80, segments = 7, states = 3)
        assertTrue(result.hadMerging)
    }

    @Test
    fun `hadMerging is false when segments equal states`() {
        val result = makeResult(total = 100, valid = 80, segments = 3, states = 3)
        assertFalse(result.hadMerging)
    }
}
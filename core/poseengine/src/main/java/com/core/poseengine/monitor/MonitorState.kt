package com.core.poseengine.monitor

import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.RawFeatureMap

// ── Frame status ──────────────────────────────────────────────────────────────

data class FrameStatus(
    val stateId: Int,
    val holdProgress: Double,
    val isCorrect: Boolean,
    val feedback: String,
    val repsCompleted: Int,
    val sequenceIndex: Int,
    val streak: Int,
    val inGrace: Boolean,
    val sessionComplete: Boolean = false,
    val phase: MonitorPhase = MonitorPhase.HOLDING,
    val deviations: Map<FeatureKey, Double> = emptyMap(),
)

enum class MonitorPhase {
    HOLDING,
    TRANSITIONING,
    GUIDANCE,
    SESSION_COMPLETE,
}

// ── Session summary ───────────────────────────────────────────────────────────

data class SessionSummary(
    val stateAccuracies: Map<Int, List<Double>>,
    val skippedStateIds: Set<Int>,
    val totalReps: Int,
    val maxStreak: Int,
) {
    val overallAccuracy: Double
        get() {
            val all = stateAccuracies.values.flatten()
            return if (all.isEmpty()) 0.0 else all.average()
        }

    fun meanAccuracyFor(stateId: Int): Double {
        val values = stateAccuracies[stateId] ?: return 0.0
        return if (values.isEmpty()) 0.0 else values.average()
    }
}

// ── Complexity ────────────────────────────────────────────────────────────────

data class ExerciseComplexity(
    val level: ComplexityLevel,
    val numStates: Int,
    val isDynamic: Boolean,
    val avgHoldSeconds: Double,
    val toleranceScale: Double,
)

enum class ComplexityLevel { SIMPLE, MODERATE, COMPLEX }

// ── Monitor configuration ─────────────────────────────────────────────────────

data class MonitorConfig(
    val confidenceThreshold: Double = 0.7,
    /** Minimum seconds between corrective feedback updates. Correct-frame
     *  feedback (hold countdown) always updates immediately regardless. */
    /** Minimum seconds between corrective feedback updates. Correct-frame
     *  feedback (hold countdown) always updates immediately regardless. */
    val feedbackDelaySeconds: Double = 1.0,
    val maxReps: Int? = null,
    val enableAdaptive: Boolean = true,
    /** Set to 0.0 to skip the guidance preview entirely. */
    val guidanceDurationSeconds: Double = 2.0,
    val transitionGraceSeconds: Double = 0.2,
    val progressiveTolerance: Boolean = true,
    val enforceToleranceFloor: Boolean = true,
) {
    companion object {
        val DEFAULT = MonitorConfig()
    }
}

// ── Accuracy tracker ──────────────────────────────────────────────────────────

data class AccuracyFrame(
    val greenFrames: Int = 0,
    val evaluatedFrames: Int = 0,
) {
    val accuracyPercent: Double
        get() = if (evaluatedFrames == 0) 0.0
        else (greenFrames.toDouble() / evaluatedFrames) * 100.0

    fun withCorrectFrame(): AccuracyFrame =
        copy(greenFrames = greenFrames + 1, evaluatedFrames = evaluatedFrames + 1)

    fun withIncorrectFrame(): AccuracyFrame =
        copy(evaluatedFrames = evaluatedFrames + 1)

    fun reset(): AccuracyFrame = AccuracyFrame()
}
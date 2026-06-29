package com.core.poseengine.monitor

import com.core.poseengine.PoseGraph

/**
 * Classifies exercise complexity and computes adaptive tolerance scale.
 *
 * Matches Python: ExerciseComplexityAnalyzer
 *
 * Returns immutable [ExerciseComplexity] instead of a Dict.
 * Does NOT mutate the graph — tolerance scaling is applied to per-frame
 * copies in the engine via [PoseState.withScaledTolerances].
 */
object ComplexityAnalyzer {

    /**
     * Analyze [graph] and return complexity classification.
     *
     * Matches Python: ExerciseComplexityAnalyzer.analyze()
     *
     * Classification rules (match Python exactly):
     *   numStates ≤ 2 AND avgHold > 2.0s → SIMPLE,  scale = 0.9
     *   numStates ≤ 4 AND NOT dynamic     → MODERATE, scale = 1.0
     *   otherwise                         → COMPLEX,  scale = 1.15
     *
     * isDynamic: avgHold < 1.0s
     */
    fun analyze(graph: PoseGraph): ExerciseComplexity {
        val numStates = graph.states.size
        val holdDurations = graph.states.map { it.minHoldDuration }

        // Matches Python: np.mean(hold_durations)
        val avgHold = if (holdDurations.isEmpty()) 0.0 else holdDurations.average()
        val isDynamic = avgHold < 1.0

        val (level, toleranceScale) = when {
            numStates <= 2 && avgHold > 2.0 -> ComplexityLevel.SIMPLE to 0.9
            numStates <= 4 && !isDynamic    -> ComplexityLevel.MODERATE to 1.0
            else                            -> ComplexityLevel.COMPLEX to 1.15
        }

        return ExerciseComplexity(
            level = level,
            numStates = numStates,
            isDynamic = isDynamic,
            avgHoldSeconds = avgHold,
            toleranceScale = toleranceScale,
        )
    }
}
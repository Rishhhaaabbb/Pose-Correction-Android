package com.core.poseengine.monitor

/**
 * Manages progression through the temporal sequence of pose states.
 *
 * Extracted from Python's [_transition_to_next_state] to isolate and test
 * the sequence indexing logic independently from timing and accuracy tracking.
 *
 * ── Special case from Python ──────────────────────────────────────────────────
 * When the first and last state in the temporal sequence are the SAME state
 * (e.g., a return-to-start exercise), and the current state is that shared
 * state, skip index 0 and jump to index 1 instead. This prevents counting
 * the return-to-start as the beginning of the next rep before the rep counter
 * has incremented.
 *
 * Python code:
 *   if (last_state_id == first_state_id and
 *       self.current_state.state_id == first_state_id):
 *       next_idx = 1
 *
 * @param temporalSequence  The ordered list of state IDs from the graph metadata.
 */
class SequenceNavigator(
    val temporalSequence: List<Int>,
) {
    init {
        require(temporalSequence.isNotEmpty()) {
            "SequenceNavigator requires a non-empty temporal sequence"
        }
    }

    /**
     * Compute the next sequence index and whether a rep was completed.
     *
     * Matches Python: _transition_to_next_state() index logic.
     *
     * @param currentIndex   The index that just completed.
     * @param currentStateId The state ID at [currentIndex] (used for the
     *   first==last special case check).
     * @return [NavigationResult] with the next index and rep-complete flag.
     */
    fun nextIndex(currentIndex: Int, currentStateId: Int): NavigationResult {
        var nextIdx = currentIndex + 1
        var repCompleted = false

        if (nextIdx >= temporalSequence.size) {
            // Wrapped around — a full rep is complete
            repCompleted = true
            nextIdx = 0
        }

        // ── Special case: first == last state ────────────────────────────────
        // Matches Python:
        //   last_state_id = self.temporal_sequence[-1]
        //   first_state_id = self.temporal_sequence[0]
        //   if (last_state_id == first_state_id and
        //       self.current_state.state_id == first_state_id):
        //       next_idx = 1
        val lastStateId = temporalSequence.last()
        val firstStateId = temporalSequence.first()
        if (lastStateId == firstStateId && currentStateId == firstStateId) {
            nextIdx = 1
        }

        return NavigationResult(
            nextSequenceIndex = nextIdx.coerceIn(0, temporalSequence.size - 1),
            nextStateId = temporalSequence[nextIdx.coerceIn(0, temporalSequence.size - 1)],
            repCompleted = repCompleted,
        )
    }
}

data class NavigationResult(
    val nextSequenceIndex: Int,
    val nextStateId: Int,
    val repCompleted: Boolean,
)
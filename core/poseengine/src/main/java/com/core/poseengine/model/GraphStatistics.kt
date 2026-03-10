package com.core.poseengine.model

/**
 * Structural statistics of a [PoseGraph].
 *
 * Replaces Python's get_graph_statistics() -> Dict[str, any].
 *
 * This is a pure value object — no serialization annotations.
 * It is computed on demand and never persisted.
 *
 * @param numStates               Number of nodes.
 * @param numTransitions          Total number of directed edges.
 * @param isLinear                True when every node has exactly one outgoing edge.
 * @param hasCycles               True when any edge points to a node with a lower or equal ID.
 *                                This is the same heuristic Python uses — not a full DFS cycle check.
 * @param maxOutgoing             Maximum outgoing edges from any single node (branching factor).
 * @param maxIncoming             Maximum incoming edges to any single node.
 * @param avgTransitionsPerState  Mean outgoing edges per node.
 */
data class GraphStatistics(
    val numStates: Int,
    val numTransitions: Int,
    val isLinear: Boolean,
    val hasCycles: Boolean,
    val maxOutgoing: Int,
    val maxIncoming: Int,
    val avgTransitionsPerState: Double,
) {
    companion object {
        /** Returned by [PoseGraph.getStatistics] when the graph has no states. */
        val EMPTY = GraphStatistics(
            numStates = 0,
            numTransitions = 0,
            isLinear = true,
            hasCycles = false,
            maxOutgoing = 0,
            maxIncoming = 0,
            avgTransitionsPerState = 0.0,
        )
    }
}
package com.presentation.graphviz

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/**
 * Computes 2D positions for pose graph nodes.
 *
 * Replaces Python: nx.circular_layout, nx.spring_layout,
 * nx.shell_layout, and PoseGraphVisualizer._hierarchical_layout()
 *
 * All layouts return a [Map<Int, Offset>] where values are in a
 * normalized coordinate space: x and y in approximately [-1.0, 1.0].
 * The caller scales to screen pixels.
 *
 * ── Layout algorithms ─────────────────────────────────────────────────────────
 * CIRCULAR:     Nodes evenly spaced on a circle. Matches nx.circular_layout.
 * HIERARCHICAL: Nodes evenly spaced left-to-right on a horizontal line.
 *               Matches Python's _hierarchical_layout().
 * SHELL:        Alias for CIRCULAR for graphs with one group. Matches nx.shell_layout.
 * SPRING:       Force-directed layout. Matches nx.spring_layout(k=3, seed=42).
 *               Simplified — sufficient for small graphs (< 20 nodes).
 */
object PoseGraphLayout {

    enum class Algorithm { CIRCULAR, HIERARCHICAL, SHELL, SPRING }

    /**
     * Compute node positions for the given layout algorithm.
     *
     * @param nodeIds   All node IDs in the graph (state IDs).
     * @param edges     Directed edges as (fromId, toId) pairs.
     * @param algorithm Layout algorithm to use.
     * @return Map from node ID to normalized [Offset] position.
     */
    fun compute(
        nodeIds: List<Int>,
        edges: List<Pair<Int, Int>>,
        algorithm: Algorithm,
    ): Map<Int, Offset> {
        if (nodeIds.isEmpty()) return emptyMap()
        if (nodeIds.size == 1) return mapOf(nodeIds[0] to Offset.Zero)

        return when (algorithm) {
            Algorithm.CIRCULAR, Algorithm.SHELL -> circular(nodeIds)
            Algorithm.HIERARCHICAL              -> hierarchical(nodeIds)
            Algorithm.SPRING                   -> spring(nodeIds, edges)
        }
    }

    /**
     * Evenly space nodes around a unit circle.
     *
     * Matches Python: nx.circular_layout(G, scale=2.0) — we use scale=1.0
     * since the caller handles pixel scaling.
     *
     * Node i is placed at angle = 2π * i / n, starting at top (−π/2 offset
     * matches NetworkX's convention of starting at the right, but top-start
     * looks better for exercise sequences so we use −π/2).
     */
    private fun circular(nodeIds: List<Int>): Map<Int, Offset> {
        val n = nodeIds.size
        return nodeIds.mapIndexed { i, id ->
            val angle = 2 * PI * i / n - PI / 2
            id to Offset(cos(angle).toFloat(), sin(angle).toFloat())
        }.toMap()
    }

    /**
     * Space nodes evenly left-to-right on a horizontal line at y=0.
     *
     * Matches Python: _hierarchical_layout()
     *   x = i / max(num_nodes - 1, 1) * 4.0 (we normalize to [-1, 1])
     *   y = 0.5 (we use 0.0)
     */
    private fun hierarchical(nodeIds: List<Int>): Map<Int, Offset> {
        val sorted = nodeIds.sorted()
        val n = sorted.size
        return sorted.mapIndexed { i, id ->
            val x = if (n == 1) 0f else (i.toFloat() / (n - 1)) * 2f - 1f
            id to Offset(x, 0f)
        }.toMap()
    }

    /**
     * Force-directed layout.
     *
     * Matches Python: nx.spring_layout(G, k=3, iterations=50, seed=42, scale=2.0)
     *
     * Uses Fruchterman-Reingold with:
     *   - Fixed random seed (42) for deterministic output
     *   - 50 iterations
     *   - Repulsion constant k = 0.5 (normalized equivalent of Python's k=3/scale=2)
     *
     * For graphs with ≤ 8 nodes (typical exercise graphs), this converges
     * quickly and produces results visually equivalent to NetworkX.
     */
    private fun spring(nodeIds: List<Int>, edges: List<Pair<Int, Int>>): Map<Int, Offset> {
        val n = nodeIds.size
        val idToIdx = nodeIds.mapIndexed { i, id -> id to i }.toMap()

        // Seeded random initial positions — matches Python seed=42
        val rng = SeededRandom(42L)
        val positions = Array(n) {
            Offset(
                (rng.nextFloat() * 2f - 1f),
                (rng.nextFloat() * 2f - 1f),
            )
        }

        val k = 0.5f          // Optimal distance between nodes
        val kSquared = k * k

        // 50 iterations — matches Python iterations=50
        repeat(50) { iteration ->
            val temperature = 1f * (1f - iteration / 50f).coerceAtLeast(0.01f)
            val displacement = Array(n) { Offset.Zero }

            // Repulsive forces between all pairs
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val delta = positions[i] - positions[j]
                    val dist = delta.getDistanceSafe()
                    val repulsion = kSquared / dist
                    val force = delta.normalizedSafe() * repulsion
                    displacement[i] = displacement[i] + force
                    displacement[j] = displacement[j] - force
                }
            }

            // Attractive forces along edges
            for ((fromId, toId) in edges) {
                val i = idToIdx[fromId] ?: continue
                val j = idToIdx[toId] ?: continue
                val delta = positions[i] - positions[j]
                val dist = delta.getDistanceSafe()
                val attraction = (dist * dist) / k
                val force = delta.normalizedSafe() * attraction
                displacement[i] = displacement[i] - force
                displacement[j] = displacement[j] + force
            }

            // Apply displacement capped at temperature
            for (i in 0 until n) {
                val dispLen = displacement[i].getDistanceSafe()
                val capped = displacement[i].normalizedSafe() * minOf(dispLen, temperature)
                positions[i] = positions[i] + capped
            }
        }

        // Normalize to [-1, 1]
        val allX = positions.map { it.x }
        val allY = positions.map { it.y }
        val minX = allX.min()
        val maxX = allX.max()
        val minY = allY.min()
        val maxY = allY.max()
        val rangeX = (maxX - minX).coerceAtLeast(1e-6f)
        val rangeY = (maxY - minY).coerceAtLeast(1e-6f)

        return nodeIds.mapIndexed { i, id ->
            id to Offset(
                (positions[i].x - minX) / rangeX * 2f - 1f,
                (positions[i].y - minY) / rangeY * 2f - 1f,
            )
        }.toMap()
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    private operator fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)
    private operator fun Offset.plus(other: Offset) = Offset(x + other.x, y + other.y)
    private operator fun Offset.times(scalar: Float) = Offset(x * scalar, y * scalar)
    private operator fun Offset.unaryMinus() = Offset(-x, -y)

    private fun Offset.getDistanceSafe(): Float =
        sqrt(x * x + y * y).coerceAtLeast(1e-6f)

    private fun Offset.normalizedSafe(): Offset {
        val d = getDistanceSafe()
        return Offset(x / d, y / d)
    }

    /**
     * Simple seeded linear congruential RNG — matches Python's numpy seed=42
     * well enough for layout initialization (same quadrant distribution).
     */
    private class SeededRandom(seed: Long) {
        private var state = seed
        fun nextFloat(): Float {
            state = (state * 6364136223846793005L + 1442695040888963407L)
            return ((state ushr 33).toFloat() / Int.MAX_VALUE.toFloat())
                .coerceIn(0f, 1f)
        }
    }
}
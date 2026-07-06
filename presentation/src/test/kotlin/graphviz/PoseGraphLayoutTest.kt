// core/poseengine/src/test/kotlin/presentation/PoseGraphLayoutTest.kt

package com.presentation.graphviz.graphviz
import com.presentation.graphviz.SkeletonNormalizer.isValidForDrawing
import androidx.compose.ui.geometry.Offset
import com.presentation.graphviz.PoseGraphLayout
import com.presentation.graphviz.SkeletonNormalizer
import kotlin.collections.iterator
import kotlin.math.*
import kotlin.test.*

class PoseGraphLayoutTest {

    private val threeNodeIds = listOf(0, 1, 2)
    private val threeEdges = listOf(0 to 1, 1 to 2, 2 to 0)

    // ── Circular layout ───────────────────────────────────────────────────────

    @Test
    fun `circular layout produces one position per node`() {
        val pos = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.CIRCULAR)
        assertEquals(3, pos.size)
        assertTrue(pos.containsKey(0))
        assertTrue(pos.containsKey(1))
        assertTrue(pos.containsKey(2))
    }

    @Test
    fun `circular layout positions are on unit circle`() {
        val pos = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.CIRCULAR)
        for ((_, offset) in pos) {
            val r = sqrt(offset.x * offset.x + offset.y * offset.y)
            assertTrue(abs(r - 1.0f) < 1e-4f, "Expected radius 1.0, got $r")
        }
    }

    @Test
    fun `circular layout nodes are evenly spaced (120 degrees apart for 3 nodes)`() {
        val pos = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.CIRCULAR)
        val offsets = listOf(pos[0]!!, pos[1]!!, pos[2]!!)
        for (i in offsets.indices) {
            val a = offsets[i]
            val b = offsets[(i + 1) % 3]
            val dot = a.x * b.x + a.y * b.y
            val angleDeg = Math.toDegrees(acos(dot.toDouble()))
            assertTrue(abs(angleDeg - 120.0) < 1.0, "Expected 120°, got $angleDeg")
        }
    }

    @Test
    fun `single node placed at origin`() {
        val pos = PoseGraphLayout.compute(listOf(0), emptyList(), PoseGraphLayout.Algorithm.CIRCULAR)
        assertEquals(Offset.Zero, pos[0])
    }

    // ── Hierarchical layout ───────────────────────────────────────────────────

    @Test
    fun `hierarchical layout places all nodes on same Y`() {
        val pos = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.HIERARCHICAL)
        val ys = pos.values.map { it.y }
        assertTrue(ys.all { it == ys[0] }, "All Y values should be equal in hierarchical layout")
    }

    @Test
    fun `hierarchical layout first node at x=-1 last at x=1`() {
        val pos = PoseGraphLayout.compute(listOf(0, 1, 2), emptyList(), PoseGraphLayout.Algorithm.HIERARCHICAL)
        assertTrue(abs(pos[0]!!.x - (-1f)) < 1e-4f, "First node should be at x=-1")
        assertTrue(abs(pos[2]!!.x - 1f) < 1e-4f, "Last node should be at x=1")
    }

    @Test
    fun `hierarchical layout with single node at x=0`() {
        val pos = PoseGraphLayout.compute(listOf(0), emptyList(), PoseGraphLayout.Algorithm.HIERARCHICAL)
        assertTrue(abs(pos[0]!!.x) < 1e-4f)
    }

    // ── Spring layout ─────────────────────────────────────────────────────────

    @Test
    fun `spring layout produces one position per node`() {
        val pos = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.SPRING)
        assertEquals(3, pos.size)
    }

    @Test
    fun `spring layout is deterministic with same seed`() {
        val pos1 = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.SPRING)
        val pos2 = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.SPRING)
        for (id in threeNodeIds) {
            assertEquals(pos1[id]!!.x, pos2[id]!!.x, absoluteTolerance = 1e-5f)
            assertEquals(pos1[id]!!.y, pos2[id]!!.y, absoluteTolerance = 1e-5f)
        }
    }

    @Test
    fun `spring layout positions are in normalized range`() {
        val pos = PoseGraphLayout.compute(threeNodeIds, threeEdges, PoseGraphLayout.Algorithm.SPRING)
        for ((_, offset) in pos) {
            assertTrue(offset.x in -1.1f..1.1f, "x out of range: ${offset.x}")
            assertTrue(offset.y in -1.1f..1.1f, "y out of range: ${offset.y}")
        }
    }
}

class SkeletonNormalizerTest {

    private fun validKeypoint(x: Double, y: Double) = x to y
    private fun invalidKeypoint() = 0.0 to 0.0
    private fun makeKeypoints(
        vararg overrides: Pair<Int, Pair<Double, Double>>,
    ): List<Pair<Double, Double>> {
        val kpts = MutableList(17) { validKeypoint(it * 10.0, it * 5.0) }
        overrides.forEach { (idx, kpt) -> kpts[idx] = kpt }
        return kpts
    }

    // ── Validity ──────────────────────────────────────────────────────────────

    @Test
    fun `all-zero keypoints return null`() {
        val allZero = List(17) { 0.0 to 0.0 }
        assertNull(SkeletonNormalizer.normalize(allZero))
    }

    @Test
    fun `valid keypoints return non-null result of size 17`() {
        val kpts = makeKeypoints()
        val result = SkeletonNormalizer.normalize(kpts)
        assertNotNull(result)
        assertEquals(17, result.size)
    }

    // ── Y-flip ────────────────────────────────────────────────────────────────

    @Test
    fun `Y axis is flipped relative to input`() {
        // Two keypoints: one above centroid, one below
        // After centering + flip, the formerly-below keypoint should have negative Y
        val kpts = List(17) { i ->
            when (i) {
                0 -> 0.0 to 100.0   // below centroid in image coords
                1 -> 0.0 to 0.0     // invalid
                else -> 0.0 to 50.0  // centroid region
            }
        }
        val result = SkeletonNormalizer.normalize(kpts) ?: fail("Expected non-null")
        // Keypoint 0 was at y=100 (below mean), so after flip it should be negative.
        assertTrue(result[0].y < 0, "Y should be negative after flip for below-centroid point")
    }

    // ── Scale ─────────────────────────────────────────────────────────────────

    @Test
    fun `normalized keypoints fit within approximately -0_5 to 0_5`() {
        val kpts = makeKeypoints()
        val result = SkeletonNormalizer.normalize(kpts) ?: fail("Expected non-null")
        for (offset in result) {
            if (offset.isValidForDrawing()) {
                assertTrue(offset.x in -0.6f..0.6f, "x out of range: ${offset.x}")
                assertTrue(offset.y in -0.6f..0.6f, "y out of range: ${offset.y}")
            }
        }
    }

    @Test
    fun `centroid of valid normalized keypoints is near zero`() {
        val kpts = makeKeypoints()
        val result = SkeletonNormalizer.normalize(kpts) ?: fail("Expected non-null")
        val validResults = result.filter { it.isValidForDrawing() }
        val meanX = validResults.map { it.x }.average()
        val meanY = validResults.map { it.y }.average()
        assertTrue(abs(meanX) < 0.05f, "Centroid x not near zero: $meanX")
        assertTrue(abs(meanY) < 0.05f, "Centroid y not near zero: $meanY")
    }

    // ── isValidForDrawing ─────────────────────────────────────────────────────

    @Test
    fun `zero offset is not valid for drawing`() {
        assertFalse(Offset.Zero.isValidForDrawing())
    }

    @Test
    fun `NaN offset is not valid for drawing`() {
        assertFalse(Offset(Float.NaN, 0f).isValidForDrawing())
        assertFalse(Offset(0f, Float.NaN).isValidForDrawing())
    }

    @Test
    fun `non-zero finite offset is valid for drawing`() {
        assertTrue(Offset(0.1f, 0.2f).isValidForDrawing())
        assertTrue(Offset(-0.5f, 0.3f).isValidForDrawing())
    }
}
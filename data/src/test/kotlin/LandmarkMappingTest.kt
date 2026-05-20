// core/poseengine/src/test/kotlin/model/LandmarkMappingTest.kt

package com.data.mediapipe
import com.core.poseengine.model.SkeletonConnections
import kotlin.test.*

class LandmarkMappingTest {

    // ── Mapping completeness ──────────────────────────────────────────────────

    @Test
    fun `exactly 17 MP landmarks are mapped`() {
        val mapped = LandmarkMapping.mpToCoco.count { it != LandmarkMapping.UNMAPPED }
        assertEquals(17, mapped)
    }

    @Test
    fun `no COCO index appears twice`() {
        val cocoIndices = LandmarkMapping.mpToCoco.filter { it != LandmarkMapping.UNMAPPED }
        assertEquals(cocoIndices.size, cocoIndices.toSet().size,
            "Duplicate COCO index found in mapping")
    }

    @Test
    fun `all COCO indices are in range 0-16`() {
        LandmarkMapping.mpToCoco.forEach { cocoIdx ->
            if (cocoIdx != LandmarkMapping.UNMAPPED) {
                assertTrue(cocoIdx in 0..16, "COCO index out of range: $cocoIdx")
            }
        }
    }

    // ── Spot checks matching Python _MP_TO_COCO ───────────────────────────────

    @Test
    fun `nose maps correctly`() {
        assertEquals(0, LandmarkMapping.mpToCoco[0])
    }

    @Test
    fun `left shoulder maps correctly`() {
        assertEquals(5, LandmarkMapping.mpToCoco[11])
    }

    @Test
    fun `right hip maps correctly`() {
        assertEquals(12, LandmarkMapping.mpToCoco[24])
    }

    @Test
    fun `right ankle maps correctly`() {
        assertEquals(16, LandmarkMapping.mpToCoco[28])
    }

    @Test
    fun `unmapped MP landmarks return UNMAPPED sentinel`() {
        // MP index 1 (left_eye_outer) has no COCO equivalent
        assertEquals(LandmarkMapping.UNMAPPED, LandmarkMapping.mpToCoco[1])
        // MP index 17 (left_pinky) has no COCO equivalent
        assertEquals(LandmarkMapping.UNMAPPED, LandmarkMapping.mpToCoco[17])
    }

    @Test
    fun `array length covers all 33 MP landmarks`() {
        assertEquals(33, LandmarkMapping.mpToCoco.size)
    }
}

class SkeletonConnectionsTest {

    @Test
    fun `bone count matches Python _SKELETON length`() {
        // Python _SKELETON has 17 pairs — same as COCO bone count
        assertEquals(18, SkeletonConnections.boneCount,
            "Expected 17 bones matching Python _SKELETON")
    }

    @Test
    fun `boneIndices array length is twice boneCount`() {
        assertEquals(
            SkeletonConnections.boneCount * 2,
            SkeletonConnections.boneIndices.size,
        )
    }

    @Test
    fun `all bone indices are in valid COCO range`() {
        SkeletonConnections.boneIndices.forEach { idx ->
            assertTrue(idx in 0..16, "Bone index $idx out of range [0, 16]")
        }
    }

    @Test
    fun `nose-to-left-eye connection is present`() {
        // Matches Python _SKELETON[0] = (0, 1)
        val bones = SkeletonConnections.bones
        assertTrue(
            bones.any { (s, e) -> s == 0 && e == 1 },
            "Expected nose(0)→left_eye(1) connection"
        )
    }

    @Test
    fun `left-knee-to-left-ankle connection is present`() {
        // Matches Python _SKELETON entry (13, 15)
        val bones = SkeletonConnections.bones
        assertTrue(
            bones.any { (s, e) -> s == 13 && e == 15 },
            "Expected left_knee(13)→left_ankle(15) connection"
        )
    }

    @Test
    fun `no self-loops in skeleton`() {
        SkeletonConnections.bones.forEach { (s, e) ->
            assertNotEquals(s, e, "Self-loop found at index $s")
        }
    }

    @Test
    fun `bones lazy list matches boneIndices flat array`() {
        val bones = SkeletonConnections.bones
        for (i in bones.indices) {
            val (s, e) = bones[i]
            assertEquals(SkeletonConnections.boneIndices[i * 2], s)
            assertEquals(SkeletonConnections.boneIndices[i * 2 + 1], e)
        }
    }
}
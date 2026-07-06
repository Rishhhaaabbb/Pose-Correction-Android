package com.core.poseengine

import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.KeypointIndex
import com.core.poseengine.model.RawFeatureMap
import com.core.poseengine.model.Skeleton2d
import com.core.poseengine.model.Skeleton3d
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Extracts normalized, scale-invariant pose features from COCO-17 skeletons.
 *
 * ── Architectural role ────────────────────────────────────────────────────────
 * Pure domain logic. No Android imports. No coroutines.
 * Called from both offline (video processing) and online (camera) pipelines.
 *
 * ── Python equivalence ────────────────────────────────────────────────────────
 * Direct port of PoseFeatureExtractor. All mathematical operations are
 * verified to produce identical IEEE 754 results:
 *
 *   np.linalg.norm([dx, dy])        → hypot(dx, dy)
 *   np.clip(x, -1.0, 1.0)           → x.coerceIn(-1.0, 1.0)
 *   np.arccos(x)                    → acos(x)       [kotlin.math]
 *   np.arctan2(y, x)                → atan2(y, x)   [kotlin.math]
 *   np.degrees(rad)                 → Math.toDegrees(rad)
 *   np.dot(v1, v2) on 2-D vectors   → dot2d(v1, v2)
 *   np.dot(v1, v2) on 3-D vectors   → dot3d(v1, v2)
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 * Stateless after construction. Safe to call from multiple coroutines.
 *
 * @param confidenceThreshold Minimum keypoint confidence to include in
 *   computation. Default 0.5 matches Python default.
 */
class FeatureExtractor(
    val confidenceThreshold: Double = 0.5,
) {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Extract features from a 2-D skeleton.
     *
     * Returns null when the skeleton fails validation (< 4/6 critical
     * keypoints above threshold, or body scale < 0.05 (normalized [0,1])).
     *
     * Matches Python: extract_features()
     */
    fun extractFeatures(skeleton: Skeleton2d): RawFeatureMap? {
        if (!validateKeypoints(skeleton)) return null

        val bodyScale = computeBodyScale(skeleton)
        if (bodyScale < BODY_SCALE_MIN) return null

        val features = EnumMap<FeatureKey, Double>(FeatureKey::class.java)
        
        // Joint angles — each is independently optional
        features.putIfNotNull(FeatureKey.LEFT_ELBOW_ANGLE, computeElbowAngle(skeleton, side = Side.LEFT))
        features.putIfNotNull(FeatureKey.RIGHT_ELBOW_ANGLE, computeElbowAngle(skeleton, side = Side.RIGHT))
        features.putIfNotNull(FeatureKey.LEFT_KNEE_ANGLE, computeKneeAngle(skeleton, side = Side.LEFT))
        features.putIfNotNull(FeatureKey.RIGHT_KNEE_ANGLE, computeKneeAngle(skeleton, side = Side.RIGHT))
        features.putIfNotNull(FeatureKey.LEFT_SHOULDER_ANGLE, computeShoulderAngle(skeleton, side = Side.LEFT))
        features.putIfNotNull(FeatureKey.RIGHT_SHOULDER_ANGLE, computeShoulderAngle(skeleton, side = Side.RIGHT))
        features.putIfNotNull(FeatureKey.LEFT_HIP_ANGLE, computeHipAngle(skeleton, side = Side.LEFT))
        features.putIfNotNull(FeatureKey.RIGHT_HIP_ANGLE, computeHipAngle(skeleton, side = Side.RIGHT))

        // Torso — always computable if validation passed (uses same 4 keypoints)
        features[FeatureKey.TORSO_ANGLE] = computeTorsoAngle(skeleton)

        // Asymmetry deltas — left minus right, requires both sides
        val leftHip = features[FeatureKey.LEFT_HIP_ANGLE]
        val rightHip = features[FeatureKey.RIGHT_HIP_ANGLE]
        if (leftHip != null && rightHip != null) {
            features[FeatureKey.HIP_ASYMMETRY_DELTA] = leftHip - rightHip
        }
        val leftKnee = features[FeatureKey.LEFT_KNEE_ANGLE]
        val rightKnee = features[FeatureKey.RIGHT_KNEE_ANGLE]
        if (leftKnee != null && rightKnee != null) {
            features[FeatureKey.KNEE_ASYMMETRY_DELTA] = leftKnee - rightKnee
        }

        // Arm elevations — optional per side
        features.putIfNotNull(
            FeatureKey.LEFT_ARM_ELEVATION,
            computeArmElevation(skeleton, KeypointIndex.LEFT_WRIST, KeypointIndex.LEFT_SHOULDER, bodyScale)
        )
        features.putIfNotNull(
            FeatureKey.RIGHT_ARM_ELEVATION,
            computeArmElevation(skeleton, KeypointIndex.RIGHT_WRIST, KeypointIndex.RIGHT_SHOULDER, bodyScale)
        )

        // Leg spread — optional per side
        features.putIfNotNull(FeatureKey.LEFT_LEG_SPREAD, computeLegSpread(skeleton, Side.LEFT, bodyScale))
        features.putIfNotNull(FeatureKey.RIGHT_LEG_SPREAD, computeLegSpread(skeleton, Side.RIGHT, bodyScale))

        // Body center
        features[FeatureKey.BODY_CENTER_Y] = computeBodyCenterY(skeleton, bodyScale)
        
        return RawFeatureMap(features)
    }

    /**
     * Extract features from a 3-D skeleton.
     *
     * Returns an empty map (not null) when no features can be computed.
     * The caller ([extractFeaturesWithFallback]) treats empty as "no 3D data".
     *
     * Matches Python: extract_features_3d()
     *
     * VP3D / MediaPipe 3-D coordinate convention:
     *   X = left/right, Y = up (positive), Z = forward/back
     *
     * IMPORTANT: The 3-D arm elevation here computes the angle between the
     * elbow→shoulder vector and the world-up axis [0, 1, 0].
     * This differs from the 2-D version which computes a normalized signed
     * vertical displacement. The Python code intentionally uses different
     * semantics per dimension — preserved here.
     */
    fun extractFeatures3d(skeleton: Skeleton3d): RawFeatureMap {
        val features = EnumMap<FeatureKey, Double>(FeatureKey::class.java)

        // Elbow angles
        features.putIfNotNull(FeatureKey.LEFT_ELBOW_ANGLE,
            angle3d(skeleton, KeypointIndex.LEFT_SHOULDER, KeypointIndex.LEFT_ELBOW, KeypointIndex.LEFT_WRIST))
        features.putIfNotNull(FeatureKey.RIGHT_ELBOW_ANGLE,
            angle3d(skeleton, KeypointIndex.RIGHT_SHOULDER, KeypointIndex.RIGHT_ELBOW, KeypointIndex.RIGHT_WRIST))

        // Knee angles
        features.putIfNotNull(FeatureKey.LEFT_KNEE_ANGLE,
            angle3d(skeleton, KeypointIndex.LEFT_HIP, KeypointIndex.LEFT_KNEE, KeypointIndex.LEFT_ANKLE))
        features.putIfNotNull(FeatureKey.RIGHT_KNEE_ANGLE,
            angle3d(skeleton, KeypointIndex.RIGHT_HIP, KeypointIndex.RIGHT_KNEE, KeypointIndex.RIGHT_ANKLE))

        // Hip angles
        features.putIfNotNull(FeatureKey.LEFT_HIP_ANGLE,
            angle3d(skeleton, KeypointIndex.LEFT_SHOULDER, KeypointIndex.LEFT_HIP, KeypointIndex.LEFT_KNEE))
        features.putIfNotNull(FeatureKey.RIGHT_HIP_ANGLE,
            angle3d(skeleton, KeypointIndex.RIGHT_SHOULDER, KeypointIndex.RIGHT_HIP, KeypointIndex.RIGHT_KNEE))

        // Shoulder angles
        // Python uses indices [7,5,11] → elbow,shoulder,hip — note the vertex order
        features.putIfNotNull(FeatureKey.LEFT_SHOULDER_ANGLE,
            angle3d(skeleton, KeypointIndex.LEFT_ELBOW, KeypointIndex.LEFT_SHOULDER, KeypointIndex.LEFT_HIP))
        features.putIfNotNull(FeatureKey.RIGHT_SHOULDER_ANGLE,
            angle3d(skeleton, KeypointIndex.RIGHT_ELBOW, KeypointIndex.RIGHT_SHOULDER, KeypointIndex.RIGHT_HIP))

        // Arm elevation — angle between arm vector and world up [0,1,0]
        features.putIfNotNull(FeatureKey.LEFT_ARM_ELEVATION,
            armElevation3d(skeleton, KeypointIndex.LEFT_SHOULDER, KeypointIndex.LEFT_ELBOW))
        features.putIfNotNull(FeatureKey.RIGHT_ARM_ELEVATION,
            armElevation3d(skeleton, KeypointIndex.RIGHT_SHOULDER, KeypointIndex.RIGHT_ELBOW))

        // Torso angle — signed forward lean in the Z-Y sagittal plane
        features.putIfNotNull(FeatureKey.TORSO_ANGLE, torsoAngle3d(skeleton))

        return RawFeatureMap(features)
    }

    /**
     * Primary call site for all production code.
     *
     * Uses 3-D features when available; falls back to 2-D silently.
     * 3-D features override 2-D features for matching keys (Python behavior).
     *
     * Matches Python: extract_features_with_fallback()
     *
     * @param skeleton2d  Required. Always computed first.
     * @param skeleton3d  Optional. When non-null, overrides matching 2-D features.
     * @return Merged feature map, or null if 2-D extraction failed.
     */
    fun extractFeaturesWithFallback(
        skeleton2d: Skeleton2d,
        skeleton3d: Skeleton3d? = null,
    ): RawFeatureMap? {
        // 2D features — if null, skeleton is invalid, return null regardless of 3D
        val features2d = extractFeatures(skeleton2d) ?: return null

        // No 3D data — return 2D features directly (no allocation)
        if (skeleton3d == null) return features2d

        // 3D features — if empty, 3D provided nothing useful, return 2D
        val features3d = extractFeatures3d(skeleton3d)
        if (features3d.isEmpty) return features2d

        // Merge: 2D base, 3D overrides — matches Python `merged.update(features_3d)`
        return features2d + features3d
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Matches Python: _validate_keypoints()
     *
     * Requires at least 4 of 6 critical keypoints above confidence threshold.
     * Critical set: both shoulders, both hips, both knees.
     */
    private fun validateKeypoints(skeleton: Skeleton2d): Boolean {
        var validCount = 0
        if (skeleton[KeypointIndex.LEFT_SHOULDER].confidence >= confidenceThreshold) validCount++
        if (skeleton[KeypointIndex.RIGHT_SHOULDER].confidence >= confidenceThreshold) validCount++
        if (skeleton[KeypointIndex.LEFT_HIP].confidence >= confidenceThreshold) validCount++
        if (skeleton[KeypointIndex.RIGHT_HIP].confidence >= confidenceThreshold) validCount++
        if (skeleton[KeypointIndex.LEFT_KNEE].confidence >= confidenceThreshold) validCount++
        if (skeleton[KeypointIndex.RIGHT_KNEE].confidence >= confidenceThreshold) validCount++

        return validCount >= 4
    }

    // ── Body scale ────────────────────────────────────────────────────────────

    /**
     * Matches Python: _compute_body_scale()
     *
     * Returns normalized [0,1] distance between shoulder midpoint and hip midpoint.
     * Used as normalization denominator for all non-angle features.
     */
    private fun computeBodyScale(skeleton: Skeleton2d): Double {
        val lShoulder = skeleton[KeypointIndex.LEFT_SHOULDER]
        val rShoulder = skeleton[KeypointIndex.RIGHT_SHOULDER]
        val lHip = skeleton[KeypointIndex.LEFT_HIP]
        val rHip = skeleton[KeypointIndex.RIGHT_HIP]

        val shoulderCx = (lShoulder.x + rShoulder.x) / 2.0
        val shoulderCy = (lShoulder.y + rShoulder.y) / 2.0
        val hipCx = (lHip.x + rHip.x) / 2.0
        val hipCy = (lHip.y + rHip.y) / 2.0

        // np.linalg.norm([dx, dy]) ≡ hypot(dx, dy)
        return hypot(shoulderCx - hipCx, shoulderCy - hipCy)
    }

    // ── Joint angles (2D) ─────────────────────────────────────────────────────

    /**
     * Matches Python: _compute_angle()
     *
     * Computes the angle at vertex [p2] formed by the ray p2→p1 and p2→p3.
     * Returns degrees in [0°, 180°], or null if any confidence is below threshold
     * or either vector is degenerate (norm < 1e-6).
     *
     * Mathematical equivalence:
     *   Python: cos_angle = np.clip(np.dot(v1, v2), -1.0, 1.0)
     *   Kotlin: cosAngle  = dot2d(v1x,v1y, v2x,v2y).coerceIn(-1.0, 1.0)
     */
    private fun computeAngle2d(
        p1x: Double, p1y: Double, p1conf: Double,
        p2x: Double, p2y: Double, p2conf: Double,
        p3x: Double, p3y: Double, p3conf: Double,
    ): Double? {
        // Matches Python: if np.any(confidences < self.conf_thresh): return None
        if (p1conf < confidenceThreshold ||
            p2conf < confidenceThreshold ||
            p3conf < confidenceThreshold
        ) return null

        // v1 = p1 - p2,  v2 = p3 - p2
        val v1x = p1x - p2x
        val v1y = p1y - p2y
        val v2x = p3x - p2x
        val v2y = p3y - p2y

        val v1Norm = hypot(v1x, v1y)
        val v2Norm = hypot(v2x, v2y)

        // Matches Python: if v1_norm < 1e-6 or v2_norm < 1e-6: return None
        if (v1Norm < EPSILON || v2Norm < EPSILON) return null

        // Normalize then dot
        val cosAngle = dot2d(v1x / v1Norm, v1y / v1Norm, v2x / v2Norm, v2y / v2Norm)
            .coerceIn(-1.0, 1.0)

        return Math.toDegrees(acos(cosAngle))
    }

    /** Convenience overload taking [KeypointIndex] references. */
    private fun computeAngle2d(
        skeleton: Skeleton2d,
        i1: KeypointIndex,
        vertex: KeypointIndex,
        i3: KeypointIndex,
    ): Double? {
        val p1 = skeleton[i1]
        val p2 = skeleton[vertex]
        val p3 = skeleton[i3]
        return computeAngle2d(
            p1.x, p1.y, p1.confidence,
            p2.x, p2.y, p2.confidence,
            p3.x, p3.y, p3.confidence,
        )
    }

    // ── Named angle helpers — each matches one Python call exactly ────────────

    /** shoulder → elbow → wrist */
    private fun computeElbowAngle(skeleton: Skeleton2d, side: Side): Double? =
        computeAngle2d(
            skeleton,
            i1 = if (side == Side.LEFT) KeypointIndex.LEFT_SHOULDER else KeypointIndex.RIGHT_SHOULDER,
            vertex = if (side == Side.LEFT) KeypointIndex.LEFT_ELBOW else KeypointIndex.RIGHT_ELBOW,
            i3 = if (side == Side.LEFT) KeypointIndex.LEFT_WRIST else KeypointIndex.RIGHT_WRIST,
        )

    /** hip → knee → ankle */
    private fun computeKneeAngle(skeleton: Skeleton2d, side: Side): Double? =
        computeAngle2d(
            skeleton,
            i1 = if (side == Side.LEFT) KeypointIndex.LEFT_HIP else KeypointIndex.RIGHT_HIP,
            vertex = if (side == Side.LEFT) KeypointIndex.LEFT_KNEE else KeypointIndex.RIGHT_KNEE,
            i3 = if (side == Side.LEFT) KeypointIndex.LEFT_ANKLE else KeypointIndex.RIGHT_ANKLE,
        )

    /**
     * hip → shoulder → elbow
     * Matches Python: left_shoulder_angle = _compute_angle(LEFT_HIP, LEFT_SHOULDER, LEFT_ELBOW)
     */
    private fun computeShoulderAngle(skeleton: Skeleton2d, side: Side): Double? =
        computeAngle2d(
            skeleton,
            i1 = if (side == Side.LEFT) KeypointIndex.LEFT_HIP else KeypointIndex.RIGHT_HIP,
            vertex = if (side == Side.LEFT) KeypointIndex.LEFT_SHOULDER else KeypointIndex.RIGHT_SHOULDER,
            i3 = if (side == Side.LEFT) KeypointIndex.LEFT_ELBOW else KeypointIndex.RIGHT_ELBOW,
        )

    /**
     * shoulder → hip → knee
     * Matches Python: left_hip_angle = _compute_angle(LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE)
     */
    private fun computeHipAngle(skeleton: Skeleton2d, side: Side): Double? =
        computeAngle2d(
            skeleton,
            i1 = if (side == Side.LEFT) KeypointIndex.LEFT_SHOULDER else KeypointIndex.RIGHT_SHOULDER,
            vertex = if (side == Side.LEFT) KeypointIndex.LEFT_HIP else KeypointIndex.RIGHT_HIP,
            i3 = if (side == Side.LEFT) KeypointIndex.LEFT_KNEE else KeypointIndex.RIGHT_KNEE,
        )

    // ── Torso angle (2D) ──────────────────────────────────────────────────────

    /**
     * Matches Python: _compute_torso_angle()
     *
     * Angle of the hip→shoulder spine vector relative to vertical.
     * 0° = upright. Positive = leaning right (in image space).
     *
     * Python: angle_rad = np.arctan2(torso_vec[0], -torso_vec[1])
     * Kotlin: atan2(torsoVecX, -torsoVecY)
     *
     * NOTE: atan2 argument order is (y, x) in both Python and Kotlin.
     * Python passes (torso_vec[0], -torso_vec[1]) which is (x_component, negated_y).
     * This is intentional — the negation flips image-Y to math-Y convention.
     */
    private fun computeTorsoAngle(skeleton: Skeleton2d): Double {
        val lShoulder = skeleton[KeypointIndex.LEFT_SHOULDER]
        val rShoulder = skeleton[KeypointIndex.RIGHT_SHOULDER]
        val lHip = skeleton[KeypointIndex.LEFT_HIP]
        val rHip = skeleton[KeypointIndex.RIGHT_HIP]

        val shoulderCx = (lShoulder.x + rShoulder.x) / 2.0
        val shoulderCy = (lShoulder.y + rShoulder.y) / 2.0
        val hipCx = (lHip.x + rHip.x) / 2.0
        val hipCy = (lHip.y + rHip.y) / 2.0

        // torso_vec = shoulder_center - hip_center
        val torsoVecX = shoulderCx - hipCx
        val torsoVecY = shoulderCy - hipCy

        // atan2(torso_vec[0], -torso_vec[1])
        return Math.toDegrees(atan2(torsoVecX, -torsoVecY))
    }

    // ── Arm elevation (2D) ────────────────────────────────────────────────────

    /**
     * Matches Python: _compute_arm_elevation()
     *
     * Signed, body-scale-normalized vertical wrist-to-shoulder displacement.
     * Positive = wrist ABOVE shoulder (remember: image Y increases downward,
     * so a smaller Y value means higher in the frame).
     *
     * Python: elevation = -(wrist_y - shoulder_y) / body_scale
     */
    private fun computeArmElevation(
        skeleton: Skeleton2d,
        wristIdx: KeypointIndex,
        shoulderIdx: KeypointIndex,
        bodyScale: Double,
    ): Double? {
        val wrist = skeleton[wristIdx]
        val shoulder = skeleton[shoulderIdx]
        if (wrist.confidence < confidenceThreshold ||
            shoulder.confidence < confidenceThreshold
        ) return null
        return -(wrist.y - shoulder.y) / bodyScale
    }

    // ── Leg spread (2D) ───────────────────────────────────────────────────────

    /**
     * Matches Python: _compute_leg_spread()
     *
     * Normalized horizontal hip-to-ankle distance. Scale-invariant stance width.
     */
    private fun computeLegSpread(
        skeleton: Skeleton2d,
        side: Side,
        bodyScale: Double,
    ): Double? {
        val hipIdx = if (side == Side.LEFT) KeypointIndex.LEFT_HIP else KeypointIndex.RIGHT_HIP
        val ankleIdx = if (side == Side.LEFT) KeypointIndex.LEFT_ANKLE else KeypointIndex.RIGHT_ANKLE
        val hip = skeleton[hipIdx]
        val ankle = skeleton[ankleIdx]
        if (hip.confidence < confidenceThreshold ||
            ankle.confidence < confidenceThreshold
        ) return null
        return abs(ankle.x - hip.x) / bodyScale
    }

    // ── Body center Y (2D) ────────────────────────────────────────────────────

    /**
     * Matches Python: _compute_body_center_y()
     *
     * Hip-center Y position normalized by body scale.
     * Distinguishes standing (low value) from sitting/lying (higher value).
     * NOTE: In image space, higher pixel Y = lower in frame.
     */
    private fun computeBodyCenterY(skeleton: Skeleton2d, bodyScale: Double): Double {
        val lHip = skeleton[KeypointIndex.LEFT_HIP]
        val rHip = skeleton[KeypointIndex.RIGHT_HIP]
        val hipCy = (lHip.y + rHip.y) / 2.0
        return hipCy / bodyScale
    }

    // ── 3D computation helpers ────────────────────────────────────────────────

    /**
     * Matches Python: _angle_3d(a, b, c)
     *
     * Computes the angle at vertex [vertexIdx] formed by rays vertex→p1 and vertex→p3.
     * Uses 3-D vectors. Returns null if any confidence below threshold or
     * either arm vector is degenerate.
     *
     * Python:
     *   ba, bc = a - b, c - b
     *   na, nb = np.linalg.norm(ba), np.linalg.norm(bc)
     *   cos_a  = np.clip(np.dot(ba, bc) / (na * nb), -1.0, 1.0)
     */
    private fun angle3d(
        skeleton: Skeleton3d,
        p1Idx: KeypointIndex,
        vertexIdx: KeypointIndex,
        p3Idx: KeypointIndex,
    ): Double? {
        val p1 = skeleton[p1Idx]
        val pv = skeleton[vertexIdx]
        val p3 = skeleton[p3Idx]

        if (p1.confidence < confidenceThreshold ||
            pv.confidence < confidenceThreshold ||
            p3.confidence < confidenceThreshold
        ) return null

        // ba = a - b (p1 - vertex),  bc = c - b (p3 - vertex)
        val bax = p1.x - pv.x;  val bay = p1.y - pv.y;  val baz = p1.z - pv.z
        val bcx = p3.x - pv.x;  val bcy = p3.y - pv.y;  val bcz = p3.z - pv.z

        val na = norm3d(bax, bay, baz)
        val nb = norm3d(bcx, bcy, bcz)
        if (na < EPSILON || nb < EPSILON) return null

        val cosA = (dot3d(bax, bay, baz, bcx, bcy, bcz) / (na * nb)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosA))
    }

    /**
     * Matches Python arm elevation loop in extract_features_3d():
     *
     *   arm = _xyz(el) - _xyz(sh)          # elbow - shoulder
     *   n   = np.linalg.norm(arm)
     *   cos_a = np.clip(np.dot(arm/n, [0,1,0]), -1.0, 1.0)
     *   result = np.degrees(np.arccos(cos_a))
     *
     * Note: np.dot(unit_arm, [0,1,0]) == unit_arm[1] (Y component only).
     * This simplification is intentional and preserved here.
     *
     * VP3D Y-axis points UP, so arm.Y > 0 means the elbow is above the shoulder.
     */
    private fun armElevation3d(
        skeleton: Skeleton3d,
        shoulderIdx: KeypointIndex,
        elbowIdx: KeypointIndex,
    ): Double? {
        val shoulder = skeleton[shoulderIdx]
        val elbow = skeleton[elbowIdx]
        if (shoulder.confidence < confidenceThreshold ||
            elbow.confidence < confidenceThreshold
        ) return null

        val armX = elbow.x - shoulder.x
        val armY = elbow.y - shoulder.y
        val armZ = elbow.z - shoulder.z
        val n = norm3d(armX, armY, armZ)
        if (n < EPSILON) return null

        // dot(arm/n, [0,1,0]) == (armY / n)
        val cosA = (armY / n).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosA))
    }

    /**
     * Matches Python torso angle 3D block:
     *
     *   mid_shoulder = (_xyz(5) + _xyz(6)) / 2
     *   mid_hip      = (_xyz(11) + _xyz(12)) / 2
     *   spine        = mid_shoulder - mid_hip
     *   spine_forward  = spine[2]   # Z
     *   spine_vertical = spine[1]   # Y
     *   result = np.degrees(np.arctan2(spine_forward, spine_vertical))
     *
     * Only computed when at least one of |spine_forward| or |spine_vertical| > 1e-6.
     */
    private fun torsoAngle3d(skeleton: Skeleton3d): Double? {
        val ls = skeleton[KeypointIndex.LEFT_SHOULDER]
        val rs = skeleton[KeypointIndex.RIGHT_SHOULDER]
        val lh = skeleton[KeypointIndex.LEFT_HIP]
        val rh = skeleton[KeypointIndex.RIGHT_HIP]

        if (ls.confidence < confidenceThreshold ||
            rs.confidence < confidenceThreshold ||
            lh.confidence < confidenceThreshold ||
            rh.confidence < confidenceThreshold
        ) return null

        val midShoulderY = (ls.y + rs.y) / 2.0
        val midShoulderZ = (ls.z + rs.z) / 2.0
        val midHipY = (lh.y + rh.y) / 2.0
        val midHipZ = (lh.z + rh.z) / 2.0

        val spineForward = midShoulderZ - midHipZ   // Z component
        val spineVertical = midShoulderY - midHipY  // Y component

        // Matches Python guard: if abs(spine_forward) > 1e-6 or abs(spine_vertical) > 1e-6
        if (abs(spineForward) <= EPSILON && abs(spineVertical) <= EPSILON) return null

        return Math.toDegrees(atan2(spineForward, spineVertical))
    }

    // ── Private math primitives ───────────────────────────────────────────────

    /** Dot product of two 2-D vectors. No allocation. */
    private fun dot2d(ax: Double, ay: Double, bx: Double, by: Double): Double =
        ax * bx + ay * by

    /** Dot product of two 3-D vectors. No allocation. */
    private fun dot3d(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double): Double =
        ax * bx + ay * by + az * bz

    /**
     * Euclidean norm of a 3-D vector.
     *
     * Matches np.linalg.norm([x, y, z]).
     * Not using hypot() because kotlin.math.hypot only accepts 2 arguments;
     * sqrt(x²+y²+z²) is the correct 3-D equivalent.
     */
    private fun norm3d(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    // ── Internal types ────────────────────────────────────────────────────────

    private enum class Side { LEFT, RIGHT }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        /** Minimum body scale in normalized [0,1] coords. Below this, detection is unreliable. */
        private const val BODY_SCALE_MIN = 0.05

        /**
         * Degenerate vector threshold.
         * Matches Python: if v1_norm < 1e-6 → return None
         */
        private const val EPSILON = 1e-6
    }
}

// ── BuilderMap extension ───────────────────────────────────────────────────────

/**
 * Adds a key-value pair only when [value] is non-null.
 * Keeps the buildMap block clean — equivalent to Python's "if x is not None: d[k] = x".
 */
private fun MutableMap<FeatureKey, Double>.putIfNotNull(key: FeatureKey, value: Double?) {
    if (value != null) put(key, value)
}
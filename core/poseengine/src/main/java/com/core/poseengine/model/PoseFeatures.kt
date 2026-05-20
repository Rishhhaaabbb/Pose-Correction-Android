package com.core.poseengine.model

import kotlinx.serialization.Serializable

/**
 * Type-safe container for all extracted pose features.
 * (See RawFeatureMap below for full design rationale.)
 */

@Serializable
enum class FeatureKey(val serializedName: String) {
    LEFT_ELBOW_ANGLE("left_elbow_angle"),
    RIGHT_ELBOW_ANGLE("right_elbow_angle"),
    LEFT_KNEE_ANGLE("left_knee_angle"),
    RIGHT_KNEE_ANGLE("right_knee_angle"),
    LEFT_SHOULDER_ANGLE("left_shoulder_angle"),
    RIGHT_SHOULDER_ANGLE("right_shoulder_angle"),
    LEFT_HIP_ANGLE("left_hip_angle"),
    RIGHT_HIP_ANGLE("right_hip_angle"),
    TORSO_ANGLE("torso_angle"),
    LEFT_ARM_ELEVATION("left_arm_elevation"),
    RIGHT_ARM_ELEVATION("right_arm_elevation"),
    LEFT_LEG_SPREAD("left_leg_spread"),
    RIGHT_LEG_SPREAD("right_leg_spread"),
    BODY_CENTER_Y("body_center_y"),
    HIP_ASYMMETRY_DELTA("hip_asymmetry_delta"),
    KNEE_ASYMMETRY_DELTA("knee_asymmetry_delta");

    /** True if this feature is a body-side asymmetry metric. */
    val isAsymmetry: Boolean get() = serializedName.contains("asymmetry")

    /** True if this feature is an angle (degrees). Drives distance normalization. */
    val isAngle: Boolean get() = serializedName.endsWith("_angle")

    /**
     * True for the structural load-bearing joints: hip, knee, elbow.
     *
     * Used by [PoseMonitorEngine.adaptiveScaleFor] to apply a tighter adaptive
     * tolerance scale to these joints (matching Python's comment:
     * "hip/knee/elbow → scale × 1.0, other → scale × 1.1").
     *
     * Shoulder and torso get the slightly wider ×1.1 scale because they are
     * more variable across individuals and camera angles.
     */
    val isCoreJoint: Boolean get() = when (this) {
        LEFT_HIP_ANGLE, RIGHT_HIP_ANGLE,
        LEFT_KNEE_ANGLE, RIGHT_KNEE_ANGLE,
        LEFT_ELBOW_ANGLE, RIGHT_ELBOW_ANGLE -> true
        else -> false
    }

    companion object {
        private val byName: Map<String, FeatureKey> =
            entries.associateBy { it.serializedName }

        /**
         * Look up by the Python-compatible string key.
         * Returns null for unknown keys (forward-compatibility safety).
         */
        fun fromSerializedName(name: String): FeatureKey? = byName[name]
    }
}

/**
 * Immutable map of pose features extracted from a single frame.
 *
 * Only features whose keypoints had sufficient confidence are present.
 * Absence of a key is meaningful (keypoint not visible) — do not default to 0.
 */
@JvmInline
value class RawFeatureMap(val map: Map<FeatureKey, Double>) {

    val size: Int get() = map.size
    val isEmpty: Boolean get() = map.isEmpty()

    operator fun get(key: FeatureKey): Double? = map[key]
    operator fun contains(key: FeatureKey): Boolean = key in map

    /**
     * Merge two feature maps — keys in [other] override keys in [this].
     * Matches Python: merged = features_2d.copy(); merged.update(features_3d)
     */
    operator fun plus(other: RawFeatureMap): RawFeatureMap =
        RawFeatureMap(map + other.map)

    /**
     * Returns a left-right mirrored representation of this feature map.
     *
     * This is used by the online monitor to allow side-invariant matching when
     * a user performs a pose from the opposite lateral direction.
     */
    fun mirroredHorizontally(): RawFeatureMap {
        val mirrored = buildMap<FeatureKey, Double>(map.size) {
            for ((key, value) in map) {
                val mappedKey = when (key) {
                    FeatureKey.LEFT_ELBOW_ANGLE -> FeatureKey.RIGHT_ELBOW_ANGLE
                    FeatureKey.RIGHT_ELBOW_ANGLE -> FeatureKey.LEFT_ELBOW_ANGLE
                    FeatureKey.LEFT_KNEE_ANGLE -> FeatureKey.RIGHT_KNEE_ANGLE
                    FeatureKey.RIGHT_KNEE_ANGLE -> FeatureKey.LEFT_KNEE_ANGLE
                    FeatureKey.LEFT_SHOULDER_ANGLE -> FeatureKey.RIGHT_SHOULDER_ANGLE
                    FeatureKey.RIGHT_SHOULDER_ANGLE -> FeatureKey.LEFT_SHOULDER_ANGLE
                    FeatureKey.LEFT_HIP_ANGLE -> FeatureKey.RIGHT_HIP_ANGLE
                    FeatureKey.RIGHT_HIP_ANGLE -> FeatureKey.LEFT_HIP_ANGLE
                    FeatureKey.LEFT_ARM_ELEVATION -> FeatureKey.RIGHT_ARM_ELEVATION
                    FeatureKey.RIGHT_ARM_ELEVATION -> FeatureKey.LEFT_ARM_ELEVATION
                    FeatureKey.LEFT_LEG_SPREAD -> FeatureKey.RIGHT_LEG_SPREAD
                    FeatureKey.RIGHT_LEG_SPREAD -> FeatureKey.LEFT_LEG_SPREAD
                    else -> key
                }

                val mappedValue = when (key) {
                    FeatureKey.TORSO_ANGLE,
                    FeatureKey.HIP_ASYMMETRY_DELTA,
                    FeatureKey.KNEE_ASYMMETRY_DELTA -> -value
                    else -> value
                }

                put(mappedKey, mappedValue)
            }
        }
        return RawFeatureMap(mirrored)
    }

    fun commonKeysWith(other: RawFeatureMap): Set<FeatureKey> =
        map.keys.intersect(other.map.keys)

    companion object {
        val EMPTY = RawFeatureMap(emptyMap())

        fun of(vararg pairs: Pair<FeatureKey, Double>): RawFeatureMap =
            RawFeatureMap(mapOf(*pairs))

        fun fromStringMap(stringMap: Map<String, Double>): RawFeatureMap {
            val result = buildMap<FeatureKey, Double>(stringMap.size) {
                for ((k, v) in stringMap) {
                    val key = FeatureKey.fromSerializedName(k) ?: continue
                    put(key, v)
                }
            }
            return RawFeatureMap(result)
        }

        fun RawFeatureMap.toStringMap(): Map<String, Double> =
            map.entries.associate { (k, v) -> k.serializedName to v }
    }
}
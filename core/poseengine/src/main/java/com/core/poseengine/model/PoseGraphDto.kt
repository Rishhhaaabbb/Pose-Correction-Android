package com.core.poseengine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.core.poseengine.PoseGraph
/**
 * kotlinx.serialization DTO for the complete pose graph JSON file.
 *
 * ── JSON compatibility ────────────────────────────────────────────────────────
 * This DTO must produce and consume JSON that is byte-for-byte compatible
 * with the Python pose_graph.py to_dict() / from_dict() output.
 *
 * Key compatibility points:
 *
 * 1. TRANSITIONS KEYS ARE STRINGS.
 *    JSON spec requires object keys to be strings.
 *    Python serializes {0: [1, 2]} as {"0": [1, 2]}.
 *    This DTO uses Map<String, List<Int>> to match exactly.
 *    The domain model uses Map<Int, List<Int>> — conversion happens in
 *    [toDomain] and [PoseGraph.toDto].
 *
 * 2. VERSION FIELD.
 *    Python writes "version": "1.1". We preserve this field.
 *    We do not use it for logic — it is informational only.
 *
 * 3. METADATA IS A NESTED OBJECT.
 *    Python: graph.metadata is a plain dict with heterogeneous values.
 *    Kotlin: [GraphMetadata] is typed. Unknown keys in the JSON are
 *    silently ignored (kotlinx default) for forward compatibility.
 *
 * 4. STATES ARE AN ORDERED ARRAY.
 *    Python preserves insertion order. We preserve list order.
 */
@Serializable
data class PoseGraphDto(

    @SerialName("version")
    val version: String = "1.1",

    @SerialName("metadata")
    val metadata: GraphMetadata = GraphMetadata.EMPTY,

    @SerialName("states")
    val states: List<PoseStateDto> = emptyList(),

    /**
     * Transition adjacency list.
     * Keys are state IDs serialized as strings (JSON requirement).
     * Values are lists of target state IDs as integers.
     *
     * Example JSON: { "0": [1], "1": [2], "2": [0] }
     */
    @SerialName("transitions")
    val transitions: Map<String, List<Int>> = emptyMap(),
) {
    /**
     * Convert this DTO to the domain [PoseGraph].
     *
     * String transition keys are parsed to Int here.
     * Matches Python: for from_id_str, to_ids in transitions.items():
     *                     from_id = int(from_id_str)
     */
    fun toDomain(): PoseGraph {
        val states = states.map { PoseState.fromSerializable(it) }
        val transitions: Map<Int, List<Int>> = buildMap {
            for ((fromIdStr, toIds) in transitions) {
                val fromId = fromIdStr.toIntOrNull()
                    ?: error("Invalid transition key '$fromIdStr' — expected integer string")
                put(fromId, toIds)
            }
        }
        return PoseGraph(
            states = states,
            transitions = transitions,
            metadata = metadata,
        )
    }
}
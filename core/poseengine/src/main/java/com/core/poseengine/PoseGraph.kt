package com.core.poseengine

import com.core.poseengine.model.GraphMetadata
import com.core.poseengine.model.GraphStatistics
import com.core.poseengine.model.PoseGraphDto
import com.core.poseengine.model.PoseState
import com.core.poseengine.model.ValidationResult
import kotlin.collections.iterator
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
/**
 * Immutable directed graph of [com.core.poseengine.model.PoseState] nodes connected by directed transitions.
 *
 * ── Graph structure ───────────────────────────────────────────────────────────
 * Nodes:  [com.core.poseengine.model.PoseState] objects identified by [com.core.poseengine.model.PoseState.stateId].
 * Edges:  Directed transitions stored as adjacency list [transitions].
 *
 * Supports:
 *   - Linear sequences (A → B → C → A)
 *   - Cycles            (A → B → C → A)
 *   - Branches          (A → B or A → C)
 *   - Merges            (A → C and B → C)
 *
 * ── Immutability ─────────────────────────────────────────────────────────────
 * [PoseGraph] is immutable after construction. The builder pattern
 * ([PoseGraph.Builder]) is used during graph generation; the online monitor
 * receives a read-only [PoseGraph].
 *
 * ── Performance ──────────────────────────────────────────────────────────────
 * State lookup is O(1) via [statesById] index. The online monitor calls
 * [getState] at every frame transition; a linear scan would be wasteful.
 *
 * ── JSON key handling ─────────────────────────────────────────────────────────
 * Transition keys are Int in memory but String in JSON (JSON spec requirement).
 * Conversion is handled entirely in [com.core.poseengine.model.PoseGraphDto] — this class never sees
 * string keys.
 *
 * @param states      Ordered list of [com.core.poseengine.model.PoseState] nodes.
 * @param transitions Adjacency list. Key = source state ID. Value = list of
 *                    target state IDs. Duplicate edges are not stored.
 * @param metadata    Graph-level metadata (fps, video path, temporal sequence...).
 */
class PoseGraph private constructor(
    val states: List<PoseState>,
    val transitions: Map<Int, List<Int>>,
    val metadata: GraphMetadata,
) {

    // ── State index (O(1) lookup) ─────────────────────────────────────────────

    /**
     * Index for O(1) state retrieval.
     * Matches Python's linear scan in get_state() but is O(1).
     */
    private val statesById: Map<Int, PoseState> =
        states.associateBy { it.stateId }

    // ── Query API ─────────────────────────────────────────────────────────────

    /**
     * Retrieve a state by ID.
     * Returns null if [stateId] is not in this graph.
     *
     * Matches Python: get_state(state_id)
     * Improves Python's O(n) linear scan to O(1).
     */
    fun getState(stateId: Int): PoseState? = statesById[stateId]

    /**
     * Get all valid next state IDs from [currentStateId].
     * Returns an empty list if [currentStateId] has no outgoing transitions.
     *
     * Matches Python: get_next_states(current_state_id)
     */
    fun getNextStates(currentStateId: Int): List<Int> =
        transitions[currentStateId] ?: emptyList()

    /**
     * Check whether a directed edge exists from [fromStateId] to [toStateId].
     *
     * Matches Python: is_valid_transition(from_state_id, to_state_id)
     */
    fun isValidTransition(fromStateId: Int, toStateId: Int): Boolean =
        toStateId in (transitions[fromStateId] ?: emptyList())

    /**
     * Get the first state in insertion order.
     * Returns null for an empty graph.
     *
     * Matches Python: get_initial_state()
     */
    val initialState: PoseState? get() = states.firstOrNull()

    /**
     * Get the last state in insertion order.
     *
     * Matches Python: get_final_state()
     */
    val finalState: PoseState? get() = states.lastOrNull()

    /**
     * Get all state IDs whose transitions include [stateId] as a target.
     *
     * Matches Python: get_incoming_transitions(state_id)
     * O(E) where E = number of edges — acceptable since this is called only
     * during graph construction and statistics, not per-frame.
     */
    fun getIncomingTransitions(stateId: Int): List<Int> =
        transitions
            .filter { (_, toIds) -> stateId in toIds }
            .map { (fromId, _) -> fromId }

    /**
     * The temporal sequence from [metadata], or an empty list if absent.
     *
     * This is the primary runtime accessor used by the online monitor.
     * Matches Python: graph.get_metadata('temporal_sequence')
     */
    val temporalSequence: List<Int>
        get() = metadata.temporalSequence ?: emptyList()

    // ── Statistics ────────────────────────────────────────────────────────────

    /**
     * Compute structural statistics about this graph.
     *
     * Matches Python: get_graph_statistics()
     *
     * Cycle detection uses the same heuristic as Python:
     * "any edge points to a node with ID ≤ the source ID."
     * This is NOT a full DFS cycle detection — it matches Python behavior exactly.
     */
    fun getStatistics(): GraphStatistics {
        if (states.isEmpty()) return GraphStatistics.Companion.EMPTY

        val numTransitions = transitions.values.sumOf { it.size }

        // Matches Python: all(len(to_ids) == 1 for to_ids in self.transitions.values())
        val isLinear = transitions.values.all { it.size == 1 }

        // Matches Python: any state transitions to a lower or equal ID
        val hasCycles = transitions.any { (fromId, toIds) ->
            toIds.any { toId -> toId <= fromId }
        }

        val maxOutgoing = transitions.values.maxOfOrNull { it.size } ?: 0

        val maxIncoming = states.maxOfOrNull { state ->
            getIncomingTransitions(state.stateId).size
        } ?: 0

        val avgTransitionsPerState =
            if (states.isEmpty()) 0.0
            else numTransitions.toDouble() / states.size

        return GraphStatistics(
            numStates = states.size,
            numTransitions = numTransitions,
            isLinear = isLinear,
            hasCycles = hasCycles,
            maxOutgoing = maxOutgoing,
            maxIncoming = maxIncoming,
            avgTransitionsPerState = avgTransitionsPerState,
        )
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validate graph structure and consistency.
     *
     * Matches Python: validate() -> Tuple[bool, List[str]]
     *
     * Checks performed (in order, matching Python):
     *   1. Graph is non-empty
     *   2. No duplicate state IDs
     *   3. All transition source IDs reference existing states
     *   4. All transition target IDs reference existing states
     *   5. All states have non-empty mean features
     *   6. All states have non-empty feature tolerances
     *   7. All states have at least one outgoing transition
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. Non-empty
        if (states.isEmpty()) {
            errors.add("Graph has no states")
            return ValidationResult.Invalid(errors)  // early return — nothing else to check
        }

        // 2. Duplicate state IDs
        val stateIds = states.map { it.stateId }
        if (stateIds.size != stateIds.toSet().size) {
            errors.add("Duplicate state IDs found")
        }

        val validStateIds = stateIds.toSet()

        // 3 & 4. Transition references
        for ((fromId, toIds) in transitions) {
            if (fromId !in validStateIds) {
                errors.add("Transition references non-existent source state: $fromId")
            }
            for (toId in toIds) {
                if (toId !in validStateIds) {
                    errors.add("Transition to non-existent state: $toId")
                }
            }
        }

        // 5 & 6. State feature completeness
        for (state in states) {
            if (state.meanFeatures.isEmpty) {
                errors.add("State ${state.stateId} has no features")
            }
            if (state.featureTolerances.isEmpty) {
                errors.add("State ${state.stateId} has no tolerances")
            }
        }

        // 7. Isolated states
        for (state in states) {
            val outgoing = transitions[state.stateId]
            if (outgoing.isNullOrEmpty()) {
                errors.add("State ${state.stateId} has no outgoing transitions (isolated)")
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid
        else ValidationResult.Invalid(errors)
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    /**
     * Convert to the serialization DTO.
     *
     * Transition keys are converted from Int to String here.
     * Matches Python: to_dict() where transitions keys are native Python ints
     * serialized to JSON strings by json.dump().
     */
    fun toDto(): PoseGraphDto = PoseGraphDto(
        version = CURRENT_VERSION,
        metadata = metadata,
        states = states.map { it.toSerializable() },
        transitions = transitions
            .mapKeys { (fromId, _) -> fromId.toString() },
    )

    /**
     * Serialize to a JSON string.
     *
     * Matches Python: json.dump(self.to_dict(), f, indent=2)
     * The [prettyPrint] flag adds indentation for human readability.
     */
    fun toJson(prettyPrint: Boolean = true): String {
        val jsonSerializer = if (prettyPrint) PRETTY_JSON else COMPACT_JSON
        return jsonSerializer.encodeToString(PoseGraphDto.serializer(), toDto())
    }

    // ── Companion / factory ───────────────────────────────────────────────────

    companion object {
        private const val CURRENT_VERSION = "1.1"

        private val PRETTY_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        private val COMPACT_JSON = Json {
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        /**
         * Deserialize from a JSON string.
         *
         * Matches Python: from_dict(json.load(f))
         * [ignoreUnknownKeys] ensures forward compatibility with future JSON fields.
         */
        fun fromJson(json: String): PoseGraph {
            val dto = PRETTY_JSON.decodeFromString(PoseGraphDto.serializer(), json)
            return dto.toDomain()
        }

        /**
         * Build a [PoseGraph] from a [DiscoveryResult].
         *
         * Matches Python: build_from_temporal_sequence() logic inside
         * offline_graph_generator.py, including:
         *   - All consecutive pairs from temporalSequence become edges
         *   - If last state ≠ first state, a wrap-around edge is added
         *
         * @param result          Output of [StateDiscovery.discoverStates].
         * @param metadata        Graph metadata to attach.
         */
        fun buildFromDiscoveryResult(
            result: DiscoveryResult,
            metadata: GraphMetadata = GraphMetadata.Companion.EMPTY,
        ): PoseGraph {
            require(result.states.isNotEmpty()) {
                "Cannot build a PoseGraph from an empty DiscoveryResult"
            }
            return Builder()
                .addStates(result.states)
                .buildTransitionsFromTemporalSequence(result.temporalSequence)
                .withMetadata(metadata.copy(temporalSequence = result.temporalSequence))
                .build()
        }

        /**
         * Build a [PoseGraph] from an explicit state list and transition map.
         * Used when loading from JSON (via [fromJson]).
         *
         * This is the internal constructor path — callers outside this file
         * should use [buildFromDiscoveryResult] or [fromJson].
         */
        internal operator fun invoke(
            states: List<PoseState>,
            transitions: Map<Int, List<Int>>,
            metadata: GraphMetadata,
        ): PoseGraph = PoseGraph(
            states = states,
            transitions = transitions,
            metadata = metadata,
        )
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Mutable builder for [PoseGraph].
     *
     * Replaces Python's mutable instance methods (add_state, add_transition,
     * build_from_temporal_sequence) with a builder that produces an immutable
     * [PoseGraph] at the end.
     *
     * Usage:
     *   val graph = PoseGraph.Builder()
     *       .addStates(states)
     *       .buildTransitionsFromTemporalSequence(temporalSequence)
     *       .withMetadata(metadata)
     *       .build()
     */
    class Builder {
        private val states = mutableListOf<PoseState>()

        // Adjacency list stored as mutable sets to enforce deduplication.
        // Matches Python: if to_state_id not in self.transitions[from_state_id]
        private val transitions = mutableMapOf<Int, MutableSet<Int>>()
        private var metadata = GraphMetadata.Companion.EMPTY

        fun addState(state: PoseState): Builder {
            states.add(state)
            transitions.getOrPut(state.stateId) { mutableSetOf() }
            return this
        }

        fun addStates(stateList: List<PoseState>): Builder {
            stateList.forEach { addState(it) }
            return this
        }

        /**
         * Add a directed edge from [fromStateId] to [toStateId].
         * Duplicate edges are silently ignored.
         *
         * Matches Python: add_transition() with dedup check.
         */
        fun addTransition(fromStateId: Int, toStateId: Int): Builder {
            transitions.getOrPut(fromStateId) { mutableSetOf() }.add(toStateId)
            return this
        }

        /**
         * Build all transitions from a temporal sequence.
         *
         * Matches Python: build_from_temporal_sequence()
         *
         * For sequence [0, 1, 2, 0, 1]:
         *   Adds edges: 0→1, 1→2, 2→0, 0→1 (deduped), 1→0 (wrap-around)
         *
         * Wrap-around: if last ≠ first, adds last→first to support looping.
         * Matches Python:
         *   if last_state_id != first_state_id:
         *       self.add_transition(last_state_id, first_state_id)
         */
        fun buildTransitionsFromTemporalSequence(temporalSequence: List<Int>): Builder {
            if (temporalSequence.size < 2) return this

            // Consecutive pairs
            for (i in 0 until temporalSequence.size - 1) {
                addTransition(temporalSequence[i], temporalSequence[i + 1])
            }

            // Wrap-around for looping exercises
            val last = temporalSequence.last()
            val first = temporalSequence.first()
            if (last != first) {
                addTransition(last, first)
            }

            return this
        }

        /**
         * Build a linear sequence (deprecated Python path preserved for
         * loading legacy graphs that used build_linear_sequence()).
         *
         * Each state transitions to the next; last transitions to first.
         * Matches Python: build_linear_sequence()
         */
        fun buildLinearSequence(): Builder {
            if (states.size < 2) return this
            for (i in 0 until states.size - 1) {
                addTransition(states[i].stateId, states[i + 1].stateId)
            }
            // Last → first (loop)
            addTransition(states.last().stateId, states.first().stateId)
            return this
        }

        fun withMetadata(meta: GraphMetadata): Builder {
            metadata = meta
            return this
        }

        /**
         * Produce the immutable [PoseGraph].
         *
         * Converts [MutableSet] transition values to [List] to freeze
         * the adjacency list. Order within each list is insertion order
         * (LinkedHashSet semantics via mutableSetOf).
         */
        fun build(): PoseGraph = PoseGraph(
            states = states.toList(),
            transitions = transitions.mapValues { (_, toIds) -> toIds.toList() },
            metadata = metadata,
        )
    }
}
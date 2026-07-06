package com.core.poseengine.model

/**
 * Internal accumulator used during state merging.
 *
 * Replaces the raw Python tuple:
 *   (state_id: int, mean_features: dict, frame_ranges: List[Tuple], kpt_ranges: List[Tuple])
 *
 * Using a named data class eliminates positional indexing bugs (e.g., [2] vs [3]).
 * This type is package-internal and never crosses the module boundary.
 *
 * [frameRanges] and [kptRanges] grow as additional segments are merged into
 * this canonical state. Both lists always have the same length.
 */
internal data class CanonicalState(
    val stateId: Int,
    val meanFeatures: RawFeatureMap,
    val frameRanges: MutableList<IntRange> = mutableListOf(),
    val kptRanges: MutableList<IntRange> = mutableListOf(),
) {
    fun addRange(frameRange: IntRange) {
        frameRanges.add(frameRange)
        kptRanges.add(frameRange)  // kpt ranges mirror frame ranges
    }
}
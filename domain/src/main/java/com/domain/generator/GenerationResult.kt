package com.domain.generator

import com.core.poseengine.PoseGraph

/**
 * Successful result of [OfflineGraphGenerator.generate].
 *
 * Replaces Python's implicit print + return PoseGraph pattern.
 * All diagnostic information that Python printed to stdout is now structured
 * data that the ViewModel can display, log, or ignore.
 *
 * @param graph           The generated [PoseGraph], ready for use by the monitor.
 * @param numFramesTotal  Total frames processed (after skip_frames).
 * @param numFramesValid  Frames where a valid pose was detected.
 * @param numStates       Number of unique discovered states.
 * @param numSegments     Total temporal segments before merging.
 * @param isLinear        True if the graph has no branches.
 * @param hasCycles       True if any edge points back (e.g., last→first).
 * @param numTransitions  Total directed edges in the graph.
 * @param outputPath      File path where the graph JSON was saved.
 */
data class GenerationResult(
    val graph: PoseGraph,
    val numFramesTotal: Int,
    val numFramesValid: Int,
    val numStates: Int,
    val numSegments: Int,
    val isLinear: Boolean,
    val hasCycles: Boolean,
    val numTransitions: Int,
    val outputPath: String,
) {
    /** Valid frame ratio [0.0, 1.0]. Matches Python's validity percentage log. */
    val validFrameRatio: Double
        get() = if (numFramesTotal == 0) 0.0
        else numFramesValid.toDouble() / numFramesTotal

    /** True when segments were merged into fewer unique states. */
    val hadMerging: Boolean get() = numSegments > numStates
}

// ── Progress events ───────────────────────────────────────────────────────────

/**
 * Progress events emitted during generation.
 *
 * Replaces Python's print() calls throughout generate_from_video().
 * The ViewModel collects these from the [OfflineGraphGenerator.generate] flow
 * and updates the UI accordingly.
 *
 * Events are emitted in the order they appear in the Python print sequence.
 */
sealed class GenerationProgress {

    /** Video metadata was read successfully. */
    data class VideoInfo(
        val width: Int,
        val height: Int,
        val fps: Double,
        val totalFrames: Int,
        val durationSeconds: Double,
    ) : GenerationProgress()

    /** A batch of frames has been processed. Matches Python's every-30-frame print. */
    data class FramesProcessed(
        val processedSoFar: Int,
        val totalEstimate: Int?,
    ) : GenerationProgress()

    /** Feature extraction phase complete. */
    data class ExtractionComplete(
        val totalFrames: Int,
        val validFrames: Int,
        val validPercent: Double,
    ) : GenerationProgress()

    /** State discovery phase complete. */
    data class StatesDiscovered(
        val numStates: Int,
        val temporalSequence: List<Int>,
        val numSegments: Int,
        val hadMerging: Boolean,
    ) : GenerationProgress()

    /** Graph construction and validation complete. */
    data class GraphBuilt(
        val isLinear: Boolean,
        val hasCycles: Boolean,
        val numTransitions: Int,
        val validationPassed: Boolean,
        val validationErrors: List<String>,
    ) : GenerationProgress()

    /** Graph saved to disk. */
    data class Saved(val outputPath: String) : GenerationProgress()

    /** Terminal success event — generation is complete. */
    data class Complete(val result: GenerationResult) : GenerationProgress()

    /** Terminal failure event. Replaces Python's `raise ValueError(...)`. */
    data class Failed(val reason: FailureReason, val detail: String) : GenerationProgress()
}

/**
 * Why generation failed.
 *
 * Replaces Python's untyped `raise ValueError(message)` with structured reasons
 * so the UI can show specific guidance rather than raw exception messages.
 */
enum class FailureReason {
    /** The video file could not be opened or decoded. */
    VIDEO_OPEN_FAILED,

    /**
     * Fewer than 10 valid poses were detected.
     * Matches Python: "Too few valid poses detected."
     */
    TOO_FEW_VALID_POSES,

    /**
     * State discovery produced zero states.
     * Matches Python: "No pose states discovered."
     */
    NO_STATES_DISCOVERED,

    /** Graph validation failed after construction. */
    GRAPH_VALIDATION_FAILED,

    /** File I/O error during save. */
    SAVE_FAILED,
}
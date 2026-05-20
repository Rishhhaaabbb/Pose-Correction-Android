package com.core.poseengine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed metadata attached to a [PoseGraph].
 *
 * Replaces Python's untyped `metadata: Dict[str, Any]`.
 *
 * All fields are nullable because:
 *   1. Older saved graphs may not have all fields.
 *   2. The online monitor only needs [temporalSequence]; other fields are
 *      informational and used by the graph generator and visualization.
 *
 * JSON field names match Python's set_metadata() keys exactly for
 * cross-platform graph file compatibility.
 */
@Serializable
data class GraphMetadata(

    /** Original video filename (basename only). */
    @SerialName("video_path")
    val videoPath: String? = null,

    /**
     * Effective FPS after skip_frames adjustment.
     * Python: fps / skip_frames
     */
    @SerialName("fps")
    val fps: Double? = null,

    /** FPS of the source video before skip_frames adjustment. */
    @SerialName("original_fps")
    val originalFps: Double? = null,

    /** Every Nth frame was processed during generation. */
    @SerialName("skip_frames")
    val skipFrames: Int? = null,

    /** Total number of frames processed (after skip_frames). */
    @SerialName("total_frames")
    val totalFrames: Int? = null,

    /** Frames where a valid pose was detected. */
    @SerialName("valid_frames")
    val validFrames: Int? = null,

    /** Number of unique PoseState nodes. */
    @SerialName("num_states")
    val numStates: Int? = null,

    /** Total number of temporal segments before merging. */
    @SerialName("num_segments")
    val numSegments: Int? = null,

    /** ISO-8601 creation timestamp. */
    @SerialName("created_at")
    val createdAt: String? = null,

    /**
     * Stability threshold used during graph generation.
     * Preserved so the online monitor can display it for debugging.
     */
    @SerialName("stability_threshold")
    val stabilityThreshold: Double? = null,

    /**
     * Ordered list of state IDs representing the temporal sequence from
     * the reference video. Used by the online monitor to replay the
     * exercise in the correct order.
     *
     * Example: [0, 1, 2, 0, 1] means the video showed state 0, then 1,
     * then 2, then 0 again, then 1 again.
     *
     * Matches Python: graph.set_metadata('temporal_sequence', temporal_sequence)
     *
     * This is the ONLY metadata field that the online monitor reads at runtime.
     */
    @SerialName("temporal_sequence")
    val temporalSequence: List<Int>? = null,

    /**
     * Required phone orientation for optimal keypoint detection.
     * "portrait" for front-facing exercises (standing poses)
     * "landscape" for side-facing exercises (plank variations, side poses)
     */
    @SerialName("orientation")
    val orientation: String? = null,
) {
    companion object {
        val EMPTY = GraphMetadata()
    }
}
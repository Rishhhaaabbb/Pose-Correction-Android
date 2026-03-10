package com.data.generator

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decoded video frame metadata.
 *
 * @param width      Frame width in pixels.
 * @param height     Frame height in pixels.
 * @param fps        Frames per second (from container metadata).
 * @param totalFrames Estimated total frame count (may be approximate for VBR).
 */
data class VideoInfo(
    val width: Int,
    val height: Int,
    val fps: Double,
    val totalFrames: Int,
) {
    val durationSeconds: Double get() = if (fps > 0) totalFrames / fps else 0.0
}

/**
 * A single decoded video frame.
 *
 * @param bitmap     ARGB_8888 bitmap ready for MediaPipe input.
 * @param frameIndex 0-based index of this frame in the decoded sequence
 *   (after skip_frames filtering — NOT the raw container index).
 * @param timestamp  Presentation timestamp in microseconds.
 */
data class DecodedFrame(
    val bitmap: Bitmap,
    val frameIndex: Int,
    val timestamp: Long,
)

/**
 * Provides decoded video frames as a cold [Flow].
 *
 * Replaces Python's cv2.VideoCapture loop in _extract_features_from_video().
 *
 * ── Frame skipping ────────────────────────────────────────────────────────────
 * [skipFrames]=N means emit every Nth decoded frame.
 * Matches Python: if frame_idx % skip_frames != 0: continue
 *
 * ── Max frames ────────────────────────────────────────────────────────────────
 * [maxFrames] is a hard cap on emitted frames (after skip_frames filtering).
 * Matches Python: if max_frames and processed_count >= max_frames: break
 *
 * ── Implementation note ───────────────────────────────────────────────────────
 * Uses [MediaMetadataRetriever.getFrameAtIndex] for simplicity and correctness.
 * This approach is synchronous per-frame but avoids the complexity of
 * MediaExtractor + MediaCodec surface decoding. For production use with
 * very long videos, consider switching to the hardware decoder pipeline.
 * For typical exercise reference videos (30–120s), retriever is adequate.
 */
class VideoFrameSource(private val context: Context) {

    /**
     * Read video metadata without decoding frames.
     *
     * Matches Python:
     *   fps = cap.get(cv2.CAP_PROP_FPS)
     *   total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
     *   width/height from cap.get(CAP_PROP_FRAME_WIDTH/HEIGHT)
     *
     * @throws IllegalArgumentException if the URI cannot be opened.
     */
    fun readVideoInfo(videoUri: String): VideoInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(videoUri))

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: error("Cannot read video width from $videoUri")

            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: error("Cannot read video height from $videoUri")

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: error("Cannot read video duration from $videoUri")

            // MediaMetadataRetriever returns FPS as a float string e.g. "30.00"
            val fps = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toDoubleOrNull()
                ?: retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toDoubleOrNull()
                    ?.let { frameCount ->
                        // Fallback: estimate FPS from frame count + duration
                        if (durationMs > 0) frameCount / (durationMs / 1000.0) else 30.0
                    }
                ?: 30.0  // Safe default if metadata is missing

            // Total frame estimate
            // Matches Python: total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            val totalFrames = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull()
                ?: (durationMs * fps / 1000.0).toInt()

            return VideoInfo(
                width = width,
                height = height,
                fps = fps,
                totalFrames = totalFrames,
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * Decode video frames as a cold [Flow<DecodedFrame>].
     *
     * Frames are emitted in temporal order. The flow completes when all
     * frames have been emitted or [maxFrames] is reached.
     *
     * The caller is responsible for recycling bitmaps after use.
     *
     * @param videoUri   Content or file URI.
     * @param skipFrames Emit every Nth frame (1 = all frames).
     * @param maxFrames  Maximum frames to emit (null = all).
     */
    fun framesFlow(
        videoUri: String,
        skipFrames: Int = 1,
        maxFrames: Int? = null,
    ): Flow<DecodedFrame> = flow {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(videoUri))

            val totalFrames = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull() ?: return@flow

            val frameLimit = if (maxFrames != null) {
                // maxFrames applies to emitted (post-skip) frames
                // To decode the right number of raw frames: limit = maxFrames * skipFrames
                minOf(totalFrames, maxFrames * skipFrames)
            } else {
                totalFrames
            }

            var emittedCount = 0

            for (rawIdx in 0 until frameLimit) {
                // Skip frames that don't fall on the skipFrames boundary
                // Matches Python: if frame_idx % skip_frames != 0: continue
                if (rawIdx % skipFrames != 0) continue

                if (maxFrames != null && emittedCount >= maxFrames) break

                // Decode frame at exact index
                val bitmap = retriever.getFrameAtIndex(rawIdx)
                    ?: continue  // Frame unavailable — treat same as invalid pose

                emit(
                    DecodedFrame(
                        bitmap = bitmap,
                        frameIndex = emittedCount,
                        timestamp = rawIdx.toLong(),  // raw container index as surrogate timestamp
                    )
                )
                emittedCount++
            }
        } finally {
            retriever.release()
        }
    }
}
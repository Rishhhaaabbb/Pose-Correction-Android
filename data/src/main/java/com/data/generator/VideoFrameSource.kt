package com.data.generator

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.MediaMetadataRetriever
import android.renderscript.Allocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decoded video frame metadata.
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
 * @param frameIndex 0-based index in the emitted (post-skip) sequence.
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
 * ── Why not getFrameAtIndex? ──────────────────────────────────────────────────
 * The original implementation used MediaMetadataRetriever.getFrameAtIndex(N)
 * in a loop. On many Android devices this method seeks from the beginning of
 * the video for EACH call, making the total decode complexity O(N²):
 *
 *   Frame 0:   seek 0  frames  → decode
 *   Frame 1:   seek 1  frame   → decode
 *   Frame 899: seek 899 frames → decode
 *   Total:     ~405,000 seek operations for a 30s@30fps video
 *
 * For a 60-second video this becomes millions of seeks — potentially minutes
 * of wall-clock time even before any MediaPipe inference runs.
 *
 * ── Fix: MediaExtractor + MediaCodec sequential decode ────────────────────────
 * MediaExtractor reads the container in forward order — it only ever seeks
 * forward, which maps directly to the hardware decoder's natural operation.
 * MediaCodec receives encoded samples and produces decoded YUV frames which
 * we convert to ARGB_8888 Bitmaps for MediaPipe.
 *
 * Total complexity: O(N) — each frame is decoded exactly once in order.
 *
 * ── Frame skipping ────────────────────────────────────────────────────────────
 * skipFrames=N: we still decode every frame (hardware decoder must see every
 * sample to maintain state), but we only emit every Nth decoded frame and
 * recycle the rest immediately. This matches Python's behavior exactly and
 * is far cheaper than seek-based skipping on most codecs.
 *
 * ── Timeout constants ────────────────────────────────────────────────────────
 * DEQUEUE_TIMEOUT_US: how long to wait for an input/output buffer from the
 * codec. 10ms is generous — typical hardware codecs respond in <1ms.
 * DRAIN_TIMEOUT_US: how long to wait when draining the codec after EOS.
 */
class VideoFrameSource(private val context: Context) {

    companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L   // 10ms
        private const val DRAIN_TIMEOUT_US   = 10_000L   // 10ms
        private const val MAX_DRAIN_ATTEMPTS = 100
    }

    /**
     * Read video metadata without decoding frames.
     *
     * Still uses MediaMetadataRetriever for metadata — this is O(1) and fine.
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

            val fps = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toDoubleOrNull()
                ?: retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toDoubleOrNull()
                    ?.let { frameCount ->
                        if (durationMs > 0) frameCount / (durationMs / 1000.0) else 30.0
                    }
                ?: 30.0

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
     * Decode video frames as a cold [Flow<DecodedFrame>] using MediaCodec.
     *
     * Frames are emitted in temporal order. Every encoded sample is decoded
     * (O(N) total), but only every [skipFrames]-th frame is emitted —
     * the rest are recycled immediately.
     *
     * The caller is responsible for recycling emitted bitmaps after use.
     */
    fun framesFlow(
        videoUri: String,
        skipFrames: Int = 1,
        maxFrames: Int? = null,
    ): Flow<DecodedFrame> = flow {

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, Uri.parse(videoUri), null)

            // Find the first video track
            val (trackIndex, format) = findVideoTrack(extractor)
                ?: return@flow  // No video track found

            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)

            // Configure for software output — we need raw pixels for MediaPipe.
            // Do NOT configure with a Surface — we need Bitmap access.
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var rawFrameIndex  = 0   // every decoded frame (including skipped)
            var emittedCount   = 0   // frames actually emitted
            var inputEos       = false
            var outputEos      = false

            while (!outputEos) {
                // ── Feed input ────────────────────────────────────────────────
                if (!inputEos) {
                    val inputBufferIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputBufferIdx >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIdx)!!
                        val sampleSize  = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            // End of stream — signal codec
                            codec.queueInputBuffer(
                                inputBufferIdx, 0, 0,
                                0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(
                                inputBufferIdx, 0, sampleSize,
                                presentationTimeUs, 0,
                            )
                            extractor.advance()
                        }
                    }
                }

                // ── Drain output ──────────────────────────────────────────────
                val outputBufferIdx = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)

                when {
                    outputBufferIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output ready yet — loop back and feed more input
                    }

                    outputBufferIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Format change — ignore, codec handles internally
                    }

                    outputBufferIdx >= 0 -> {
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        // Check if we should emit this frame
                        val shouldEmit = (rawFrameIndex % skipFrames == 0) &&
                                (maxFrames == null || emittedCount < maxFrames)

                        if (shouldEmit && bufferInfo.size > 0) {
                            // Get the decoded frame as a Bitmap.
                            // getOutputImage() returns a YUV Image on API 29+.
                            // For broad compatibility we use the output buffer directly
                            // via an intermediary Bitmap rendered from the codec output.
                            val bitmap = renderBitmapFromCodec(codec, outputBufferIdx, bufferInfo, format)

                            if (bitmap != null) {
                                emit(
                                    DecodedFrame(
                                        bitmap     = bitmap,
                                        frameIndex = emittedCount,
                                        timestamp  = bufferInfo.presentationTimeUs,
                                    )
                                )
                                emittedCount++
                            }
                        }

                        codec.releaseOutputBuffer(outputBufferIdx, false)
                        rawFrameIndex++

                        if (isEos) outputEos = true
                        if (maxFrames != null && emittedCount >= maxFrames) outputEos = true
                    }
                }
            }

        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns (trackIndex, MediaFormat) for the first video track, or null. */
    private fun findVideoTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime   = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i to format
        }
        return null
    }

    /**
     * Extract a decoded frame from the codec output buffer as an ARGB_8888 Bitmap.
     *
     * MediaCodec's default color format is COLOR_FormatYUV420Flexible on API 21+.
     * We use getOutputImage() (API 21+) to get the Image object and convert
     * YUV → ARGB via a manual NV21 conversion, which is allocation-efficient.
     *
     * Falls back to null if the buffer is empty or conversion fails — the
     * caller treats null the same as a frame with no detected pose.
     */
    private fun renderBitmapFromCodec(
        codec: MediaCodec,
        outputBufferIdx: Int,
        bufferInfo: MediaCodec.BufferInfo,
        format: MediaFormat,
    ): Bitmap? {
        return try {
            val image = codec.getOutputImage(outputBufferIdx) ?: return null
            val width  = image.width
            val height = image.height

            // Convert YUV_420_888 → ARGB_8888
            // YUV_420_888 has 3 planes: Y (luminance), U, V (chrominance).
            // This is the format guaranteed by MediaCodec on API 21+.
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val argbPixels = IntArray(width * height)

            for (row in 0 until height) {
                for (col in 0 until width) {
                    val yIdx = row * yRowStride + col
                    val uvRow = row / 2
                    val uvCol = col / 2
                    val uvIdx = uvRow * uvRowStride + uvCol * uvPixelStride

                    val y = (yBuffer.get(yIdx).toInt() and 0xFF)
                    val u = (uBuffer.get(uvIdx).toInt() and 0xFF) - 128
                    val v = (vBuffer.get(uvIdx).toInt() and 0xFF) - 128

                    // BT.601 YUV → RGB
                    val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
                    val g = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                    val b = (y + 1.772f * u).toInt().coerceIn(0, 255)

                    argbPixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            image.close()

            Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }
}
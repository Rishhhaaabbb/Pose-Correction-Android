package com.data.generator

import android.content.Context
import android.net.Uri
import com.core.poseengine.*
import com.core.poseengine.model.GraphMetadata
import com.domain.generator.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.datetime.Clock
import java.io.File
import javax.inject.Inject
import com.data.mediapipe.MediaPipePoseProvider
/**
 * Production implementation of [OfflineGraphGenerator].
 *
 * Orchestrates the full pipeline on [Dispatchers.Default] (CPU-bound work).
 * MediaPipe inference is called synchronously per-frame on this dispatcher.
 *
 * ── Pipeline steps (matching Python generate_from_video) ─────────────────────
 * 1.  Read video metadata          → emit [GenerationProgress.VideoInfo]
 * 2.  Decode frames (skipped)      → emit [GenerationProgress.FramesProcessed] every 30
 * 3.  Run MediaPipe per frame      → collect feature + keypoint sequences
 * 4.  Check validity threshold     → fail with [FailureReason.TOO_FEW_VALID_POSES]
 * 5.  Run StateDiscovery           → emit [GenerationProgress.StatesDiscovered]
 * 6.  Check states non-empty       → fail with [FailureReason.NO_STATES_DISCOVERED]
 * 7.  Build PoseGraph              → emit [GenerationProgress.GraphBuilt]
 * 8.  Validate graph               → warn or fail
 * 9.  Attach metadata              → typed [GraphMetadata]
 * 10. Save JSON to [outputPath]    → emit [GenerationProgress.Saved]
 * 11. Return result                → emit [GenerationProgress.Complete]
 */
class OfflineGraphGeneratorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val poseProvider: MediaPipePoseProvider,
    private val featureExtractor: FeatureExtractor,
) : OfflineGraphGenerator {
    private fun resolveDisplayName(context: Context, videoUri: String): String {
        // For content:// URIs (gallery picks), query the MediaStore display name.
        // Uri.lastPathSegment returns the media ID (e.g. "1000178312"), not the filename.
        return try {
            val uri = Uri.parse(videoUri)
            if (uri.scheme == "content") {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)  // e.g. "side-touch.mp4"
                    } else null
                }
            } else null
        } catch (e: Exception) { null }
            ?: Uri.parse(videoUri).lastPathSegment  // fallback for file:// URIs
            ?: videoUri
    }
    override fun generate(
        videoUri: String,
        config: GeneratorConfig,
        outputPath: String,
    ): Flow<GenerationProgress> = flow {
        // ── Step 1: Read video metadata ───────────────────────────────────────
        val frameSource = VideoFrameSource(context)

        val videoInfo = runCatching { frameSource.readVideoInfo(videoUri) }
            .getOrElse { e ->
                emit(
                    GenerationProgress.Failed(
                        reason = FailureReason.VIDEO_OPEN_FAILED,
                        detail = e.message ?: "Unknown error opening video",
                    )
                )
                return@flow
            }

        emit(
            GenerationProgress.VideoInfo(
                width = videoInfo.width,
                height = videoInfo.height,
                fps = videoInfo.fps,
                totalFrames = videoInfo.totalFrames,
                durationSeconds = videoInfo.durationSeconds,
            )
        )

        val effectiveFps = config.effectiveFps(videoInfo.fps)

        // ── Steps 2–3: Decode frames + run MediaPipe ──────────────────────────
        // Matches Python: _extract_features_from_video()
        val featureSequence = mutableListOf<com.core.poseengine.model.RawFeatureMap>()
        val keypointSequence = mutableListOf<com.core.poseengine.model.Skeleton2d?>()

        var processedCount = 0

        frameSource.framesFlow(
            videoUri = videoUri,
            skipFrames = config.skipFrames,
            maxFrames = config.maxFrames,
        ).collect { decodedFrame ->
            // Respect coroutine cancellation (user navigated away)
            if (!currentCoroutineContext().isActive) return@collect

            // Run MediaPipe on this frame
            val skeleton: com.core.poseengine.model.Skeleton2d? =
                poseProvider.detect(decodedFrame.bitmap)

            // Recycle immediately — MediaPipe has copied pixel data
            decodedFrame.bitmap.recycle()

            if (skeleton != null) {
                val features = featureExtractor.extractFeatures(skeleton)
                // Matches Python: feature_sequence.append(features if features else {})
                featureSequence.add(features ?: com.core.poseengine.model.RawFeatureMap.EMPTY)
            } else {
                // No skeleton detected — matches Python: feature_sequence.append({})
                featureSequence.add(com.core.poseengine.model.RawFeatureMap.EMPTY)
            }
            keypointSequence.add(skeleton)

            processedCount++

            // Emit progress every 30 frames — matches Python: if processed_count % 30 == 0
            if (processedCount % 30 == 0) {
                emit(
                    GenerationProgress.FramesProcessed(
                        processedSoFar = processedCount,
                        totalEstimate = videoInfo.totalFrames / config.skipFrames,
                    )
                )
            }
        }

        // Final frame count update
        val validFrames = featureSequence.count { !it.isEmpty }
        emit(
            GenerationProgress.ExtractionComplete(
                totalFrames = featureSequence.size,
                validFrames = validFrames,
                validPercent = if (featureSequence.isEmpty()) 0.0
                else validFrames.toDouble() / featureSequence.size * 100.0,
            )
        )

        // ── Step 4: Validity check ────────────────────────────────────────────
        // Matches Python: if valid_frames < 10: raise ValueError(...)
        if (validFrames < MINIMUM_VALID_FRAMES) {
            emit(
                GenerationProgress.Failed(
                    reason = FailureReason.TOO_FEW_VALID_POSES,
                    detail = "Only $validFrames valid poses detected out of " +
                            "${featureSequence.size} frames. " +
                            "Check video quality or camera angle.",
                )
            )
            return@flow
        }

        // ── Step 5: State discovery ───────────────────────────────────────────
        val stateDiscovery = StateDiscovery(
            stabilityThreshold = config.stabilityThreshold,
            minHoldFrames = config.minHoldFrames,
            smoothingWindow = config.smoothingWindow,
            transitionBuffer = config.transitionBuffer,
            toleranceMultiplier = config.toleranceMultiplier,
        )
        
        android.util.Log.d("GraphGenerator", "StateDiscovery params:")
        android.util.Log.d("GraphGenerator", "  stabilityThreshold: ${stateDiscovery.stabilityThreshold}")
        android.util.Log.d("GraphGenerator", "  mergeThreshold: ${stateDiscovery.mergeThreshold}")
        android.util.Log.d("GraphGenerator", "  minHoldFrames: ${stateDiscovery.minHoldFrames}")
        android.util.Log.d("GraphGenerator", "  smoothingWindow: ${stateDiscovery.smoothingWindow}")

        // Matches Python:
        //   states = self.state_discovery.discover_states(feature_sequence, fps, keypoints)
        //   temporal_sequence = self.state_discovery.get_temporal_sequence()
        //
        // Now a single call returning DiscoveryResult — no two-call pattern.
        val discoveryResult = stateDiscovery.discoverStates(
            featureSequence = featureSequence,
            fps = effectiveFps,
            keypointSequence = keypointSequence,
        )

        val numSegments = discoveryResult.temporalSequence.size

        // ── Step 6: Check states non-empty ────────────────────────────────────
        // Matches Python: if not states: raise ValueError(...)
        if (discoveryResult.isEmpty) {
            emit(
                GenerationProgress.Failed(
                    reason = FailureReason.NO_STATES_DISCOVERED,
                    detail = "No pose states discovered. " +
                            "Try adjusting stabilityThreshold or minHoldFrames, " +
                            "or check that the video contains clear pose holds.",
                )
            )
            return@flow
        }

        emit(
            GenerationProgress.StatesDiscovered(
                numStates = discoveryResult.states.size,
                temporalSequence = discoveryResult.temporalSequence,
                numSegments = numSegments,
                hadMerging = numSegments > discoveryResult.states.size,
            )
        )

        // ── Step 7: Build graph ───────────────────────────────────────────────
        // Matches Python:
        //   graph = PoseGraph()
        //   graph.build_from_temporal_sequence(states, temporal_sequence)
        val videoFilename = resolveDisplayName(context, videoUri)
        val now = Clock.System.now().toString()  // ISO-8601, matches Python datetime.now().isoformat()

        val metadata = GraphMetadata(
            videoPath = videoFilename,
            fps = effectiveFps,
            originalFps = videoInfo.fps,
            skipFrames = config.skipFrames,
            totalFrames = featureSequence.size,
            validFrames = validFrames,
            numStates = discoveryResult.states.size,
            numSegments = numSegments,
            createdAt = now,
            stabilityThreshold = config.stabilityThreshold,
            temporalSequence = discoveryResult.temporalSequence,
        )

        val graph = PoseGraph.buildFromDiscoveryResult(
            result = discoveryResult,
            metadata = metadata,
        )

        val stats = graph.getStatistics()

        // ── Step 8: Validate ──────────────────────────────────────────────────
        val validationResult = graph.validate()

        emit(
            GenerationProgress.GraphBuilt(
                isLinear = stats.isLinear,
                hasCycles = stats.hasCycles,
                numTransitions = stats.numTransitions,
                validationPassed = validationResult.isValid,
                validationErrors = when (validationResult) {
                    is com.core.poseengine.model.ValidationResult.Invalid ->
                        validationResult.errors
                    else -> emptyList()
                },
            )
        )

        // Fail hard on validation failure — matches Python's WARNING but we treat
        // it as a fatal error since a broken graph cannot be used by the monitor.
        if (!validationResult.isValid) {
            emit(
                GenerationProgress.Failed(
                    reason = FailureReason.GRAPH_VALIDATION_FAILED,
                    detail = "Graph validation failed: " +
                    (validationResult as? com.core.poseengine.model.ValidationResult.Invalid)
                        ?.errors?.joinToString("; ") ?: "Unknown error",
                )
            )
            return@flow
        }

        // ── Steps 9–10: Save JSON ─────────────────────────────────────────────
        // Matches Python: graph.save_json(output_json_path)
        val saveResult = runCatching {
            val json = graph.toJson(prettyPrint = true)
            File(outputPath).writeText(json, Charsets.UTF_8)
        }

        if (saveResult.isFailure) {
            emit(
                GenerationProgress.Failed(
                    reason = FailureReason.SAVE_FAILED,
                    detail = saveResult.exceptionOrNull()?.message ?: "File write failed",
                )
            )
            return@flow
        }

        emit(GenerationProgress.Saved(outputPath = outputPath))

        // ── Step 11: Complete ─────────────────────────────────────────────────
        val result = GenerationResult(
            graph = graph,
            numFramesTotal = featureSequence.size,
            numFramesValid = validFrames,
            numStates = discoveryResult.states.size,
            numSegments = numSegments,
            isLinear = stats.isLinear,
            hasCycles = stats.hasCycles,
            numTransitions = stats.numTransitions,
            outputPath = outputPath,
        )

        emit(GenerationProgress.Complete(result))

    }.flowOn(Dispatchers.Default)  // CPU-bound pipeline off main thread

    companion object {
        /**
         * Minimum valid frames required for state discovery.
         * Matches Python: if valid_frames < 10: raise ValueError(...)
         */
        private const val MINIMUM_VALID_FRAMES = 10
    }
}
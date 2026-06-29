package com.domain.generator

import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for the offline graph generation pipeline.
 *
 * Replaces Python: OfflineGraphGenerator class (public API only).
 * Implementation lives in [data/generator/OfflineGraphGeneratorImpl].
 *
 * ── Pipeline (matches Python generate_from_video) ─────────────────────────────
 * 1. Decode video → raw frames
 * 2. Run MediaPipe PoseLandmarker on each frame
 * 3. Extract normalized features from landmarks
 * 4. Discover stable pose states via temporal analysis
 * 5. Merge similar segments into canonical states
 * 6. Build directed graph from temporal transitions
 * 7. Attach metadata (fps, video path, temporal sequence...)
 * 8. Validate graph
 * 9. Save JSON to [outputPath]
 * 10. Emit [GenerationProgress.Complete]
 *
 * ── Usage ─────────────────────────────────────────────────────────────────────
 *   generator.generate(uri, config, outputPath)
 *       .collect { progress ->
 *           when (progress) {
 *               is GenerationProgress.FramesProcessed -> updateProgressBar(...)
 *               is GenerationProgress.Complete        -> showSuccess(progress.result)
 *               is GenerationProgress.Failed          -> showError(progress.reason)
 *               else -> {}
 *           }
 *       }
 *
 * The flow is cold — generation starts on collection and stops if the
 * collector is cancelled (e.g., the user navigates away).
 */
interface OfflineGraphGenerator {

    /**
     * Run the full generation pipeline, emitting [GenerationProgress] events.
     *
     * The flow always terminates with exactly one of:
     *   - [GenerationProgress.Complete] — success
     *   - [GenerationProgress.Failed]   — any failure
     *
     * @param videoUri    Android content URI or file URI for the source video.
     * @param config      Generation parameters.
     * @param outputPath  Absolute path where the graph JSON will be saved.
     */
    fun generate(
        videoUri: String,
        config: GeneratorConfig,
        outputPath: String,
    ): Flow<GenerationProgress>
}
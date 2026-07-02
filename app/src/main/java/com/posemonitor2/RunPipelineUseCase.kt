// domain/src/main/java/com/domain/usecase/RunPipelineUseCase.kt

package com.posemonitor2

import android.util.Log
import com.core.poseengine.FeatureExtractor
import com.core.poseengine.PoseGraph
import com.core.poseengine.model.RawFeatureMap
import com.core.poseengine.model.Skeleton2d
import com.core.poseengine.monitor.FrameStatus
import com.core.poseengine.monitor.MonitorConfig
import com.core.poseengine.monitor.PoseMonitorEngine
import com.core.poseengine.monitor.SystemTimeSource
import com.data.mediapipe.PoseDetectorResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Orchestrates the full pipeline for one frame:
 *
 *   PoseDetectorResult
 *       ↓  Layer 3
 *   Skeleton2d → FeatureExtractor → RawFeatureMap
 *       ↓  Layer 4
 *   PoseMonitorEngine → FrameStatus
 *
 * ── Performance change ────────────────────────────────────────────────────────
 * All Log.d calls are now gated behind [DEBUG_LOG].
 *
 * Original code called Log.d on every frame (30fps), including:
 *   - "Layer 3 ✅ — features extracted: ..." with joinToString() allocation
 *   - "Layer 4 ✅ — state=..." with string formatting
 *   - buildLayerLog() which iterated all features with padEnd + format per key
 *   - A separate "FeatureDebug" log dumping the entire feature map
 *
 * Each of those triggered: String allocation → char[] allocation → I/O syscall.
 * At 30fps that's ~120 unnecessary allocations/second just from logging.
 *
 * Set DEBUG_LOG = true only during active debugging. Never ship with it on.
 */
class RunPipelineUseCase(
    graph: PoseGraph,
    config: MonitorConfig = MonitorConfig.DEFAULT,
    holdTimeOverrideSeconds: Double? = null,
) {
    private val extractor = FeatureExtractor(confidenceThreshold = 0.25)
    val engine = PoseMonitorEngine(
        graph = graph,
        config = config,
        time = SystemTimeSource,
        holdTimeOverrideSeconds = holdTimeOverrideSeconds,
    )

    /**
     * Map a flow of [PoseDetectorResult] into a flow of [PipelineResult].
     *
     * Each upstream frame produces exactly one [PipelineResult] downstream —
     * the ViewModel always gets an update, even for frames with no pose.
     */
    fun process(detectorFlow: Flow<PoseDetectorResult>): Flow<PipelineResult> =
        detectorFlow.map { detectorResult ->
            when (detectorResult) {
                is PoseDetectorResult.NoLandmarker -> {
                    val status = engine.processFeatures(null)
                    if (DEBUG_LOG) Log.d(TAG, "Landmarker not ready")
                    PipelineResult(
                        detectorResult = detectorResult,
                        skeleton = null,
                        features = null,
                        frameStatus = status,
                        layerLog = if (DEBUG_LOG) "Landmarker not ready" else "",
                    )
                }

                is PoseDetectorResult.NoPose -> {
                    val status = engine.processFeatures(null)
                    if (DEBUG_LOG) Log.d(TAG, "Layer 1 ❌ No pose detected")
                    PipelineResult(
                        detectorResult = detectorResult,
                        skeleton = null,
                        features = null,
                        frameStatus = status,
                        layerLog = if (DEBUG_LOG) "Layer 1 ❌ No pose detected" else "",
                    )
                }

                is PoseDetectorResult.MultiplePoses -> {
                    engine.notifyMultiplePeople()
                    val status = engine.processFeatures(null)
                    if (DEBUG_LOG) Log.d(TAG, "Layer 1 ❌ Multiple poses detected")
                    PipelineResult(
                        detectorResult = detectorResult,
                        skeleton = null,
                        features = null,
                        frameStatus = status,
                        layerLog = if (DEBUG_LOG) "Layer 1 ❌ Multiple poses detected" else "",
                    )
                }

                is PoseDetectorResult.Detected -> {
                    val skeleton = detectorResult.skeleton

                    // Layer 3 — Feature extraction
                    val features = extractor.extractFeaturesWithFallback(skeleton)

                    if (features == null) {
                        if (DEBUG_LOG) Log.d(TAG, "Layer 3 ❌ — feature extraction failed")
                        val status = engine.processFeatures(null)
                        PipelineResult(
                            detectorResult = detectorResult,
                            skeleton = skeleton,
                            features = null,
                            frameStatus = status,
                            layerLog = if (DEBUG_LOG) "Layer 3 ❌ Feature extraction failed" else "",
                        )
                    } else {
                        // Layer 4 — Engine
                        val status = engine.processFeatures(features)

                        // All string work is skipped entirely in production.
                        // buildLayerLog iterates every feature key with padEnd+format —
                        // that's ~15 string allocations per frame avoided here.
                        if (DEBUG_LOG) {
                            Log.d(TAG, "Layer 3 ✅ — features: ${features.map.entries
                                .take(4)
                                .joinToString { "${it.key.name}=${"%.1f".format(it.value)}" }
                            }...")
                            Log.d(TAG, "Layer 4 ✅ — state=${status.stateId} " +
                                    "correct=${status.isCorrect} " +
                                    "progress=${"%.2f".format(status.holdProgress)} " +
                                    "reps=${status.repsCompleted}")
                            Log.d("FeatureDebug", features.map.entries.joinToString("\n") {
                                "${it.key.name}: ${"%.2f".format(it.value)}"
                            })
                        }

                        PipelineResult(
                            detectorResult = detectorResult,
                            skeleton = skeleton,
                            features = features,
                            frameStatus = status,
                            layerLog = if (DEBUG_LOG) buildLayerLog(detectorResult.landmarkCount, features, status) else "",
                        )
                    }
                }
            }
        }

    private fun buildLayerLog(
        landmarkCount: Int,
        features: RawFeatureMap,
        status: FrameStatus,
    ): String = buildString {
        appendLine("Layer 1 ✅  MP landmarks: $landmarkCount")
        appendLine("Layer 2 ✅  COCO keypoints: 17")
        appendLine("Layer 3 ✅  Features: ${features.map.size}")
        features.map.entries.take(6).forEach { (k, v) ->
            appendLine("   ${k.name.padEnd(24)} ${"%.1f".format(v)}")
        }
        appendLine("Layer 4 ✅  State: ${status.stateId}  " +
                "Correct: ${status.isCorrect}  " +
                "Progress: ${"%.0f".format(status.holdProgress * 100)}%  " +
                "Reps: ${status.repsCompleted}")
        append("   Feedback: ${status.feedback}")
    }

    companion object {
        private const val TAG = "Pipeline"

        /**
         * Master debug switch for all pipeline logging.
         *
         * FALSE in production — eliminates all string allocations and I/O from the
         * hot frame path (~120 allocations/second at 30fps removed when false).
         *
         * Set to true only when actively debugging layer output.
         */
        const val DEBUG_LOG = false
    }
}

// ── Result type ───────────────────────────────────────────────────────────────

data class PipelineResult(
    val detectorResult: PoseDetectorResult,
    val skeleton: Skeleton2d?,
    val features: RawFeatureMap?,
    val frameStatus: FrameStatus,
    val layerLog: String,
)
// app/src/main/java/com/posemonitor2/DebugViewModel.kt

package com.posemonitor2

import android.app.Application
import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.poseengine.PoseGraph
import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.PoseState
import com.core.poseengine.model.Skeleton2d
import com.core.poseengine.monitor.FrameStatus
import com.core.poseengine.monitor.MonitorConfig
import com.data.mediapipe.PoseDetector
import com.data.mediapipe.PoseDetectorResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class DebugViewModel(application: Application) : AndroidViewModel(application) {

    val detector = PoseDetector(application)

    val currentSkeleton      = MutableStateFlow<Skeleton2d?>(null)
    val cameraFrameSize      = MutableStateFlow<Size?>(null)
    val frameStatus          = MutableStateFlow<FrameStatus?>(null)
    val targetState          = MutableStateFlow<PoseState?>(null)
    val guidancePreviewState = MutableStateFlow<PoseState?>(null)
    val lastDeviations       = MutableStateFlow<Map<FeatureKey, Double>>(emptyMap())
    val initError            = MutableStateFlow<String?>(null)
    val cameraFacing         = MutableStateFlow(detector.currentLensFacing())
    val zoomRatio            = MutableStateFlow(detector.currentZoomRatio())
    val zoomRange            = MutableStateFlow(detector.currentZoomRange())
    val speechPreferences    = MutableStateFlow(MonitorSpeechPreferences())

    private var useCase: RunPipelineUseCase? = null

    init {
        detector.onZoomRatioChanged = { ratio ->
            zoomRatio.value = ratio
        }
        detector.onZoomRangeChanged = { minZoom, maxZoom ->
            zoomRange.value = minZoom..maxZoom
        }

        try {
            detector.initialize(confidenceThreshold = 0.5f)
            Log.d(TAG, "PoseDetector initialised")
        } catch (e: Exception) {
            Log.e(TAG, "PoseDetector init failed: ${e.message}")
            initError.value = e.message
        }
    }

    fun startPipeline(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        graphEntry: GraphEntry,
    ) {
        val app = getApplication<Application>()
        val graph = runCatching { loadGraph(app, graphEntry) }.getOrElse { e ->
            Log.e(TAG, "Failed to load graph: ${e.message}")
            initError.value = "Failed to load graph: ${e.message}"
            return
        }

        speechPreferences.value = MonitorSpeechPreferences(
            language = graphEntry.speechLanguage,
            speedMode = graphEntry.speechSpeedMode,
        )

        useCase = RunPipelineUseCase(
            graph = graph,
            config = MonitorConfig(
                guidanceDurationSeconds = if (graphEntry.enableGuidance) 2.0 else 0.0,
                enableAdaptive          = true,
                progressiveTolerance    = true,
            ),
        )

        detector.onFrameSize = { size -> cameraFrameSize.value = size }

        viewModelScope.launch {
            useCase!!.process(detector.frameFlow(lifecycleOwner))
                .catch { e ->
                    Log.e(TAG, "Pipeline error: ${e.message}")
                    initError.value = e.message
                }
                .collect { result ->
                    currentSkeleton.value      = result.skeleton
                    frameStatus.value          = result.frameStatus
                    lastDeviations.value       = result.frameStatus.deviations
                    targetState.value          = useCase!!.engine.targetState
                    guidancePreviewState.value = useCase!!.engine.guidancePreviewState

                    if (RunPipelineUseCase.DEBUG_LOG) {
                        Log.d(TAG, buildLayerLog(result))
                    }
                }
        }
    }

    fun skipCurrentPose() {
        useCase?.engine?.skipCurrentState()
    }

    fun toggleCameraFacing() {
        cameraFacing.value = detector.toggleCameraFacing()
    }

    fun setZoomPreset(presetZoomRatio: Float) {
        zoomRatio.value = detector.applyPresetZoom(presetZoomRatio)
    }

    fun onPinchZoom(scaleFactor: Float) {
        zoomRatio.value = detector.zoomByScale(scaleFactor)
    }

    fun cameraFacingLabel(): String =
        if (cameraFacing.value == CameraSelector.LENS_FACING_BACK) "BACK" else "FRONT"

    private fun loadGraph(context: Context, entry: GraphEntry): PoseGraph {
        return if (entry.isAsset) {
            val json = context.assets.open(entry.fileName).bufferedReader().readText()
            PoseGraph.fromJson(json)
        } else {
            val json = File(entry.filePath).readText(Charsets.UTF_8)
            PoseGraph.fromJson(json)
        }
    }

    private fun buildLayerLog(result: PipelineResult): String {
        val status = result.frameStatus
        val sb = StringBuilder()
        sb.append("[L1] ")
        sb.append(when (result.detectorResult) {
            is PoseDetectorResult.Detected     -> "MP landmarks: ${result.detectorResult.landmarkCount}"
            is PoseDetectorResult.NoPose       -> "No pose"
            is PoseDetectorResult.NoLandmarker -> "Initialising"
        })
        sb.append(" | [L2] ")
        sb.append(if (result.skeleton != null) "COCO-17 OK" else "No skeleton")
        if (result.features != null) {
            sb.append(" | [L3] ${result.features.map.size} features")
            result.features.map.entries.forEach { (k, v) ->
                sb.append("\n  ${k.name.padEnd(24)} ${"%.2f".format(v)}")
            }
        } else {
            sb.append(" | [L3] failed")
        }
        sb.append("\n[L4] state=${status.stateId} correct=${status.isCorrect}" +
                " progress=${"%.0f".format(status.holdProgress * 100)}%" +
                " reps=${status.repsCompleted} streak=${status.streak}" +
                " phase=${status.phase.name} feedback=${status.feedback}")
        return sb.toString()
    }

    companion object {
        private const val TAG = "DebugViewModel"
    }
}
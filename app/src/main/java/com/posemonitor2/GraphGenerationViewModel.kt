// app/src/main/java/com/posemonitor2/GraphGenerationViewModel.kt

package com.posemonitor2

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.core.poseengine.FeatureExtractor
import com.data.generator.OfflineGraphGeneratorImpl
import com.data.mediapipe.MediaPipePoseProviderImpl
import com.domain.generator.GenerationProgress
import com.domain.generator.GeneratorConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class GraphGenerationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private val generator by lazy {
        OfflineGraphGeneratorImpl(
            context = application,
            poseProvider = MediaPipePoseProviderImpl(application),
            featureExtractor = FeatureExtractor(confidenceThreshold = 0.5),
        )
    }

    /**
     * @param graphName User-provided name for the graph (no extension).
     *   If blank, falls back to the video filename.
     */
    fun startGeneration(videoUri: String, config: GeneratorConfig, graphName: String = "") {
        viewModelScope.launch {
            val outputDir = File(getApplication<Application>().filesDir, "graphs")
            outputDir.mkdirs()

            val fileName = graphName.trim()
                .takeIf { it.isNotEmpty() }
                ?.plus(".json")
                ?: resolveVideoFileName(videoUri)

            val outputPath = File(outputDir, fileName).absolutePath

            Log.d(TAG, "Starting generation: $videoUri → $outputPath")
            Log.d(TAG, "Config: $config")

            generator.generate(
                videoUri   = videoUri,
                config     = config,
                outputPath = outputPath,
            ).collect { progress ->
                when (progress) {
                    is GenerationProgress.VideoInfo -> {
                        _uiState.value = GenerationUiState.Processing(
                            message          = "Reading video...",
                            detail           = "${progress.width}×${progress.height} @ ${progress.fps.toInt()} fps  ${progress.totalFrames} frames",
                            progressFraction = 0f,
                        )
                    }
                    is GenerationProgress.FramesProcessed -> {
                        val fraction = progress.totalEstimate
                            ?.let { progress.processedSoFar.toFloat() / it } ?: 0f
                        _uiState.value = GenerationUiState.Processing(
                            message          = "Extracting poses...",
                            detail           = "${progress.processedSoFar} frames processed",
                            progressFraction = fraction.coerceIn(0f, 0.8f),
                        )
                    }
                    is GenerationProgress.ExtractionComplete -> {
                        _uiState.value = GenerationUiState.Processing(
                            message          = "Analysing poses...",
                            detail           = "${progress.validFrames} valid frames (${"%.1f".format(progress.validPercent)}%)",
                            progressFraction = 0.85f,
                        )
                    }
                    is GenerationProgress.StatesDiscovered -> {
                        _uiState.value = GenerationUiState.Processing(
                            message          = "Building graph...",
                            detail           = "${progress.numStates} states from ${progress.numSegments} segments",
                            progressFraction = 0.92f,
                        )
                    }
                    is GenerationProgress.GraphBuilt -> {
                        _uiState.value = GenerationUiState.Processing(
                            message          = "Validating graph...",
                            detail           = if (progress.validationPassed) "Graph validated ✓"
                            else "Warnings: ${progress.validationErrors.joinToString()}",
                            progressFraction = 0.96f,
                        )
                    }
                    is GenerationProgress.Saved -> {
                        _uiState.value = GenerationUiState.Processing(
                            message          = "Saving...",
                            detail           = progress.outputPath,
                            progressFraction = 0.99f,
                        )
                    }
                    is GenerationProgress.Complete -> {
                        Log.d(TAG, "Generation complete: ${progress.result.outputPath}")
                        _uiState.value = GenerationUiState.Success(
                            outputPath = progress.result.outputPath,
                            numStates  = progress.result.numStates,
                            numFrames  = progress.result.numFramesValid,
                        )
                    }
                    is GenerationProgress.Failed -> {
                        Log.e(TAG, "Generation failed: ${progress.reason} — ${progress.detail}")
                        _uiState.value = GenerationUiState.Error(
                            reason = progress.reason.name,
                            detail = progress.detail,
                        )
                    }
                }
            }
        }
    }

    private fun resolveVideoFileName(videoUri: String): String {
        val uri = Uri.parse(videoUri)
        var displayName: String? = null

        try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (col >= 0) displayName = cursor.getString(col)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve display name for $videoUri: ${e.message}")
        }

        return displayName
            ?.substringBeforeLast(".")
            ?.plus(".json")
            ?: "graph_${System.currentTimeMillis()}.json"
    }

    fun reset() {
        _uiState.value = GenerationUiState.Idle
    }

    companion object {
        private const val TAG = "GenerationViewModel"
    }
}

sealed class GenerationUiState {
    object Idle : GenerationUiState()
    data class Processing(
        val message: String,
        val detail: String,
        val progressFraction: Float,
    ) : GenerationUiState()
    data class Success(
        val outputPath: String,
        val numStates: Int,
        val numFrames: Int,
    ) : GenerationUiState()
    data class Error(
        val reason: String,
        val detail: String,
    ) : GenerationUiState()
}
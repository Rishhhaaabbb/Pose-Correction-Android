#!/usr/bin/env kotlin

package com.domain

import com.core.poseengine.PoseGraph
import com.core.poseengine.monitor.*
import com.core.poseengine.model.RawFeatureMap
import kotlinx.coroutines.flow.Flow

/**
 * Use case interface for real-time pose monitoring.
 *
 * Defines the contract between the data layer (MediaPipe + camera) and the
 * presentation layer (ViewModel). Implemented in [data/] where Android
 * imports are permitted.
 *
 * ── Data flow ─────────────────────────────────────────────────────────────────
 *
 *  Camera frame
 *      ↓
 *  MediaPipePoseProvider.detect(frame) → Skeleton2d
 *      ↓
 *  FeatureExtractor.extractFeaturesWithFallback(skeleton) → RawFeatureMap
 *      ↓
 *  PoseMonitorEngine.processFeatures(features) → FrameStatus
 *      ↓
 *  StateFlow<FrameStatus> → ViewModel → Compose UI
 */
interface MonitorPoseUseCase {

    /**
     * Emits [FrameStatus] for each processed camera frame.
     *
     * The flow is cold — collection starts the camera and MediaPipe pipeline.
     * Cancelling the flow stops the camera.
     *
     * @param graph   Loaded pose graph to monitor against.
     * @param config  Engine configuration.
     */
    fun frameStatusFlow(
        graph: PoseGraph,
        config: MonitorConfig,
    ): Flow<FrameStatus>

    /**
     * Skip the current pose state immediately.
     * Safe to call from any coroutine context.
     */
    suspend fun skipCurrentState()

    /**
     * Retrieve the session summary at any point during or after the session.
     */
    suspend fun getSessionSummary(): SessionSummary
}

/**
 * Repository interface for loading and saving [PoseGraph] files.
 */
interface GraphRepository {

    /**
     * Load a [PoseGraph] from a file path (local storage).
     *
     * @throws IllegalArgumentException if the file is not a valid pose graph.
     * @throws java.io.IOException if the file cannot be read.
     */
    suspend fun loadGraph(filePath: String): PoseGraph

    /**
     * Save a [PoseGraph] to a file path.
     *
     * @throws java.io.IOException if the file cannot be written.
     */
    suspend fun saveGraph(graph: PoseGraph, filePath: String)

    /**
     * List all saved graph files in the default app storage directory.
     */
    suspend fun listSavedGraphs(): List<String>
}
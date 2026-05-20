// app/src/main/java/com/posemonitor2/DebugViewModel.kt

package com.posemonitor2

import android.app.Application
import android.content.Context
import android.util.Log
import android.util.Size
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
import com.domain.model.DailyStat
import com.domain.model.Exercise
import com.domain.model.PoseGraphRecord
import com.domain.model.SessionMetric
import com.domain.model.SessionStatus
import com.domain.model.WorkoutSession
import com.domain.repository.AuthRepository
import com.domain.repository.ExerciseRepository
import com.domain.usecase.ObserveAuthSessionUseCase
import com.domain.usecase.ObserveDailyStatsUseCase
import com.domain.usecase.SavePoseGraphUseCase
import com.domain.usecase.SaveSessionMetricUseCase
import com.domain.usecase.SaveWorkoutSessionUseCase
import androidx.camera.core.ZoomState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    application: Application,
    private val observeAuthSessionUseCase: ObserveAuthSessionUseCase,
    private val observeDailyStatsUseCase: ObserveDailyStatsUseCase,
    private val saveWorkoutSessionUseCase: SaveWorkoutSessionUseCase,
    private val saveSessionMetricUseCase: SaveSessionMetricUseCase,
    private val upsertDailyStatUseCase: com.domain.usecase.UpsertDailyStatUseCase,
    private val savePoseGraphUseCase: SavePoseGraphUseCase,
    private val exerciseRepository: ExerciseRepository,
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {

    val detector = PoseDetector(application)

    // ── Public StateFlows ─────────────────────────────────────────────────────

    val isFrontCamera       = MutableStateFlow(true)
    val isMuted             = MutableStateFlow(false)
    val currentSkeleton      = MutableStateFlow<Skeleton2d?>(null)
    val cameraFrameSize      = MutableStateFlow<Size?>(null)
    val frameStatus          = MutableStateFlow<FrameStatus?>(null)
    val targetState          = MutableStateFlow<PoseState?>(null)
    val guidancePreviewState = MutableStateFlow<PoseState?>(null)
    val lastDeviations       = MutableStateFlow<Map<FeatureKey, Double>>(emptyMap())
    val initError            = MutableStateFlow<String?>(null)
    val sessionHoldDurationSeconds = MutableStateFlow<Double?>(null)
    val frameRotationDegrees = MutableStateFlow(0)
    
    // ── Camera Zoom State (Hardware) ──────────────────────────────────────────
    val zoomLevel = MutableStateFlow(0f) // User input [0.0 - 1.0]
    
    // Live hardware ratios (from detector)
    val zoomState: StateFlow<ZoomState?> = detector.zoomStateFlow
    
    val currentZoomRatio = zoomState.map { it?.zoomRatio ?: 1.0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
        
    val minZoomRatio = zoomState.map { it?.minZoomRatio ?: 1.0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
        
    val maxZoomRatio = zoomState.map { it?.maxZoomRatio ?: 1.0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    // Tracks the historical accuracy of each completed rep [0.0 - 1.0]
    val completedRepAccuracies = MutableStateFlow<List<Double>>(emptyList())

    // Target rep count for current session
    val targetReps = MutableStateFlow(10)
    val currentRepCount = MutableStateFlow(0)
    val sessionCompleted = MutableStateFlow(false)

    // ── Internal pipeline ─────────────────────────────────────────────────────

    private var useCase: RunPipelineUseCase? = null
    private var currentUserId: String? = null
    private var currentGraphEntry: GraphEntry? = null
    private var sessionStartEpochMs: Long = 0L
    private var completionPersisted: Boolean = false

    // Shared auth state - updated from repository
    private val authUserId: String?
        get() = currentUserId

    fun getCurrentExerciseName(): String? = currentGraphEntry?.fileName

    init {
        // Defer heavy MediaPipe initialisation until we actually start the
        // monitoring pipeline to reduce cold-start time for the app UI.

        viewModelScope.launch {
            observeAuthSessionUseCase().collect { authUser ->
                currentUserId = authUser?.id
                Log.d(TAG, "Auth session updated: userId=${authUser?.id}")
            }
        }

        // Also observe directly from repository to catch dummy sessions
        viewModelScope.launch {
            authRepository.observeSession().collect { authUser ->
                if (authUser != null && authUser.id.isNotBlank()) {
                    currentUserId = authUser.id
                    Log.d(TAG, "Direct session update: userId=${authUser.id}")
                }
            }
        }

        viewModelScope.launch {
            detector.frameRotationDegreesFlow.collect { rotation ->
                frameRotationDegrees.value = rotation
            }
        }
    }

    fun setTargetReps(reps: Int) {
        targetReps.value = reps.coerceAtLeast(1)
        currentRepCount.value = 0
        sessionCompleted.value = false
        lastRepsCompleted = 0
        skipUsedInCurrentRep = false
        completedRepAccuracies.value = emptyList()
    }

    fun resetSessionState() {
        sessionCompleted.value = false
    }

    fun setSessionHoldDuration(seconds: Double?) {
        sessionHoldDurationSeconds.value = seconds?.coerceAtLeast(0.1)
    }

    fun stopPipeline() {
        useCase = null
    }

    fun startPipeline(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        graphEntry: GraphEntry,
    ) {
        currentGraphEntry = graphEntry
        sessionStartEpochMs = System.currentTimeMillis()
        completionPersisted = false
        lastRepsCompleted = 0
        currentRepFrameCount = 0
        currentRepCorrectCount = 0
        skipUsedInCurrentRep = false
        completedRepAccuracies.value = emptyList()

        val app = getApplication<Application>()
        val graph = runCatching { loadGraph(app, graphEntry) }.getOrElse { e ->
            Log.e(TAG, "Failed to load graph: ${e.message}")
            initError.value = "Failed to load graph: ${e.message}"
            return
        }

        useCase = RunPipelineUseCase(
            graph = graph,
            config = MonitorConfig(
                guidanceDurationSeconds = 2.0,
                enableAdaptive = true,
                progressiveTolerance = true,
            ),
            holdTimeOverrideSeconds = sessionHoldDurationSeconds.value,
        )

        // Wire frame size callback — reported once on first frame after rotation.
        detector.onFrameSize = { size -> cameraFrameSize.value = size }

        val cameraSelector = if (isFrontCamera.value) 
            androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA 
        else 
            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
        // Ensure MediaPipe/landmarker is ready before binding the CameraX pipeline.
        ensureDetectorInitialized()

        viewModelScope.launch {
            useCase!!.process(detector.frameFlow(lifecycleOwner, cameraSelector))
                .catch { e ->
                    Log.e(TAG, "Pipeline error: ${e.message}")
                    initError.value = e.message
                }
                .collect { result ->
                    // Stop processing when session is completed
                    if (sessionCompleted.value) return@collect
                    
                    currentSkeleton.value      = result.skeleton
                    frameStatus.value          = result.frameStatus
                    lastDeviations.value       = result.frameStatus.deviations
                    targetState.value          = useCase!!.engine.targetState
                    guidancePreviewState.value = useCase!!.engine.guidancePreviewState

                    // ── Track Rep History & Diagnostic Logs ───────────────────
                    updateRepHistory(result.frameStatus)
                    persistSessionIfComplete(result.frameStatus)
                    
                    val repsDone = result.frameStatus.repsCompleted
                    
                    // Force show results when target reps reached
                    if (!sessionCompleted.value && targetReps.value > 0 && repsDone >= targetReps.value) {
                        Log.d("RepTracker", "Setting sessionCompleted = true, repsDone=$repsDone, target=${targetReps.value}")
                        sessionCompleted.value = true
                    }
                    
                    if (repsDone != lastRepsCompleted && !sessionCompleted.value) {
                        Log.d("RepTracker", "Rep Incremented: $repsDone (last was $lastRepsCompleted)")
                        
                        val cappedReps = repsDone.coerceAtMost(targetReps.value)
                        currentRepCount.value = cappedReps
                        lastRepsCompleted = repsDone
                        
                        if (cappedReps >= targetReps.value) {
                            sessionCompleted.value = true
                            Log.d("RepTracker", "Target reps reached: ${targetReps.value}")
                        }
                    }

                    // ── Performance: per-frame log gated behind DEBUG_LOG ──────
                    // Original: buildLayerLog() ran unconditionally every frame.
                    // It iterated all features with padEnd + format per key,
                    // then called Log.d — allocations + I/O at 30fps.
                    // Now: entire buildLayerLog call is skipped in production.
                    // Flip RunPipelineUseCase.DEBUG_LOG to re-enable.
                    if (RunPipelineUseCase.DEBUG_LOG) {
                        Log.d(TAG, buildLayerLog(result))
                    }
                }
        }
    }

    // Lazily initialise detector on-demand to avoid blocking app startup.
    private fun ensureDetectorInitialized() {
        try {
            if (!detector.isInitialized()) {
                detector.initialize(confidenceThreshold = 0.5f)
                Log.d(TAG, "PoseDetector initialised (lazy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PoseDetector init failed: ${e.message}")
            initError.value = e.message
        }
    }

    fun skipCurrentPose() {
        skipUsedInCurrentRep = true
        useCase?.engine?.skipCurrentState()
    }

    fun toggleCamera() {
        isFrontCamera.value = !isFrontCamera.value
    }

    fun toggleMute() {
        isMuted.value = !isMuted.value
    }

    fun setZoom(value: Float) {
        val coerced = value.coerceIn(0f, 1f)
        zoomLevel.value = coerced
        detector.setLinearZoom(coerced)
    }

    private var lastRepsCompleted = 0
    private var currentRepFrameCount = 0
    private var currentRepCorrectCount = 0
    private var skipUsedInCurrentRep = false

    private fun updateRepHistory(status: FrameStatus) {
        // Handle session reset/restart (if repsCompleted resets to 0)
        if (status.repsCompleted < lastRepsCompleted) {
            lastRepsCompleted = status.repsCompleted
            completedRepAccuracies.value = emptyList()
            currentRepFrameCount = 0
            currentRepCorrectCount = 0
            skipUsedInCurrentRep = false
        }

        // If it's a new rep, save the previous rep's accuracy (if any)
        if (status.repsCompleted > lastRepsCompleted) {
            val accuracy = when {
                skipUsedInCurrentRep -> 0.0
                currentRepFrameCount > 0 -> {
                    (currentRepCorrectCount.toDouble() / currentRepFrameCount).coerceIn(0.0, 1.0)
                }
                else -> 0.0
            }
            
            Log.d("RepTracker", "Storing Accuracy for Rep $lastRepsCompleted: ${"%.2f".format(accuracy * 100)}%")
            completedRepAccuracies.value = completedRepAccuracies.value + accuracy
            
            // Reset for next rep
            currentRepFrameCount = 0
            currentRepCorrectCount = 0
            skipUsedInCurrentRep = false
            lastRepsCompleted = status.repsCompleted
        }

        // Accumulate accuracy for THE CURRENT ACTIVE REP
        // Skip Guidance phase as it doesn't count towards accuracy
        if (!status.sessionComplete && status.phase == com.core.poseengine.monitor.MonitorPhase.HOLDING) {
            currentRepFrameCount++
            if (status.isCorrect) {
                currentRepCorrectCount++
            }
        }
    }

    private fun persistSessionIfComplete(status: FrameStatus) {
        val target = targetReps.value
        val currentReps = status.repsCompleted
        val isTargetReached = currentReps >= target
        
        // Don't save if already saved OR if target not yet reached
        if (completionPersisted || !isTargetReached) return

        // Get userId directly from repository to ensure we have the latest
        var userId: String? = null
        viewModelScope.launch {
            authRepository.observeSession().first()?.let { authUser ->
                userId = authUser.id
            }
            
            // Fall back to default user if no auth session available
            if (userId.isNullOrBlank()) {
                userId = DEFAULT_USER_ID
                Log.w(TAG, "Using default user ID since no authenticated session available")
            }
            
            Log.d(TAG, "persistSessionIfComplete: userId=$userId, currentReps=$currentReps, target=$target")
            
            saveSession(userId!!, currentReps, target, status)
        }
    }

    private suspend fun saveSession(userId: String, currentReps: Int, target: Int, status: FrameStatus) {
        val activeUseCase = useCase ?: run {
            Log.w(TAG, "persistSessionIfComplete: useCase is null")
            return
        }
        
        val now = System.currentTimeMillis()
        val durationSec = ((now - sessionStartEpochMs) / 1000L).toInt().coerceAtLeast(0)
        val totalReps = currentReps.coerceAtMost(target) // Cap at target
        
        // Use completedRepAccuracies from ViewModel (tracked per-rep during workout)
        val accuracies = completedRepAccuracies.value
        val accuracyPercent = if (accuracies.isNotEmpty()) {
            (accuracies.average() * 100.0).coerceIn(0.0, 100.0)
        } else {
            // Fall back to engine summary if no per-rep tracking
            val summary = activeUseCase.engine.getSessionSummary()
            (summary.overallAccuracy * 100.0).coerceIn(0.0, 100.0)
        }
        
        val sessionId = UUID.randomUUID().toString()
        val graphEntry = currentGraphEntry
        val exerciseName = graphEntry?.fileName?.removeSuffix(".json") ?: "pose-session"

        Log.d(TAG, "Saving session: id=$sessionId, userId=$userId, exercise=$exerciseName, reps=$totalReps, accuracy=$accuracyPercent")

        // Calculate default calories (will be recalculated properly in coroutine)
        val defaultCalories = totalReps * 2.5
        
        completionPersisted = true

        try {
            // Get exercise data for calorie calculation
            val exercise = exerciseRepository.getByName(exerciseName)
            val caloriesPerRep = exercise?.caloriesPerRep ?: 2.5
            val totalCalories = totalReps * caloriesPerRep
            
            Log.d(TAG, "Calories: totalReps=$totalReps, caloriesPerRep=$caloriesPerRep, totalCalories=$totalCalories")

            val session = WorkoutSession(
                id = sessionId,
                userId = userId,
                exerciseName = exerciseName,
                targetReps = target,
                totalReps = totalReps,
                accuracyPercent = accuracyPercent,
                totalCalories = totalCalories,
                startedAtEpochMs = sessionStartEpochMs,
                endedAtEpochMs = now,
                durationSec = durationSec,
                status = SessionStatus.COMPLETED,
            )
            
            val result = saveWorkoutSessionUseCase(session)
            result.onSuccess {
                Log.d(TAG, "Session saved successfully to local DB")
            }.onFailure { err ->
                Log.e(TAG, "Failed to persist workout session", err)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saving session", e)
        }

        // Use default calories in case of exception
        saveSessionMetricUseCase(
            SessionMetric(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                userId = userId,
                metricName = "total_calories",
                metricValue = defaultCalories,
            )
        ).onFailure {
            Log.e(TAG, "Failed to persist calories metric", it)
        }

        val statDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val existingDayStat = observeDailyStatsUseCase(userId, limit = 365)
            .first()
            .firstOrNull { it.statDate == statDate }

        val previousSessions = existingDayStat?.sessionsCount ?: 0
        val newSessionsCount = previousSessions + 1
        val newTotalReps = (existingDayStat?.totalReps ?: 0) + totalReps
        val newAverageAccuracy = if (previousSessions == 0) {
            accuracyPercent
        } else {
            ((existingDayStat?.avgAccuracy ?: 0.0) * previousSessions + accuracyPercent) / newSessionsCount
        }

        val dailyStatId = existingDayStat?.id
            ?: UUID.nameUUIDFromBytes("$userId:$statDate".toByteArray(StandardCharsets.UTF_8)).toString()

        upsertDailyStatUseCase(
            DailyStat(
                id = dailyStatId,
                userId = userId,
                statDate = statDate,
                sessionsCount = newSessionsCount,
                totalReps = newTotalReps,
                avgAccuracy = newAverageAccuracy,
            )
        ).onFailure {
            Log.e(TAG, "Failed to upsert daily stats", it)
        }

        val metadata = """{"session_id":"$sessionId","total_reps":$totalReps,"accuracy_percent":${"%.2f".format(Locale.US, accuracyPercent)}}"""
        savePoseGraphUseCase(
            PoseGraphRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                graphName = exerciseName,
                storagePath = graphEntry?.filePath ?: "assets:${graphEntry?.fileName ?: "unknown"}",
                metadataJson = metadata,
            )
        ).onFailure {
            Log.e(TAG, "Failed to persist pose graph metadata", it)
        }

        Log.i(
            TAG,
            "Persisted completed session id=$sessionId reps=$totalReps accuracy=${"%.2f".format(Locale.US, accuracyPercent)} durationSec=$durationSec"
        )
    }

    // ── Graph loading ─────────────────────────────────────────────────────────

    private fun loadGraph(context: Context, entry: GraphEntry): PoseGraph {
        return if (entry.isAsset) {
            val json = context.assets.open(entry.fileName).bufferedReader().readText()
            PoseGraph.fromJson(json)
        } else {
            val json = File(entry.filePath).readText(Charsets.UTF_8)
            PoseGraph.fromJson(json)
        }
    }

    // ── Layer logging (only called when DEBUG_LOG = true) ─────────────────────

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

    fun saveCurrentSessionPartial() {
        val userId = currentUserId ?: return
        val graphEntry = currentGraphEntry ?: return
        val now = System.currentTimeMillis()
        val durationSec = ((now - sessionStartEpochMs) / 1000L).toInt().coerceAtLeast(0)
        val totalReps = currentRepCount.value
        val accuracies = completedRepAccuracies.value
        val avgAccuracy = if (accuracies.isNotEmpty()) accuracies.average() * 100 else 0.0
        val target = targetReps.value
        val exerciseName = graphEntry.fileName?.removeSuffix(".json") ?: "pose-session"
        
        val calories = if (totalReps > 0) (totalReps * 2.5 * (avgAccuracy / 100.0)).toInt() else 0

        viewModelScope.launch {
            val sessionId = UUID.randomUUID().toString()
            saveWorkoutSessionUseCase(
                WorkoutSession(
                    id = sessionId,
                    userId = userId,
                    exerciseName = exerciseName,
                    targetReps = target,
                    totalReps = totalReps,
                    accuracyPercent = avgAccuracy,
                    totalCalories = calories.toDouble(),
                    startedAtEpochMs = sessionStartEpochMs,
                    endedAtEpochMs = now,
                    durationSec = durationSec,
                    status = SessionStatus.COMPLETED,
                )
            ).onFailure {
                Log.e(TAG, "Failed to save partial session", it)
            }
            Log.d(TAG, "Partial session saved: $totalReps reps, $calories calories")
        }
    }

    companion object {
        private const val TAG = "DebugViewModel"
        private const val DEFAULT_USER_ID = "default-local-user"
    }
}
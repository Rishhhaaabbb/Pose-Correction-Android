// app/src/main/java/com/posemonitor2/MainActivity.kt

package com.posemonitor2

import android.Manifest
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.posemonitor2.settings.AppLocaleManager
import com.posemonitor2.onboarding.OnboardingActivity
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import dagger.hilt.android.AndroidEntryPoint

data class ExerciseResult(
    val name: String,
    val repsDone: Int,
    val targetReps: Int,
    val accuracy: Int,
    val calories: Int
) : java.io.Serializable

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DebugViewModel by viewModels()
    private lateinit var voiceManager: VoiceManager
    
    companion object {
        // Temporary storage for session results during workout transitions
        private val tempSessionResults = mutableListOf<ExerciseResult>()
        
        fun setSessionResults(results: List<ExerciseResult>) {
            tempSessionResults.clear()
            tempSessionResults.addAll(results)
        }
        
        fun getAndClearSessionResults(): List<ExerciseResult> {
            val results = tempSessionResults.toList()
            tempSessionResults.clear()
            return results
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Permission denied — handled in UI
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    // Handle new intents when activity is already running (singleTop or similar)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainActivity", "onNewIntent: received new intent with extras: ${intent.extras?.keySet()}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Install a default uncaught exception handler that writes a crash dump
        // to the app's files directory. This helps debugging crashes on devices
        // where adb/logcat isn't immediately available.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "crash_log.txt")
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("--- Crash captured: ${System.currentTimeMillis()} ---")
                throwable.printStackTrace(pw)
                pw.println()
                crashFile.appendText(sw.toString())
            } catch (e: Exception) {
                // Best-effort only
            }
            // Delegate to previous handler (may kill process / show system dialog)
            previousHandler?.uncaughtException(thread, throwable)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        voiceManager = VoiceManager(this)

        setContent {
            PoseMonitorAppTheme {
                MainNavigation(
                    viewModel = viewModel,
                    activity = this,
                    voiceManager = voiceManager
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "onConfigurationChanged: orientation=${newConfig.orientation}")
        
        // Prevent activity restart on rotation
        // The screen orientation will be locked per exercise in the monitoring screen
    }

    override fun onDestroy() {
        voiceManager.shutdown()
        super.onDestroy()
    }

    /**
     * Toggles Immersive Mode (hiding status/navigation bars).
     * USES BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE so bars auto-hide after a few seconds
     * if revealed by a swipe.
     */
    fun setImmersiveMode(enabled: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

// ── Navigation state ──────────────────────────────────────────────────────────

private enum class Screen { GRAPH_SELECT, MONITOR }

@Composable
private fun MainNavigation(
    viewModel: DebugViewModel,
    activity: MainActivity,
    voiceManager: VoiceManager
) {
    val context = LocalContext.current

    // Read initial intent synchronously to avoid briefly showing the selector UI
    val initialIntent = activity.intent
    val initialFileName = initialIntent.getStringExtra("EXTRA_FILE_NAME")
    val initialIsAsset = initialIntent.getBooleanExtra("EXTRA_IS_ASSET", false)
    val initialTargetReps = initialIntent.getIntExtra("EXTRA_TARGET_REPS", 10)
    val initialHoldDuration = initialIntent.getDoubleExtra("EXTRA_HOLD_DURATION_SECONDS", -1.0)
    val initialLockOrientation = initialIntent.getBooleanExtra("EXTRA_LOCK_ORIENTATION", false)
    val initialworkoutId = initialIntent.getStringExtra("EXTRA_WORKOUT_ID")
    val initialExerciseIndex = initialIntent.getIntExtra("EXTRA_CURRENT_INDEX", 0)
    val initialExerciseList = initialIntent.getStringExtra("EXTRA_EXERCISE_LIST")

    var selectedGraph by remember { mutableStateOf<GraphEntry?>(
        initialFileName?.let { fn ->
            GraphEntry(fileName = fn, filePath = if (initialIsAsset) "assets:$fn" else "", isAsset = initialIsAsset)
        }
    ) }

    var screen by remember { mutableStateOf(if (initialFileName != null) Screen.MONITOR else Screen.GRAPH_SELECT) }
    var shouldLockOrientation by remember { mutableStateOf(initialLockOrientation) }
    
    // Sequence state
    var workoutId by remember { mutableStateOf(initialworkoutId) }
    var exerciseIndex by remember { mutableIntStateOf(initialExerciseIndex) }
    var exerciseList by remember { mutableStateOf(initialExerciseList?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }

    // If we started with an intent that requested auto-start, apply those settings once
    LaunchedEffect(initialFileName) {
        if (initialFileName != null) {
            Log.d(
                "MainActivity",
                "Initial intent auto-start: fileName=$initialFileName, targetReps=$initialTargetReps, holdDuration=$initialHoldDuration"
            )
            viewModel.setTargetReps(initialTargetReps)
            viewModel.setSessionHoldDuration(
                if (initialHoldDuration > 0.0) initialHoldDuration else null
            )
            // Read and consume sequence extras
            workoutId = initialIntent.getStringExtra("EXTRA_WORKOUT_ID")
            exerciseIndex = initialIntent.getIntExtra("EXTRA_CURRENT_INDEX", 0)
            exerciseList = initialIntent.getStringExtra("EXTRA_EXERCISE_LIST")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            
            initialIntent.removeExtra("EXTRA_FILE_NAME")
            initialIntent.removeExtra("EXTRA_IS_ASSET")
            initialIntent.removeExtra("EXTRA_TARGET_REPS")
            initialIntent.removeExtra("EXTRA_HOLD_DURATION_SECONDS")
            initialIntent.removeExtra("EXTRA_WORKOUT_ID")
            initialIntent.removeExtra("EXTRA_CURRENT_INDEX")
            initialIntent.removeExtra("EXTRA_EXERCISE_LIST")
            
            if (initialIntent.hasExtra("EXTRA_LOCK_ORIENTATION")) {
                shouldLockOrientation = initialIntent.getBooleanExtra("EXTRA_LOCK_ORIENTATION", false)
                initialIntent.removeExtra("EXTRA_LOCK_ORIENTATION")
            }
        }
    }

    // Also handle late in-flight new intents (e.g., when activity already running)
    LaunchedEffect(activity.intent) {
        val intent = activity.intent
        val fileName = intent.getStringExtra("EXTRA_FILE_NAME")
        val isAsset  = intent.getBooleanExtra("EXTRA_IS_ASSET", false)
        val targetReps = intent.getIntExtra("EXTRA_TARGET_REPS", 10)
        val holdDurationSeconds = intent.getDoubleExtra("EXTRA_HOLD_DURATION_SECONDS", -1.0)

        Log.d(
            "MainActivity",
            "LaunchedEffect: checking for auto-start. fileName=$fileName, targetReps=$targetReps, holdDurationSeconds=$holdDurationSeconds"
        )

        if (fileName != null) {
            workoutId = intent.getStringExtra("EXTRA_WORKOUT_ID")
            exerciseIndex = intent.getIntExtra("EXTRA_CURRENT_INDEX", 0)
            exerciseList = intent.getStringExtra("EXTRA_EXERCISE_LIST")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

            intent.removeExtra("EXTRA_FILE_NAME")
            intent.removeExtra("EXTRA_IS_ASSET")
            intent.removeExtra("EXTRA_TARGET_REPS")
            intent.removeExtra("EXTRA_HOLD_DURATION_SECONDS")
            intent.removeExtra("EXTRA_WORKOUT_ID")
            intent.removeExtra("EXTRA_CURRENT_INDEX")
            intent.removeExtra("EXTRA_EXERCISE_LIST")
            
            if (intent.hasExtra("EXTRA_LOCK_ORIENTATION")) {
                shouldLockOrientation = intent.getBooleanExtra("EXTRA_LOCK_ORIENTATION", false)
                intent.removeExtra("EXTRA_LOCK_ORIENTATION")
            }

            selectedGraph = GraphEntry(
                fileName = fileName,
                filePath = if (isAsset) "assets:$fileName" else "",
                isAsset  = isAsset
            )
            viewModel.setTargetReps(targetReps)
            viewModel.setSessionHoldDuration(
                if (holdDurationSeconds > 0.0) holdDurationSeconds else null
            )
            screen = Screen.MONITOR
            Log.d(
                "MainActivity",
                "Auto-starting monitoring for $fileName with $targetReps reps (hold=${if (holdDurationSeconds > 0.0) holdDurationSeconds else "default"})"
            )
        }
    }
    
    // Toggle immersive mode when switching between selection and monitoring
    LaunchedEffect(screen) {
        activity.setImmersiveMode(screen == Screen.MONITOR)
    }
    
    // Lock orientation when starting monitoring
    LaunchedEffect(screen, selectedGraph) {
        val graph = selectedGraph
        if (screen == Screen.MONITOR && graph != null) {
            if (shouldLockOrientation) activity.lockOrientationForExercise(graph.fileName)
        } else if (screen == Screen.GRAPH_SELECT) {
            activity.unlockOrientation()
        }
    }

    when (screen) {
        Screen.GRAPH_SELECT -> {
            val context = LocalContext.current
            
                GraphSelectorScreen(
                context = context,
                onGraphSelected = { entry ->
                    selectedGraph = entry
                    viewModel.setSessionHoldDuration(null)
                    // Lock orientation synchronously before switching to monitor
                    // to avoid CameraX picking an unintended rotation during bind.
                    // Don't auto-lock orientation here; let the caller (exercise detail) request locking via intent.
                    screen = Screen.MONITOR
                },
            )
        }

        Screen.MONITOR -> {
            val graph = selectedGraph
            if (graph != null) {
                val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
                val sessionCompleted by viewModel.sessionCompleted.collectAsStateWithLifecycle()
                val currentReps by viewModel.currentRepCount.collectAsStateWithLifecycle()
                val targetReps by viewModel.targetReps.collectAsStateWithLifecycle()
                val accuracies by viewModel.completedRepAccuracies.collectAsStateWithLifecycle()
                val frameStatus by viewModel.frameStatus.collectAsStateWithLifecycle()
                
                LaunchedEffect(graph, isFrontCamera) {
                    viewModel.resetSessionState()
                    try {
                        viewModel.startPipeline(activity, graph)
                    } catch (e: Exception) {
                        // Surface any start errors to the UI without crashing the process.
                        Log.e("MainActivity", "startPipeline failed: ${e.message}", e)
                        viewModel.initError.value = e.message
                    }
                }
                val sessionResults = remember { 
                    mutableStateListOf<ExerciseResult>().apply {
                        addAll(MainActivity.getAndClearSessionResults())
                    }
                }
                var showSummary by remember { mutableStateOf(false) }

                MonitorScreen(
                    viewModel = viewModel,
                    voiceManager = voiceManager,
                    currentReps = currentReps,
                    targetReps = targetReps,
                    sessionCompleted = sessionCompleted,
                    accuracies = accuracies,
                    workoutId = workoutId,
                    exerciseIndex = exerciseIndex,
                    exerciseList = exerciseList,
                    onQuit = { 
                        viewModel.saveCurrentSessionPartial()
                        viewModel.stopPipeline()
                        val intent = Intent(context, UserFlowActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    },
                    onFinish = { 
                        viewModel.stopPipeline()
                        val intent = Intent(context, UserFlowActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    },
                    onNext = { nextIdx, currentResult ->
                        sessionResults.add(currentResult)
                        viewModel.stopPipeline()
                        val nextExercise = exerciseList[nextIdx]
                        
                        // Parse target reps map to get target reps for next exercise
                        val targetRepsMapParam = activity.intent.getStringExtra("EXTRA_TARGET_REPS_MAP")
                        val nextTargetReps = if (targetRepsMapParam != null) {
                            // Parse "exercise1:reps1,exercise2:reps2,..." format
                            val repsMap = targetRepsMapParam.split(",").mapNotNull { pair ->
                                val parts = pair.split(":")
                                if (parts.size == 2) {
                                    val exerciseName = parts[0]
                                    val reps = parts[1].toIntOrNull()
                                    if (reps != null) exerciseName to reps else null
                                } else null
                            }.toMap()
                            repsMap[nextExercise] ?: 10
                        } else {
                            10
                        }
                        
                        // Save session results to companion object for next activity
                        MainActivity.setSessionResults(sessionResults)
                        
                        // Directly start MainActivity with next exercise to avoid home screen flash
                        val newIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("EXTRA_FILE_NAME", "$nextExercise.json")
                            putExtra("EXTRA_IS_ASSET", true)
                            putExtra("EXTRA_TARGET_REPS", nextTargetReps)
                            putExtra("EXTRA_LOCK_ORIENTATION", true)
                            putExtra("EXTRA_WORKOUT_ID", workoutId)
                            putExtra("EXTRA_CURRENT_INDEX", nextIdx)
                            putExtra("EXTRA_EXERCISE_LIST", exerciseList.joinToString(","))
                            putExtra("EXTRA_TARGET_REPS_MAP", targetRepsMapParam)
                        }
                        context.startActivity(newIntent)
                        (context as? Activity)?.finish()
                    },
                    onShowSummary = { finalResult ->
                        sessionResults.add(finalResult)
                        showSummary = true
                    }
                )

                if (showSummary) {
                    BackHandler {
                        viewModel.stopPipeline()
                        val intent = Intent(context, UserFlowActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }
                    
                    CustomWorkoutSummaryOverlay(
                        results = sessionResults,
                        onFinish = {
                            viewModel.stopPipeline()
                            val intent = Intent(context, UserFlowActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                            (context as? Activity)?.finish()
                        }
                    )
                }
            }
        }
    }
}

// ── Monitor screen ────────────────────────────────────────────────────────────

@Composable
fun MonitorScreen(
    viewModel: DebugViewModel,
    voiceManager: VoiceManager,
    currentReps: Int = 0,
    targetReps: Int = 10,
    sessionCompleted: Boolean = false,
    accuracies: List<Double> = emptyList(),
    workoutId: String? = null,
    exerciseIndex: Int = 0,
    exerciseList: List<String> = emptyList(),
    onQuit: () -> Unit = {},
    onFinish: () -> Unit = {},
    onNext: (Int, ExerciseResult) -> Unit = { _, _ -> },
    onShowSummary: (ExerciseResult) -> Unit = {},
) {
    val context = LocalContext.current
    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
    }
    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }
    val skeleton        by viewModel.currentSkeleton.collectAsStateWithLifecycle()
    val frameStatus     by viewModel.frameStatus.collectAsStateWithLifecycle()
    val targetState     by viewModel.targetState.collectAsStateWithLifecycle()
    val guidancePreview by viewModel.guidancePreviewState.collectAsStateWithLifecycle()
    val deviations      by viewModel.lastDeviations.collectAsStateWithLifecycle()
    val repHistory      by viewModel.completedRepAccuracies.collectAsStateWithLifecycle()
    val initError       by viewModel.initError.collectAsStateWithLifecycle()
    val rawFrameSize    by viewModel.cameraFrameSize.collectAsStateWithLifecycle()
    val isFrontCamera   by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val isMuted         by viewModel.isMuted.collectAsStateWithLifecycle()
    val zoomLevel       by viewModel.zoomLevel.collectAsStateWithLifecycle()
    val zoomState       by viewModel.zoomState.collectAsStateWithLifecycle()
    val currentZoomRatio by viewModel.currentZoomRatio.collectAsStateWithLifecycle()
    val minZoomRatio    by viewModel.minZoomRatio.collectAsStateWithLifecycle()
    val maxZoomRatio    by viewModel.maxZoomRatio.collectAsStateWithLifecycle()
    val sessionHoldDurationSeconds by viewModel.sessionHoldDurationSeconds.collectAsStateWithLifecycle()
    val frameRotationDegrees by viewModel.frameRotationDegrees.collectAsStateWithLifecycle()
    val currentReps     by viewModel.currentRepCount.collectAsStateWithLifecycle()
    val targetReps      by viewModel.targetReps.collectAsStateWithLifecycle()
    val sessionCompleted by viewModel.sessionCompleted.collectAsStateWithLifecycle()
    val completedRepAccuracies by viewModel.completedRepAccuracies.collectAsStateWithLifecycle()

    var lastBeepedReps by remember { mutableIntStateOf(0) }
    var lastObservedStateId by remember { mutableIntStateOf(-1) }
    var lastObservedStateReps by remember { mutableIntStateOf(0) }

    // ── Auto-flow countdown state ──────────────────────────────────────────
    var countdownSeconds by remember { mutableIntStateOf(5) }
    val hasNext = exerciseIndex + 1 < exerciseList.size
    var resultAdded by remember { mutableStateOf(false) }
    
    LaunchedEffect(sessionCompleted) {
        if (sessionCompleted && !resultAdded) {
            val repsDone = if (currentReps > 0) currentReps else targetReps
            val calories = (repsDone * 2.5).toInt()
            val avgAccuracy = if (accuracies.isNotEmpty()) {
                (accuracies.sum() / accuracies.size * 100).toInt()
            } else 0
            
            val result = ExerciseResult(
                name = viewModel.getCurrentExerciseName() ?: "Exercise",
                repsDone = repsDone,
                targetReps = targetReps,
                accuracy = avgAccuracy,
                calories = calories
            )

            if (hasNext) {
                while (countdownSeconds > 0) {
                    kotlinx.coroutines.delay(1000)
                    countdownSeconds--
                }
                onNext(exerciseIndex + 1, result)
            } else if (exerciseList.size > 1) {
                // If it's a workout (more than 1 exercise), show summary after 2s
                kotlinx.coroutines.delay(2000)
                onShowSummary(result)
            }
            resultAdded = true
        }
    }

    // ── Sync UI Slider with Hardware (Pinch Support) ──────────────────────
    LaunchedEffect(zoomState) {
        viewModel.applyDefaultZoomIfNeeded(zoomState)
        viewModel.applyPendingZoomIfNeeded(zoomState)
    }

    // Convert android.util.Size → compose Size for the overlay crop rect math.
    // Defaults to Size.Zero until the first frame arrives — overlay falls back
    // to direct mapping in that case.
    val cameraFrameSize = rawFrameSize?.let {
        Size(it.width.toFloat(), it.height.toFloat())
    } ?: Size.Zero

    // ── Voice Guidance Logic ──────────────────────────────────────────────
    
    // Determine the active instruction based on the current phase (Guidance vs Holding)
    val isGuidancePhase = frameStatus?.phase == com.core.poseengine.monitor.MonitorPhase.GUIDANCE
    val activeInstruction = if (isGuidancePhase && guidancePreview != null) {
        guidancePreview?.instruction
    } else {
        targetState?.instruction
    }

    // 1. Instructions: Speak when the instruction changes OR periodically if they are in frame
    var lastSpokenInstruction by remember { mutableStateOf<String?>(null) }
    var lastInstructionTime by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(activeInstruction, skeleton != null, isMuted) {
        while (true) {
            val now = System.currentTimeMillis()
            val isVisible = skeleton != null
            if (!isMuted && isVisible && activeInstruction != null) {
                // Speak if it's a new instruction (use AI for variety) OR if it's been 15s (use direct for sharpness)
                if (activeInstruction != lastSpokenInstruction) {
                    voiceManager.speak(activeInstruction!!, isInstruction = true, useGroq = true)
                    lastSpokenInstruction = activeInstruction
                    lastInstructionTime = now
                } else if (now - lastInstructionTime > 15000L) {
                    voiceManager.speak(activeInstruction!!, isInstruction = true, useGroq = false)
                    lastInstructionTime = now
                }
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    LaunchedEffect(currentReps) {
        if (currentReps > lastBeepedReps) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            lastBeepedReps = currentReps
        }
    }

    // 1b. Hold completion cue: beep when pose state advances without rep increment.
    LaunchedEffect(frameStatus?.stateId, currentReps) {
        val stateId = frameStatus?.stateId ?: return@LaunchedEffect

        if (lastObservedStateId == -1) {
            lastObservedStateId = stateId
            lastObservedStateReps = currentReps
            return@LaunchedEffect
        }

        if (stateId != lastObservedStateId) {
            if (currentReps == lastObservedStateReps) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 90)
            }
            lastObservedStateId = stateId
            lastObservedStateReps = currentReps
        }
    }

    // 2. Feedback: Speak corrections or confirmation
    var lastCorrectState by remember { mutableStateOf(false) }
    var lastGoodTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(frameStatus?.feedback, skeleton != null, isMuted, frameStatus?.isCorrect) {
        while (true) {
            val feedback = frameStatus?.feedback
            val isCorrect = frameStatus?.isCorrect ?: false
            val isVisible = skeleton != null
            val now = System.currentTimeMillis()
            
            if (isMuted || frameStatus?.phase == com.core.poseengine.monitor.MonitorPhase.SESSION_COMPLETE) {
                kotlinx.coroutines.delay(1000)
                continue
            }

            // Special case: Urgent warnings should be spoken even if skeleton is null
            val isUrgentFeedback = feedback?.contains("one person", ignoreCase = true) == true || 
                                 feedback?.contains("visible", ignoreCase = true) == true ||
                                 feedback?.contains("to begin", ignoreCase = true) == true
            if (!isVisible && !isUrgentFeedback) {
                kotlinx.coroutines.delay(1000)
                continue
            }

            if (isCorrect) {
                // User requested to remove "Good, hold it!"
                // We now only provide periodic encouragement while holding correctly
                if (lastCorrectState && (now - lastGoodTime > 10000L)) {
                    voiceManager.speak("Excellent, keep going!", isInstruction = false)
                    lastGoodTime = now
                } else if (!lastCorrectState) {
                    // User just entered correct state.
                    lastCorrectState = true
                    lastGoodTime = now
                }
            } else {
                // Speak corrections (VoiceManager handles its own internal 1.5s/4s debouncing)
                if (!feedback.isNullOrEmpty() && !feedback.startsWith("Good") && !feedback.startsWith("Get ready")) {
                    voiceManager.speak(feedback!!, isInstruction = false, useGroq = true)
                }
                lastCorrectState = false
            }
            
            // Re-check every 2 seconds. VoiceManager's internal timers (samePhraseCooldownMs) 
            // handle the actual speech repetition frequency.
            kotlinx.coroutines.delay(2000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ────────────────────────────────────────────────────
        // FIT_CENTER: the full camera frame is scaled to fit the screen with
        // letterboxing (black bars) rather than cropping.
        //
        // WHY THIS MATTERS FOR SKELETON ALIGNMENT:
        // MediaPipe returns keypoints in [0,1] relative to the camera frame.
        // The overlay Canvas maps those coords to screen pixels assuming the
        // camera frame fills the entire screen. When PreviewView uses FILL_CENTER
        // (the default), it crops the camera frame to fill the screen — so the
        // rendered image and the keypoint coordinate space are different rectangles.
        // This causes the skeleton to appear shifted/scaled relative to the body.
        //
        // FIT_CENTER makes them the same rectangle: the camera frame is scaled
        // uniformly to fit inside the screen, and the overlay Canvas covers the
        // same area. Keypoint [0,1] → screen pixel mapping is now 1:1 correct.
        key(isFrontCamera) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        // FILL_CENTER: camera fills screen edge-to-edge, no black bars.
                        // The overlay compensates for the crop using cameraFrameSize
                        // to remap keypoints into the visible camera rect precisely.
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

                        // Trigger a re-bind when the factory runs (on flip)
                        viewModel.detector.setSurfaceProvider(previewView.surfaceProvider)

                        // ── Pinch-to-Zoom Gesture ─────────────────────────────
                        val listener = object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                                val currentZoom = viewModel.zoomLevel.value
                                val delta = detector.scaleFactor - 1f
                                viewModel.setZoom(currentZoom + delta)
                                return true
                            }
                        }
                        val scaleDetector = android.view.ScaleGestureDetector(ctx, listener)
                        previewView.setOnTouchListener { _, event ->
                            scaleDetector.onTouchEvent(event)
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Monitoring overlay ────────────────────────────────────────────────
        MonitorOverlay(
            frameStatus = frameStatus,
            skeleton = skeleton,
            targetState = targetState,
            guidancePreviewState = guidancePreview,
            deviations = deviations,
            repHistory = repHistory,
            cameraFrameSize = cameraFrameSize,
            frameRotationDegrees = frameRotationDegrees,
            isFrontCamera = isFrontCamera, // Pass camera state to overlay for mirroring
            onToggleCamera = { viewModel.toggleCamera() }, // Pass toggle action
            isMuted = isMuted,
            onToggleMute = { viewModel.toggleMute() },
            zoomLevel = zoomLevel,
            onZoomChange = { viewModel.setZoom(it) },
            currentZoomRatio = currentZoomRatio,
            minZoomRatio = minZoomRatio,
            maxZoomRatio = maxZoomRatio,
            onZoomRatioChange = { viewModel.setZoomRatio(it) },
            targetReps = targetReps,
            targetHoldDurationSeconds = sessionHoldDurationSeconds,
            exerciseName = viewModel.getCurrentExerciseName(),
            onOpenHelp = {
                context.startActivity(
                    OnboardingActivity.createIntent(context, OnboardingActivity.MODE_FULL_TOUR)
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Top-left quit button ──────────────────────────────────────────────
        TextButton(
            onClick = onQuit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xCCFFFFFF)),
        ) {
            Text("← Quit", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

/*
        // ── Skip button — bottom right ────────────────────────────────────────
        Button(
            onClick = { viewModel.skipCurrentPose() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xCC424242),
                contentColor = Color(0xFF90CAF9),
            ),
        ) {
            Text("SKIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
*/

        // ── Init error banner ─────────────────────────────────────────────────
        // Keep an explicit skip control visible during live tracking so the user
        // can advance past a pose state that isn't being detected reliably.
        if (!sessionCompleted) {
            Button(
                onClick = { viewModel.skipCurrentPose() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xCC1F1F1F),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.monitor_skip),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        initError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color(0xEEAA0000), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text("⚠️ $error", color = Color(0xFFFF8A80), fontSize = 13.sp)
            }
        }

        // ── Results Overlay ──────────────────────────────────────────────────
        if (sessionCompleted) {
            // Use viewModel to get exercise name
            val exerciseName = viewModel.getCurrentExerciseName()?.removeSuffix(".json")?.replace("-", " ")?.uppercase() ?: "Exercise"
            
            // Use targetReps since currentReps may be 0 at this point
            val repsDone = if (currentReps > 0) currentReps else targetReps
            
            // Calculate calories based on exercise (using default 2.5 cal/rep)
            val calories = (repsDone * 2.5).toInt()
            
            val avgAccuracy = if (accuracies.isNotEmpty()) {
                (accuracies.sum() / accuracies.size * 100).toInt()
            } else 0

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier.widthIn(max = 500.dp).wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (exerciseList.size > 1) "Workout Accomplished!" else "Exercise Completed!",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0D3B34)
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            exerciseName,
                            fontSize = 16.sp,
                            color = Color(0xFF888888)
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ResultStatItem("Reps", "$repsDone/$targetReps")
                            ResultStatItem("Accuracy", "$avgAccuracy%")
                            ResultStatItem("Calories", "$calories")
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        if (hasNext) {
                            val nextExercise = exerciseList[exerciseIndex + 1].replace("-", " ").uppercase()
                            Text(
                                text = "Next up: $nextExercise",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE8622E)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Starting in $countdownSeconds seconds...",
                                fontSize = 12.sp,
                                color = Color(0xFF888888)
                            )
                            Spacer(Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onFinish,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text("End Workout", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { 
                                        val repsDone = if (currentReps > 0) currentReps else targetReps
                                        val calories = (repsDone * 2.5).toInt()
                                        val avgAccuracy = if (accuracies.isNotEmpty()) {
                                            (accuracies.sum() / accuracies.size * 100).toInt()
                                        } else 0
                                        val result = ExerciseResult(
                                            name = viewModel.getCurrentExerciseName() ?: "Exercise",
                                            repsDone = repsDone,
                                            targetReps = targetReps,
                                            accuracy = avgAccuracy,
                                            calories = calories
                                        )
                                        onNext(exerciseIndex + 1, result) 
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E))
                                ) {
                                    Text("Next", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (exerciseList.size > 1) {
                                        val repsDone = if (currentReps > 0) currentReps else targetReps
                                        val calories = (repsDone * 2.5).toInt()
                                        val avgAccuracy = if (accuracies.isNotEmpty()) {
                                            (accuracies.sum() / accuracies.size * 100).toInt()
                                        } else 0
                                        val result = ExerciseResult(
                                            name = viewModel.getCurrentExerciseName() ?: "Exercise",
                                            repsDone = repsDone,
                                            targetReps = targetReps,
                                            accuracy = avgAccuracy,
                                            calories = calories
                                        )
                                        onShowSummary(result)
                                    } else {
                                        onFinish()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E))
                            ) {
                                Text(if (exerciseList.size > 1) "See Summary" else "Finish Exercise", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun ResultStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D3B34))
        Text(label, fontSize = 14.sp, color = Color(0xFF888888))
    }
}

@Composable
fun CustomWorkoutSummaryOverlay(
    results: List<ExerciseResult>,
    onFinish: () -> Unit
) {
    val totalCalories = results.sumOf { it.calories }
    val totalReps = results.sumOf { it.repsDone }
    val avgAccuracy = if (results.isNotEmpty()) results.sumOf { it.accuracy } / results.size else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE000000))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            modifier = Modifier.widthIn(max = 500.dp).fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Close button in top right
                IconButton(
                    onClick = onFinish,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Close Summary",
                        tint = Color.Gray
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                item {
                    Text(
                        "Workout Accomplished!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0D3B34)
                    )
                    
                    Text(
                        "Great job! You've finished all exercises.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryStatCircle("Cals", "$totalCalories", Color(0xFFFF9800))
                        SummaryStatCircle("Acc", "$avgAccuracy%", Color(0xFF4CAF50))
                        SummaryStatCircle("Reps", "$totalReps", Color(0xFF2196F3))
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        "Workout Breakdown",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                }

                items(results) { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                result.name.removeSuffix(".json").replace("-", " ").uppercase(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${result.repsDone}/${result.targetReps} reps • ${result.accuracy}% accuracy",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Text("✅", fontSize = 18.sp)
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E))
                    ) {
                        Text("Finish Workout", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            }
        }
    }

}

@Composable
private fun SummaryStatCircle(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

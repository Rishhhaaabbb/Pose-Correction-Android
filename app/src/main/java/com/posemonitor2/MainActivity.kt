// app/src/main/java/com/posemonitor2/MainActivity.kt

package com.posemonitor2

import android.Manifest
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.posemonitor2.settings.ProfileSettingsPreferences
import com.posemonitor2.onboarding.OnboardingActivity
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import dagger.hilt.android.AndroidEntryPoint

data class ExerciseResult(
    val name: String,
    val repsDone: Int,
    val targetReps: Int,
    val accuracy: Int,
    val calories: Int,
    val skippedReps: Int = 0
) : java.io.Serializable

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DebugViewModel by viewModels()
    private lateinit var voiceManager: VoiceManager

    companion object {
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
        viewModel.cameraPermissionGranted.value = granted
        if (!granted) {
            // If the system won't show the rationale anymore, the user hit "Don't ask again"
            val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            viewModel.cameraPermissionPermanentlyDenied.value = permanentlyDenied
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
            viewModel.cameraPermissionEverRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            // Permission was already granted (e.g. on subsequent launches)
            viewModel.cameraPermissionGranted.value = true
        }

        voiceManager = VoiceManager(this)

        setContent {
            PoseMonitorAppTheme {
                MainNavigation(
                    viewModel = viewModel,
                    activity = this,
                    voiceManager = voiceManager,
                    onRequestCameraPermission = { requestCameraPermissionOrOpenSettings() }
                )
            }
        }
    }

    /**
     * Request camera permission intelligently:
     * - If the system will still show a rationale dialog → launch the permission request.
     * - If the user chose "Don't ask again" (rationale no longer shown after first denial)
     *   → open app Settings so the user can manually enable it.
     *
     * On the very first ask, shouldShowRequestPermissionRationale returns false too,
     * so we track whether we've asked before via the ViewModel flag to distinguish
     * "never asked" from "permanently denied".
     */
    fun requestCameraPermissionOrOpenSettings() {
        when {
            // Already granted — nothing to do
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> {
                viewModel.cameraPermissionGranted.value = true
            }
            // System says we can still show the rationale/dialog, OR we haven't asked yet
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ||
                    !viewModel.cameraPermissionEverRequested -> {
                viewModel.cameraPermissionEverRequested = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            // Permanently denied — send user to app Settings
            else -> {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check in case the user granted camera permission from Android Settings
        // and returned to the app. The permissionLauncher callback won't fire in that
        // path, so we sync the ViewModel flag here.
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted && !viewModel.cameraPermissionGranted.value) {
            viewModel.cameraPermissionGranted.value = true
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
    voiceManager: VoiceManager,
    onRequestCameraPermission: () -> Unit,
) {
    val context = LocalContext.current

    // Observe the user's auto-rotate preference from DataStore.
    // Defaults to true so first-run behaviour is identical to before.
    val autoRotateScreen by ProfileSettingsPreferences
        .observeAutoRotateScreen(context)
        .collectAsStateWithLifecycle(initialValue = true)

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
    val initialTargetRepsMap = initialIntent.getStringExtra("EXTRA_TARGET_REPS_MAP")

    var selectedGraph by remember { mutableStateOf<GraphEntry?>(
        initialFileName?.let { fn ->
            val filePath = if (initialIsAsset) {
                "assets:$fn"
            } else {
                // Custom graph - construct full path
                File(context.filesDir, "graphs/$fn").absolutePath
            }
            GraphEntry(fileName = fn, filePath = filePath, isAsset = initialIsAsset)
        }
    ) }

    var screen by remember { mutableStateOf(if (initialFileName != null) Screen.MONITOR else Screen.GRAPH_SELECT) }
    var shouldLockOrientation by remember { mutableStateOf(initialLockOrientation) }
    
    // Sequence state
    var workoutId by remember { mutableStateOf(initialworkoutId) }
    var exerciseIndex by remember { mutableIntStateOf(initialExerciseIndex) }
    var exerciseList by remember { mutableStateOf(initialExerciseList?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }
    var targetRepsMapStr by remember { mutableStateOf(initialTargetRepsMap) }

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
            targetRepsMapStr = initialIntent.getStringExtra("EXTRA_TARGET_REPS_MAP")
            
            initialIntent.removeExtra("EXTRA_FILE_NAME")
            initialIntent.removeExtra("EXTRA_IS_ASSET")
            initialIntent.removeExtra("EXTRA_TARGET_REPS")
            initialIntent.removeExtra("EXTRA_HOLD_DURATION_SECONDS")
            initialIntent.removeExtra("EXTRA_WORKOUT_ID")
            initialIntent.removeExtra("EXTRA_CURRENT_INDEX")
            initialIntent.removeExtra("EXTRA_EXERCISE_LIST")
            initialIntent.removeExtra("EXTRA_TARGET_REPS_MAP")
            
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
            targetRepsMapStr = intent.getStringExtra("EXTRA_TARGET_REPS_MAP")

            intent.removeExtra("EXTRA_FILE_NAME")
            intent.removeExtra("EXTRA_IS_ASSET")
            intent.removeExtra("EXTRA_TARGET_REPS")
            intent.removeExtra("EXTRA_HOLD_DURATION_SECONDS")
            intent.removeExtra("EXTRA_WORKOUT_ID")
            intent.removeExtra("EXTRA_CURRENT_INDEX")
            intent.removeExtra("EXTRA_EXERCISE_LIST")
            intent.removeExtra("EXTRA_TARGET_REPS_MAP")
            
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
    
    // Lock orientation when starting monitoring, driven by the user's auto-rotate
    // preference. Also re-runs when the preference changes mid-session so toggling
    // it in Profile Settings takes effect immediately.
    LaunchedEffect(screen, selectedGraph, autoRotateScreen) {
        val graph = selectedGraph
        if (screen == Screen.MONITOR && graph != null) {
            if (autoRotateScreen) {
                activity.lockOrientationForExercise(graph.fileName)
            } else {
                // User disabled auto-rotate — release any lock so the device
                // responds to physical rotation freely.
                activity.unlockOrientation()
            }
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
                val skippedReps by viewModel.skippedRepCount.collectAsStateWithLifecycle()
                val frameStatus by viewModel.frameStatus.collectAsStateWithLifecycle()
                val cameraPermissionGranted by viewModel.cameraPermissionGranted.collectAsStateWithLifecycle()

                LaunchedEffect(graph, isFrontCamera, cameraPermissionGranted) {
                    if (!cameraPermissionGranted) {
                        // Permission not yet granted — show nothing, wait for the
                        // launcher callback to flip the flag and re-trigger this effect.
                        viewModel.initError.value = null
                        return@LaunchedEffect
                    }
                    viewModel.resetSessionState()
                    viewModel.initError.value = null
                    try {
                        viewModel.startPipeline(activity, graph)
                    } catch (e: Exception) {
                        // Surface any start errors to the UI without crashing the process.
                        Log.e("MainActivity", "startPipeline failed: ${e.message}", e)
                        viewModel.initError.value = e.message
                    }
                }
                val sessionResults = remember { mutableStateListOf<ExerciseResult>() }
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
                    onRequestCameraPermission = onRequestCameraPermission,
                    onQuit = { 
                        viewModel.saveCurrentSessionPartial()
                        viewModel.stopPipeline()
                        (context as? Activity)?.finish()
                    },
                    onFinish = { 
                        viewModel.stopPipeline()
                        (context as? Activity)?.finish()
                    },
                    onNext = { nextIdx, currentResult ->
                        sessionResults.add(currentResult)
                        viewModel.stopPipeline()
                        val nextExercise = exerciseList[nextIdx]
                        // Parse targetRepsMap to get reps for the next exercise
                        val nextTargetReps = targetRepsMapStr?.let { mapStr ->
                            mapStr.split(",").mapNotNull { entry ->
                                val parts = entry.split(":")
                                if (parts.size == 2) parts[0].trim() to (parts[1].trim().toIntOrNull() ?: 10) else null
                            }?.toMap()?.get(nextExercise)
                        } ?: targetReps

                        // Auto-advance: stay in this activity and load next exercise directly
                        exerciseIndex = nextIdx
                        val nextGraphEntry = GraphEntry(
                            fileName = "$nextExercise.json",
                            filePath = "assets:$nextExercise.json",
                            isAsset = true
                        )
                        viewModel.setTargetReps(nextTargetReps ?: 10)
                        selectedGraph = nextGraphEntry
                    },
                    onShowSummary = { finalResult ->
                        if (!showSummary) {
                            sessionResults.add(finalResult)
                            showSummary = true
                        }
                    }
                )

                if (showSummary) {
                    BackHandler {
                        viewModel.stopPipeline()
                        (context as? Activity)?.finish()
                    }
                    
                    CustomWorkoutSummaryOverlay(
                        results = sessionResults,
                        onFinish = {
                            viewModel.stopPipeline()
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
    onRequestCameraPermission: () -> Unit = {},
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

    // Keep the screen on while monitoring — the user can't tap the screen during exercise.
    // FLAG_KEEP_SCREEN_ON needs no extra manifest permission and is automatically cleared
    // when this composable leaves the composition (session ends / user quits).
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(Unit) {
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val skeleton        by viewModel.currentSkeleton.collectAsStateWithLifecycle()
    val frameStatus     by viewModel.frameStatus.collectAsStateWithLifecycle()
    val targetState     by viewModel.targetState.collectAsStateWithLifecycle()
    val guidancePreview by viewModel.guidancePreviewState.collectAsStateWithLifecycle()
    val deviations      by viewModel.lastDeviations.collectAsStateWithLifecycle()
    val repHistory      by viewModel.completedRepAccuracies.collectAsStateWithLifecycle()
    val skippedReps     by viewModel.skippedRepCount.collectAsStateWithLifecycle()
    val liveAccuracy    by viewModel.liveRepAccuracy.collectAsStateWithLifecycle()
    val liveFps         by viewModel.liveFps.collectAsStateWithLifecycle()
    val liveFeatureCount by viewModel.liveFeatureCount.collectAsStateWithLifecycle()
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

    // ── First-up countdown: shown before the first exercise in a multi-exercise workout ──
    var showFirstUpCountdown by remember { mutableStateOf(
        exerciseList.size > 1 && exerciseIndex == 0
    ) }
    var firstUpCountdownSeconds by remember { mutableIntStateOf(5) }

    // Timer for first-up countdown
    LaunchedEffect(showFirstUpCountdown) {
        if (showFirstUpCountdown) {
            firstUpCountdownSeconds = 5
            while (firstUpCountdownSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                firstUpCountdownSeconds--
            }
            showFirstUpCountdown = false
        }
    }

    // Reset countdown and result flag when exercise changes (auto-advance)
    LaunchedEffect(exerciseIndex) {
        countdownSeconds = 5
        resultAdded = false
    }

    // ── End-session confirmation ───────────────────────────────────────────
    var showEndSessionDialog by remember { mutableStateOf(false) }
    // Tracks which reason the user selected — null means step 1 (reason picker) is shown
    var quitReason by remember { mutableStateOf<String?>(null) }

    // Intercept back press only while the session is still in progress.
    // Once completed the summary is showing and back should not interrupt.
    BackHandler(enabled = !sessionCompleted) {
        quitReason = null
        showEndSessionDialog = true
    }

    if (showEndSessionDialog) {
        // Helper colours
        val reasonBg    = Color(0xFF1E1E1E)
        val reasonBorder = Color(0xFF444444)
        val accentOrange = Color(0xFFE8622E)

        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showEndSessionDialog = false
                quitReason = null
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF121212),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 420.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (quitReason == null) {
                        // ── Step 1: Reason picker ─────────────────────────────
                        Text(
                            text = "Why give up?",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            text = "Tell us what's stopping you. We might be able to help.",
                            color = Color(0xFF999999),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )

                        Spacer(Modifier.height(4.dp))

                        val reasons = listOf(
                            "🫧" to "Just taking a tour",
                            "😿" to "Exercise is too hard",
                            "⁉️" to "Don't know how to do it",
                        )
                        reasons.forEach {(emoji, label) ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = reasonBg,
                                border = BorderStroke(1.dp, reasonBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { quitReason = label },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        TextButton(
                            onClick = {
                                showEndSessionDialog = false
                                quitReason = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "← Keep going",
                                color = accentOrange,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }

                    } else {
                        // ── Step 2: Response + actions ────────────────────────
                        val (supportEmoji, supportMsg) = when (quitReason) {
                            "Just taking a tour" ->
                                "🙌" to "No worries! You can always come back and do it for real. Every rep counts."
                            "Too hard right now" ->
                                "💡" to "That's totally fine — try a lighter variation or take a short break. You've already started, which is the hardest part."
                            "Don't know how to do it" ->
                                "📖" to "Tap the tutorial video icon in settings during the exercise — it walks you through every move step by step."
                            else ->
                                "👋" to "No problem. Come back when you're ready!"
                        }

                        Text(supportEmoji, fontSize = 40.sp)

                        Text(
                            text = quitReason!!,
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        Text(
                            text = supportMsg,
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp,
                        )

                        Spacer(Modifier.height(4.dp))

                        // Resume button (primary)
                        Button(
                            onClick = {
                                showEndSessionDialog = false
                                quitReason = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentOrange),
                        ) {
                            Text(
                                "Resume Workout",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }

                        // Quit button (secondary)
                        OutlinedButton(
                            onClick = {
                                showEndSessionDialog = false
                                quitReason = null
                                onQuit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCC3333)),
                            border = BorderStroke(1.dp, Color(0xFFCC3333)),
                        ) {
                            Text(
                                "Quit Session",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }

                        // Back to reasons
                        TextButton(onClick = { quitReason = null }) {
                            Text(
                                "← Back",
                                color = Color(0xFF777777),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
    
    LaunchedEffect(sessionCompleted) {
        if (sessionCompleted && !resultAdded) {
            val repsDone = if (currentReps > 0) currentReps else targetReps
            val effectiveReps = (repsDone - skippedReps).coerceAtLeast(0)
            val calories = (effectiveReps * 2.5).toInt()
            val avgAccuracy = if (accuracies.isNotEmpty()) {
                (accuracies.sum() / accuracies.size * 100).toInt()
            } else 0
            
            val result = ExerciseResult(
                name = viewModel.getCurrentExerciseName() ?: "Exercise",
                repsDone = repsDone,
                targetReps = targetReps,
                accuracy = avgAccuracy,
                calories = calories,
                skippedReps = skippedReps
            )

            if (hasNext) {
                while (countdownSeconds > 0) {
                    kotlinx.coroutines.delay(1000)
                    countdownSeconds--
                }
                if (!resultAdded) {
                    resultAdded = true
                    onNext(exerciseIndex + 1, result)
                }
            } else if (exerciseList.size > 1) {
                kotlinx.coroutines.delay(2000)
                if (!resultAdded) {
                    resultAdded = true
                    onShowSummary(result)
                }
            }
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
    
    // ── Voice Guidance ────────────────────────────────────────────────────────
    //
    // Three independent channels — each fires from its own LaunchedEffect:
    //
    //  1. PRESENCE   — skeleton absent or multiple people detected
    //  2. INSTRUCTION — new pose state, routed through Groq with exercise context
    //  3. CORRECTION  — joint corrections during holding, direct Android TTS
    //
    // LaunchedEffect keys are chosen so each effect only restarts when its own
    // inputs actually change — not on every frame tick.

    val exerciseName = remember(viewModel) {
        viewModel.getCurrentExerciseName()?.removeSuffix(".json")?.replace("-", " ") ?: ""
    }

    val isGuidancePhase = frameStatus?.phase == com.core.poseengine.monitor.MonitorPhase.GUIDANCE
    val activeInstruction = if (isGuidancePhase && guidancePreview != null) {
        guidancePreview?.instruction
    } else {
        targetState?.instruction
    }

    // ── 1. Presence channel ───────────────────────────────────────────────────
    // Keys: skeleton visibility + feedback text.
    // Fires "step into frame" when skeleton disappears, "one person only" on
    // multi-person. Does NOT restart on every feedback string change.
    val isSkeletonVisible = skeleton != null
    val presenceFeedback  = frameStatus?.feedback ?: ""

    LaunchedEffect(isSkeletonVisible, isMuted) {
        while (true) {
            if (!isMuted) {
                if (!isSkeletonVisible) {
                    val msg = if (presenceFeedback.contains("one person", ignoreCase = true))
                        "Please make sure only one person is in the frame"
                    else
                        "Step into the camera frame"
                    voiceManager.speakPresence(msg)
                }
            }
            kotlinx.coroutines.delay(3_500)
        }
    }

    // Also handle multi-person warning immediately when feedback text contains it
    LaunchedEffect(presenceFeedback, isMuted) {
        if (!isMuted && presenceFeedback.contains("one person", ignoreCase = true)) {
            voiceManager.speakPresence("Please make sure only one person is in the frame")
        }
    }

    // ── 2. Instruction channel ────────────────────────────────────────────────
    // Keys: activeInstruction only — restarts only when the pose state changes,
    // NOT on every frame. Groq is called once per state transition.
    LaunchedEffect(activeInstruction, isMuted) {
        if (isMuted || activeInstruction.isNullOrBlank()) return@LaunchedEffect
        // Wait briefly for skeleton to appear before speaking
        kotlinx.coroutines.delay(400)
        voiceManager.speakInstruction(activeInstruction, exerciseName)
    }

    // ── Beep on rep completion ────────────────────────────────────────────────
    LaunchedEffect(currentReps) {
        if (currentReps > lastBeepedReps) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            lastBeepedReps = currentReps
        }
    }

    // ── Beep on state advance (no rep increment) ──────────────────────────────
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

    // ── 3. Correction channel ─────────────────────────────────────────────────
    // Keys: isMuted + sessionCompleted only. The while(true) loop reads
    // frameStatus at tick time so it is NOT restarted on every feedback change.
    // VoiceManager.speakCorrection() handles cooldowns internally.
    var lastCorrectState by remember { mutableStateOf(false) }
    var lastGoodTime     by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isMuted, sessionCompleted) {
        while (true) {
            kotlinx.coroutines.delay(2_500)

            if (isMuted || sessionCompleted) continue
            if (frameStatus?.phase == com.core.poseengine.monitor.MonitorPhase.SESSION_COMPLETE) continue
            // Don't speak corrections during guidance or when skeleton not visible
            if (frameStatus?.phase == com.core.poseengine.monitor.MonitorPhase.GUIDANCE) continue
            if (!isSkeletonVisible) continue

            val feedback  = frameStatus?.feedback ?: continue
            val isCorrect = frameStatus?.isCorrect ?: false
            val now       = System.currentTimeMillis()

            if (isCorrect) {
                if (lastCorrectState && now - lastGoodTime > 10_000L) {
                    voiceManager.speakCorrection("Excellent, keep going!")
                    lastGoodTime = now
                } else if (!lastCorrectState) {
                    lastCorrectState = true
                    lastGoodTime = now
                }
            } else {
                lastCorrectState = false
                // Skip non-actionable strings — VoiceManager also filters these,
                // but filtering here avoids the function call overhead at 30fps.
                if (feedback.isBlank() ||
                    feedback.startsWith("Get ready") ||
                    feedback.startsWith("Hold steady") ||
                    feedback.startsWith("Focus on") ||
                    feedback.startsWith("Step into") ||
                    feedback.startsWith("Please make sure")
                ) continue
                voiceManager.speakCorrection(feedback)
            }
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
            liveAccuracy = liveAccuracy,
            liveFps = liveFps,
            liveFeatureCount = liveFeatureCount,
            cameraFrameSize = cameraFrameSize,
            frameRotationDegrees = frameRotationDegrees,
            isFrontCamera = isFrontCamera,
            onToggleCamera = { viewModel.toggleCamera() },
            isMuted = isMuted,
            onToggleMute = { viewModel.toggleMute() },
            voiceManager = voiceManager,
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
        // Shows the same "End session?" confirmation dialog as the back press,
        // so the user can't accidentally exit mid-workout with a stray tap.
        // Fade out when session is completed so it doesn't conflict with the summary.
        AnimatedVisibility(
            visible = !sessionCompleted,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp),
        ) {
            TextButton(
                onClick = { showEndSessionDialog = true },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xCCFFFFFF)),
            ) {
                Text("← Quit", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
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

        // ── Skip button — bottom right ────────────────────────────────────────
        // Keep a skip control visible during live tracking so the user
        // can advance past a pose state that isn't being detected reliably.
        AnimatedVisibility(
            visible = !sessionCompleted,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            Button(
                onClick = { viewModel.skipCurrentPose() },
                modifier = Modifier.height(44.dp),
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

        val cameraPermissionGranted by viewModel.cameraPermissionGranted.collectAsStateWithLifecycle()
        val cameraPermissionPermanentlyDenied by viewModel.cameraPermissionPermanentlyDenied.collectAsStateWithLifecycle()
        if (!cameraPermissionGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1C1C1C),
                    modifier = Modifier
                        .padding(32.dp)
                        .widthIn(max = 400.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "📷",
                            fontSize = 48.sp,
                        )
                        Text(
                            text = "Camera Access Needed",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            text = if (cameraPermissionPermanentlyDenied)
                                "Camera permission was denied. Please enable it in your device Settings to continue."
                            else
                                "This exercise requires the camera to track your pose in real time. Please grant camera access to continue.",
                            color = Color(0xFFAAAAAA),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Button(
                            onClick = onRequestCameraPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8622E),
                            ),
                        ) {
                            Text(
                                text = if (cameraPermissionPermanentlyDenied)
                                    "Open Settings"
                                else
                                    "Grant Camera Permission",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                        TextButton(onClick = onQuit) {
                            Text(
                                text = "Cancel",
                                color = Color(0xFF888888),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        // ── First-up countdown overlay ─────────────────────────────────────
        // Shown before the first exercise in a multi-exercise custom workout.
        // Camera/model loads during the countdown.
        AnimatedVisibility(
            visible = showFirstUpCountdown,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(500)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Exercise name
                    val firstUpName = if (exerciseList.isNotEmpty()) {
                        exerciseList[0].replace("-", " ").uppercase()
                    } else ""

                    Text(
                        text = "FIRST UP",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF888888),
                        letterSpacing = 3.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = firstUpName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))

                    // Countdown circle
                    val infiniteTransition = rememberInfiniteTransition(label = "first_up_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pulse",
                    )

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .background(Color(0xFFE8622E), RoundedCornerShape(50.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$firstUpCountdownSeconds",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Get ready!",
                        fontSize = 14.sp,
                        color = Color(0xFFAAAAAA),
                    )
                }
            }
        }

        // ── Results Overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = sessionCompleted,
            enter = fadeIn(tween(300)) + scaleIn(
                animationSpec = tween(300),
                initialScale = 0.9f
            ),
        ) {
            val exerciseName = viewModel.getCurrentExerciseName()?.removeSuffix(".json")?.replace("-", " ")?.uppercase() ?: "Exercise"
            val repsDone = if (currentReps > 0) currentReps else targetReps
            val effectiveReps = (repsDone - skippedReps).coerceAtLeast(0)
            val calories = (effectiveReps * 2.5).toInt()
            val avgAccuracy = if (accuracies.isNotEmpty()) {
                (accuracies.sum() / accuracies.size * 100).toInt()
            } else 0

            val surfaceColor = MaterialTheme.colorScheme.surface
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = surfaceColor,
                    modifier = Modifier.widthIn(max = 440.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (hasNext) "Exercise Complete!" else "All Done!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = onSurfaceColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(exerciseName, fontSize = 13.sp, color = mutedColor)

                        Spacer(Modifier.height(20.dp))

                        // Stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ResultStatItem("Reps", "$repsDone/$targetReps", onSurfaceColor, mutedColor)
                            ResultStatItem("Acc", "$avgAccuracy%", onSurfaceColor, mutedColor)
                            ResultStatItem("Cal", "$calories", onSurfaceColor, mutedColor)
                            if (skippedReps > 0) {
                                ResultStatItem("Skip", "$skippedReps", onSurfaceColor, mutedColor)
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        if (hasNext) {
                            val nextExercise = exerciseList[exerciseIndex + 1].replace("-", " ").uppercase()
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("UP NEXT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(nextExercise, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            val infiniteTransition = rememberInfiniteTransition(label = "countdown_pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f, targetValue = 1.08f,
                                animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                label = "pulse_scale"
                            )
                            Text(
                                text = "Starting in $countdownSeconds...",
                                fontSize = 13.sp, color = mutedColor,
                                modifier = Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                            )
                            Spacer(Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) {
                                    Text("End Workout", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = {
                                        if (resultAdded) return@Button
                                        val result = ExerciseResult(exerciseName, repsDone, targetReps, avgAccuracy, calories, skippedReps)
                                        resultAdded = true
                                        onNext(exerciseIndex + 1, result)
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E))
                                ) {
                                    Text("Next", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                            }
                        } else if (exerciseList.size <= 1) {
                            Spacer(Modifier.height(4.dp))
                            Text("Save this session to your history?", fontSize = 13.sp, color = mutedColor)
                            Spacer(Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.discardLastSession(); onFinish() },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCC3333)),
                                    border = BorderStroke(1.dp, Color(0xFFCC3333)),
                                ) { Text("Discard", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                Button(
                                    onClick = onFinish,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
                                ) { Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White) }
                            }
                        } else {
                            Button(
                                onClick = {
                                    val result = ExerciseResult(exerciseName, repsDone, targetReps, avgAccuracy, calories, skippedReps)
                                    onShowSummary(result)
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
                            ) { Text("See Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStatItem(label: String, value: String, valueColor: Color = Color(0xFF0D3B34), labelColor: Color = Color(0xFF888888)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 12.sp, color = labelColor)
    }
}

@Composable
fun CustomWorkoutSummaryOverlay(
    results: List<ExerciseResult>,
    onFinish: () -> Unit
) {
    val totalCalories = results.sumOf { it.calories }
    val totalReps = results.sumOf { it.repsDone }
    val totalSkipped = results.sumOf { it.skippedReps }
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
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 500.dp).fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        "Great job! You've finished all exercises.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        if (totalSkipped > 0) {
                            SummaryStatCircle("Skipped", "$totalSkipped", Color(0xFF9E9E9E))
                        }
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
                            val skippedText = if (result.skippedReps > 0) " • ${result.skippedReps} skipped" else ""
                            Text(
                                "${result.repsDone}/${result.targetReps} reps • ${result.accuracy}% accuracy$skippedText",
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
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(surfaceVariant, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

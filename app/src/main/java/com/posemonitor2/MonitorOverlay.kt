package com.posemonitor2

import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.core.poseengine.model.FeatureKey
import com.core.poseengine.model.PoseState
import com.core.poseengine.model.Skeleton2d
import com.core.poseengine.model.SkeletonConnections
import com.core.poseengine.monitor.FrameStatus
import com.core.poseengine.monitor.MonitorPhase
import com.posemonitor2.component.ZoomableVideoPlayer
import com.presentation.graphviz.SkeletonNormalizer
import com.presentation.graphviz.drawAnatomicalFigure
import kotlin.math.abs
import kotlin.math.min
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Colors ────────────────────────────────────────────────────────────────────

private val COLOR_CORRECT      = Color(0xFF32CD32)
private val COLOR_WRONG        = Color(0xFF0064FF)
private val COLOR_GRACE        = Color(0xFF00FFFF)
private val COLOR_BONE_OK      = Color(0xFF4CAF50)
private val COLOR_JOINT_WHITE  = Color.White
private val COLOR_ERROR_RED    = Color(0xFFFF0000)
private val COLOR_ERROR_YELLOW = Color(0xFFFFFF00)
private val COLOR_ERROR_GREEN  = Color(0xFF00FF00)
private val COLOR_PANEL_TEXT   = Color(0xFF282828)

// ── Joint → FeatureKey mapping ────────────────────────────────────────────────

private val JOINT_FEATURE_MAP: Map<Int, FeatureKey> = mapOf(
    7  to FeatureKey.LEFT_ELBOW_ANGLE,
    8  to FeatureKey.RIGHT_ELBOW_ANGLE,
    13 to FeatureKey.LEFT_KNEE_ANGLE,
    14 to FeatureKey.RIGHT_KNEE_ANGLE,
    11 to FeatureKey.LEFT_HIP_ANGLE,
    12 to FeatureKey.RIGHT_HIP_ANGLE,
    5  to FeatureKey.LEFT_SHOULDER_ANGLE,
    6  to FeatureKey.RIGHT_SHOULDER_ANGLE,
)

// ── Main overlay ──────────────────────────────────────────────────────────────

enum class TutorialDisplayMode {
    OFF,
    COMPACT,
    WIDESPREAD
}

// ── Orientation handling ──────────────────────────────────────────────────────

/**
 * Lock screen orientation based on exercise requirements.
 * Call this when starting monitoring for an exercise.
 */
fun Context.lockOrientationForExercise(exerciseName: String) {
    try {
        val orientation = OrientationManager.getRequiredOrientation(exerciseName, this)
        val screenOrientation = OrientationManager.getScreenOrientationConstant(orientation)
        (this as? android.app.Activity)?.requestedOrientation = screenOrientation
        Log.d("OrientationManager", "Locked orientation for $exerciseName: $orientation")
    } catch (e: Exception) {
        Log.e("OrientationManager", "Failed to lock orientation", e)
    }
}

/**
 * Unlock orientation and allow auto-rotation.
 */
fun Context.unlockOrientation() {
    // Disable sensor-driven automatic rotation so the camera stays fixed
    // unless the user explicitly requests a rotation via the UI.
    (this as? android.app.Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
    Log.d("OrientationManager", "Unlocked orientation (nosensor)")
}

@Composable
fun MonitorOverlay(
    frameStatus: FrameStatus?,
    skeleton: Skeleton2d?,
    targetState: PoseState?,
    guidancePreviewState: PoseState?,
    deviations: Map<FeatureKey, Double>,
    repHistory: List<Double> = emptyList(),
    cameraFrameSize: Size = Size.Zero,
    frameRotationDegrees: Int = 0,
    isFrontCamera: Boolean = true,
    onToggleCamera: () -> Unit = {},
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    zoomLevel: Float = 0f,
    onZoomChange: (Float) -> Unit = {},
    currentZoomRatio: Float = 1.0f,
    minZoomRatio: Float = 1.0f,
    maxZoomRatio: Float = 5.0f,
    onZoomRatioChange: (Float) -> Unit = {},
    targetReps: Int = 10,
    targetHoldDurationSeconds: Double? = null,
    exerciseName: String? = null,
    onOpenHelp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (frameStatus == null) return

    val isGuidance   = frameStatus.phase == MonitorPhase.GUIDANCE
    val displayState = if (isGuidance) guidancePreviewState else targetState
    var panelExpanded by rememberSaveable { mutableStateOf(false) }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    var zoomExpanded by rememberSaveable { mutableStateOf(false) }
    var tutorialMode by rememberSaveable { mutableStateOf(TutorialDisplayMode.OFF) }
    var showTargetPanel by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }

    var debugEnabled by rememberSaveable { mutableStateOf(false) }
    val debugTapTimes = remember { mutableStateListOf<Long>() }
    val debugEvents = remember { mutableStateListOf<String>() }
    var lastLoggedStateId by remember { mutableStateOf(frameStatus.stateId) }
    var lastLoggedPhase by remember { mutableStateOf(frameStatus.phase) }

    val holdTarget = targetHoldDurationSeconds ?: targetState?.minHoldDuration
    val holdElapsed = holdTarget?.times(frameStatus.holdProgress.coerceIn(0.0, 1.0))
    val minDisplayZoomRatio = 0.5f
    var requestedZoomRatio by rememberSaveable { mutableFloatStateOf(currentZoomRatio.coerceIn(minDisplayZoomRatio, maxZoomRatio)) }
    var isZoomDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentZoomRatio, minZoomRatio, maxZoomRatio, minDisplayZoomRatio, isZoomDragging) {
        if (isZoomDragging) return@LaunchedEffect
        val coercedCurrent = currentZoomRatio.coerceIn(minDisplayZoomRatio, maxZoomRatio)
        if (requestedZoomRatio >= minZoomRatio || kotlin.math.abs(requestedZoomRatio - coercedCurrent) < 0.01f) {
            requestedZoomRatio = coercedCurrent
        }
    }

    fun appendDebugEvent(message: String) {
        val stamped = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}  $message"
        debugEvents.add(0, stamped)
        if (debugEvents.size > 50) debugEvents.removeLast()
        if (debugEnabled) {
            appendTransitionDebugLog(context, stamped)
        }
    }

    LaunchedEffect(frameStatus.stateId, frameStatus.phase, frameStatus.repsCompleted, frameStatus.isCorrect) {
        if (frameStatus.stateId != lastLoggedStateId) {
            appendDebugEvent("Transition state $lastLoggedStateId -> ${frameStatus.stateId} (reps=${frameStatus.repsCompleted})")
            lastLoggedStateId = frameStatus.stateId
        }
        if (frameStatus.phase != lastLoggedPhase) {
            appendDebugEvent("Phase ${lastLoggedPhase.name} -> ${frameStatus.phase.name}")
            lastLoggedPhase = frameStatus.phase
        }
        if (!frameStatus.isCorrect && frameStatus.feedback.isNotBlank()) {
            appendDebugEvent("Correction: ${frameStatus.feedback}")
        }
    }

    val videoAssetPath = exerciseName?.removeSuffix(".json")?.let { "$it.mp4" }

    Box(modifier = modifier.fillMaxSize()) {

        // ── Video Tutorial Panel (Top Center / Center) ──────────────────────
        if (videoAssetPath != null && tutorialMode != TutorialDisplayMode.OFF) {
            val isCompact = tutorialMode == TutorialDisplayMode.COMPACT
            val panelSize = if (isCompact) {
                Modifier.size(width = 200.dp, height = 112.dp)
            } else {
                Modifier.fillMaxWidth(0.85f).aspectRatio(16f/9f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (isCompact) 110.dp else 0.dp),
                contentAlignment = if (isCompact) Alignment.TopCenter else Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter   = fadeIn() + if (isCompact) slideInVertically(initialOffsetY = { -it }) else expandIn(expandFrom = Alignment.Center),
                    exit    = fadeOut() + if (isCompact) slideOutVertically(targetOffsetY = { -it }) else shrinkOut(shrinkTowards = Alignment.Center),
                ) {
                    Surface(
                        modifier = panelSize,
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        shadowElevation = if (isCompact) 8.dp else 0.dp
                    ) {
                        ZoomableVideoPlayer(
                            videoAssetPath = videoAssetPath,
                            useController = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // ── Instruction Banner (Top Center) ───────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), contentAlignment = Alignment.TopCenter) {
            InstructionBanner(
                instruction = displayState?.instruction,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        if (isGuidance && guidancePreviewState != null) {
            GuidanceOverlay(
                previewState = guidancePreviewState, 
                feedback = frameStatus.feedback,
                isFrontCamera = isFrontCamera
            )
        } else {
            skeleton?.let {
                LiveSkeletonOverlay(
                    skeleton = it,
                    deviations = deviations,
                    targetState = targetState,
                    cameraFrameSize = cameraFrameSize,
                    frameRotationDegrees = frameRotationDegrees,
                    isCorrect = frameStatus.isCorrect,
                    inGrace = frameStatus.inGrace,
                    isFrontCamera = isFrontCamera,
                )
            }
        }

        AnimatedVisibility(
            visible = panelExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000))
                    .clickable { panelExpanded = false },
            )
        }

        if (displayState != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    // ── Settings Hub (Vertical Column) ───────────────────────
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        // ── Master Settings Toggle (Top) ──────────────────────
                        IconButton(
                            onClick = { settingsExpanded = !settingsExpanded },
                            modifier = Modifier
                                .background(
                                    if (settingsExpanded) Color(0x99000000) else Color(0x66000000),
                                    RoundedCornerShape(50)
                                )
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.cd_settings),
                                tint = if (settingsExpanded) Color(0xFFE8622E) else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // ── Sub-Controls (Expand Vertically Downwards) ───────
                        AnimatedVisibility(
                            visible = settingsExpanded,
                            enter   = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit    = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                // ── Flip Camera Toggle ────────────────────────
                                IconButton(
                                    onClick = onToggleCamera,
                                    modifier = Modifier
                                        .background(Color(0x66000000), RoundedCornerShape(50))
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FlipCameraAndroid,
                                        contentDescription = stringResource(R.string.monitor_flip_camera),
                                        tint = if (!isFrontCamera) Color(0xFFE8622E) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // ── Mute Toggle ───────────────────────────────
                                IconButton(
                                    onClick = onToggleMute,
                                    modifier = Modifier
                                        .background(Color(0x66000000), RoundedCornerShape(50))
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                        contentDescription = stringResource(R.string.monitor_toggle_mute),
                                        tint = if (isMuted) Color(0xFFE8622E) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // ── Video Tutorial Toggle ─────────────────────
                                IconButton(
                                    onClick = {
                                        tutorialMode = when(tutorialMode) {
                                            TutorialDisplayMode.OFF -> TutorialDisplayMode.COMPACT
                                            TutorialDisplayMode.COMPACT -> TutorialDisplayMode.WIDESPREAD
                                            TutorialDisplayMode.WIDESPREAD -> TutorialDisplayMode.OFF
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color(0x66000000), RoundedCornerShape(50))
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SmartDisplay,
                                        contentDescription = stringResource(R.string.monitor_toggle_tutorial_video),
                                        tint = if (tutorialMode != TutorialDisplayMode.OFF) Color(0xFFE8622E) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // ── Target Panel Toggle ───────────────────────
                                IconButton(
                                    onClick = { showTargetPanel = !showTargetPanel },
                                    modifier = Modifier
                                        .background(Color(0x66000000), RoundedCornerShape(50))
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showTargetPanel) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = stringResource(R.string.monitor_toggle_target_panel),
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // ── Nested Zoom Toggle ────────────────────────
                                IconButton(
                                    onClick = { showHelpDialog = true },
                                    modifier = Modifier
                                        .background(Color(0x66000000), RoundedCornerShape(50))
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Help,
                                        contentDescription = stringResource(R.string.home_help_title),
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { zoomExpanded = !zoomExpanded },
                                    modifier = Modifier
                                        .background(
                                            if (zoomExpanded) Color(0x99000000) else Color(0x66000000),
                                            RoundedCornerShape(50)
                                        )
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (zoomExpanded) Icons.Rounded.ZoomOut else Icons.Rounded.ZoomIn,
                                        contentDescription = stringResource(R.string.monitor_toggle_zoom_slider),
                                        tint = if (zoomExpanded) Color(0xFFE8622E) else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                // Manual rotate control: user can explicitly rotate the UI.
                                TextButton(
                                    onClick = {
                                        // Toggle between portrait and landscape on user request.
                                        val act = context as? android.app.Activity ?: return@TextButton
                                        try {
                                            val current = act.requestedOrientation
                                            val newOrient = when (current) {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                ActivityInfo.SCREEN_ORIENTATION_NOSENSOR -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            }
                                            act.requestedOrientation = newOrient
                                            appendDebugEvent("User requested rotate -> $newOrient")
                                        } catch (e: Exception) {
                                            appendDebugEvent("Rotate failed: ${e.message}")
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color(0x66000000), RoundedCornerShape(8.dp))
                                        .height(36.dp)
                                ) {
                                    Text(text = "Rotate", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // ── Target Panel ──────────────────────────────────────────
                    AnimatedVisibility(
                        visible = showTargetPanel,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ReferencePosePanel(
                            state    = displayState,
                            expanded = panelExpanded,
                            onToggle = { panelExpanded = !panelExpanded },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

/*
        StatusBar(
            frameStatus = frameStatus,
            modifier    = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(end = if (panelExpanded) 0.dp else 132.dp),
        )

        if (!panelExpanded) {
            AccuracyMeter(
                accuracy = computeDisplayAccuracy(frameStatus),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 56.dp)
                    .size(80.dp),
            )
        }

        RepCounter(
            reps     = frameStatus.repsCompleted,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )
*/
//        StreakCounter(
//            streak   = frameStatus.streak,
//            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 72.dp),
//        )

        if (frameStatus.feedback.isNotEmpty()) {
            FeedbackBar(
                feedback  = frameStatus.feedback,
                isCorrect = frameStatus.isCorrect,
                inGrace   = frameStatus.inGrace,
                modifier  = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp) // Above the rep counter / accuracy bar
                    .padding(horizontal = 24.dp),
            )
        }

// ── Custom Bar Rep Counter ────────────────────────────────────────────
        BarRepCounter(
            repsCompleted = frameStatus.repsCompleted.coerceAtMost(targetReps),
            holdProgress  = frameStatus.holdProgress.toFloat(),
            repHistory    = repHistory,
            totalReps     = targetReps,
            onRepCounterTap = {
                val now = System.currentTimeMillis()
                debugTapTimes.removeAll { now - it > 1500L }
                debugTapTimes.add(now)
                if (debugTapTimes.size >= 5) {
                    debugTapTimes.clear()
                    debugEnabled = !debugEnabled
                    appendDebugEvent(if (debugEnabled) "Debug overlay enabled" else "Debug overlay disabled")
                }
            },
            modifier      = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp),
        )

        if (debugEnabled) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 12.dp)
                    .width(320.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC111111),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Debug Transition Overlay", color = Color(0xFFFFB79E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("State: ${frameStatus.stateId}   Phase: ${frameStatus.phase.name}", color = Color.White, fontSize = 12.sp)
                    Text(
                        "Hold elapsed: ${holdElapsed?.let { "%.2f".format(it) } ?: "--"}s / ${holdTarget?.let { "%.2f".format(it) } ?: "--"}s",
                        color = Color.White,
                        fontSize = 12.sp,
                    )

                    val topDeviations = deviations.entries
                        .sortedByDescending { abs(it.value) }
                        .take(5)

                    if (topDeviations.isNotEmpty()) {
                        Text("Top deviations:", color = Color(0xFF9BD2D8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        topDeviations.forEach {
                            Text(
                                "${it.key.name}: ${"%.3f".format(it.value)}",
                                color = Color(0xFFD9E8E9),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    if (debugEvents.isNotEmpty()) {
                        Text("Recent logs:", color = Color(0xFF9BD2D8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        debugEvents.take(7).forEach {
                            Text(it, color = Color(0xFFD9E8E9), fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }
        }

        targetHoldDurationSeconds?.let { holdTarget ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xAA1C1C1C),
            ) {
                Text(
                    text = "Hold ${"%.1f".format(holdTarget)}s",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("Help & Tutorial") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                showHelpDialog = false
                                context.startActivity(
                                    com.posemonitor2.onboarding.OnboardingActivity.createIntent(
                                        context,
                                        com.posemonitor2.onboarding.OnboardingActivity.MODE_INTRO,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
                        ) {
                            Text("Replay Onboarding")
                        }
                        Button(
                            onClick = {
                                showHelpDialog = false
                                context.startActivity(
                                    com.posemonitor2.onboarding.OnboardingActivity.createIntent(
                                        context,
                                        com.posemonitor2.onboarding.OnboardingActivity.MODE_FULL_TOUR,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33000000)),
                        ) {
                            Text("Full App Tour")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }


        // ── Performance Accuracy Bar (Right Side) ──────────────────────────────
        PerformanceAccuracyBar(
            isCorrect = frameStatus.isCorrect,
            holdProgress = frameStatus.holdProgress.toFloat(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
                .width(6.dp)
                .height(200.dp)
        )

        // ── Hardware Zoom Slider (Toggled) ────────────────────────────────────
        AnimatedVisibility(
            visible = zoomExpanded,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 48.dp)
                    .width(60.dp)
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                val ratioRange = (maxZoomRatio - minDisplayZoomRatio).takeIf { it > 0f } ?: 1f
                val requestedNormalized = ((requestedZoomRatio - minDisplayZoomRatio) / ratioRange).coerceIn(0f, 1f)
                val actualNormalized = ((currentZoomRatio - minDisplayZoomRatio) / ratioRange).coerceIn(0f, 1f)
                val sliderValue = requestedZoomRatio.coerceIn(minDisplayZoomRatio, maxZoomRatio)

                // Track Area height is 200dp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    // Max Zoom Label (Fixed at Top)
                    Text(
                        text = "${"%.1f".format(maxZoomRatio)}x",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    // The Track + Flowing Indicator
                    Box(
                        modifier = Modifier
                            .height(200.dp)
                            .width(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. Vertical Track (More Contrast)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(6.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        ) {
                            // Progress Fill (from bottom up)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(requestedNormalized.coerceIn(0.01f, 1f))
                                    .align(Alignment.BottomCenter)
                                    .background(Color(0xFFE8622E), RoundedCornerShape(3.dp))
                            )
                        }

                        // 2. Floating "Flowing" Indicator
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = BiasAlignment(0f, 1f - 2f * actualNormalized.coerceIn(0f, 1f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .offset(x = (-34).dp)
                                    .background(Color(0xAA1A1A1A), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${"%.1f".format(currentZoomRatio)}x",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.width(2.dp))
                                Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
                            }
                        }

                        // 3. Invisible Slider for interactions
                        androidx.compose.material3.Slider(
                            value = sliderValue,
                            onValueChange = {
                                isZoomDragging = true
                                requestedZoomRatio = it
                                onZoomRatioChange(it)
                            },
                            onValueChangeFinished = { isZoomDragging = false },
                            valueRange = minDisplayZoomRatio..maxZoomRatio,
                            modifier = Modifier
                                .graphicsLayer { rotationZ = -90f }
                                .width(200.dp)
                                .height(60.dp),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            )
                        )
                    }

                    // Min Zoom Label (Fixed at Bottom)
                    Text(
                        text = "${"%.1f".format(minDisplayZoomRatio)}x",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}


// ── Live skeleton ─────────────────────────────────────────────────────────────

@Composable
private fun LiveSkeletonOverlay(
    skeleton: Skeleton2d,
    deviations: Map<FeatureKey, Double>,
    targetState: PoseState?,
    cameraFrameSize: Size,
    frameRotationDegrees: Int,
    isCorrect: Boolean,
    inGrace: Boolean,
    isFrontCamera: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenW = size.width
        val screenH = size.height

        // ── FILL_CENTER crop rect computation ─────────────────────────────────
        // PreviewView uses FILL_CENTER: the camera frame is scaled UP uniformly
        // until it fills the screen in both dimensions, then the excess is cropped.
        //
        // MediaPipe keypoints are in [0,1] relative to the FULL camera frame.
        // We need to remap them into the VISIBLE portion of the camera frame
        // (the crop rect) mapped to screen pixels.
        //
        // Math:
        //   scale = max(screenW / camW, screenH / camH)   — fill scale factor
        //   scaledW = camW * scale,  scaledH = camH * scale
        //   cropX = (scaledW - screenW) / 2  — pixels cropped from each side
        //   cropY = (scaledH - screenH) / 2  — pixels cropped from top/bottom
        //
        //   visible portion in [0,1] camera space:
        //     visX0 = cropX / scaledW,  visX1 = (scaledW - cropX) / scaledW
        //     visY0 = cropY / scaledH,  visY1 = (scaledH - cropY) / scaledH
        //
        //   remap keypoint (kx, ky) → screen pixel:
        //     px = (kx - visX0) / (visX1 - visX0) * screenW
        //     py = (ky - visY0) / (visY1 - visY0) * screenH
        //
        // When cameraFrameSize is unknown (Zero), fall back to direct mapping.

        val camW = cameraFrameSize.width
        val camH = cameraFrameSize.height
        val hasCamSize = camW > 0f && camH > 0f

        // Visible range in [0,1] camera coords
        val visX0: Float
        val visX1: Float
        val visY0: Float
        val visY1: Float

        if (hasCamSize) {
            val scale = maxOf(screenW / camW, screenH / camH)
            val scaledW = camW * scale
            val scaledH = camH * scale
            val cropX = (scaledW - screenW) / 2f
            val cropY = (scaledH - screenH) / 2f
            visX0 = cropX / scaledW
            visX1 = (scaledW - cropX) / scaledW
            visY0 = cropY / scaledH
            visY1 = (scaledH - cropY) / scaledH
        } else {
            // Fallback: no crop (full frame = screen)
            visX0 = 0f; visX1 = 1f; visY0 = 0f; visY1 = 1f
        }

        val visRangeX = visX1 - visX0
        val visRangeY = visY1 - visY0

        // Map a keypoint from [0,1] camera space → screen pixel, accounting for
        // FILL_CENTER crop and optional front camera X mirror.
        fun kptToScreen(kx: Float, ky: Float): Offset {
            // PoseDetector rotates frames before MediaPipe inference, so keypoints
            // are already in upright normalized space regardless of device rotation.
            // Only apply front-camera horizontal mirror and convert Y back to
            // top-origin screen space for overlay drawing.
            val mx = if (isFrontCamera) 1f - kx else kx
            val my = 1f - ky

            val px = (mx - visX0) / visRangeX * screenW
            val py = (my - visY0) / visRangeY * screenH
            return Offset(px, py)
        }

        // Draw bones using Diamond style
        for (i in 0 until SkeletonConnections.boneCount) {
            val aIdx = SkeletonConnections.boneIndices[i * 2]
            val bIdx = SkeletonConnections.boneIndices[i * 2 + 1]
            val aKpt = skeleton.keypoints[aIdx]
            val bKpt = skeleton.keypoints[bIdx]
            if (aKpt.confidence < 0.2f) continue
            if (bKpt.confidence < 0.2f) continue

            val start = kptToScreen(aKpt.x.toFloat(), aKpt.y.toFloat())
            val end   = kptToScreen(bKpt.x.toFloat(), bKpt.y.toFloat())

            // ── Dynamic Bone Color ───────────────────────────────────────────
            val skeletonColor = when {
                isCorrect -> COLOR_CORRECT
                inGrace   -> COLOR_GRACE
                else      -> Color.Red
            }

            // Draw the volumetric diamond limb
            drawDiamondBone(
                start = start,
                end = end,
                auraColor = skeletonColor.copy(alpha = 0.5f),
                coreColor = Color.White.copy(alpha = 0.8f),
                maxWidth = 16f
            )
        }

        // Draw joints as glowing spheres
        skeleton.keypoints.forEachIndexed { idx, kpt ->
            if (kpt.confidence < 0.2f) return@forEachIndexed
            val center     = kptToScreen(kpt.x.toFloat(), kpt.y.toFloat())
            val featureKey = JOINT_FEATURE_MAP[idx]
            val tolerance  = featureKey?.let { targetState?.featureTolerances?.get(it) }
            val deviation  = featureKey?.let { deviations[it] }

            val (statusColor, radius) = when {
                deviation != null && tolerance != null -> {
                    val ratio = abs(deviation) / tolerance
                    when {
                        ratio > 1.0 -> COLOR_ERROR_RED    to 12f
                        ratio > 0.7 -> COLOR_ERROR_YELLOW to 10f
                        else        -> COLOR_ERROR_GREEN  to 8f
                    }
                }
                else -> COLOR_JOINT_WHITE to 6f
            }

            // Glowing sphere effect
            // 1. Large soft aura
            drawCircle(statusColor.copy(alpha = 0.3f), radius = radius * 2.2f, center = center)
            // 2. Main color body
            drawCircle(statusColor, radius = radius, center = center)
            // 3. Bright core
            drawCircle(Color.White, radius = radius * 0.4f, center = center)
        }
    }
}

/**
 * Draws a diamond-shaped limb (bipyramid) with a central bone core.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiamondBone(
    start: Offset,
    end: Offset,
    auraColor: Color,
    coreColor: Color,
    maxWidth: Float
) {
    val vector = end - start
    val length = vector.getDistance()
    if (length < 1f) return
    
    val unitVector = vector / length
    val perpVector = Offset(-unitVector.y, unitVector.x) * maxWidth
    val center = start + (vector * 0.5f)

    // Define the diamond path
    val path = Path().apply {
        moveTo(start.x, start.y)
        lineTo(center.x + perpVector.x, center.y + perpVector.y)
        lineTo(end.x, end.y)
        lineTo(center.x - perpVector.x, center.y - perpVector.y)
        close()
    }

    // 1. Draw the volumetric aura
    drawPath(path = path, color = auraColor)
    
    // 2. Draw the bone core (thin white line)
    drawLine(
        color = coreColor,
        start = start,
        end = end,
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )
}

// ── Reference panel ───────────────────────────────────────────────────────────

@Composable
private fun ReferencePosePanel(
    state: PoseState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ── Performance: single animation float, four values lerped from it ───────
    // Original used four separate animateDpAsState calls:
    //   val panelWidth  by animateDpAsState(...)
    //   val panelHeight by animateDpAsState(...)
    //   val topPad      by animateDpAsState(...)
    //   val endPad      by animateDpAsState(...)
    //
    // Each animateDpAsState runs its own Choreographer callback and triggers a
    // recomposition tick independently. Four of them running simultaneously
    // during expand/collapse caused Compose to recompose ReferencePosePanel
    // on every animation frame — typically 4 recompositions per vsync.
    //
    // Fix: one animateFloatAsState drives a single [0,1] progress value.
    // All four layout dimensions are lerped from that single float.
    // Result: one recomposition per vsync during animation instead of four.

    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(250),
        label = "panelProgress",
    )

    // Lerp all four dimensions from the single progress float.
    val panelWidth  : Dp = lerp(120f, 260f, progress).dp
    val panelHeight : Dp = lerp(160f, 380f, progress).dp
    val topPad      : Dp = lerp(56f,  80f,  progress).dp
    val endPad      : Dp = lerp(8f,   16f,  progress).dp

    val rawKpts    = state.meanKeypoints
    val normalized = remember(rawKpts) { rawKpts?.let { SkeletonNormalizer.normalize(it) } }

    Box(
        modifier = modifier
            .padding(top = topPad, end = endPad)
            .width(panelWidth)
            .height(panelHeight)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (expanded) 12.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.monitor_target),
                fontSize     = if (expanded) 13.sp else 9.sp,
                fontWeight   = FontWeight.Bold,
                color        = COLOR_PANEL_TEXT,
                letterSpacing = 1.sp,
            )
            Text(
                stringResource(R.string.monitor_state_label, state.stateId + 1),
                fontSize = if (expanded) 11.sp else 8.sp,
                color    = Color(0xFF666666),
            )
            if (expanded) Text(stringResource(R.string.monitor_tap_to_close), fontSize = 9.sp, color = Color(0xFF999999))

            if (normalized != null) {
                val isExpanded = expanded
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx         = size.width / 2f
                    val cy         = size.height * if (isExpanded) 0.52f else 0.56f
                    val nodeRadius = min(size.width, size.height) * if (isExpanded) 0.52f else 0.42f
                    // Mirror horizontally so the target figure matches what the user
                    // sees in the camera (front camera is already mirrored).
                    // scale(-1, 1) around the center X flips the canvas horizontally.
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(cx, cy)) {
                        drawAnatomicalFigure(normalized, Offset(cx, cy), nodeRadius)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.monitor_no_preview), fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── Guidance overlay ──────────────────────────────────────────────────────────

@Composable
private fun GuidanceOverlay(previewState: PoseState, feedback: String, isFrontCamera: Boolean) {
    val rawKpts    = previewState.meanKeypoints
    val normalized = remember(rawKpts) { rawKpts?.let { SkeletonNormalizer.normalize(it) } }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF2F2F2F2)),
        contentAlignment = Alignment.Center,
    ) {
        if (normalized != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center     = Offset(size.width / 2f, size.height * 0.50f)
                val nodeRadius = min(size.width, size.height) * 0.56f
                
                // Mirror the reference figure ONLY if the camera is mirrored.
                // This keeps the "Target" oriented the same way as the user's reflection.
                val mirrorX = if (isFrontCamera) -1f else 1f
                scale(scaleX = mirrorX, scaleY = 1f, pivot = center) {
                    drawAnatomicalFigure(normalized, center, nodeRadius)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
        ) {
            Text(stringResource(R.string.monitor_next_pose), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
            Text(stringResource(R.string.monitor_state_label, previewState.stateId + 1), fontSize = 15.sp, color = Color(0xFF555555))
        }

        Text(
            text       = feedback,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = Color(0xFF222222),
            modifier   = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .background(Color(0x22000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(frameStatus: FrameStatus, modifier: Modifier = Modifier) {
    val (text, color, icon) = when {
        frameStatus.inGrace -> Triple(stringResource(R.string.monitor_status_transitioning), COLOR_GRACE, ">>")
        frameStatus.phase == MonitorPhase.SESSION_COMPLETE -> Triple(stringResource(R.string.monitor_status_session_complete), COLOR_CORRECT, "✓")
        frameStatus.isCorrect -> Triple(stringResource(R.string.monitor_status_correct_form), COLOR_CORRECT, "✓")
        else -> Triple(stringResource(R.string.monitor_status_adjust_form), COLOR_WRONG, "!!")
    }
    Row(
        modifier = modifier
            .padding(8.dp)
            .background(Color(0xCC1A1A1A), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ── Accuracy meter ────────────────────────────────────────────────────────────

@Composable
private fun AccuracyMeter(accuracy: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r  = minOf(cx, cy) - 6f
            drawCircle(Color(0xFF2A2A2A), r, Offset(cx, cy), style = Stroke(10f))
            if (accuracy > 0) {
                val arcColor = when {
                    accuracy >= 85 -> COLOR_CORRECT
                    accuracy >= 70 -> Color(0xFF00D7FF)
                    else           -> Color(0xFFFF4500)
                }
                drawArc(
                    color      = arcColor,
                    startAngle = -90f,
                    sweepAngle = accuracy * 3.6f,
                    useCenter  = false,
                    topLeft    = Offset(cx - r, cy - r),
                    size       = Size(r * 2, r * 2),
                    style      = Stroke(10f, cap = StrokeCap.Round),
                )
            }
            drawCircle(Color(0xFF1A1A1A), r - 10f, Offset(cx, cy))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$accuracy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("%", fontSize = 8.sp, color = Color(0xFF9E9E9E))
        }
    }
}

// ── Rep / Streak / Feedback ───────────────────────────────────────────────────

@Composable
private fun BarRepCounter(
    repsCompleted: Int,
    holdProgress: Float,
    repHistory: List<Double>,
    totalReps: Int,
    onRepCounterTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Column(
            modifier = modifier.clickable(onClick = onRepCounterTap),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.monitor_reps_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fractional count: 3/10
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = repsCompleted.toString(),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = "/$totalReps",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp).alignByBaseline()
                    )
                }

                // Vertical bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    repeat(totalReps) { index ->
                        val barWidth = 10.dp
                        val barHeight = 40.dp

                        Box(
                            modifier = Modifier
                                .size(width = barWidth, height = barHeight)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Gray.copy(alpha = 0.2f))
                        ) {
                            when {
                                // 1. COMPLETED REPS (History): Color based on accuracy
                                index < repsCompleted -> {
                                    val accuracy = if (index < repHistory.size) repHistory[index].toFloat() else 0.0f
                                    val barColor = when {
                                        accuracy >= 0.85f -> Color(0xFF00E676) // Bright Green (Vibrant)
                                        accuracy >= 0.60f -> Color(0xFFFFD600) // Yellow/Gold
                                        accuracy >= 0.30f -> Color(0xFFFF9100) // Orange
                                        else             -> Color(0xFFFF3D00) // Deep Red/Orange
                                    }
                                    if (accuracy > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(accuracy.coerceIn(0f, 1.0f))
                                                .align(Alignment.BottomCenter)
                                                .background(barColor)
                                        )
                                    }
                                }

                                // 2. CURRENT REP (Live): Show White with height = holdProgress
                                index == repsCompleted -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(holdProgress.coerceIn(0.01f, 1.0f))
                                            .align(Alignment.BottomCenter)
                                            .background(Color.White)
                                    )
                                }

                                // 3. FUTURE REPS: Empty (Grey background from parent Box)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun appendTransitionDebugLog(context: Context, message: String) {
    runCatching {
        val externalDir = context.getExternalFilesDir(null) ?: return
        val logFile = File(externalDir, "debug_transitions.log")
        logFile.appendText(message + "\n")
    }
}

@Composable
fun RepCounter(reps: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xCC282828), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(stringResource(R.string.monitor_reps_counter, reps), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
    }
}

@Composable
fun StreakCounter(streak: Int, modifier: Modifier = Modifier) {
    val (label, color) = when {
        streak >= 10 -> stringResource(R.string.monitor_streak_fire) to Color(0xFFFF6600)
        streak >= 5  -> stringResource(R.string.monitor_streak_active) to COLOR_CORRECT
        else         -> stringResource(R.string.monitor_streak) to Color(0xFFBBBBBB)
    }
    Box(
        modifier = modifier
            .background(Color(0xCC282828), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(stringResource(R.string.monitor_streak_counter, label, streak), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun PerformanceAccuracyBar(
    isCorrect: Boolean,
    holdProgress: Float,
    modifier: Modifier = Modifier
) {
    if (holdProgress <= 0.01f) return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Gray.copy(alpha = 0.2f))
    ) {
        val barColor = if (isCorrect) Color.Green else Color.Red
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(holdProgress.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .background(barColor)
        )
    }
}

@Composable
fun FeedbackBar(
    feedback: String,
    isCorrect: Boolean,
    inGrace: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        inGrace   -> COLOR_GRACE
        isCorrect -> COLOR_CORRECT
        else      -> Color(0xFF00D7FF)
    }
    Box(
        modifier = modifier
            .background(Color(0xEE1A1A1A), RoundedCornerShape(10.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color        = borderColor,
                size         = size,
                cornerRadius = CornerRadius(10.dp.toPx()),
                style        = Stroke(3f),
            )
        }
        Text(
            feedback,
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color.White,
            textAlign  = TextAlign.Center,
        )
    }
}

@Composable
private fun InstructionBanner(instruction: String?, modifier: Modifier = Modifier) {
    if (instruction.isNullOrBlank()) return

    Surface(
        color = Color(0xAA000000),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = Color(0xFFE8622E),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = instruction,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}


// ── Helpers ───────────────────────────────────────────────────────────────────

fun computeDisplayAccuracy(status: FrameStatus): Int =
    if (status.isCorrect) (status.holdProgress * 100).toInt().coerceIn(0, 100) else 0

package com.posemonitor2

import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.core.poseengine.model.KeypointIndex
import kotlin.math.*
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

// ── Gradient Brush Cache for Performance ──────────────────────────────────────
// Pre-computed radial gradient brushes to avoid creating new Color objects every frame
// This eliminates ~170 Color allocations per frame (85 per skeleton × 2 skeletons)

private class GradientBrushCache {
    // Cache for ghost skeleton gradients
    private val ghostGradients = mutableMapOf<Pair<Color, Float>, androidx.compose.ui.graphics.Brush>()
    
    // Cache for live skeleton gradients
    private val liveGradients = mutableMapOf<Pair<Color, Float>, androidx.compose.ui.graphics.Brush>()
    
    fun getGhostGradient(baseColor: Color, alpha: Float, center: Offset, radius: Float): androidx.compose.ui.graphics.Brush {
        val key = baseColor to alpha
        return ghostGradients.getOrPut(key) {
            androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = alpha * 0.3f),  // Outer glow
                    baseColor.copy(alpha = alpha),          // Mid ring
                    baseColor.copy(alpha = alpha),          // Inner body
                    Color.White.copy(alpha = alpha * 0.8f), // Bright core
                )
            )
        }
    }
    
    fun getLiveGradient(statusColor: Color, center: Offset, radius: Float): androidx.compose.ui.graphics.Brush {
        // For live skeleton, we use statusColor directly (already varies by joint)
        // We cache by color to avoid repeated gradient creation
        return liveGradients.getOrPut(statusColor to radius) {
            androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    statusColor.copy(alpha = 0.3f),  // Outer glow
                    statusColor.copy(alpha = 0.6f),  // Mid ring  
                    statusColor,                      // Main body
                    Color.White.copy(alpha = 0.9f),  // Bright core
                )
            )
        }
    }
    
    fun clear() {
        ghostGradients.clear()
        liveGradients.clear()
    }
}

private val gradientCache = GradientBrushCache()

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

    val holdTarget = targetHoldDurationSeconds ?: displayState?.minHoldDuration
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
        // ... (existing code for Video Tutorial Panel)

        val isBodyMissing = skeleton == null || frameStatus.feedback.contains("only one person")

        if (isGuidance && guidancePreviewState != null) {
            GuidanceOverlay(
                previewState = guidancePreviewState,
                userSkeleton = skeleton,
                cameraFrameSize = cameraFrameSize,
                deviations = deviations,
                feedback = frameStatus.feedback,
                isCorrect = frameStatus.isCorrect,
                isFrontCamera = isFrontCamera
            )
        } else {
            // ── Performance ghost skeleton (Target template) ─────────────────
            if (targetState != null && !isBodyMissing) {
                BlinkingGhostSkeleton(
                    targetState = targetState,
                    userSkeleton = skeleton,
                    cameraFrameSize = cameraFrameSize,
                    deviations = deviations,
                    isFrontCamera = isFrontCamera,
                    isAligned = frameStatus.isCorrect
                )
            }

            if (skeleton != null && !isBodyMissing) {
                LiveSkeletonOverlay(
                    skeleton = skeleton,
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

        // ── Critical Status Message ──────────────────────────────────────────
        if (isBodyMissing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = frameStatus.feedback,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp)
                    )
                }
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
                            state         = displayState,
                            expanded      = panelExpanded,
                            onToggle      = { panelExpanded = !panelExpanded },
                            isFrontCamera = isFrontCamera,
                            modifier      = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        // FeedbackBar removed: textual correction display is disabled to
        // reduce per-frame UI work. Voice feedback remains active.

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
            inGrace = frameStatus.inGrace,
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
private fun BlinkingGhostSkeleton(
    targetState: PoseState,
    userSkeleton: Skeleton2d?,
    cameraFrameSize: Size,
    deviations: Map<FeatureKey, Double>,
    isFrontCamera: Boolean,
    isAligned: Boolean
) {
    // ── Overall Visibility Animation ─────────────────────────────────────
    val targetAlpha = if (isAligned || userSkeleton == null) 0f else 1f
    val overallAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(600),
        label = "ghost_fade"
    )

    if (overallAlpha <= 0.01f || userSkeleton == null) return

    val infiniteTransition = rememberInfiniteTransition(label = "ghost_blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghost_alpha"
    )

    // Sky Blue / Cyan (Matching the requested style)
    val ghostColor = Color(0xFF00D2FF)
    val rawKpts = targetState.meanKeypoints
    val normalized = remember(rawKpts) { rawKpts?.let { SkeletonNormalizer.normalize(it) } }

    if (normalized != null) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = overallAlpha }) {
            val screenW = size.width
            val screenH = size.height

            val camW = cameraFrameSize.width
            val camH = cameraFrameSize.height
            val hasCamSize = camW > 0f && camH > 0f

            val visX0: Float; val visX1: Float; val visY0: Float; val visY1: Float
            if (hasCamSize) {
                val scaleFactor = maxOf(screenW / camW, screenH / camH)
                val scaledW = camW * scaleFactor; val scaledH = camH * scaleFactor
                val cropX = (scaledW - screenW) / 2f; val cropY = (scaledH - screenH) / 2f
                visX0 = cropX / scaledW; visX1 = (scaledW - cropX) / scaledW
                visY0 = cropY / scaledH; visY1 = (scaledH - cropY) / scaledH
            } else {
                visX0 = 0f; visX1 = 1f; visY0 = 0f; visY1 = 1f
            }
            val visRangeX = visX1 - visX0
            val visRangeY = visY1 - visY0

            fun kptToScreen(kx: Float, ky: Float): Offset {
                val mx = if (isFrontCamera) 1f - kx else kx
                val px = (mx - visX0) / visRangeX * screenW
                val py = (ky - visY0) / visRangeY * screenH
                return Offset(px, py)
            }

            // ── Robust Anatomical Scaling (Nose, Eyes, Shoulders, Hips) ─────
            // We use as many points as possible to anchor the ghost exactly to the user.
            val uPts = mutableListOf<Offset>()
            val tPts = mutableListOf<Offset>()
            
            // Priority indices: Face (0,1,2), Shoulders (5,6), Hips (11,12)
            listOf(0, 1, 2, 5, 6, 11, 12).forEach { i ->
                val kpt = userSkeleton?.keypoints?.get(i)
                if (kpt != null && kpt.confidence > 0.15f) {
                    uPts.add(kptToScreen(kpt.x.toFloat(), kpt.y.toFloat()))
                    tPts.add(normalized[i])
                }
            }

            var finalScale = min(screenW, screenH) * 0.5f
            var finalCenter = Offset(screenW / 2f, screenH * 0.5f)
            var finalRef = Offset.Zero

            if (uPts.size >= 2) {
                // User center of mass for visible points
                finalCenter = Offset(
                    uPts.map { it.x }.average().toFloat(),
                    uPts.map { it.y }.average().toFloat()
                )
                // Target center for the same points in normalized space
                finalRef = Offset(
                    tPts.map { it.x }.average().toFloat(),
                    tPts.map { it.y }.average().toFloat()
                )

                // Calculate average scale based on spread from center
                val uDist = uPts.map { (it - finalCenter).getDistance() }.average().toFloat()
                val tDist = tPts.map { (it - finalRef).getDistance() }.average().toFloat()
                
                if (tDist > 0.01f && uDist > 5f) {
                    finalScale = uDist / tDist
                }
            }

            // Apply mirroring and draw. 
            // Note: finalScale is pixels-per-normalized-unit.
            val mirrorX = if (isFrontCamera) -1f else 1f
            scale(scaleX = mirrorX, scaleY = 1f, pivot = finalCenter) {
                drawGhostStickFigure(
                    keypoints = normalized,
                    center = finalCenter,
                    scale = finalScale,
                    targetOffset = finalRef,
                    deviations = deviations,
                    tolerances = targetState.featureTolerances.map,
                    blinkAlpha = blinkAlpha,
                    color = ghostColor
                )
            }
        }
    }
}

private val BONE_TO_FEATURES: Map<Int, List<FeatureKey>> = mapOf(
    4 to listOf(FeatureKey.TORSO_ANGLE),
    5 to listOf(FeatureKey.TORSO_ANGLE),
    6 to listOf(FeatureKey.TORSO_ANGLE),
    7 to listOf(FeatureKey.LEFT_SHOULDER_ANGLE, FeatureKey.LEFT_ARM_ELEVATION),
    8 to listOf(FeatureKey.LEFT_ELBOW_ANGLE),
    9 to listOf(FeatureKey.RIGHT_SHOULDER_ANGLE, FeatureKey.RIGHT_ARM_ELEVATION),
    10 to listOf(FeatureKey.RIGHT_ELBOW_ANGLE),
    11 to listOf(FeatureKey.LEFT_HIP_ANGLE, FeatureKey.LEFT_LEG_SPREAD, FeatureKey.TORSO_ANGLE),
    12 to listOf(FeatureKey.RIGHT_HIP_ANGLE, FeatureKey.RIGHT_LEG_SPREAD, FeatureKey.TORSO_ANGLE),
    13 to listOf(FeatureKey.LEFT_HIP_ANGLE, FeatureKey.RIGHT_HIP_ANGLE),
    14 to listOf(FeatureKey.LEFT_HIP_ANGLE, FeatureKey.LEFT_LEG_SPREAD),
    15 to listOf(FeatureKey.LEFT_KNEE_ANGLE),
    16 to listOf(FeatureKey.RIGHT_HIP_ANGLE, FeatureKey.RIGHT_LEG_SPREAD),
    17 to listOf(FeatureKey.RIGHT_KNEE_ANGLE),
)

private val JOINT_TO_FEATURES: Map<Int, List<FeatureKey>> = mapOf(
    0 to listOf(FeatureKey.TORSO_ANGLE),
    5 to listOf(FeatureKey.LEFT_SHOULDER_ANGLE, FeatureKey.LEFT_ARM_ELEVATION),
    6 to listOf(FeatureKey.RIGHT_SHOULDER_ANGLE, FeatureKey.RIGHT_ARM_ELEVATION),
    7 to listOf(FeatureKey.LEFT_ELBOW_ANGLE),
    8 to listOf(FeatureKey.RIGHT_ELBOW_ANGLE),
    9 to listOf(FeatureKey.LEFT_ELBOW_ANGLE),
    10 to listOf(FeatureKey.RIGHT_ELBOW_ANGLE),
    11 to listOf(FeatureKey.LEFT_HIP_ANGLE, FeatureKey.LEFT_LEG_SPREAD),
    12 to listOf(FeatureKey.RIGHT_HIP_ANGLE, FeatureKey.RIGHT_LEG_SPREAD),
    13 to listOf(FeatureKey.LEFT_KNEE_ANGLE),
    14 to listOf(FeatureKey.RIGHT_KNEE_ANGLE),
    15 to listOf(FeatureKey.LEFT_KNEE_ANGLE),
    16 to listOf(FeatureKey.RIGHT_KNEE_ANGLE)
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhostStickFigure(
    keypoints: List<Offset>,
    center: Offset,
    scale: Float,
    targetOffset: Offset,
    deviations: Map<FeatureKey, Double>,
    tolerances: Map<FeatureKey, Double>,
    blinkAlpha: Float,
    color: Color
) {
    fun toCanvas(kpt: Offset) = Offset(
        center.x + (kpt.x - targetOffset.x) * scale,
        center.y - (kpt.y - targetOffset.y) * scale,
    )
    val pts = Array(keypoints.size) { toCanvas(keypoints[it]) }
    val valid = BooleanArray(keypoints.size) { 
        val kpt = keypoints[it]
        !(kpt.x == 0f && kpt.y == 0f) && !kpt.x.isNaN() && !kpt.y.isNaN()
    }

    // ── OPTIMIZATION: Pre-compute joint alphas in a single pass ──────────────
    // Original: O(features × joints) forEach loop per joint during draw
    // Optimized: O(joints) single pass, then O(1) array lookup per joint
    val jointAlphas = FloatArray(keypoints.size)
    for (idx in pts.indices) {
        if (!valid[idx]) {
            jointAlphas[idx] = 0f
            continue
        }
        
        val features = JOINT_TO_FEATURES[idx]
        if (features == null) {
            jointAlphas[idx] = 0.2f * blinkAlpha
            continue
        }
        
        var maxRatio = 0f
        var hasInfo = false
        for (key in features) {
            val dev = deviations[key]
            if (dev != null) {
                hasInfo = true
                val tol = tolerances[key] ?: 1.0
                val ratio = (abs(dev) / tol).toFloat()
                if (ratio > maxRatio) maxRatio = ratio
            }
        }
        
        jointAlphas[idx] = if (!hasInfo || maxRatio > 1.0f) {
            blinkAlpha
        } else {
            0.1f * blinkAlpha 
        }
    }

    // Helper to get bone alpha from pre-computed joint alphas
    fun getBoneAlpha(bone: Int?): Float {
        if (bone == null) return blinkAlpha
        val features = BONE_TO_FEATURES[bone] ?: return blinkAlpha
        
        var maxRatio = 0f
        var hasInfo = false
        for (key in features) {
            val dev = deviations[key]
            if (dev != null) {
                hasInfo = true
                val tol = tolerances[key] ?: 1.0
                val ratio = (abs(dev) / tol).toFloat()
                if (ratio > maxRatio) maxRatio = ratio
            }
        }
        
        return if (!hasInfo || maxRatio > 1.0f) {
            blinkAlpha
        } else {
            0.1f * blinkAlpha 
        }
    }

    // 1. Draw Bones as Diamonds (Matching the live skeleton style)
    for (i in 0 until SkeletonConnections.boneCount) {
        val aIdx = SkeletonConnections.boneIndices[i * 2]
        val bIdx = SkeletonConnections.boneIndices[i * 2 + 1]
        if (valid[aIdx] && valid[bIdx]) {
            val alpha = getBoneAlpha(i)
            if (alpha <= 0.01f) continue
            
            val start = pts[aIdx]
            val end   = pts[bIdx]
            
            drawDiamondBone(
                start = start,
                end = end,
                auraColor = color.copy(alpha = alpha * 0.5f),
                coreColor = Color.White.copy(alpha = alpha * 0.9f),
                maxWidth = 16f * (scale / 400f).coerceIn(0.5f, 2.5f)
            )
        }
    }

    // 2. Draw Joints as Glowing Spheres with Radial Gradient
    // ── OPTIMIZATION: Single drawCircle with radial gradient brush ───────────
    // Original: 3 drawCircle calls per joint = 51 calls for 17 joints
    // Optimized: 1 drawCircle per joint = 17 calls (66% reduction)
    val scaleFactor = (scale / 400f).coerceIn(0.8f, 2.0f)
    
    for (idx in pts.indices) {
        if (!valid[idx]) continue
        
        val alpha = jointAlphas[idx]
        if (alpha <= 0.01f) continue
        
        val pt = pts[idx]
        val radius = 6f * scaleFactor
        
        // Single drawCircle with cached radial gradient (replaces 3 separate calls)
        // OPTIMIZATION: Uses pre-computed gradient from cache to avoid Color.copy() allocations
        val gradient = gradientCache.getGhostGradient(color, alpha, pt, radius * 2.2f)
        drawCircle(
            brush = gradient,
            radius = radius * 2.2f,
            center = pt
        )
    }
}

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
            val my = ky

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

        // Draw joints as glowing spheres with radial gradient
        // ── OPTIMIZATION: Single drawCircle with radial gradient brush ───────────
        // Original: 3 drawCircle calls per joint = 51 calls for 17 joints
        // Optimized: 1 drawCircle per joint = 17 calls (66% reduction)
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

            // Single drawCircle with cached radial gradient (replaces 3 separate calls)
            // This reduces from 51 to 17 draw calls per frame
            // OPTIMIZATION: Uses pre-computed gradient from cache to avoid Color.copy() allocations
            val gradient = gradientCache.getLiveGradient(statusColor, center, radius * 2.2f)
            drawCircle(
                brush = gradient,
                radius = radius * 2.2f,
                center = center
            )
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
    isFrontCamera: Boolean,
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
                    // Mirror horizontally only for front camera so the target figure
                    // matches what the user sees in the mirror view.
                    val mirrorX = if (isFrontCamera) -1f else 1f
                    scale(scaleX = mirrorX, scaleY = 1f, pivot = Offset(cx, cy)) {
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
private fun GuidanceOverlay(
    previewState: PoseState,
    userSkeleton: Skeleton2d?,
    cameraFrameSize: Size,
    deviations: Map<FeatureKey, Double>,
    feedback: String,
    isCorrect: Boolean,
    isFrontCamera: Boolean
) {
    val rawKpts    = previewState.meanKeypoints
    val normalized = remember(rawKpts) { rawKpts?.let { SkeletonNormalizer.normalize(it) } }
    val isBodyMissing = userSkeleton == null || feedback.contains("only one person")

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (normalized != null && !isBodyMissing) {
            // ── Background Preview (Anatomical Figure) ──────────────────────
            // This provides the clear, high-contrast reference preview for the 
            // exercise state during the instruction phase.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx         = size.width / 2f
                val cy         = size.height * 0.5f
                val nodeRadius = min(size.width, size.height) * 0.42f
                // Mirror horizontally to match camera view convention
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(cx, cy)) {
                    drawAnatomicalFigure(normalized, Offset(cx, cy), nodeRadius)
                }
            }

            BlinkingGhostSkeleton(
                targetState = previewState,
                userSkeleton = userSkeleton,
                cameraFrameSize = cameraFrameSize,
                deviations = deviations,
                isFrontCamera = isFrontCamera,
                isAligned = isCorrect
            )
        }

        // Guidance overlay text removed to eliminate on-screen textual cues.
        // Visual preview (anatomical figure and ghost skeleton) remains.
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────

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
fun PerformanceAccuracyBar(
    isCorrect: Boolean,
    inGrace: Boolean,
    holdProgress: Float,
    modifier: Modifier = Modifier
) {
    if (holdProgress <= 0.01f) return

    val barColor = when {
        inGrace   -> COLOR_GRACE
        isCorrect -> Color.Green
        else      -> Color.Red
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Gray.copy(alpha = 0.2f))
    ) {
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

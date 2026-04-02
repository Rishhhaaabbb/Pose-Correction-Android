// app/src/main/java/com/posemonitor2/MonitorOverlay.kt

package com.posemonitor2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
import com.presentation.graphviz.SkeletonNormalizer
import com.presentation.graphviz.drawAnatomicalFigure
import kotlin.math.abs
import kotlin.math.min

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

@Composable
fun MonitorOverlay(
    skeleton: Skeleton2d?,
    frameStatus: FrameStatus?,
    targetState: PoseState?,
    guidancePreviewState: PoseState?,
    deviations: Map<FeatureKey, Double>,
    cameraFrameSize: androidx.compose.ui.geometry.Size = androidx.compose.ui.geometry.Size.Zero,
    modifier: Modifier = Modifier,
) {
    if (frameStatus == null) return

    val isGuidance   = frameStatus.phase == MonitorPhase.GUIDANCE
    val displayState = if (isGuidance) guidancePreviewState else targetState
    var panelExpanded by remember { mutableStateOf(false) }

    // ── Accuracy meter: retain last value on incorrect frames ─────────────────
    // Prevents the meter from snapping to 0 on brief form breaks.
    // The displayed value only advances forward (on correct frames) or holds
    // steady (on incorrect frames). It resets to 0 only when a new state begins.
    var displayedAccuracy by remember { mutableStateOf(0) }
    val newAccuracy = if (frameStatus.isCorrect)
        (frameStatus.holdProgress * 100).toInt().coerceIn(0, 100)
    else
        displayedAccuracy
    // Reset when state changes
    val currentStateId = frameStatus.stateId
    var lastStateId by remember { mutableStateOf(currentStateId) }
    if (currentStateId != lastStateId) {
        displayedAccuracy = 0
        lastStateId = currentStateId
    } else {
        displayedAccuracy = newAccuracy
    }
    val animatedAccuracy by animateIntAsState(
        targetValue    = displayedAccuracy,
        animationSpec  = tween(200),
        label          = "accuracy",
    )

    // ── Grace period pulse: flash status bar on rep completion ────────────────
    var pulseActive by remember { mutableStateOf(false) }
    var lastReps by remember { mutableStateOf(frameStatus.repsCompleted) }
    if (frameStatus.repsCompleted != lastReps) {
        pulseActive = true
        lastReps = frameStatus.repsCompleted
    }
    // Auto-dismiss pulse after 600ms
    LaunchedEffect(pulseActive) {
        if (pulseActive) {
            kotlinx.coroutines.delay(600)
            pulseActive = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        if (isGuidance && guidancePreviewState != null) {
            GuidanceOverlay(previewState = guidancePreviewState, feedback = frameStatus.feedback)
        } else {
            skeleton?.let {
                LiveSkeletonOverlay(
                    skeleton        = it,
                    deviations      = deviations,
                    targetState     = targetState,
                    cameraFrameSize = cameraFrameSize,
                )
            }
        }

        AnimatedVisibility(
            visible = panelExpanded,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(200)),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000)).clickable { panelExpanded = false })
        }

        if (displayState != null) {
            ReferencePosePanel(
                state    = displayState,
                expanded = panelExpanded,
                onToggle = { panelExpanded = !panelExpanded },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        StatusBar(
            frameStatus  = frameStatus,
            pulseActive  = pulseActive,
            modifier     = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(end = if (panelExpanded) 0.dp else 132.dp),
        )

        if (!panelExpanded) {
            AccuracyMeter(
                accuracy = animatedAccuracy,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 56.dp)
                    .size(80.dp),
            )
        }

        RepCounter(reps = frameStatus.repsCompleted, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        StreakCounter(streak = frameStatus.streak, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 72.dp))

        if (frameStatus.feedback.isNotEmpty()) {
            FeedbackBar(
                feedback  = frameStatus.feedback,
                isCorrect = frameStatus.isCorrect,
                inGrace   = frameStatus.inGrace,
                modifier  = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 24.dp),
            )
        }
    }
}

// ── Live skeleton ─────────────────────────────────────────────────────────────

@Composable
private fun LiveSkeletonOverlay(
    skeleton: Skeleton2d,
    deviations: Map<FeatureKey, Double>,
    targetState: PoseState?,
    cameraFrameSize: androidx.compose.ui.geometry.Size,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenW = size.width
        val screenH = size.height

        val camW = cameraFrameSize.width
        val camH = cameraFrameSize.height
        val hasCamSize = camW > 0f && camH > 0f

        val visX0: Float; val visX1: Float
        val visY0: Float; val visY1: Float

        if (hasCamSize) {
            val scale   = maxOf(screenW / camW, screenH / camH)
            val scaledW = camW * scale
            val scaledH = camH * scale
            val cropX   = (scaledW - screenW) / 2f
            val cropY   = (scaledH - screenH) / 2f
            visX0 = cropX / scaledW;        visX1 = (scaledW - cropX) / scaledW
            visY0 = cropY / scaledH;        visY1 = (scaledH - cropY) / scaledH
        } else {
            visX0 = 0f; visX1 = 1f; visY0 = 0f; visY1 = 1f
        }

        val visRangeX = visX1 - visX0
        val visRangeY = visY1 - visY0

        fun kptToScreen(kx: Float, ky: Float): Offset {
            val mx = 1f - kx
            val my = 1f - ky
            return Offset(
                (mx - visX0) / visRangeX * screenW,
                (my - visY0) / visRangeY * screenH,
            )
        }

        for (i in 0 until SkeletonConnections.boneCount) {
            val aIdx = SkeletonConnections.boneIndices[i * 2]
            val bIdx = SkeletonConnections.boneIndices[i * 2 + 1]
            val aKpt = skeleton.keypoints[aIdx]
            val bKpt = skeleton.keypoints[bIdx]
            if (aKpt.confidence < 0.2f) continue
            if (bKpt.confidence < 0.2f) continue
            drawLine(
                color       = COLOR_BONE_OK,
                start       = kptToScreen(aKpt.x.toFloat(), aKpt.y.toFloat()),
                end         = kptToScreen(bKpt.x.toFloat(), bKpt.y.toFloat()),
                strokeWidth = 5f,
                cap         = StrokeCap.Round,
            )
        }

        skeleton.keypoints.forEachIndexed { idx, kpt ->
            if (kpt.confidence < 0.2f) return@forEachIndexed
            val center     = kptToScreen(kpt.x.toFloat(), kpt.y.toFloat())
            val featureKey = JOINT_FEATURE_MAP[idx]
            val tolerance  = featureKey?.let { targetState?.featureTolerances?.get(it) }
            val deviation  = featureKey?.let { deviations[it] }

            val (fillColor, radius) = when {
                deviation != null && tolerance != null -> {
                    val ratio = abs(deviation) / tolerance
                    when {
                        ratio > 1.0 -> COLOR_ERROR_RED    to 14f
                        ratio > 0.7 -> COLOR_ERROR_YELLOW to 12f
                        else        -> COLOR_ERROR_GREEN  to 10f
                    }
                }
                else -> COLOR_JOINT_WHITE to 8f
            }
            drawCircle(fillColor, radius = radius, center = center)
            drawCircle(COLOR_JOINT_WHITE, radius = radius + 2f, center = center, style = Stroke(2f))
        }
    }
}

// ── Reference panel ───────────────────────────────────────────────────────────

@Composable
private fun ReferencePosePanel(
    state: PoseState,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue   = if (expanded) 1f else 0f,
        animationSpec = tween(250),
        label         = "panelProgress",
    )

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
            modifier = Modifier.fillMaxSize().padding(if (expanded) 12.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("TARGET", fontSize = if (expanded) 13.sp else 9.sp, fontWeight = FontWeight.Bold, color = COLOR_PANEL_TEXT, letterSpacing = 1.sp)
            Text("State ${state.stateId + 1}", fontSize = if (expanded) 11.sp else 8.sp, color = Color(0xFF666666))
            if (expanded) Text("Tap to close", fontSize = 9.sp, color = Color(0xFF999999))

            if (normalized != null) {
                val isExpanded = expanded
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx         = size.width / 2f
                    val cy         = size.height * if (isExpanded) 0.52f else 0.56f
                    val nodeRadius = min(size.width, size.height) * if (isExpanded) 0.52f else 0.42f
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(cx, cy)) {
                        drawAnatomicalFigure(normalized, Offset(cx, cy), nodeRadius)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No\npreview", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ── Guidance overlay ──────────────────────────────────────────────────────────

@Composable
private fun GuidanceOverlay(previewState: PoseState, feedback: String) {
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
                scale(scaleX = -1f, scaleY = 1f, pivot = center) {
                    drawAnatomicalFigure(normalized, center, nodeRadius)
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
        ) {
            Text("NEXT POSE", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
            Text("State ${previewState.stateId + 1}", fontSize = 15.sp, color = Color(0xFF555555))
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
private fun StatusBar(
    frameStatus: FrameStatus,
    pulseActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val (text, color, icon) = when {
        frameStatus.inGrace                                -> Triple("TRANSITIONING...", COLOR_GRACE,   ">>")
        frameStatus.phase == MonitorPhase.SESSION_COMPLETE -> Triple("SESSION COMPLETE!", COLOR_CORRECT, "✓")
        frameStatus.isCorrect                              -> Triple("CORRECT FORM",      COLOR_CORRECT, "✓")
        else                                               -> Triple("ADJUST FORM",       COLOR_WRONG,   "!!")
    }

    // Pulse background flashes green briefly on rep completion
    val pulseBg = if (pulseActive) Color(0xCC1A4A1A) else Color(0xCC1A1A1A)
    val animatedBg by animateFloatAsState(
        targetValue   = if (pulseActive) 1f else 0f,
        animationSpec = tween(150),
        label         = "pulse",
    )
    val bgColor = androidx.compose.ui.graphics.lerp(Color(0xCC1A1A1A), Color(0xCC1A4A1A), animatedBg)

    Row(
        modifier = modifier
            .padding(8.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(Color(0x33FFFFFF), RoundedCornerShape(4.dp)),
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
private fun RepCounter(reps: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xCC282828), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text("REPS  $reps", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
    }
}

@Composable
private fun StreakCounter(streak: Int, modifier: Modifier = Modifier) {
    val (label, color) = when {
        streak >= 10 -> "🔥 FIRE"   to Color(0xFFFF6600)
        streak >= 5  -> "✨ STREAK" to COLOR_CORRECT
        else         -> "STREAK"    to Color(0xFFBBBBBB)
    }
    Box(modifier = modifier.background(Color(0xCC282828), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text("$label  $streak", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun FeedbackBar(feedback: String, isCorrect: Boolean, inGrace: Boolean, modifier: Modifier = Modifier) {
    val borderColor = when { inGrace -> COLOR_GRACE; isCorrect -> COLOR_CORRECT; else -> Color(0xFF00D7FF) }
    Box(modifier = modifier.background(Color(0xEE1A1A1A), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(color = borderColor, size = size, cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(3f))
        }
        Text(feedback, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

private fun computeDisplayAccuracy(status: FrameStatus): Int =
    if (status.isCorrect) (status.holdProgress * 100).toInt().coerceIn(0, 100) else 0
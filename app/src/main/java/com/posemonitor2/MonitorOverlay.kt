// app/src/main/java/com/posemonitor2/MonitorOverlay.kt

package com.posemonitor2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
    modifier: Modifier = Modifier,
) {
    if (frameStatus == null) return

    val isGuidance   = frameStatus.phase == MonitorPhase.GUIDANCE
    val displayState = if (isGuidance) guidancePreviewState else targetState
    var panelExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {

        if (isGuidance && guidancePreviewState != null) {
            GuidanceOverlay(previewState = guidancePreviewState, feedback = frameStatus.feedback)
        } else {
            skeleton?.let {
                LiveSkeletonOverlay(skeleton = it, deviations = deviations, targetState = targetState)
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
            ReferencePosePanel(
                state    = displayState,
                expanded = panelExpanded,
                onToggle = { panelExpanded = !panelExpanded },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

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
        StreakCounter(
            streak   = frameStatus.streak,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 72.dp),
        )

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
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // ── Performance: no intermediate pts list ─────────────────────────────
        // Original code did:
        //   val pts = skeleton.keypoints.map { kpt -> Offset(...) }
        // which allocated a new List<Offset> (+ 17 Offset objects) every frame.
        //
        // Fix: compute each Offset inline at the draw call site.
        // Skeleton2d.keypoints is a List backed by an Array — random access is O(1).
        // The two extra property reads per endpoint (vs list lookup) are negligible
        // compared to the GC pressure removed by eliminating the allocation.

        // Draw bones
        for (i in 0 until SkeletonConnections.boneCount) {
            val aIdx = SkeletonConnections.boneIndices[i * 2]
            val bIdx = SkeletonConnections.boneIndices[i * 2 + 1]
            val aKpt = skeleton.keypoints[aIdx]
            val bKpt = skeleton.keypoints[bIdx]
            if (aKpt.confidence < 0.2f) continue
            if (bKpt.confidence < 0.2f) continue
            drawLine(
                color       = COLOR_BONE_OK,
                start       = Offset((1f - aKpt.x.toFloat()) * w, aKpt.y.toFloat() * h),
                end         = Offset((1f - bKpt.x.toFloat()) * w, bKpt.y.toFloat() * h),
                strokeWidth = 5f,
                cap         = StrokeCap.Round,
            )
        }

        // Draw joints
        skeleton.keypoints.forEachIndexed { idx, kpt ->
            if (kpt.confidence < 0.2f) return@forEachIndexed
            val center     = Offset((1f - kpt.x.toFloat()) * w, kpt.y.toFloat() * h)
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
                "TARGET",
                fontSize     = if (expanded) 13.sp else 9.sp,
                fontWeight   = FontWeight.Bold,
                color        = COLOR_PANEL_TEXT,
                letterSpacing = 1.sp,
            )
            Text(
                "State ${state.stateId + 1}",
                fontSize = if (expanded) 11.sp else 8.sp,
                color    = Color(0xFF666666),
            )
            if (expanded) Text("Tap to close", fontSize = 9.sp, color = Color(0xFF999999))

            if (normalized != null) {
                val isExpanded = expanded
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx         = size.width / 2f
                    val cy         = size.height * if (isExpanded) 0.52f else 0.56f
                    val nodeRadius = min(size.width, size.height) * if (isExpanded) 0.52f else 0.42f
                    drawAnatomicalFigure(normalized, Offset(cx, cy), nodeRadius)
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
                drawAnatomicalFigure(normalized, center, nodeRadius)
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
private fun StatusBar(frameStatus: FrameStatus, modifier: Modifier = Modifier) {
    val (text, color, icon) = when {
        frameStatus.inGrace -> Triple("TRANSITIONING...", COLOR_GRACE, ">>")
        frameStatus.phase == MonitorPhase.SESSION_COMPLETE -> Triple("SESSION COMPLETE!", COLOR_CORRECT, "✓")
        frameStatus.isCorrect -> Triple("CORRECT FORM", COLOR_CORRECT, "✓")
        else -> Triple("ADJUST FORM", COLOR_WRONG, "!!")
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
private fun RepCounter(reps: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xCC282828), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
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
    Box(
        modifier = modifier
            .background(Color(0xCC282828), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("$label  $streak", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun FeedbackBar(
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
        modifier = modifier.background(Color(0xEE1A1A1A), RoundedCornerShape(10.dp)),
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
            modifier   = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun computeDisplayAccuracy(status: FrameStatus): Int =
    if (status.isCorrect) (status.holdProgress * 100).toInt().coerceIn(0, 100) else 0
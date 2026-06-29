// presentation/src/main/java/com/presentation/graphviz/AnatomicalFigureRenderer.kt

package com.presentation.graphviz

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.presentation.graphviz.SkeletonNormalizer.isValidForDrawing
import kotlin.math.atan2
import kotlin.math.hypot

private val ANAT_SKIN       = Color(0xFFFFCBA4)
private val ANAT_SKIN_DARK  = Color(0xFFE8A882)
private val ANAT_SHIRT      = Color(0xFFE8622E) // Project Orange
private val ANAT_SHIRT_DARK = Color(0xFFBF4D21)
private val ANAT_PANT       = Color(0xFF2D5A5E) // Project Teal
private val ANAT_PANT_DARK  = Color(0xFF1F3F41)
private val ANAT_SHOE       = Color(0xFF212121)
private val ANAT_HAIR       = Color(0xFF4E342E)
private val ANAT_EYE        = Color(0xFF222222)
private val ANAT_MOUTH      = Color(0xFFc0836a)

fun DrawScope.drawAnatomicalFigure(
    keypoints: List<Offset>,
    center: Offset,
    nodeRadius: Float,
) {
    fun toCanvas(kpt: Offset) = Offset(
        center.x + kpt.x * nodeRadius * 2f,
        center.y - kpt.y * nodeRadius * 2f,
    )
    val pts   = Array(keypoints.size) { toCanvas(keypoints[it]) }
    val valid = BooleanArray(keypoints.size) { keypoints[it].isValidForDrawing() }

    val u: Float = if (valid[5] && valid[6]) {
        hypot((pts[6].x - pts[5].x).toDouble(), (pts[6].y - pts[5].y).toDouble()).toFloat() * 0.5f
    } else {
        nodeRadius * 0.5f
    }.coerceIn(nodeRadius * 0.15f, nodeRadius * 0.8f)

    drawAnatomicalBody(pts, valid, u)
}

internal fun DrawScope.drawAnatomicalBody(pts: Array<Offset>, valid: BooleanArray, u: Float) {

    // ── Legs ──────────────────────────────────────────────────────────────────
    if (valid[12] && valid[14]) drawCapsule(pts[12], u * 0.38f, pts[14], u * 0.33f, ANAT_PANT)
    if (valid[14] && valid[16]) drawCapsule(pts[14], u * 0.30f, pts[16], u * 0.24f, ANAT_PANT)
    if (valid[11] && valid[13]) drawCapsule(pts[11], u * 0.38f, pts[13], u * 0.33f, ANAT_PANT)
    if (valid[13] && valid[15]) drawCapsule(pts[13], u * 0.30f, pts[15], u * 0.24f, ANAT_PANT)
    // Knee joints — match pant color, no white
    if (valid[13]) drawCircle(ANAT_PANT_DARK, radius = u * 0.28f, center = pts[13])
    if (valid[14]) drawCircle(ANAT_PANT_DARK, radius = u * 0.28f, center = pts[14])
    if (valid[15]) drawShoe(pts[15], u)
    if (valid[16]) drawShoe(pts[16], u)

    // ── Torso ─────────────────────────────────────────────────────────────────
    val shoulderCx = when { valid[5] && valid[6] -> (pts[5].x + pts[6].x) / 2f; valid[5] -> pts[5].x; valid[6] -> pts[6].x; else -> return }
    val shoulderCy = when { valid[5] && valid[6] -> (pts[5].y + pts[6].y) / 2f; valid[5] -> pts[5].y; else -> pts[6].y }
    val hipCx = when { valid[11] && valid[12] -> (pts[11].x + pts[12].x) / 2f; valid[11] -> pts[11].x; valid[12] -> pts[12].x; else -> shoulderCx }
    val hipCy = when { valid[11] && valid[12] -> (pts[11].y + pts[12].y) / 2f; valid[11] -> pts[11].y; valid[12] -> pts[12].y; else -> shoulderCy + u * 2f }

    drawCapsule(Offset(shoulderCx, shoulderCy), u * 1.0f, Offset(hipCx, hipCy), u * 0.90f, ANAT_SHIRT)
    if (valid[5] && valid[6])   drawCapsule(pts[5], u * 0.40f, pts[6], u * 0.40f, ANAT_SHIRT)
    if (valid[11] && valid[12]) drawCapsule(pts[11], u * 0.36f, pts[12], u * 0.36f, ANAT_PANT)

    // ── Arms ──────────────────────────────────────────────────────────────────
    if (valid[6] && valid[8])  drawCapsule(pts[6],  u * 0.34f, pts[8],  u * 0.28f, ANAT_SHIRT)
    if (valid[8] && valid[10]) drawCapsule(pts[8],  u * 0.26f, pts[10], u * 0.20f, ANAT_SKIN)
    if (valid[5] && valid[7])  drawCapsule(pts[5],  u * 0.34f, pts[7],  u * 0.28f, ANAT_SHIRT)
    if (valid[7] && valid[9])  drawCapsule(pts[7],  u * 0.26f, pts[9],  u * 0.20f, ANAT_SKIN)
    // Elbow joints — match shirt color, no white
    if (valid[7]) drawCircle(ANAT_SHIRT_DARK, radius = u * 0.22f, center = pts[7])
    if (valid[8]) drawCircle(ANAT_SHIRT_DARK, radius = u * 0.22f, center = pts[8])
    // Wrists — match skin color, no white
    if (valid[9])  drawCircle(ANAT_SKIN_DARK, radius = u * 0.18f, center = pts[9])
    if (valid[10]) drawCircle(ANAT_SKIN_DARK, radius = u * 0.18f, center = pts[10])

    // ── Neck ──────────────────────────────────────────────────────────────────
    if (valid[0]) {
        drawCapsule(Offset(shoulderCx, shoulderCy - u * 0.1f), u * 0.28f,
            Offset(pts[0].x, pts[0].y + u * 0.55f), u * 0.24f, ANAT_SKIN)
    }

    // ── Head ──────────────────────────────────────────────────────────────────
    if (valid[0]) {
        val headR  = u * 0.72f
        val headCx = pts[0].x
        val headCy = pts[0].y - headR * 0.3f

        // Hair circle (shifted up) then skin face (shifted down) on top
        drawCircle(ANAT_HAIR, headR, Offset(headCx, headCy - headR * 0.18f))
        drawCircle(ANAT_SKIN, headR * 0.88f, Offset(headCx, headCy + headR * 0.12f))
        drawCircle(ANAT_SKIN_DARK, headR, Offset(headCx, headCy), style = Stroke(u * 0.04f))

        val eyeY   = headCy + headR * 0.05f
        val eyeOff = headR * 0.30f
        val eyeR   = u * 0.10f
        drawCircle(ANAT_EYE, eyeR * 1.6f, Offset(headCx - eyeOff, eyeY))
        drawCircle(ANAT_EYE, eyeR * 1.6f, Offset(headCx + eyeOff, eyeY))
        drawCircle(ANAT_EYE, eyeR,         Offset(headCx - eyeOff, eyeY))
        drawCircle(ANAT_EYE, eyeR,         Offset(headCx + eyeOff, eyeY))

        val mouthY = headCy + headR * 0.42f
        val smilePath = Path().apply {
            moveTo(headCx - headR * 0.32f, mouthY)
            quadraticBezierTo(headCx, mouthY + headR * 0.12f, headCx + headR * 0.32f, mouthY)
        }
        drawPath(smilePath, ANAT_MOUTH, style = Stroke(width = u * 0.10f, cap = StrokeCap.Round))
    }
}

internal fun DrawScope.drawCapsule(p1: Offset, r1: Float, p2: Offset, r2: Float, fill: Color) {
    val dx  = p2.x - p1.x; val dy = p2.y - p1.y
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
    val ux  = -dy / len;   val uy = dx / len
    val a1  = Math.toDegrees(atan2(uy.toDouble(), ux.toDouble())).toFloat()
    val path = Path().apply {
        moveTo(p1.x + ux * r1, p1.y + uy * r1)
        lineTo(p2.x + ux * r2, p2.y + uy * r2)
        arcTo(Rect(center = p2, radius = r2), a1,        180f, false)
        lineTo(p1.x - ux * r1, p1.y - uy * r1)
        arcTo(Rect(center = p1, radius = r1), a1 + 180f, 180f, false)
        close()
    }
    drawPath(path, fill)
}

internal fun DrawScope.drawShoe(ankle: Offset, u: Float) {
    drawPath(Path().apply {
        addOval(Rect(left = ankle.x - u * 0.52f, top = ankle.y, right = ankle.x + u * 0.48f, bottom = ankle.y + u * 0.30f))
    }, ANAT_SHOE)
}
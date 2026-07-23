package com.posemonitor2.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.posemonitor2.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun BmiDetailDialog(
    bmi: Double,
    weightKg: Double?,
    targetWeightKg: Double?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_bmi_detail_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    BmiGauge(bmi = bmi)

                    if (weightKg != null && targetWeightKg != null) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.home_current_status), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${weightKg.toInt()} / ${targetWeightKg.toInt()} kg", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

@Composable
internal fun BmiGauge(bmi: Double) {
    val clampedBmi = bmi.coerceIn(10.0, 40.0)
    val normalizedPos = ((clampedBmi - 10.0) / 30.0).toFloat()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val animProgress = remember { Animatable(0f) }
    val animNeedle = remember { Animatable(0f) }
    val animNumber = remember { Animatable(0f) }

    LaunchedEffect(bmi) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
    }
    LaunchedEffect(bmi) {
        animNeedle.animateTo(
            targetValue = normalizedPos,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }
    LaunchedEffect(bmi) {
        animNumber.animateTo(
            targetValue = clampedBmi.toFloat(),
            animationSpec = tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
    }

    val displayedBmi = animNumber.value

    val segments = listOf(
        Triple(Color(0xFF2196F3), "Underweight", Pair(180f, 225f)),
        Triple(Color(0xFF4CAF50), "Normal", Pair(225f, 270f)),
        Triple(Color(0xFFFF9800), "Overweight", Pair(270f, 315f)),
        Triple(Color(0xFFF44336), "Obese", Pair(315f, 360f)),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.Center),
        ) {
            val strokeWidth = 32.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height)
            val totalSweep = 180f * animProgress.value

            for ((color, label, range) in segments) {
                val segStart = range.first
                val segEnd = range.second
                val visibleEnd = minOf(segEnd, 180f + totalSweep)
                val visibleSweep = (visibleEnd - segStart).coerceAtLeast(0f)

                if (visibleSweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = segStart,
                        sweepAngle = visibleSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                    )
                }
            }

            val needleAngle = 180f + animNeedle.value * 180f
            val needleLength = radius - 2.dp.toPx()
            val needleRad = Math.toRadians(needleAngle.toDouble())
            val needleEnd = Offset(
                x = center.x + needleLength * cos(needleRad).toFloat(),
                y = center.y + needleLength * sin(needleRad).toFloat(),
            )

            drawLine(
                color = onSurfaceColor,
                start = center,
                end = needleEnd,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            drawCircle(
                color = onSurfaceColor,
                radius = 5.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = center,
            )

            for ((color, label, range) in segments) {
                val midAngle = Math.toRadians(((range.first + range.second) / 2).toDouble())
                val labelRadius = radius + strokeWidth / 2 + 14.dp.toPx()
                val labelX = center.x + labelRadius * cos(midAngle).toFloat()
                val labelY = center.y + labelRadius * sin(midAngle).toFloat()

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            this.color = android.graphics.Color.parseColor("#666666")
                            textSize = 9.dp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawText(label, labelX, labelY + 3.dp.toPx(), paint)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = String.format("%.1f", displayedBmi),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "BMI",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
    }
}

// presentation/src/main/java/com/presentation/graphviz/PoseGraphVisualizationScreen.kt

package com.presentation.graphviz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.core.poseengine.PoseGraph
import com.presentation.graphviz.PoseGraphLayout.Algorithm
import com.presentation.graphviz.SkeletonNormalizer.isValidForDrawing
import kotlin.math.*

private val COLOR_BACKGROUND  = Color(0xFFF5F5F5)
private val COLOR_NODE_FILL   = Color(0xFFFAFAFA)
private val COLOR_NODE_BORDER = Color(0xFF333333)
private val COLOR_EDGE        = Color(0xFF555555)
private val COLOR_LABEL_BG    = Color.White

private const val NODE_RADIUS_FRACTION  = 0.18f
private const val LABEL_OFFSET_FRACTION = 1.5f
private const val ZOOM_MIN = 0.5f
private const val ZOOM_MAX = 5.0f

@Composable
fun PoseGraphVisualizationScreen(graph: PoseGraph) {
    var selectedLayout by remember { mutableStateOf(Algorithm.CIRCULAR) }
    var scale   by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var statsExpanded       by remember { mutableStateOf(true) }
    var transitionsExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(COLOR_BACKGROUND)) {

        // ── Title ─────────────────────────────────────────────────────────────
        Surface(shadowElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Pose State Transition Graph", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Video: ${graph.metadata.videoPath ?: "Unknown"}  •  States: ${graph.states.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Layout selector ───────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Algorithm.entries.forEach { algo ->
                FilterChip(
                    selected = selectedLayout == algo,
                    onClick  = { selectedLayout = algo; scale = 1f; offsetX = 0f; offsetY = 0f },
                    label    = { Text(algo.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        // ── Zoom controls ─────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom: ${"%.1f".format(scale)}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { scale = (scale * 1.25f).coerceAtMost(ZOOM_MAX) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("+", fontSize = 16.sp) }
            OutlinedButton(onClick = { scale = (scale / 1.25f).coerceAtLeast(ZOOM_MIN) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("−", fontSize = 16.sp) }
            OutlinedButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text("Reset", fontSize = 12.sp) }
            Text("Pinch or drag to pan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }

        Spacer(Modifier.height(8.dp))

        // ── Graph canvas ──────────────────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale    = (scale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
        ) {
            val canvasWidth  = maxWidth.value
            val canvasHeight = maxHeight.value
            val nodeRadius   = minOf(canvasWidth, canvasHeight) * NODE_RADIUS_FRACTION

            val positions = remember(graph, selectedLayout) {
                PoseGraphLayout.compute(
                    nodeIds   = graph.states.map { it.stateId },
                    edges     = graph.transitions.flatMap { (from, tos) -> tos.map { from to it } },
                    algorithm = selectedLayout,
                )
            }

            val normalizedSkeletons = remember(graph) {
                graph.states.associate { state ->
                    state.stateId to state.meanKeypoints?.let { SkeletonNormalizer.normalize(it) }
                }
            }

            val textMeasurer = rememberTextMeasurer()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY },
            ) {
                val centerX = size.width  / 2f
                val centerY = size.height / 2f
                val scaleX  = size.width  * 0.38f
                val scaleY  = size.height * 0.38f

                fun normalizedToCanvas(pos: Offset) = Offset(centerX + pos.x * scaleX, centerY + pos.y * scaleY)

                // Edges
                for ((fromId, toIds) in graph.transitions) {
                    for (toId in toIds) {
                        val fromPos = positions[fromId] ?: continue
                        val toPos   = positions[toId]   ?: continue
                        drawDirectedEdge(normalizedToCanvas(fromPos), normalizedToCanvas(toPos), nodeRadius)
                    }
                }

                // Nodes + anatomical figures
                for (state in graph.states) {
                    val normPos   = positions[state.stateId] ?: continue
                    val canvasPos = normalizedToCanvas(normPos)
                    val skeleton  = normalizedSkeletons[state.stateId]

                    drawCircle(COLOR_NODE_FILL,   nodeRadius, canvasPos)
                    drawCircle(COLOR_NODE_BORDER, nodeRadius, canvasPos, style = Stroke(2.5f))

                    if (skeleton != null) {
                        // ── Anatomical figure ─────────────────────────────────
                        drawAnatomicalFigure(
                            keypoints  = skeleton,
                            center     = canvasPos,
                            nodeRadius = nodeRadius * 1.1f,
                        )
                    } else {
                        val label = textMeasurer.measure(
                            text  = "S${state.stateId}",
                            style = TextStyle(fontSize = (nodeRadius * 0.4f).sp, fontWeight = FontWeight.Bold),
                        )
                        drawText(label, topLeft = Offset(canvasPos.x - label.size.width / 2f, canvasPos.y - label.size.height / 2f))
                    }

                    // Label below node
                    val labelText   = "State ${state.stateId}\n${"%.1f".format(state.minHoldDuration)}s"
                    val labelLayout = textMeasurer.measure(
                        AnnotatedString(labelText),
                        TextStyle(fontSize = (nodeRadius * 0.28f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    )
                    val labelY  = canvasPos.y + nodeRadius * LABEL_OFFSET_FRACTION
                    val labelX  = canvasPos.x - labelLayout.size.width / 2f
                    val padding = 6f

                    drawRoundRect(COLOR_LABEL_BG,    Offset(labelX - padding, labelY - padding), Size(labelLayout.size.width + padding * 2, labelLayout.size.height + padding * 2), androidx.compose.ui.geometry.CornerRadius(4f))
                    drawRoundRect(COLOR_NODE_BORDER, Offset(labelX - padding, labelY - padding), Size(labelLayout.size.width + padding * 2, labelLayout.size.height + padding * 2), androidx.compose.ui.geometry.CornerRadius(4f), style = Stroke(1.5f))
                    drawText(labelLayout, topLeft = Offset(labelX, labelY))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Collapsible panels ────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            CollapsibleCard("Transition Sequence", transitionsExpanded, { transitionsExpanded = !transitionsExpanded }, Modifier.padding(horizontal = 16.dp)) {
                TransitionSequenceContent(graph)
            }
            Spacer(Modifier.height(8.dp))
            CollapsibleCard("Graph Statistics", statsExpanded, { statsExpanded = !statsExpanded }, Modifier.padding(horizontal = 16.dp)) {
                GraphStatsContent(graph)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Collapsible card ──────────────────────────────────────────────────────────

@Composable
private fun CollapsibleCard(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }
            AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = content)
            }
        }
    }
}

// ── Transition sequence ───────────────────────────────────────────────────────

@Composable
private fun TransitionSequenceContent(graph: PoseGraph) {
    val sequence = graph.temporalSequence
    if (sequence.isEmpty()) {
        Text("No temporal sequence stored in graph metadata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val chunks = sequence.chunked(8)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Temporal order (${sequence.size} steps, ${sequence.distinct().size} unique states):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        chunks.forEachIndexed { idx, chunk ->
            val prefix = if (idx > 0) "↳  " else ""
            val suffix = if (idx < chunks.lastIndex) " →" else ""
            Text(prefix + chunk.joinToString(" → ") { "S$it" } + suffix, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Text("Edges:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        graph.transitions.entries.sortedBy { it.key }.forEach { (fromId, toIds) ->
            StatRow("S$fromId →", toIds.joinToString(", ") { "S$it" })
        }
    }
}

// ── Graph statistics ──────────────────────────────────────────────────────────

@Composable
private fun GraphStatsContent(graph: PoseGraph) {
    val stats         = remember(graph) { graph.getStatistics() }
    val holdDurations = graph.states.map { it.minHoldDuration }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StatRow("States",      stats.numStates.toString())
        StatRow("Transitions", stats.numTransitions.toString())
        StatRow("Graph type",  if (stats.isLinear) "Linear" else "Non-Linear")
        StatRow("Has cycles",  if (stats.hasCycles) "Yes" else "No")
        if (holdDurations.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Hold Durations", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatRow("Min",   "${"%.2f".format(holdDurations.min())}s")
            StatRow("Max",   "${"%.2f".format(holdDurations.max())}s")
            StatRow("Mean",  "${"%.2f".format(holdDurations.average())}s")
            StatRow("Total", "${"%.2f".format(holdDurations.sum())}s")
        }
        Spacer(Modifier.height(6.dp))
        StatRow("States with keypoints", "${graph.states.count { it.meanKeypoints != null }} / ${graph.states.size}")
    }
}

// ── Edge drawing ──────────────────────────────────────────────────────────────

private fun DrawScope.drawDirectedEdge(from: Offset, to: Offset, nodeRadius: Float) {
    if (from == to) return
    val dx = to.x - from.x; val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6f)
    val nx = dx / len; val ny = dy / len

    val edgeStart = Offset(from.x + nx * nodeRadius, from.y + ny * nodeRadius)
    val edgeEnd   = Offset(to.x   - nx * nodeRadius, to.y   - ny * nodeRadius)
    val curveOffset = 0.08f * len
    val control = Offset((edgeStart.x + edgeEnd.x) / 2f - ny * curveOffset, (edgeStart.y + edgeEnd.y) / 2f + nx * curveOffset)

    val path = Path().apply { moveTo(edgeStart.x, edgeStart.y); quadraticBezierTo(control.x, control.y, edgeEnd.x, edgeEnd.y) }
    drawPath(path, COLOR_EDGE.copy(alpha = 0.7f), style = Stroke(2.5f, cap = StrokeCap.Round))

    val arrowLen = nodeRadius * 0.4f; val arrowWidth = nodeRadius * 0.2f
    val tLen = sqrt((edgeEnd.x - control.x).pow(2) + (edgeEnd.y - control.y).pow(2)).coerceAtLeast(1e-6f)
    val tx = (edgeEnd.x - control.x) / tLen; val ty = (edgeEnd.y - control.y) / tLen
    val arrowBase = Offset(edgeEnd.x - tx * arrowLen, edgeEnd.y - ty * arrowLen)
    val arrowPath = Path().apply {
        moveTo(edgeEnd.x, edgeEnd.y)
        lineTo(arrowBase.x + ty * arrowWidth, arrowBase.y - tx * arrowWidth)
        lineTo(arrowBase.x - ty * arrowWidth, arrowBase.y + tx * arrowWidth)
        close()
    }
    drawPath(arrowPath, COLOR_EDGE)
}

// ── Stat row ──────────────────────────────────────────────────────────────────

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
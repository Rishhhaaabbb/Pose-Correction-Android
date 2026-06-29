// presentation/src/main/java/com/presentation/graphviz/PoseGraphVisualizationScreen.kt

package com.presentation.graphviz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.core.poseengine.PoseGraph
import com.presentation.graphviz.PoseGraphLayout.Algorithm
import com.presentation.graphviz.SkeletonNormalizer.isValidForDrawing
import kotlin.math.*

private val COLOR_BACKGROUND  = Color(0xFFFDF7F2) // BgCream
private val COLOR_NODE_FILL   = Color.White
private val COLOR_NODE_BORDER = Color(0xFF2D5A5E) // TealDark
private val COLOR_EDGE        = Color(0xFF2D5A5E) // TealDark
private val COLOR_LABEL_BG    = Color.White
private val COLOR_ORANGE      = Color(0xFFE8622E)
private val COLOR_TEAL_DARK   = Color(0xFF2D5A5E)

private const val NODE_RADIUS_FRACTION  = 0.18f
private const val LABEL_OFFSET_FRACTION = 1.5f
private const val ZOOM_MIN = 0.5f
private const val ZOOM_MAX = 5.0f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoseGraphVisualizationScreen(graph: PoseGraph) {
    var selectedLayout by remember { mutableStateOf(Algorithm.CIRCULAR) }
    var scale   by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var statsExpanded       by remember { mutableStateOf(true) }
    var transitionsExpanded by remember { mutableStateOf(true) }
    var isFullScreen        by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(COLOR_BACKGROUND)) {

            // ── Fixed Top Header ─────────────────────────────────────────────────
            Surface(shadowElevation = 1.dp, color = COLOR_BACKGROUND) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "Pose State Transition Graph",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = COLOR_TEAL_DARK
                    )
                    Text(
                        text = "States: ${graph.states.size}  •  Transitions: ${graph.transitions.values.sumOf { it.size }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = COLOR_TEAL_DARK.copy(alpha = 0.7f),
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Layout Algorithm Chips - Scrollable Single Line
                    Text("Algorithm:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = COLOR_TEAL_DARK)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Algorithm.entries.forEach { algo ->
                            FilterChip(
                                selected = selectedLayout == algo,
                                onClick  = { selectedLayout = algo; scale = 1f; offsetX = 0f; offsetY = 0f },
                                label    = { Text(algo.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                                modifier = Modifier.height(32.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = COLOR_ORANGE,
                                    selectedLabelColor = Color.White,
                                    labelColor = COLOR_TEAL_DARK
                                )
                            )
                        }
                    }
                }
            }

            // ── Scrollable Content Area ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(vertical = 16.dp)
            ) {

                // ── Fixed Graph Canvas (Non-interactive) ──────────────────────────
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    GraphDrawingArea(
                        graph = graph,
                        selectedLayout = selectedLayout,
                        scale = 1f,
                        onScaleChange = { },
                        offsetX = 0f,
                        onOffsetXChange = { },
                        offsetY = 0f,
                        onOffsetYChange = { },
                        isInteractive = false,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ── Small Full Screen Icon (Top Right) ───────────────────────
                    IconButton(
                        onClick = { isFullScreen = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Screen",
                            tint = COLOR_TEAL_DARK,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Information Sections ──────────────────────────────────────────
                CollapsibleCard("Transition Sequence", transitionsExpanded, { transitionsExpanded = !transitionsExpanded }, Modifier.padding(horizontal = 16.dp)) {
                    TransitionSequenceContent(graph)
                }
                Spacer(Modifier.height(12.dp))
                CollapsibleCard("Graph Statistics", statsExpanded, { statsExpanded = !statsExpanded }, Modifier.padding(horizontal = 16.dp)) {
                    GraphStatsContent(graph)
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }

        // ── Full Screen Interactive Dialog ──────────────────────────────────
        if (isFullScreen) {
            Dialog(
                onDismissRequest = { isFullScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = COLOR_BACKGROUND) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GraphDrawingArea(
                            graph = graph,
                            selectedLayout = selectedLayout,
                            scale = scale,
                            onScaleChange = { scale = it },
                            offsetX = offsetX,
                            onOffsetXChange = { offsetX = it },
                            offsetY = offsetY,
                            onOffsetYChange = { offsetY = it },
                            isInteractive = true,
                            modifier = Modifier.fillMaxSize()
                        )

                        // ── Overlay Controls (Scrollable) ───────────────────────────
                        Surface(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = Color.White.copy(alpha = 0.92f),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Zoom controls
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Zoom: ${"%.1f".format(scale)}×", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = COLOR_TEAL_DARK)
                                    OutlinedIconButton(
                                        onClick = { scale = (scale * 1.2f).coerceAtMost(ZOOM_MAX) },
                                        modifier = Modifier.size(28.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, COLOR_TEAL_DARK)
                                    ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = COLOR_TEAL_DARK) }
                                    
                                    OutlinedIconButton(
                                        onClick = { scale = (scale / 1.2f).coerceAtLeast(ZOOM_MIN) },
                                        modifier = Modifier.size(28.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, COLOR_TEAL_DARK)
                                    ) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp), tint = COLOR_TEAL_DARK) }
                                }

                                Button(
                                    onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(28.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = COLOR_ORANGE)
                                ) {
                                    Text("Reset View", fontSize = 10.sp)
                                }

                                Spacer(Modifier.weight(1f))

                                IconButton(
                                    onClick = { isFullScreen = false },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FullscreenExit,
                                        contentDescription = "Exit Full Screen",
                                        tint = COLOR_TEAL_DARK,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphDrawingArea(
    graph: PoseGraph,
    selectedLayout: Algorithm,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    offsetX: Float,
    onOffsetXChange: (Float) -> Unit,
    offsetY: Float,
    onOffsetYChange: (Float) -> Unit,
    isInteractive: Boolean,
    modifier: Modifier = Modifier
) {
    // ── GESTURE FIX: Use rememberUpdatedState to avoid capturing stale values ──
    val currentScale by rememberUpdatedState(scale)
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnOffsetXChange by rememberUpdatedState(onOffsetXChange)
    val currentOnOffsetYChange by rememberUpdatedState(onOffsetYChange)

    BoxWithConstraints(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, COLOR_TEAL_DARK.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .pointerInput(isInteractive) {
                if (isInteractive) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        currentOnScaleChange((currentScale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX))
                        currentOnOffsetXChange(currentOffsetX + pan.x)
                        currentOnOffsetYChange(currentOffsetY + pan.y)
                    }
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
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        ) {
            val centerX = size.width  / 2f
            val centerY = size.height / 2f
            val scaleFactor = minOf(size.width, size.height) * 0.35f

            fun normalizedToCanvas(pos: Offset) = Offset(centerX + pos.x * scaleFactor, centerY + pos.y * scaleFactor)

            for ((fromId, toIds) in graph.transitions) {
                for (toId in toIds) {
                    val fromPos = positions[fromId] ?: continue
                    val toPos   = positions[toId]   ?: continue
                    drawDirectedEdge(normalizedToCanvas(fromPos), normalizedToCanvas(toPos), nodeRadius)
                }
            }

            for (state in graph.states) {
                val normPos   = positions[state.stateId] ?: continue
                val canvasPos = normalizedToCanvas(normPos)
                val skeleton  = normalizedSkeletons[state.stateId]

                drawCircle(COLOR_NODE_FILL,   nodeRadius, canvasPos)
                drawCircle(COLOR_NODE_BORDER, nodeRadius, canvasPos, style = Stroke(2.5f))

                if (skeleton != null) {
                    drawAnatomicalFigure(keypoints = skeleton, center = canvasPos, nodeRadius = nodeRadius * 1.1f)
                } else {
                    val label = textMeasurer.measure("S${state.stateId}", TextStyle(fontSize = (nodeRadius * 0.4f).sp, fontWeight = FontWeight.Bold))
                    drawText(label, topLeft = Offset(canvasPos.x - label.size.width / 2f, canvasPos.y - label.size.height / 2f))
                }

                val labelText   = "State ${state.stateId}\n${"%.1f".format(state.minHoldDuration)}s"
                val labelLayout = textMeasurer.measure(AnnotatedString(labelText), TextStyle(fontSize = (nodeRadius * 0.28f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                val labelY  = canvasPos.y + nodeRadius * LABEL_OFFSET_FRACTION
                val labelX  = canvasPos.x - labelLayout.size.width / 2f
                val padding = 6f

                drawRoundRect(COLOR_LABEL_BG, Offset(labelX - padding, labelY - padding), Size(labelLayout.size.width + padding * 2, labelLayout.size.height + padding * 2), CornerRadius(4f))
                drawRoundRect(COLOR_NODE_BORDER, Offset(labelX - padding, labelY - padding), Size(labelLayout.size.width + padding * 2, labelLayout.size.height + padding * 2), CornerRadius(4f), style = Stroke(1.5f))
                drawText(labelLayout, topLeft = Offset(labelX, labelY))
            }
        }
    }
}

// ── Collapsible card ──────────────────────────────────────────────────────────

@Composable
private fun CollapsibleCard(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = COLOR_TEAL_DARK
                )
                IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = COLOR_TEAL_DARK
                    )
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

    val path = Path().apply { moveTo(edgeStart.x, edgeStart.y); quadraticTo(control.x, control.y, edgeEnd.x, edgeEnd.y) }
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

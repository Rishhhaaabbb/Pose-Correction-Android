// app/src/main/java/com/posemonitor2/GraphGenerationActivity.kt

package com.posemonitor2

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.core.poseengine.PoseGraph
import com.domain.generator.GeneratorConfig
import com.presentation.graphviz.PoseGraphVisualizationScreen
import java.io.File

class GraphGenerationActivity : ComponentActivity() {

    private val viewModel: GraphGenerationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                GraphGenerationScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun GraphGenerationScreen(viewModel: GraphGenerationViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingVideoUri by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) pendingVideoUri = uri.toString()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF1F2)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            uiState !is GenerationUiState.Idle -> {
                when (val state = uiState) {
                    is GenerationUiState.Processing -> {
                        Box(modifier = Modifier.padding(24.dp)) {
                            ProcessingContent(state)
                        }
                    }
                    is GenerationUiState.Success -> {
                        SuccessContent(
                            state = state,
                            onGenerateAnother = { pendingVideoUri = null; viewModel.reset() }
                        )
                    }
                    is GenerationUiState.Error -> {
                        Box(modifier = Modifier.padding(24.dp)) {
                            ErrorContent(
                                state = state,
                                onRetry = { pendingVideoUri = null; viewModel.reset() }
                            )
                        }
                    }
                    else -> {}
                }
            }

            pendingVideoUri != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    SettingsContent(
                        videoUri = pendingVideoUri!!,
                        onConfirm = { config, graphName ->
                            viewModel.startGeneration(
                                videoUri  = pendingVideoUri!!,
                                config    = config,
                                graphName = graphName,
                            )
                        },
                        onBack = { pendingVideoUri = null },
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.padding(24.dp)) {
                    IdleContent(onPickVideo = { videoPicker.launch("video/*") })
                }
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onPickVideo: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Generate Pose Graph", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D3E40))
        Text(
            "Select a video of the exercise to generate\na pose graph for real-time monitoring.",
            fontSize = 15.sp, color = Color(0xFF8B959E), textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPickVideo,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E6B6C)),
        ) {
            Text("Choose Video", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

@Composable
private fun SettingsContent(
    videoUri: String,
    onConfirm: (GeneratorConfig, String) -> Unit,
    onBack: () -> Unit,
) {
    val defaults = GeneratorConfig.DEFAULT
    var graphName           by remember { mutableStateOf("") }
    var stabilityThreshold  by remember { mutableStateOf(defaults.stabilityThreshold.toString()) }
    var minHoldFrames       by remember { mutableStateOf(defaults.minHoldFrames.toString()) }
    var skipFrames          by remember { mutableStateOf(defaults.skipFrames.toString()) }
    var smoothingWindow     by remember { mutableStateOf(defaults.smoothingWindow.toString()) }
    var transitionBuffer    by remember { mutableStateOf(defaults.transitionBuffer.toString()) }
    var toleranceMultiplier by remember { mutableStateOf(defaults.toleranceMultiplier.toString()) }
    var confidenceThreshold by remember { mutableStateOf(defaults.confidenceThreshold.toString()) }
    var validationError     by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF2F4F5),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF075246),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                "Generation Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF064E43),
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingCardField("Video ID", Uri.parse(videoUri).lastPathSegment ?: videoUri, enabled = false)

            ParamField("Stability Threshold", "Max feature distance to stay in same segment\n(default: 0.12)",
                stabilityThreshold, { stabilityThreshold = it }, KeyboardType.Decimal)
            ParamField("Min Hold Frames", "Minimum frames a pose must be held (default: 18)",
                minHoldFrames, { minHoldFrames = it }, KeyboardType.Number)
            ParamField("Skip Frames", "Process every Nth frame - 1 = all frames (default: 1)",
                skipFrames, { skipFrames = it }, KeyboardType.Number)
            ParamField("Smoothing Window", "Rolling average window size (default: 7)",
                smoothingWindow, { smoothingWindow = it }, KeyboardType.Number)
            ParamField("Transition Buffer", "Frames skipped at segment boundaries (default: 3)",
                transitionBuffer, { transitionBuffer = it }, KeyboardType.Number)
            ParamField("Tolerance Multiplier", "Feature tolerance = std x this value (default: 2.0)",
                toleranceMultiplier, { toleranceMultiplier = it }, KeyboardType.Decimal)
            ParamField("Confidence Threshold", "Min keypoint confidence 0.0-1.0 (default: 0.6)",
                confidenceThreshold, { confidenceThreshold = it }, KeyboardType.Decimal)

            if (validationError != null) {
                Text(validationError!!, fontSize = 13.sp, color = Color(0xFFE74C3C))
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val config = buildConfig(
                        stabilityThreshold, minHoldFrames, skipFrames,
                        smoothingWindow, transitionBuffer, toleranceMultiplier, confidenceThreshold,
                    )
                    if (config == null) {
                        validationError = "Please check all fields — values must be valid numbers within range."
                    } else {
                        validationError = null
                        onConfirm(config, graphName.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = RoundedCornerShape(38.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006A67)),
            ) {
                Text("Start Generation", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ── Processing ────────────────────────────────────────────────────────────────

@Composable
private fun ProcessingContent(state: GenerationUiState.Processing) {
    val progress = state.progressFraction.coerceIn(0f, 1f)
    val progressVisual = progress.coerceAtLeast(0.02f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF1F2)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(136.dp))

            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFFF8F9FA),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 30.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(58.dp))
                            .border(7.dp, Color(0xFFE5E9EF), RoundedCornerShape(58.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = Color(0xFFFF7A4A),
                            modifier = Modifier.size(42.dp),
                        )
                    }

                    Text(
                        state.message,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111A3A),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        state.detail,
                        fontSize = 14.sp,
                        color = Color(0xFF8FA0B7),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "OVERALL PROGRESS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8FA0B7),
                                letterSpacing = 1.sp,
                            )
                            Text(
                                "${"%.0f".format(progress * 100)}%",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE84E1B),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(Color(0xFFE3E8EF), RoundedCornerShape(999.dp)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressVisual)
                                    .background(Color(0xFFFF5B26), RoundedCornerShape(999.dp)),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "Please keep the app open while we\nanalyze your movement.",
                fontSize = 14.sp,
                color = Color(0xFF8FA0B7),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}

// ── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(
    state: GenerationUiState.Success,
    onGenerateAnother: () -> Unit,
) {
    val graph = remember(state.outputPath) {
        runCatching {
            PoseGraph.fromJson(File(state.outputPath).readText(Charsets.UTF_8))
        }.getOrNull()
    }

    var showVisualization by remember { mutableStateOf(false) }

    if (showVisualization && graph != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = { showVisualization = false }) {
                    Text("← Back", color = Color(0xFF1E6B6C), fontSize = 15.sp)
                }
                Text(
                    text = "Graph Visualization",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0D3E40),
                )
            }
            PoseGraphVisualizationScreen(graph = graph)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("✅", fontSize = 56.sp)
            Text("Graph Generated!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D3E40))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatRow("States discovered", "${state.numStates}")
                    StatRow("Valid frames", "${state.numFrames}")
                    StatRow("Saved to", state.outputPath.substringAfterLast("/"))
                }
            }

            Spacer(Modifier.height(4.dp))

            if (graph != null) {
                Button(
                    onClick = { showVisualization = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B959E)),
                ) {
                    Text("View Graph", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }

            Button(
                onClick = onGenerateAnother,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E6B6C)),
            ) {
                Text("Generate Another", fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(state: GenerationUiState.Error, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("❌", fontSize = 56.sp)
        Text("Generation Failed", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D3E40))
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(state.reason.replace("_", " "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE74C3C))
                Spacer(Modifier.height(8.dp))
                Text(state.detail, fontSize = 13.sp, color = Color(0xFF8B959E), lineHeight = 20.sp)
            }
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E6B6C)),
        ) {
            Text("Try Again", fontSize = 15.sp, color = Color.White)
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun ParamField(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFFF7F8F9),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF053F35))
            Text(description, fontSize = 12.sp, color = Color(0xFF8A95A5), lineHeight = 19.sp)
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F3F4), RoundedCornerShape(18.dp)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF053F35),
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF1F3F4),
                    unfocusedContainerColor = Color(0xFFF1F3F4),
                    focusedTextColor = Color(0xFF053F35),
                    unfocusedTextColor = Color(0xFF053F35),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFF006A67),
                ),
            )
        }
    }
}

@Composable
private fun SettingCardField(
    label: String,
    value: String,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF7F8F9),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF677485))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF053F35), fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color(0xFF8B959E))
        Text(value, fontSize = 13.sp, color = Color(0xFF0D3E40), fontWeight = FontWeight.Medium)
    }
}

private fun buildConfig(
    stabilityThreshold: String, minHoldFrames: String, skipFrames: String,
    smoothingWindow: String, transitionBuffer: String, toleranceMultiplier: String,
    confidenceThreshold: String,
): GeneratorConfig? = try {
    GeneratorConfig(
        stabilityThreshold  = stabilityThreshold.toDouble().also  { require(it > 0.0) },
        minHoldFrames       = minHoldFrames.toInt().also           { require(it >= 1) },
        skipFrames          = skipFrames.toInt().also              { require(it >= 1) },
        smoothingWindow     = smoothingWindow.toInt().also         { require(it >= 1) },
        transitionBuffer    = transitionBuffer.toInt().also        { require(it >= 0) },
        toleranceMultiplier = toleranceMultiplier.toDouble().also  { require(it > 0.0) },
        confidenceThreshold = confidenceThreshold.toDouble().also  { require(it in 0.0..1.0) },
    )
} catch (e: Exception) { null }
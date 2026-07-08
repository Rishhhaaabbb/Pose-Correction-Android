// app/src/main/java/com/posemonitor2/GraphGenerationActivity.kt

package com.posemonitor2

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posemonitor2.settings.AppLocaleManager
import com.core.poseengine.PoseGraph
import com.domain.generator.GeneratorConfig
import com.posemonitor2.R
import com.posemonitor2.ui.theme.PoseMonitorAppTheme
import com.presentation.graphviz.PoseGraphVisualizationScreen
import java.io.File

class GraphGenerationActivity : ComponentActivity() {

    private val viewModel: GraphGenerationViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PoseMonitorAppTheme {
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
        if (uri != null) {
            pendingVideoUri = uri.toString()
            // Ensure we're in idle state when video is selected
            viewModel.reset()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Show settings when video is selected and not processing
            pendingVideoUri != null && uiState is GenerationUiState.Idle -> {
                Box(modifier = Modifier.padding(24.dp)) {
                    SettingsContent(
                        videoUri = pendingVideoUri!!,
                        onConfirm = { config, graphName ->
                            viewModel.startGeneration(
                                videoUri  = pendingVideoUri!!,
                                config    = config,
                                graphName = graphName,
                            )
                        },
                        onBack = { 
                            pendingVideoUri = null
                            viewModel.reset()
                        },
                    )
                }
            }

            // Show processing/success/error states
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

            // Show initial idle screen
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
        Text(stringResource(R.string.gg_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            stringResource(R.string.gg_subtitle),
            fontSize = 15.sp, color = Color(0xFF9E9E9E), textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPickVideo,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3), contentColor = Color.White),
        ) {
            Text(stringResource(R.string.gg_choose_video), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

@Composable
fun SettingsContent(
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
    val validationErrorMessage = stringResource(R.string.gg_validation_error)

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.gg_settings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Video: ${Uri.parse(videoUri).lastPathSegment ?: videoUri}",
            fontSize = 13.sp, color = Color(0xFF9E9E9E),
        )

        Spacer(Modifier.height(4.dp))

        // ── Graph name ────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.gg_graph_name), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(
                stringResource(R.string.gg_graph_name_hint),
                fontSize = 12.sp, color = Color(0xFF757575),
            )
            OutlinedTextField(
                value = graphName,
                onValueChange = { graphName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.gg_graph_name_placeholder), color = Color(0xFF555555)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFF424242),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF2196F3),
                ),
                shape = RoundedCornerShape(8.dp),
            )
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        // ── Advanced parameters ───────────────────────────────────────────────
        ParamField("Stability Threshold", "Max feature distance to stay in same segment (default: 0.12)",
            stabilityThreshold, { stabilityThreshold = it }, KeyboardType.Decimal)
        ParamField("Min Hold Frames", "Minimum frames a pose must be held (default: 18)",
            minHoldFrames, { minHoldFrames = it }, KeyboardType.Number)
        ParamField("Skip Frames", "Process every Nth frame — 1 = all frames (default: 1)",
            skipFrames, { skipFrames = it }, KeyboardType.Number)
        ParamField("Smoothing Window", "Rolling average window size (default: 7)",
            smoothingWindow, { smoothingWindow = it }, KeyboardType.Number)
        ParamField("Transition Buffer", "Frames skipped at segment boundaries (default: 3)",
            transitionBuffer, { transitionBuffer = it }, KeyboardType.Number)
        ParamField("Tolerance Multiplier", "Feature tolerance = std × this value (default: 2.0)",
            toleranceMultiplier, { toleranceMultiplier = it }, KeyboardType.Decimal)
        ParamField("Confidence Threshold", "Min keypoint confidence 0.0–1.0 (default: 0.6)",
            confidenceThreshold, { confidenceThreshold = it }, KeyboardType.Decimal)

        if (validationError != null) {
            Text(validationError!!, fontSize = 13.sp, color = Color(0xFFEF5350))
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text(stringResource(R.string.common_back), fontSize = 15.sp, color = Color.White) }

            Button(
                onClick = {
                    val config = buildConfig(
                        stabilityThreshold, minHoldFrames, skipFrames,
                        smoothingWindow, transitionBuffer, toleranceMultiplier, confidenceThreshold,
                    )
                    if (config == null) {
                        validationError = validationErrorMessage
                    } else {
                        validationError = null
                        onConfirm(config, graphName.trim())
                    }
                },
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3), contentColor = Color.White),
            ) { Text(stringResource(R.string.gg_start_generation), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Processing ────────────────────────────────────────────────────────────────

@Composable
private fun ProcessingContent(state: GenerationUiState.Processing) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.size(80.dp),
            color = Color(0xFF2196F3),
            strokeWidth = 6.dp,
        )
        Text(state.message, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(state.detail, fontSize = 13.sp, color = Color(0xFF9E9E9E), textAlign = TextAlign.Center)
        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Color(0xFF2196F3),
            trackColor = Color(0xFF2A2A2A),
        )
        Text("${"%.0f".format(state.progressFraction * 100)}%", fontSize = 13.sp, color = Color(0xFF9E9E9E))
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
    val fileName = remember(state.outputPath) {
        state.outputPath.substringAfterLast("/").substringBeforeLast(".")
    }

    if (showVisualization && graph != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = { showVisualization = false }) {
                    Text(stringResource(R.string.common_back_arrow), color = Color(0xFF2196F3), fontSize = 15.sp)
                }
                Text(
                    text = stringResource(R.string.gg_graph_visualization),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
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
            Text(stringResource(R.string.gg_generated_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1A1A),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatRow(stringResource(R.string.gg_graph_name), fileName)
                    StatRow(stringResource(R.string.gg_states_discovered), "${state.numStates}")
                    StatRow(stringResource(R.string.gg_valid_frames), "${state.numFrames}")
                }
            }

            Spacer(Modifier.height(4.dp))

            // Always show View Graph button
            Button(
                onClick = {
                    if (graph != null) {
                        showVisualization = true
                    }
                },
                enabled = graph != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF37474F),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = Color(0xFF666666)
                ),
            ) {
                Text(stringResource(R.string.gg_view_graph), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onGenerateAnother,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3), contentColor = Color.White),
            ) {
                Text(stringResource(R.string.gg_generate_another), fontSize = 15.sp)
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
        Text(stringResource(R.string.gg_failed_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1A1A), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(state.reason.replace("_", " "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF5350))
                Spacer(Modifier.height(8.dp))
                Text(state.detail, fontSize = 13.sp, color = Color(0xFF9E9E9E), lineHeight = 20.sp)
            }
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242), contentColor = Color.White),
        ) { Text(stringResource(R.string.common_try_again), fontSize = 15.sp) }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Text(description, fontSize = 12.sp, color = Color(0xFF757575))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color(0xFF424242),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF2196F3),
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color(0xFF9E9E9E))
        Text(value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
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
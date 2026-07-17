package com.posemonitor2

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphGenerationScreen(viewModel: GraphGenerationViewModel) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingVideoUri by remember { mutableStateOf<String?>(null) }
    var showVisualization by remember { mutableStateOf(false) }

    BackHandler {
        if (showVisualization) {
            showVisualization = false
        } else {
            activity?.finish()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            pendingVideoUri = uri.toString()
            viewModel.reset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showVisualization) {
                            showVisualization = false
                        } else {
                            activity?.finish()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
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
                                showVisualization = showVisualization,
                                onToggleVisualization = { showVisualization = it },
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

                else -> {
                    Box(modifier = Modifier.padding(24.dp)) {
                        IdleContent(onPickVideo = { videoPicker.launch("video/*") })
                    }
                }
            }
        }
    }
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onPickVideo: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(stringResource(R.string.gg_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = onSurface)
        Text(
            stringResource(R.string.gg_subtitle),
            fontSize = 15.sp, color = onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPickVideo,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

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

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = primary,
        unfocusedBorderColor = outline,
        focusedTextColor = onSurface,
        unfocusedTextColor = onSurface,
        cursorColor = primary,
        focusedPlaceholderColor = onSurfaceVariant.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = onSurfaceVariant.copy(alpha = 0.5f),
    )

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.gg_settings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = onSurface)
        Text(
            "Video: ${Uri.parse(videoUri).lastPathSegment ?: videoUri}",
            fontSize = 13.sp, color = onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.gg_graph_name), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = onSurface)
            Text(
                stringResource(R.string.gg_graph_name_hint),
                fontSize = 12.sp, color = onSurfaceVariant,
            )
            OutlinedTextField(
                value = graphName,
                onValueChange = { graphName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.gg_graph_name_placeholder)) },
                colors = textFieldColors,
                shape = RoundedCornerShape(8.dp),
            )
        }

        HorizontalDivider(color = outline.copy(alpha = 0.3f))

        ParamField("Stability Threshold", "Max feature distance to stay in same segment (default: 0.12)",
            stabilityThreshold, { stabilityThreshold = it }, KeyboardType.Decimal)
        ParamField("Min Hold Frames", "Minimum frames a pose must be held (default: 18)",
            minHoldFrames, { minHoldFrames = it }, KeyboardType.Number)
        ParamField("Skip Frames", "Process every Nth frame \u2014 1 = all frames (default: 1)",
            skipFrames, { skipFrames = it }, KeyboardType.Number)
        ParamField("Smoothing Window", "Rolling average window size (default: 7)",
            smoothingWindow, { smoothingWindow = it }, KeyboardType.Number)
        ParamField("Transition Buffer", "Frames skipped at segment boundaries (default: 3)",
            transitionBuffer, { transitionBuffer = it }, KeyboardType.Number)
        ParamField("Tolerance Multiplier", "Feature tolerance = std \u00d7 this value (default: 2.0)",
            toleranceMultiplier, { toleranceMultiplier = it }, KeyboardType.Decimal)
        ParamField("Confidence Threshold", "Min keypoint confidence 0.0\u20131.0 (default: 0.6)",
            confidenceThreshold, { confidenceThreshold = it }, KeyboardType.Decimal)

        if (validationError != null) {
            Text(validationError!!, fontSize = 13.sp, color = error)
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.common_back), fontSize = 15.sp) }

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
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text(stringResource(R.string.gg_start_generation), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── Processing ────────────────────────────────────────────────────────────────

@Composable
private fun ProcessingContent(state: GenerationUiState.Processing) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.size(80.dp),
            color = primary,
            strokeWidth = 6.dp,
        )
        Text(state.message, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = onSurface)
        Text(state.detail, fontSize = 13.sp, color = onSurfaceVariant, textAlign = TextAlign.Center)
        LinearProgressIndicator(
            progress = { state.progressFraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = primary,
            trackColor = surfaceVariant,
        )
        Text("${"%.0f".format(state.progressFraction * 100)}%", fontSize = 13.sp, color = onSurfaceVariant)
    }
}

// ── Success ───────────────────────────────────────────────────────────────────

@Composable
private fun SuccessContent(
    state: GenerationUiState.Success,
    showVisualization: Boolean,
    onToggleVisualization: (Boolean) -> Unit,
    onGenerateAnother: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    val graph = remember(state.outputPath) {
        runCatching {
            PoseGraph.fromJson(File(state.outputPath).readText(Charsets.UTF_8))
        }.getOrNull()
    }

    val fileName = remember(state.outputPath) {
        state.outputPath.substringAfterLast("/").substringBeforeLast(".")
    }

    if (showVisualization && graph != null) {
        PoseGraphVisualizationScreen(graph = graph)
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("\u2705", fontSize = 56.sp)
            Text(stringResource(R.string.gg_generated_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = onSurface)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = surfaceVariant,
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

            Button(
                onClick = {
                    if (graph != null) {
                        onToggleVisualization(true)
                    }
                },
                enabled = graph != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = surfaceVariant,
                    contentColor = onSurface,
                    disabledContainerColor = surfaceVariant.copy(alpha = 0.5f),
                    disabledContentColor = onSurfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Text(stringResource(R.string.gg_view_graph), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onGenerateAnother,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) {
                Text(stringResource(R.string.gg_generate_another), fontSize = 15.sp)
            }
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(state: GenerationUiState.Error, onRetry: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val error = MaterialTheme.colorScheme.error

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("\u274C", fontSize = 56.sp)
        Text(stringResource(R.string.gg_failed_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = onSurface)
        Surface(shape = RoundedCornerShape(12.dp), color = surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(state.reason.replace("_", " "), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = error)
                Spacer(Modifier.height(8.dp))
                Text(state.detail, fontSize = 13.sp, color = onSurfaceVariant, lineHeight = 20.sp)
            }
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = surfaceVariant, contentColor = onSurface),
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = onSurface)
        Text(description, fontSize = 12.sp, color = onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primary,
                unfocusedBorderColor = outline,
                focusedTextColor = onSurface,
                unfocusedTextColor = onSurface,
                cursorColor = primary,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = onSurfaceVariant)
        Text(value, fontSize = 13.sp, color = onSurface, fontWeight = FontWeight.Medium)
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

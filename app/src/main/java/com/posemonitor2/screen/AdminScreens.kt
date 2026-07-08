package com.posemonitor2.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.posemonitor2.*
import com.core.poseengine.PoseGraph
import com.domain.generator.GeneratorConfig
import com.presentation.graphviz.PoseGraphVisualizationScreen
import java.io.File

@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: GraphGenerationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var capturedVideoFile by remember { mutableStateOf<File?>(null) }

    // Theme-aware colors — use MaterialTheme, never isSystemInDarkTheme()
    val isDark      = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor     = MaterialTheme.colorScheme.background
    val surfaceColor  = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val tealColor   = MaterialTheme.colorScheme.primary
    val mutedColor  = MaterialTheme.colorScheme.onSurfaceVariant
    val actionColor = if (isDark) Color(0xFF4DB6AC) else Color(0xFF0D4A4E)
    val actionOnColor = if (isDark) Color.Black else Color.White

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) pendingVideoUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && capturedVideoFile != null) {
            pendingVideoUri = Uri.fromFile(capturedVideoFile)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is GenerationUiState.Idle -> {
                if (pendingVideoUri == null) {
                    // ── Upload / record picker ────────────────────────────────
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "Admin Video Library",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = tealColor
                        )
                        Text(
                            text = "Upload or record an exercise video to automatically generate a pose graph.",
                            fontSize = 14.sp,
                            color = mutedColor,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { videoPicker.launch("video/*") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = actionColor,
                                contentColor = actionOnColor,
                            )
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Upload Video")
                        }

                        OutlinedButton(
                            onClick = {
                                val file = File(
                                    context.cacheDir,
                                    "captured_video_${System.currentTimeMillis()}.mp4"
                                )
                                capturedVideoFile = file
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, actionColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = actionColor)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Record with Camera")
                        }
                    }
                } else {
                    // ── Confirm graph name before generating ──────────────────
                    var graphName by remember { mutableStateOf("") }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Confirm Graph Details",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                        OutlinedTextField(
                            value = graphName,
                            onValueChange = { graphName = it },
                            label = { Text("Exercise Name (Optional)") },
                            placeholder = { Text("e.g. squat, bird-dog", color = mutedColor) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = onSurfaceColor,
                                unfocusedTextColor = onSurfaceColor,
                                focusedBorderColor = tealColor,
                                unfocusedBorderColor = mutedColor
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.startGeneration(
                                    videoUri = pendingVideoUri.toString(),
                                    config = GeneratorConfig.DEFAULT,
                                    graphName = graphName
                                )
                                pendingVideoUri = null
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tealColor)
                        ) {
                            Text("Generate Graph", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        TextButton(onClick = { pendingVideoUri = null }) {
                            Text("Cancel", color = mutedColor)
                        }
                    }
                }
            }

            is GenerationUiState.Processing -> {
                // ── Progress ──────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { state.progressFraction },
                        color = tealColor,
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 6.dp,
                    )
                    Text(
                        state.message,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = onSurfaceColor
                    )
                    Text(
                        state.detail,
                        fontSize = 13.sp,
                        color = mutedColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            is GenerationUiState.Success -> {
                // ── Success — show name + direct navigation to exercise ───────
                val exerciseName = remember(state.outputPath) {
                    File(state.outputPath).nameWithoutExtension
                        .replace("-", " ")
                        .replaceFirstChar { it.uppercase() }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = surfaceColor,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✅", fontSize = 48.sp)
                        Text(
                            "Graph Generated!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            exerciseName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = tealColor,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "${state.numStates} states  •  ${state.numFrames} frames",
                            fontSize = 13.sp,
                            color = mutedColor
                        )

                        Spacer(Modifier.height(8.dp))

                        // Primary: go straight to the Exercises tab to start working out
                        Button(
                            onClick = {
                                viewModel.reset()
                                navController.navigate(UserRoute.Exercises.route) {
                                    popUpTo(UserRoute.Home.route)
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tealColor)
                        ) {
                            Icon(
                                Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Go to Exercise",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Secondary: stay in the library and generate another
                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, mutedColor)
                        ) {
                            Text("Generate Another", color = mutedColor)
                        }
                    }
                }
            }

            is GenerationUiState.Error -> {
                // ── Error ─────────────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = surfaceColor,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("❌", fontSize = 48.sp)
                        Text(
                            "Generation Failed",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            state.detail,
                            fontSize = 13.sp,
                            color = mutedColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tealColor)
                        ) {
                            Text("Try Again", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GraphsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    var graphs by remember { mutableStateOf(loadAvailableGraphs(context)) }
    var selectedGraph by remember { mutableStateOf<GraphEntry?>(null) }
    var graphPendingDelete by remember { mutableStateOf<GraphEntry?>(null) }

    val isDark         = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor        = MaterialTheme.colorScheme.background
    val surfaceColor   = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val tealColor      = MaterialTheme.colorScheme.primary
    val mutedColor     = MaterialTheme.colorScheme.onSurfaceVariant

    if (selectedGraph != null) {
        val entry = selectedGraph!!
        val graph = remember(entry) {
            runCatching {
                val json = if (entry.isAsset) {
                    context.assets.open(entry.fileName).bufferedReader().readText()
                } else {
                    File(entry.filePath).readText(Charsets.UTF_8)
                }
                PoseGraph.fromJson(json)
            }.getOrNull()
        }

        Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedGraph = null }) {
                    Text("← Back", color = tealColor)
                }
                Text(
                    entry.fileName.removeSuffix(".json"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = onSurfaceColor
                )
            }
            if (graph != null) {
                PoseGraphVisualizationScreen(graph = graph)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load graph", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pose Graphs Library",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealColor
                )

                if (graphPendingDelete != null) {
                    IconButton(
                        onClick = {
                            val file = File(graphPendingDelete!!.filePath)
                            if (file.exists()) {
                                file.delete()
                                graphs = loadAvailableGraphs(context)
                            }
                            graphPendingDelete = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Graph",
                            tint = Color(0xFFE85A2E)
                        )
                    }
                }
            }

            if (graphs.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "No graphs yet. Generate some from the Library tab.",
                        color = mutedColor,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(graphs) { entry ->
                        val isPendingDelete = graphPendingDelete == entry
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isPendingDelete) 2.dp else 0.dp,
                                    color = if (isPendingDelete) Color(0xFFE85A2E) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (graphPendingDelete != null) {
                                            graphPendingDelete = if (isPendingDelete) null else entry
                                        } else {
                                            selectedGraph = entry
                                        }
                                    },
                                    onLongClick = {
                                        if (!entry.isAsset) graphPendingDelete = entry
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPendingDelete)
                                    (if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0))
                                else
                                    surfaceColor
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (entry.isAsset) "📦" else "📊", fontSize = 24.sp)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        entry.fileName.removeSuffix(".json"),
                                        fontWeight = FontWeight.Bold,
                                        color = onSurfaceColor
                                    )
                                    Text(
                                        if (entry.isAsset) "System Provided" else "Generated",
                                        fontSize = 12.sp,
                                        color = mutedColor
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

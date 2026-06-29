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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
            .background(Color(0xFFF2EEE4))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is GenerationUiState.Idle -> {
                if (pendingVideoUri == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "Admin Video Library",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0A3D3F)
                        )
                        Text(
                            text = "Upload or record an exercise video to automatically generate a pose graph.",
                            fontSize = 14.sp,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(horizontal = 32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Button(
                            onClick = { videoPicker.launch("video/*") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D4A4E))
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Upload Video")
                        }

                        OutlinedButton(
                            onClick = {
                                val file = File(context.cacheDir, "captured_video_${System.currentTimeMillis()}.mp4")
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0D4A4E)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0D4A4E))
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose Camera (Record)")
                        }
                    }
                } else {
                    // Automatically start generation with default config or show settings
                    // For "automatic" requirement, we'll just use defaults or a simple name prompt
                    var graphName by remember { mutableStateOf("") }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Confirm Graph Details", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = graphName,
                            onValueChange = { graphName = it },
                            label = { Text("Graph Name (Optional)") },
                            modifier = Modifier.fillMaxWidth()
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Generate Graph Automatically")
                        }
                        TextButton(onClick = { pendingVideoUri = null }) {
                            Text("Cancel")
                        }
                    }
                }
            }
            is GenerationUiState.Processing -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { state.progressFraction })
                    Spacer(Modifier.height(16.dp))
                    Text(state.message, fontWeight = FontWeight.Bold)
                    Text(state.detail, fontSize = 12.sp, color = Color.Gray)
                }
            }
            is GenerationUiState.Success -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅ Success!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text("Graph saved to library.", fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.reset() }) {
                        Text("Done")
                    }
                }
            }
            is GenerationUiState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❌ Error", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    Text(state.detail, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.reset() }) {
                        Text("Try Again")
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

        Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedGraph = null }) {
                    Text("← Back")
                }
                Text(entry.fileName.removeSuffix(".json"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            if (graph != null) {
                PoseGraphVisualizationScreen(graph = graph)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load graph", color = Color.Red)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2EEE4))
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
                    color = Color(0xFF0A3D3F)
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
                            tint = Color(0xFFE85A2E) // Orange
                        )
                    }
                }
            }

            if (graphs.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No graphs yet. Generate some from the Library tab.", color = Color.Gray)
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
                                        if (!entry.isAsset) {
                                            graphPendingDelete = entry
                                        }
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPendingDelete) Color(0xFFFFF3E0) else Color.White
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (entry.isAsset) "📦" else "📊", fontSize = 24.sp)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(entry.fileName.removeSuffix(".json"), fontWeight = FontWeight.Bold)
                                    Text(if (entry.isAsset) "System Provided" else "Generated", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

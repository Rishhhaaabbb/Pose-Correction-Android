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
        if (uri != null) {
            android.util.Log.d("LibraryScreen", "Video selected: $uri")
            viewModel.reset()  // Ensure clean state
            pendingVideoUri = uri
            android.util.Log.d("LibraryScreen", "State reset, pendingVideoUri set")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && capturedVideoFile != null) {
            pendingVideoUri = Uri.fromFile(capturedVideoFile)
        } else {
            capturedVideoFile = null
        }
    }

    var showError by remember { mutableStateOf<String?>(null) }

    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("Camera Error") },
            text = { Text(showError ?: "") },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text("OK")
                }
            }
        )
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
                android.util.Log.d("LibraryScreen", "State is Idle, pendingVideoUri=$pendingVideoUri")
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
                                try {
                                    val file = File(context.cacheDir, "captured_video_${System.currentTimeMillis()}.mp4")
                                    capturedVideoFile = file
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    showError = "Camera launch failed: ${e.message}"
                                }
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
                    // Show full parameter settings
                    android.util.Log.d("LibraryScreen", "Showing SettingsContent")
                    SettingsContent(
                        videoUri = pendingVideoUri.toString(),
                        onConfirm = { config, graphName ->
                            viewModel.startGeneration(
                                videoUri = pendingVideoUri.toString(),
                                config = config,
                                graphName = graphName
                            )
                        },
                        onBack = { 
                            pendingVideoUri = null
                            viewModel.reset()
                        }
                    )
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
                val fileName = remember(state.outputPath) {
                    state.outputPath.substringAfterLast("/").substringBeforeLast(".")
                }
                
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
                                .background(Color(0xFF1A1A1A))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(onClick = { showVisualization = false }) {
                                Text("← Back", color = Color(0xFF2196F3), fontSize = 15.sp)
                            }
                            Text(
                                text = "Graph Visualization",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                        PoseGraphVisualizationScreen(graph = graph)
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✅ Success!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        Text("Graph saved to library.", fontSize = 14.sp)
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF5F5F5),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Filename", fontSize = 13.sp, color = Color.Gray)
                                    Text(fileName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("States", fontSize = 13.sp, color = Color.Gray)
                                    Text("${state.numStates}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Valid Frames", fontSize = 13.sp, color = Color.Gray)
                                    Text("${state.numFrames}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                if (graph != null) {
                                    showVisualization = true
                                }
                            },
                            enabled = graph != null,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF37474F),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFF2A2A2A),
                                disabledContentColor = Color(0xFF666666)
                            ),
                        ) {
                            Text("View Graph", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3),
                                contentColor = Color.White
                            ),
                        ) {
                            Text("Generate Another", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
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
    var graphPendingRename by remember { mutableStateOf<GraphEntry?>(null) }
    
    // Refresh graphs when returning to this screen
    DisposableEffect(Unit) {
        onDispose {
            // Reload graphs when leaving to ensure fresh state on return
            graphs = loadAvailableGraphs(context)
        }
    }
    
    // Rename dialog
    graphPendingRename?.let { entry ->
        var newName by remember { mutableStateOf(entry.fileName.removeSuffix(".json")) }
        
        AlertDialog(
            onDismissRequest = { graphPendingRename = null },
            title = { Text("Rename Graph") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Graph Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty() && !entry.isAsset) {
                            val oldFile = File(entry.filePath)
                            val newFile = File(oldFile.parent, "$trimmed.json")
                            if (oldFile.renameTo(newFile)) {
                                graphs = loadAvailableGraphs(context)
                            }
                        }
                        graphPendingRename = null
                    },
                    enabled = newName.trim().isNotEmpty()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { graphPendingRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Rename button (only for non-assets)
                        if (!graphPendingDelete!!.isAsset) {
                            IconButton(
                                onClick = {
                                    graphPendingRename = graphPendingDelete
                                    graphPendingDelete = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add, // Using Add as placeholder for rename/edit
                                    contentDescription = "Rename Graph",
                                    tint = Color(0xFF2196F3)
                                )
                            }
                        }
                        
                        // Delete button
                        IconButton(
                            onClick = {
                                val file = File(graphPendingDelete!!.filePath)
                                if (file.exists()) {
                                    file.delete()
                                }
                                graphs = loadAvailableGraphs(context)
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

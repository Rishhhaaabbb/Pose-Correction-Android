// app/src/main/java/com/posemonitor2/MainActivity.kt

package com.posemonitor2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: DebugViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Permission denied — handled in UI
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permission upfront so it's ready before graph selection
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                MainNavigation(viewModel = viewModel, activity = this)
            }
        }
    }
}

// ── Navigation state ──────────────────────────────────────────────────────────

private enum class Screen { GRAPH_SELECT, MONITOR }

@Composable
private fun MainNavigation(viewModel: DebugViewModel, activity: MainActivity) {
    var screen by remember { mutableStateOf(Screen.GRAPH_SELECT) }
    var selectedGraph by remember { mutableStateOf<GraphEntry?>(null) }

    when (screen) {
        Screen.GRAPH_SELECT -> {
            val context = LocalContext.current
            GraphSelectorScreen(
                context = context,
                onGraphSelected = { entry ->
                    selectedGraph = entry
                    // Check permission before starting camera
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        screen = Screen.MONITOR
                    } else {
                        // Show error — user hasn't granted camera permission
                        // The launcher already requested it in onCreate
                        screen = Screen.MONITOR  // will show initError from ViewModel
                    }
                },
            )
        }

        Screen.MONITOR -> {
            val graph = selectedGraph
            if (graph != null) {
                // Start pipeline once when screen first appears
                LaunchedEffect(graph) {
                    viewModel.startPipeline(activity, graph)
                }
                MonitorScreen(
                    viewModel = viewModel,
                    onBack = {
                        screen = Screen.GRAPH_SELECT
                    },
                )
            }
        }
    }
}

// ── Monitor screen ────────────────────────────────────────────────────────────

@Composable
fun MonitorScreen(viewModel: DebugViewModel, onBack: () -> Unit) {
    val skeleton         by viewModel.currentSkeleton.collectAsStateWithLifecycle()
    val frameStatus      by viewModel.frameStatus.collectAsStateWithLifecycle()
    val targetState      by viewModel.targetState.collectAsStateWithLifecycle()
    val guidancePreview  by viewModel.guidancePreviewState.collectAsStateWithLifecycle()
    val deviations       by viewModel.lastDeviations.collectAsStateWithLifecycle()
    val initError        by viewModel.initError.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also {
                    viewModel.detector.setSurfaceProvider(it.surfaceProvider)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Monitoring overlay ────────────────────────────────────────────────
        MonitorOverlay(
            skeleton = skeleton,
            frameStatus = frameStatus,
            targetState = targetState,
            guidancePreviewState = guidancePreview,
            deviations = deviations,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Top-left back button ──────────────────────────────────────────────
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xCCFFFFFF)),
        ) {
            Text("← Back", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        // ── Skip button — bottom right ────────────────────────────────────────
        Button(
            onClick = { viewModel.skipCurrentPose() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xCC424242),
                contentColor = Color(0xFF90CAF9),
            ),
        ) {
            Text("SKIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        // ── Init error banner ─────────────────────────────────────────────────
        initError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color(0xEEAA0000), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text("⚠️ $error", color = Color(0xFFFF8A80), fontSize = 13.sp)
            }
        }
    }
}
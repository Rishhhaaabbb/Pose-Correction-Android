// app/src/main/java/com/posemonitor2/MainActivity.kt

package com.posemonitor2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.abs

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
                    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        screen = Screen.MONITOR
                    } else {
                        screen = Screen.MONITOR
                    }
                },
            )
        }

        Screen.MONITOR -> {
            val graph = selectedGraph
            if (graph != null) {
                LaunchedEffect(graph) {
                    viewModel.startPipeline(activity, graph)
                }
                MonitorScreen(
                    viewModel = viewModel,
                    onBack = { screen = Screen.GRAPH_SELECT },
                )
            }
        }
    }
}

// ── Monitor screen ────────────────────────────────────────────────────────────

@Composable
fun MonitorScreen(viewModel: DebugViewModel, onBack: () -> Unit) {
    val context = LocalContext.current

    val skeleton        by viewModel.currentSkeleton.collectAsStateWithLifecycle()
    val frameStatus     by viewModel.frameStatus.collectAsStateWithLifecycle()
    val targetState     by viewModel.targetState.collectAsStateWithLifecycle()
    val guidancePreview by viewModel.guidancePreviewState.collectAsStateWithLifecycle()
    val deviations      by viewModel.lastDeviations.collectAsStateWithLifecycle()
    val initError       by viewModel.initError.collectAsStateWithLifecycle()
    val rawFrameSize    by viewModel.cameraFrameSize.collectAsStateWithLifecycle()
    val cameraFacing    by viewModel.cameraFacing.collectAsStateWithLifecycle()
    val zoomRatio       by viewModel.zoomRatio.collectAsStateWithLifecycle()
    val zoomRange       by viewModel.zoomRange.collectAsStateWithLifecycle()
    val speechPreferences by viewModel.speechPreferences.collectAsStateWithLifecycle()

    val speechCoach = remember(context) { MonitorSpeechCoach(context) }

    DisposableEffect(speechCoach) {
        onDispose { speechCoach.shutdown() }
    }

    LaunchedEffect(Unit) {
        speechCoach.applyPreferences(speechPreferences)
        speechCoach.announceSessionStarted(cameraFacing)
    }

    LaunchedEffect(speechPreferences) {
        speechCoach.applyPreferences(speechPreferences)
    }

    LaunchedEffect(cameraFacing) {
        speechCoach.announceCameraFacing(cameraFacing)
    }

    LaunchedEffect(
        frameStatus?.feedback,
        frameStatus?.phase,
        frameStatus?.stateId,
        frameStatus?.repsCompleted,
        guidancePreview?.stateId,
    ) {
        val status = frameStatus ?: return@LaunchedEffect
        speechCoach.onFrameStatus(status, guidancePreview?.stateId)
    }

    // Convert android.util.Size → compose Size for the overlay crop rect math.
    // Defaults to Size.Zero until the first frame arrives — overlay falls back
    // to direct mapping in that case.
    val cameraFrameSize = rawFrameSize?.let {
        Size(it.width.toFloat(), it.height.toFloat())
    } ?: Size.Zero

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ────────────────────────────────────────────────────
        // FIT_CENTER: the full camera frame is scaled to fit the screen with
        // letterboxing (black bars) rather than cropping.
        //
        // WHY THIS MATTERS FOR SKELETON ALIGNMENT:
        // MediaPipe returns keypoints in [0,1] relative to the camera frame.
        // The overlay Canvas maps those coords to screen pixels assuming the
        // camera frame fills the entire screen. When PreviewView uses FILL_CENTER
        // (the default), it crops the camera frame to fill the screen — so the
        // rendered image and the keypoint coordinate space are different rectangles.
        // This causes the skeleton to appear shifted/scaled relative to the body.
        //
        // FIT_CENTER makes them the same rectangle: the camera frame is scaled
        // uniformly to fit inside the screen, and the overlay Canvas covers the
        // same area. Keypoint [0,1] → screen pixel mapping is now 1:1 correct.
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    // FILL_CENTER: camera fills screen edge-to-edge, no black bars.
                    // The overlay compensates for the crop using cameraFrameSize
                    // to remap keypoints into the visible camera rect precisely.
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                    viewModel.detector.setSurfaceProvider(previewView.surfaceProvider)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraFacing, zoomRange) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            viewModel.onPinchZoom(zoom)
                        }
                    }
                },
        )

        // ── Monitoring overlay ────────────────────────────────────────────────
        MonitorOverlay(
            skeleton = skeleton,
            frameStatus = frameStatus,
            targetState = targetState,
            guidancePreviewState = guidancePreview,
            deviations = deviations,
            cameraFrameSize = cameraFrameSize,
            mirrorHorizontally = cameraFacing == CameraSelector.LENS_FACING_FRONT,
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

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { viewModel.toggleCameraFacing() },
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xCC1E1E1E),
                    contentColor = Color(0xFF81D4FA),
                ),
            ) {
                val cameraLabel = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                    "CAM: BACK"
                } else {
                    "CAM: FRONT"
                }
                Text(cameraLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // ── Skip button — bottom right ────────────────────────────────────
            Button(
                onClick = { viewModel.skipCurrentPose() },
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xCC424242),
                    contentColor = Color(0xFF90CAF9),
                ),
            ) {
                Text("SKIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        val zoomPresets = remember(zoomRange) {
            listOf(0.6f, 1.0f, 1.5f)
                .map { it.coerceIn(zoomRange.start, zoomRange.endInclusive) }
                .distinctBy { (it * 20f).toInt() }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                zoomPresets.forEach { preset ->
                    val selected = abs(zoomRatio - preset) < 0.08f
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setZoomPreset(preset) },
                        label = {
                            Text(
                                text = formatZoomLabel(preset),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xCC2196F3),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xCC212121),
                            labelColor = Color(0xFFE0E0E0),
                        ),
                    )
                }
            }

            Text(
                text = "Zoom ${formatZoomLabel(zoomRatio)}  |  Pinch ${formatZoomLabel(zoomRange.start)}-${formatZoomLabel(zoomRange.endInclusive)}",
                color = Color(0xCCEEEEEE),
                fontSize = 10.sp,
            )
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

private fun formatZoomLabel(ratio: Float): String {
    return if (abs(ratio - ratio.toInt()) < 0.05f) {
        "${ratio.toInt()}x"
    } else {
        String.format(Locale.US, "%.1fx", ratio)
    }
}
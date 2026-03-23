// app/src/main/java/com/posemonitor2/HomeActivity.kt

package com.posemonitor2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.poseengine.PoseGraph
import com.presentation.graphviz.PoseGraphVisualizationScreen
import java.io.File

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                HomeNav()
            }
        }
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────

private sealed class HomeScreen {
    object Home : HomeScreen()
    object GraphList : HomeScreen()
    data class GraphView(val entry: GraphEntry) : HomeScreen()
}

@Composable
private fun HomeNav() {
    var screen by remember { mutableStateOf<HomeScreen>(HomeScreen.Home) }

    when (val s = screen) {
        is HomeScreen.Home -> HomeScreen(
            onViewGraphs = { screen = HomeScreen.GraphList },
        )
        is HomeScreen.GraphList -> GraphListScreen(
            onBack = { screen = HomeScreen.Home },
            onViewGraph = { entry -> screen = HomeScreen.GraphView(entry) },
        )
        is HomeScreen.GraphView -> GraphViewScreen(
            entry = s.entry,
            onBack = { screen = HomeScreen.GraphList },
        )
    }
}

// ── Home ──────────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(onViewGraphs: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F3))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Text(
                text = "PoseMonitor",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0D3E40),
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = "Real-time exercise pose analysis",
                fontSize = 15.sp,
                color = Color(0xFF667085),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            HomeCard(
                title = "Generate Graph",
                description = "Upload an exercise video to create a\ndetailed page graph for form analysis.",
                buttonLabel = "Choose Video",
                buttonIcon = Icons.Outlined.CloudUpload,
                buttonColor = Color(0xFFFFC107),
                buttonTextColor = Color(0xFF075246),
                borderColor = Color(0xFFFFC107),
                onClick = {
                    context.startActivity(
                        Intent(context, GraphGenerationActivity::class.java)
                    )
                }
            )

            HomeCard(
                title = "Start Monitoring",
                description = "Use your camera to monitor your exercise\nform in real time with AI guidance.",
                buttonLabel = "Open Camera",
                buttonIcon = Icons.Outlined.CameraAlt,
                buttonColor = Color(0xFF025B50),
                buttonTextColor = Color.White,
                borderColor = Color(0xFF00695C),
                onClick = {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                    )
                }
            )

            HomeCard(
                title = "View Graphs",
                description = "Browse and inspect your previously\ngenerated pose graphs and progress.",
                buttonLabel = "View Graphs",
                buttonIcon = Icons.Outlined.BarChart,
                buttonColor = Color(0xFFFE4E1A),
                buttonTextColor = Color.White,
                borderColor = Color(0xFFFE4E1A),
                onClick = onViewGraphs,
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ── Graph list ────────────────────────────────────────────────────────────────

@Composable
private fun GraphListScreen(
    onBack: () -> Unit,
    onViewGraph: (GraphEntry) -> Unit,
) {
    val context = LocalContext.current
    val graphs = remember { loadAvailableGraphs(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF1F2)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF2F4F5),
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.TopStart),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF075246),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Pose Graphs",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF064E43),
                )
                Text(
                    "Select a pose graph to monitor",
                    fontSize = 14.sp,
                    color = Color(0xFF5F6368),
                )
            }
        }

        HorizontalDivider(
            color = Color(0xFFE5E7EB),
            thickness = 1.dp,
        )

        if (graphs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "No graphs yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0D3E40),
                    )
                    Text(
                        "Generate a graph first from\nthe home screen.",
                        fontSize = 14.sp,
                        color = Color(0xFF8B959E),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(graphs) { entry ->
                    GraphBrowseCard(entry = entry, onClick = { onViewGraph(entry) })
                }
                item {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun GraphBrowseCard(entry: GraphEntry, onClick: () -> Unit) {
    val normalizedName = entry.fileName.removeSuffix(".json").lowercase()
    val (iconBgColor, iconTintColor, icon) = when (normalizedName) {
        "side-touch", "side_touch", "arm-extension", "arm_extension" -> Triple(
            Color(0xFFFFE8D6),
            Color(0xFFFF5B26),
            Icons.Outlined.Inventory2,
        )
        "squat-analysis", "squat_analysis", "core-balance", "core_balance" -> Triple(
            Color(0xFFFFF4CC),
            Color(0xFFF5B400),
            Icons.Outlined.BarChart,
        )
        "lunge-tracker", "lunge_tracker" -> Triple(
            Color(0xFFDDF0EE),
            Color(0xFF0E6A5F),
            Icons.Outlined.Bolt,
        )
        else -> Triple(
            Color(0xFFDDF0EE),
            Color(0xFF0E6A5F),
            Icons.Outlined.Bolt,
        )
    }

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(iconBgColor, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(30.dp),
                )
            }

            Text(
                text = normalizedName.replace('_', '-'),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF064E43),
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFD0D5DD),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

// ── Graph viewer ──────────────────────────────────────────────────────────────

@Composable
private fun GraphViewScreen(entry: GraphEntry, onBack: () -> Unit) {
    val context = LocalContext.current

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

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F1EE))) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color(0xFF1E6B6C), fontSize = 15.sp)
            }
            Text(
                text = entry.fileName.removeSuffix(".json"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0D3E40),
            )
        }

        if (graph != null) {
            PoseGraphVisualizationScreen(graph = graph)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("⚠️", fontSize = 48.sp)
                    Text(
                        "Failed to load graph",
                        fontSize = 16.sp,
                        color = Color(0xFFE74C3C),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        entry.filePath,
                        fontSize = 12.sp,
                        color = Color(0xFF8B959E),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Home card ─────────────────────────────────────────────────────────────────

@Composable
private fun HomeCard(
    title: String,
    description: String,
    buttonLabel: String,
    buttonIcon: ImageVector,
    buttonColor: Color,
    buttonTextColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = borderColor, shape = RoundedCornerShape(30.dp)),
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFFF6F7F8),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 5.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF075246),
                )
                Text(
                    text = description,
                    fontSize = 15.sp,
                    color = Color(0xFF616B7D),
                    lineHeight = 24.sp,
                )
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                ) {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null,
                        tint = buttonTextColor,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = buttonLabel,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = buttonTextColor,
                    )
                }
            }
        }
    }
}
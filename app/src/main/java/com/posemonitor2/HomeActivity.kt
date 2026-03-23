// app/src/main/java/com/posemonitor2/HomeActivity.kt

package com.posemonitor2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
            MaterialTheme(colorScheme = darkColorScheme()) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "PoseMonitor",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Real-time exercise pose analysis",
                fontSize = 15.sp,
                color = Color(0xFF9E9E9E),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            HomeCard(
                title = "Generate Graph",
                description = "Upload an exercise video to create a pose graph",
                buttonLabel = "Choose Video",
                buttonColor = Color(0xFF2196F3),
                onClick = {
                    context.startActivity(
                        Intent(context, GraphGenerationActivity::class.java)
                    )
                }
            )

            HomeCard(
                title = "Start Monitoring",
                description = "Use the camera to monitor your exercise form in real time",
                buttonLabel = "Open Camera",
                buttonColor = Color(0xFF4CAF50),
                onClick = {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                    )
                }
            )

            HomeCard(
                title = "View Graphs",
                description = "Browse and inspect your generated pose graphs",
                buttonLabel = "View Graphs",
                buttonColor = Color(0xFF9C27B0),
                onClick = onViewGraphs,
            )
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
            .background(Color(0xFF0F0F0F))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color(0xFF9E9E9E), fontSize = 14.sp)
            }
            Text(
                "Pose Graphs",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        if (graphs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("📂", fontSize = 48.sp)
                    Text(
                        "No graphs yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        "Generate a graph first from\nthe home screen.",
                        fontSize = 14.sp,
                        color = Color(0xFF9E9E9E),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(graphs) { entry ->
                    GraphBrowseCard(entry = entry, onClick = { onViewGraph(entry) })
                }
            }
        }
    }
}

@Composable
private fun GraphBrowseCard(entry: GraphEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = if (entry.isAsset) "📦" else "📊",
            fontSize = 24.sp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.fileName.removeSuffix(".json"),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = if (entry.isAsset) "Bundled" else "Generated",
                fontSize = 12.sp,
                color = Color(0xFF757575),
            )
        }
        Text("›", fontSize = 20.sp, color = Color(0xFF555555))
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

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = Color(0xFF2196F3), fontSize = 15.sp)
            }
            Text(
                text = entry.fileName.removeSuffix(".json"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
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
                        color = Color(0xFFEF5350),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        entry.filePath,
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
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
    buttonColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF9E9E9E),
                lineHeight = 20.sp,
            )
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            ) {
                Text(text = buttonLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
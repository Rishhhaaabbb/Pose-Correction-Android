// app/src/main/java/com/posemonitor2/GraphSelectorScreen.kt

package com.posemonitor2

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class GraphEntry(
    val fileName: String,       // e.g. "side-touch.json"
    val filePath: String,       // absolute path or "assets:side-touch.json"
    val isAsset: Boolean = false,
)

@Composable
fun GraphSelectorScreen(
    context: Context,
    onGraphSelected: (GraphEntry) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var graphs by remember { mutableStateOf(loadAvailableGraphs(context)) }
    var renameTarget by remember { mutableStateOf<GraphEntry?>(null) }

    // Rename dialog
    renameTarget?.let { target ->
        RenameDialog(
            currentName = target.fileName.removeSuffix(".json"),
            onConfirm = { newName ->
                val trimmed = newName.trim()
                if (trimmed.isNotEmpty() && !target.isAsset) {
                    val oldFile = File(target.filePath)
                    val newFile = File(oldFile.parent, "$trimmed.json")
                    if (oldFile.renameTo(newFile)) {
                        val updatedEntry = target.copy(
                            fileName = newFile.name,
                            filePath = newFile.absolutePath,
                        )
                        graphs = graphs.map { if (it == target) updatedEntry else it }
                    }
                }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF1F2)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Surface(
                color = Color(0xFFF7F8F9),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Surface(
                        onClick = { onBack?.invoke() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF2F4F5),
                        modifier = Modifier
                            .size(44.dp)
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
                            .padding(top = 52.dp, bottom = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Pose Graphs",
                            fontSize = 27.sp,
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
            }

            HorizontalDivider(
                color = Color(0xFFE5E7EB),
                thickness = 1.dp,
            )

            if (graphs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("📂", fontSize = 48.sp)
                        Text(
                            "No exercises available",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0D3E40),
                        )
                        Text(
                            "Generate a graph first using\nthe Graph Generation screen.",
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
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(graphs) { entry ->
                        GraphEntryCard(
                            entry = entry,
                            onClick = { onGraphSelected(entry) },
                            onLongClick = if (!entry.isAsset) {
                                { renameTarget = entry }
                            } else null,
                        )
                    }
                    item {
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

// ── Rename dialog ─────────────────────────────────────────────────────────────

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = Color(0xFF0D3E40),
        textContentColor = Color(0xFF8B959E),
        title = { Text("Rename Exercise", fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Exercise name", color = Color(0xFFC0C0C0)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1E6B6C),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedTextColor = Color(0xFF0D3E40),
                    unfocusedTextColor = Color(0xFF0D3E40),
                    cursorColor = Color(0xFF1E6B6C),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().isNotEmpty(),
            ) {
                Text("Rename", color = Color(0xFF1E6B6C), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8B959E))
            }
        },
    )
}

// ── Graph entry card ──────────────────────────────────────────────────────────

@Composable
private fun GraphEntryCard(
    entry: GraphEntry,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val normalizedName = entry.fileName.removeSuffix(".json").replace('_', '-').lowercase()
    val (iconBgColor, iconTintColor, icon) = when (normalizedName) {
        "side-touch", "arm-extension" -> Triple(
            Color(0xFFFFE8D6),
            Color(0xFFFF5B26),
            Icons.Outlined.Inventory2,
        )
        "squat-analysis", "core-balance", "bicep-curl" -> Triple(
            Color(0xFFFFF4CC),
            Color(0xFFF5B400),
            Icons.Outlined.BarChart,
        )
        "lunge-tracker", "deep-squat" -> Triple(
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
            .height(94.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
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
                    .background(iconBgColor, RoundedCornerShape(19.dp)),
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
                text = normalizedName,
                fontSize = 18.sp,
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

// ── Load available graphs ─────────────────────────────────────────────────────

fun loadAvailableGraphs(context: Context): List<GraphEntry> {
    val result = mutableListOf<GraphEntry>()

    val graphsDir = File(context.filesDir, "graphs")
    if (graphsDir.exists()) {
        graphsDir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { file ->
                result.add(GraphEntry(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    isAsset = false,
                ))
            }
    }

    try {
        context.assets.list("")
            ?.filter { it.endsWith(".json") }
            ?.forEach { name ->
                if (result.none { it.fileName == name }) {
                    result.add(GraphEntry(
                        fileName = name,
                        filePath = "assets:$name",
                        isAsset = true,
                    ))
                }
            }
    } catch (_: Exception) {}

    return result
}
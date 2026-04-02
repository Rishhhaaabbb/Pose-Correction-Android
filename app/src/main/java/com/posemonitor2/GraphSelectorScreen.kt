// app/src/main/java/com/posemonitor2/GraphSelectorScreen.kt

package com.posemonitor2

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val fileName: String,
    val filePath: String,
    val isAsset: Boolean = false,
    val enableGuidance: Boolean = true,
)

@Composable
fun GraphSelectorScreen(
    context: Context,
    onGraphSelected: (GraphEntry) -> Unit,
) {
    var graphs by remember { mutableStateOf(loadAvailableGraphs(context)) }
    var selected by remember { mutableStateOf<GraphEntry?>(null) }
    var renameTarget by remember { mutableStateOf<GraphEntry?>(null) }
    var enableGuidance by remember { mutableStateOf(true) }

    renameTarget?.let { target ->
        RenameDialog(
            currentName = target.fileName.removeSuffix(".json"),
            onConfirm = { newName ->
                val trimmed = newName.trim()
                if (trimmed.isNotEmpty() && !target.isAsset) {
                    val oldFile = File(target.filePath)
                    val newFile = File(oldFile.parent, "$trimmed.json")
                    if (oldFile.renameTo(newFile)) {
                        val updatedEntry = target.copy(fileName = newFile.name, filePath = newFile.absolutePath)
                        graphs = graphs.map { if (it == target) updatedEntry else it }
                        if (selected == target) selected = updatedEntry
                    }
                }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(32.dp))

            Text("Select Exercise", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Choose a pose graph to monitor", fontSize = 14.sp, color = Color(0xFF9E9E9E))

            Spacer(Modifier.height(8.dp))

            if (graphs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📂", fontSize = 48.sp)
                        Text("No graphs found", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(
                            "Generate a graph first using\nthe Graph Generation screen.",
                            fontSize = 14.sp, color = Color(0xFF9E9E9E),
                            textAlign = TextAlign.Center, lineHeight = 20.sp,
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(graphs) { entry ->
                        GraphEntryCard(
                            entry = entry,
                            isSelected = selected == entry,
                            onClick = { selected = entry },
                            onLongClick = if (!entry.isAsset) { { renameTarget = entry } } else null,
                        )
                    }
                }
            }

            // ── Guidance preview toggle ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Show pose preview", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Show next pose before each transition", fontSize = 12.sp, color = Color(0xFF757575))
                }
                Switch(
                    checked = enableGuidance,
                    onCheckedChange = { enableGuidance = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = Color(0xFF2196F3),
                        uncheckedThumbColor = Color(0xFF9E9E9E),
                        uncheckedTrackColor = Color(0xFF444444),
                    ),
                )
            }

            Button(
                onClick = { selected?.let { onGraphSelected(it.copy(enableGuidance = enableGuidance)) } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = Color(0xFF555555),
                ),
            ) {
                Text(
                    text = if (selected != null) "Start Monitoring" else "Select a graph above",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Rename dialog ─────────────────────────────────────────────────────────────

@Composable
private fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCCCCCC),
        title = { Text("Rename Graph", fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Graph name", color = Color(0xFF666666)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2196F3),
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF2196F3),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.trim().isNotEmpty()) {
                Text("Rename", color = Color(0xFF2196F3), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF9E9E9E)) }
        },
    )
}

// ── Graph entry card ──────────────────────────────────────────────────────────

@Composable
private fun GraphEntryCard(entry: GraphEntry, isSelected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val borderColor = if (isSelected) Color(0xFF2196F3) else Color(0xFF2A2A2A)
    val bgColor     = if (isSelected) Color(0xFF0D2A3E) else Color(0xFF1A1A1A)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = if (entry.isAsset) "📦" else "📄", fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.fileName.removeSuffix(".json"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = if (entry.isAsset) "Bundled" else "Generated · hold to rename", fontSize = 12.sp, color = Color(0xFF757575))
        }
        if (isSelected) {
            Box(modifier = Modifier.size(22.dp).background(Color(0xFF2196F3), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(modifier = Modifier.size(22.dp).border(1.5.dp, Color(0xFF555555), RoundedCornerShape(11.dp)))
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
            ?.forEach { file -> result.add(GraphEntry(fileName = file.name, filePath = file.absolutePath, isAsset = false)) }
    }
    try {
        context.assets.list("")
            ?.filter { it.endsWith(".json") }
            ?.forEach { name ->
                if (result.none { it.fileName == name }) {
                    result.add(GraphEntry(fileName = name, filePath = "assets:$name", isAsset = true))
                }
            }
    } catch (_: Exception) {}
    return result
}
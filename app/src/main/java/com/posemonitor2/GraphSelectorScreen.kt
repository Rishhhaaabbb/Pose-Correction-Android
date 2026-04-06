// app/src/main/java/com/posemonitor2/GraphSelectorScreen.kt

package com.posemonitor2

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.core.poseengine.monitor.CoachingMode
import com.core.poseengine.monitor.CoachingProfile
import java.io.File

data class GraphEntry(
    val fileName: String,
    val filePath: String,
    val isAsset: Boolean = false,
    val enableGuidance: Boolean = true,
    val speechLanguage: SpeechLanguageOption = SpeechLanguageOption.ENGLISH,
    val speechSpeedMode: SpeechSpeedMode = SpeechSpeedMode.NORMAL,
    val coachingProfile: CoachingProfile = CoachingProfile.YOGA_GENTLE,
    val coachingMode: CoachingMode = CoachingMode.BALANCED,
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
    var speechLanguage by remember { mutableStateOf(SpeechLanguageOption.ENGLISH) }
    var speechSpeedMode by remember { mutableStateOf(SpeechSpeedMode.NORMAL) }
    var coachingProfile by remember { mutableStateOf(CoachingProfile.YOGA_GENTLE) }
    var coachingMode by remember { mutableStateOf(CoachingMode.BALANCED) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 32.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Select Exercise", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Choose a pose graph to monitor", fontSize = 14.sp, color = Color(0xFF9E9E9E))
            }

            if (graphs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("📂", fontSize = 48.sp)
                            Text("No graphs found", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                "Generate a graph first using\nthe Graph Generation screen.",
                                fontSize = 14.sp,
                                color = Color(0xFF9E9E9E),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Exercises",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFBDBDBD),
                    )
                }
                items(graphs) { entry ->
                    GraphEntryCard(
                        entry = entry,
                        isSelected = selected == entry,
                        onClick = { selected = entry },
                        onLongClick = if (!entry.isAsset) { { renameTarget = entry } } else null,
                    )
                }
            }

            item {
                // ── Guidance preview toggle ────────────────────────────────────
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
            }

            item {
                SettingsChoiceCard(
                    title = "Speech speed",
                    subtitle = "Normal or extra slow for better clarity",
                ) {
                    ChoiceChipRow {
                        ChoiceChip(
                            label = "Normal",
                            selected = speechSpeedMode == SpeechSpeedMode.NORMAL,
                            onClick = { speechSpeedMode = SpeechSpeedMode.NORMAL },
                        )
                        ChoiceChip(
                            label = "Extra slow",
                            selected = speechSpeedMode == SpeechSpeedMode.EXTRA_SLOW,
                            onClick = { speechSpeedMode = SpeechSpeedMode.EXTRA_SLOW },
                        )
                    }
                }
            }

            item {
                SettingsChoiceCard(
                    title = "Speech language",
                    subtitle = "Select the coaching voice language",
                ) {
                    ChoiceChipRow {
                        SpeechLanguageOption.entries.forEach { option ->
                            ChoiceChip(
                                label = option.displayName,
                                selected = speechLanguage == option,
                                onClick = { speechLanguage = option },
                            )
                        }
                    }
                }
            }

            item {
                SettingsChoiceCard(
                    title = "Exercise profile",
                    subtitle = "Gentle yoga guidance or stricter workout guidance",
                ) {
                    ChoiceChipRow {
                        ChoiceChip(
                            label = "Yoga gentle",
                            selected = coachingProfile == CoachingProfile.YOGA_GENTLE,
                            onClick = { coachingProfile = CoachingProfile.YOGA_GENTLE },
                        )
                        ChoiceChip(
                            label = "Workout strict",
                            selected = coachingProfile == CoachingProfile.WORKOUT_STRICT,
                            onClick = { coachingProfile = CoachingProfile.WORKOUT_STRICT },
                        )
                    }
                }
            }

            item {
                SettingsChoiceCard(
                    title = "Coaching intensity",
                    subtitle = "Balanced is calm. Full gives extra body-part corrections.",
                ) {
                    ChoiceChipRow {
                        ChoiceChip(
                            label = "Balanced",
                            selected = coachingMode == CoachingMode.BALANCED,
                            onClick = { coachingMode = CoachingMode.BALANCED },
                        )
                        ChoiceChip(
                            label = "Full coaching",
                            selected = coachingMode == CoachingMode.FULL,
                            onClick = { coachingMode = CoachingMode.FULL },
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                selected?.let {
                    onGraphSelected(
                        it.copy(
                            enableGuidance = enableGuidance,
                            speechLanguage = speechLanguage,
                            speechSpeedMode = speechSpeedMode,
                            coachingProfile = coachingProfile,
                            coachingMode = coachingMode,
                        )
                    )
                }
            },
            enabled = selected != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3),
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor = Color(0xFF555555),
            ),
        ) {
            Text(
                text = if (selected != null) "Start Monitoring" else "Select a graph above",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
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

@Composable
private fun SettingsChoiceCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(subtitle, fontSize = 12.sp, color = Color(0xFF757575), lineHeight = 16.sp)
        content()
    }
}

@Composable
private fun ChoiceChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF2196F3),
            selectedLabelColor = Color.White,
            containerColor = Color(0xFF2A2A2A),
            labelColor = Color(0xFFDDDDDD),
        ),
    )
}
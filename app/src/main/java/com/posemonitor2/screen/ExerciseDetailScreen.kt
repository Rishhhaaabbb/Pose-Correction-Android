// app/src/main/java/com/posemonitor2/screen/ExerciseDetailScreen.kt

package com.posemonitor2.screen

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.core.poseengine.PoseGraph
import com.posemonitor2.MainActivity
import com.posemonitor2.OrientationManager
import com.posemonitor2.R
import com.posemonitor2.component.TutorialDialog

private enum class HoldPreset(val seconds: Double?) {
    DEFAULT(null),
    EIGHT(8.0),
    TEN(10.0),
    TWENTY_ONE(21.0),
}

@Composable
fun ExerciseDetailScreen(
    fileName: String,
    isAsset: Boolean,
    workoutId: String? = null,
    exerciseIndex: Int = 0,
    exerciseList: String? = null,
    category: String? = null,
    difficulty: String? = null,
    targetReps: Int? = null,
    targetRepsMap: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showRepDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var placementConfirmed by rememberSaveable(fileName) { mutableStateOf(false) }
    var holdPreset by rememberSaveable(fileName) { mutableStateOf(HoldPreset.DEFAULT) }
    var holdInputError by rememberSaveable(fileName) { mutableStateOf<String?>(null) }
    var defaultHoldDuration by remember(fileName) { mutableStateOf(2.0) }
    
    // Auto-start flag for custom workout mode
    val isCustomWorkoutMode = targetReps != null
    var hasAutoStarted by rememberSaveable(fileName, targetReps) { mutableStateOf(false) }
    
    val videoAssetPath = fileName.removeSuffix(".json") + ".mp4"
    val displayName = fileName.removeSuffix(".json").replace("-", " ").uppercase()
    val requiredOrientation = remember(fileName) {
        OrientationManager.getRequiredOrientation(fileName, context)
    }
    val orientationLabel = if (requiredOrientation == OrientationManager.ExerciseOrientation.PORTRAIT) {
        "PORTRAIT"
    } else {
        "LANDSCAPE"
    }
    val placementInstructions = remember(requiredOrientation) {
        OrientationManager.getPlacementInstructions(requiredOrientation)
    }

    val selectedHoldDuration = remember(holdPreset) {
        when (holdPreset) {
            HoldPreset.DEFAULT -> null
            HoldPreset.EIGHT -> 8.0
            HoldPreset.TEN -> 10.0
            HoldPreset.TWENTY_ONE -> 21.0
            else -> null
        }
    }

    val recommendedHoldText = remember(fileName) {
        when (fileName.removeSuffix(".json")) {
            "tricep-stretch", "neck-stretch", "shoulder-stretch", "tree", "squat-hold", "child" ->
                "Recommended for this exercise: longer hold (10-21s)."
            "high-knees", "pushup", "lunge", "bridge" ->
                "Recommended for this exercise: shorter hold (default or 8-10s)."
            else -> null
        }
    }

    LaunchedEffect(videoAssetPath) {
        try {
            val retriever = MediaMetadataRetriever()
            val afd = context.assets.openFd("tutorial_videos/$videoAssetPath")
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            thumbnail = retriever.getFrameAtTime(1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            afd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(fileName) {
        defaultHoldDuration = runCatching {
            val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val graph = PoseGraph.fromJson(json)
            val holdValues = graph.states.map { it.minHoldDuration }.sorted()
            when {
                holdValues.isEmpty() -> 2.0
                holdValues.size % 2 == 1 -> holdValues[holdValues.size / 2]
                else -> {
                    val mid = holdValues.size / 2
                    (holdValues[mid - 1] + holdValues[mid]) / 2.0
                }
            }
        }.getOrDefault(2.0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F2628))
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text(stringResource(R.string.exercise_detail_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(48.dp))

        // ── Tutorial Preview Card (Image 1 logic) ────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0x33FFFFFF),
            onClick = {
                if (placementConfirmed) {
                    showTutorialDialog = true
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background Thumbnail
                thumbnail?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Dark overlay for legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color(0xFFE8622E),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "COACH GUIDED",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        tint = Color(0xFF0D3B34)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = "WATCH TUTORIAL",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = displayName,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.exercise_detail_description),
            color = Color(0xAAFFFFFF),
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0x1AFFFFFF),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PHONE PLACEMENT",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x33E8622E),
                    ) {
                        Text(
                            text = orientationLabel,
                            color = Color(0xFFFFB79E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = placementInstructions,
                    color = Color(0xE6FFFFFF),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { placementConfirmed = true },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !placementConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (placementConfirmed) Color(0x3355CC88) else Color(0xFF1E6E60),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (placementConfirmed) "Placement confirmed" else "I switched to $orientationLabel",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0x1AFFFFFF),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HOLD DURATION",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Default from exercise graph: ${"%.1f".format(defaultHoldDuration)}s",
                    color = Color(0xCCFFFFFF),
                    fontSize = 12.sp,
                )

                recommendedHoldText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        color = Color(0xFFFFD6C5),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.wrapContentWidth(Alignment.Start),
                ) {
                    HoldPreset.values().forEach { preset ->
                        val selected = holdPreset == preset
                        FilterChip(
                            selected = selected,
                            onClick = {
                                holdPreset = preset
                                holdInputError = null
                            },
                            label = {
                                val text = when (preset) {
                                    HoldPreset.DEFAULT -> "Default"
                                    HoldPreset.EIGHT -> "8 counts"
                                    HoldPreset.TEN -> "10 counts"
                                    HoldPreset.TWENTY_ONE -> "21 counts"
                                }
                                Text(text, color = if (selected) Color.White else Color(0xFFBDBDBD))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                labelColor = Color(0xFFBDBDBD),
                                selectedContainerColor = Color(0xFFE8622E),
                                selectedLabelColor = Color.White,
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Quick-rep buttons removed per user request.

        Spacer(Modifier.height(16.dp))

        // Keep the manual Start button which opens the rep-count dialog (Default behavior)
        Button(
            onClick = {
                if (!placementConfirmed) {
                    holdInputError = null
                    return@Button
                }
                if (isCustomWorkoutMode) {
                    // Custom workout mode: auto-start with target reps
                    startExerciseSession(
                        context = context,
                        fileName = fileName,
                        isAsset = isAsset,
                        repCount = targetReps!!,
                        workoutId = workoutId,
                        exerciseIndex = exerciseIndex,
                        exerciseList = exerciseList,
                        selectedHoldDuration = selectedHoldDuration,
                        placementConfirmed = placementConfirmed,
                        targetRepsMap = targetRepsMap,
                    )
                } else {
                    // Default/manual flow: show rep dialog
                    showRepDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = placementConfirmed,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E), contentColor = Color.White),
        ) {
            Text(
                text = if (isCustomWorkoutMode) {
                    "START SESSION ($targetReps REPS)"
                } else {
                    stringResource(R.string.exercise_detail_start_session)
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = Color.White
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showTutorialDialog) {
        TutorialDialog(
            exerciseName = displayName,
            videoAssetPath = videoAssetPath,
            onDismiss = { showTutorialDialog = false }
        )
    }

    if (!placementConfirmed) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1B2628)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Switch phone orientation first",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = placementInstructions,
                        color = Color(0xE6FFFFFF),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Button(
                        onClick = { placementConfirmed = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
                    ) {
                        Text("Got it, continue", color = Color.White)
                    }
                }
            }
        }
    }

    if (showRepDialog) {
        RepCountDialog(
            onDismiss = { showRepDialog = false },
            onConfirm = { repCount ->
                showRepDialog = false
                startExerciseSession(
                    context = context,
                    fileName = fileName,
                    isAsset = isAsset,
                    repCount = repCount,
                    workoutId = workoutId,
                    exerciseIndex = exerciseIndex,
                    exerciseList = exerciseList,
                    selectedHoldDuration = selectedHoldDuration,
                    placementConfirmed = placementConfirmed,
                    targetRepsMap = targetRepsMap,
                )
            }
        )
    }
}

private fun startExerciseSession(
    context: android.content.Context,
    fileName: String,
    isAsset: Boolean,
    repCount: Int,
    workoutId: String?,
    exerciseIndex: Int,
    exerciseList: String?,
    selectedHoldDuration: Double?,
    placementConfirmed: Boolean,
    targetRepsMap: String? = null,
) {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("EXTRA_FILE_NAME", fileName)
        putExtra("EXTRA_IS_ASSET", isAsset)
        putExtra("EXTRA_TARGET_REPS", repCount)
        putExtra("EXTRA_LOCK_ORIENTATION", placementConfirmed)
        putExtra("EXTRA_WORKOUT_ID", workoutId)
        putExtra("EXTRA_CURRENT_INDEX", exerciseIndex)
        putExtra("EXTRA_EXERCISE_LIST", exerciseList)
        putExtra("EXTRA_TARGET_REPS_MAP", targetRepsMap)
        selectedHoldDuration?.let { putExtra("EXTRA_HOLD_DURATION_SECONDS", it) }
    }
    context.startActivity(intent)
}

@Composable
private fun RepCountDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var repCount by remember { mutableStateOf("10") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set Rep Count",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D3B34)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "How many reps do you want to do?",
                    fontSize = 14.sp,
                    color = Color(0xFF888888)
                )

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = repCount,
                    onValueChange = {
                        repCount = it.filter { c -> c.isDigit() }
                        isError = false
                    },
                    label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {{ Text("Please enter a valid number") }} else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val count = repCount.toIntOrNull()
                            if (count != null && count > 0) {
                                onConfirm(count)
                            } else {
                                isError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E))
                    ) {
                        Text("Start", color = Color.White)
                    }
                }
            }
        }
    }
}

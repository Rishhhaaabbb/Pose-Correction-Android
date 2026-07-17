package com.posemonitor2.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.posemonitor2.GraphEntry
import com.posemonitor2.MainActivity
import com.posemonitor2.loadAvailableGraphs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutOverviewScreen(
    workoutId: String,
    onBack: () -> Unit,
    viewModel: CustomWorkoutViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val graphs = remember { loadAvailableGraphs(context) }
    val uiState by viewModel.uiState.collectAsState()

    val workout = remember(workoutId, uiState) { viewModel.getWorkoutById(workoutId) }
    val exerciseNames = remember(workoutId, uiState) { viewModel.getCustomWorkoutExerciseNames(workoutId) ?: emptyList() }

    val totalReps = remember(exerciseNames, workoutId) {
        exerciseNames.sumOf { name -> viewModel.getTargetRepsForExercise(workoutId, name) }
    }

    val estimatedMinutes = remember(exerciseNames, totalReps) {
        if (exerciseNames.isEmpty()) 0
        else {
            val repMinutes = kotlin.math.ceil(totalReps / 18.0).toInt()
            val transitionMinutes = (exerciseNames.size - 1) * 1
            (repMinutes + transitionMinutes).coerceAtLeast(1)
        }
    }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val secondary = MaterialTheme.colorScheme.secondary
    val onSecondary = MaterialTheme.colorScheme.onSecondary
    val error = MaterialTheme.colorScheme.error
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = workout?.title ?: "Workout",
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Hero summary card ────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SummaryItem(
                        icon = Icons.Outlined.FitnessCenter,
                        value = "${exerciseNames.size}",
                        label = "Exercises",
                        accentColor = primary,
                    )
                    SummaryItem(
                        icon = Icons.Outlined.FitnessCenter,
                        value = "$totalReps",
                        label = "Total Reps",
                        accentColor = secondary,
                    )
                    SummaryItem(
                        icon = Icons.Outlined.Schedule,
                        value = "${estimatedMinutes}m",
                        label = "Est. Time",
                        accentColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Exercises",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(exerciseNames) { index, name ->
                    val reps = viewModel.getTargetRepsForExercise(workoutId, name)
                    val displayName = name.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                    val hasGraph = graphs.any { it.fileName == "$name.json" }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Step number
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (hasGraph) primary else surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (hasGraph) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = onPrimary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (hasGraph) "$reps reps" else "Graph not available",
                                    fontSize = 12.sp,
                                    color = if (hasGraph) onSurfaceVariant else error,
                                )
                            }

                            if (hasGraph) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = secondary.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        text = "$reps reps",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Start Session button ─────────────────────────────────────
            val allGraphsAvailable = exerciseNames.all { name ->
                graphs.any { it.fileName == "$name.json" }
            }

            Button(
                onClick = {
                    if (exerciseNames.isEmpty() || !allGraphsAvailable) return@Button

                    val listStr = exerciseNames.joinToString(",")
                    val targetRepsMap = exerciseNames.joinToString(",") { name ->
                        val reps = viewModel.getTargetRepsForExercise(workoutId, name)
                        "$name:$reps"
                    }
                    val firstExercise = exerciseNames.first()
                    val firstGraph = graphs.first { it.fileName == "$firstExercise.json" }
                    val firstReps = viewModel.getTargetRepsForExercise(workoutId, firstExercise)

                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("EXTRA_FILE_NAME", firstGraph.fileName)
                        putExtra("EXTRA_IS_ASSET", firstGraph.isAsset)
                        putExtra("EXTRA_TARGET_REPS", firstReps)
                        putExtra("EXTRA_LOCK_ORIENTATION", true)
                        putExtra("EXTRA_WORKOUT_ID", workoutId)
                        putExtra("EXTRA_CURRENT_INDEX", 0)
                        putExtra("EXTRA_EXERCISE_LIST", listStr)
                        putExtra("EXTRA_TARGET_REPS_MAP", targetRepsMap)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary),
                enabled = exerciseNames.isNotEmpty() && allGraphsAvailable,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Start Session",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = onSurface,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = onSurfaceVariant,
        )
    }
}

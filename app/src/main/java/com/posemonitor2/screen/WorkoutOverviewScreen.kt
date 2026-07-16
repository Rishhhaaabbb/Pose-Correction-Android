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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.posemonitor2.GraphEntry
import com.posemonitor2.MainActivity
import com.posemonitor2.loadAvailableGraphs

private val AccentOrange = Color(0xFFE85A2E)

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

    // Reactively derive workout data from ViewModel state (which loads asynchronously)
    val workout = remember(workoutId, uiState) { viewModel.getWorkoutById(workoutId) }
    val exerciseNames = remember(workoutId, uiState) { viewModel.getCustomWorkoutExerciseNames(workoutId) ?: emptyList() }

    val totalReps = remember(exerciseNames, workoutId) {
        exerciseNames.sumOf { name ->
            viewModel.getTargetRepsForExercise(workoutId, name)
        }
    }

    val estimatedMinutes = remember(exerciseNames, totalReps) {
        if (exerciseNames.isEmpty()) 0
        else {
            val repMinutes = kotlin.math.ceil(totalReps / 18.0).toInt()
            val transitionMinutes = (exerciseNames.size - 1) * 1
            (repMinutes + transitionMinutes).coerceAtLeast(1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = workout?.title ?: "Workout",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
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
            Spacer(Modifier.height(8.dp))

            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryChip(label = "Exercises", value = "${exerciseNames.size}")
                SummaryChip(label = "Total Reps", value = "$totalReps")
                SummaryChip(label = "Est. Time", value = "${estimatedMinutes}m")
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Exercises",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(12.dp))

            // Exercise list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(exerciseNames) { index, name ->
                    val reps = viewModel.getTargetRepsForExercise(workoutId, name)
                    val displayName = name.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                    val hasGraph = graphs.any { it.fileName == "$name.json" }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Number badge - centered in circle
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(AccentOrange, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!hasGraph) {
                                    Text(
                                        text = "Graph not available",
                                        fontSize = 11.sp,
                                        color = Color(0xFFCC3333),
                                    )
                                }
                            }

                            Text(
                                text = "$reps reps",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AccentOrange,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Start Session button
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
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                enabled = exerciseNames.isNotEmpty() && allGraphsAvailable,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
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
private fun SummaryChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AccentOrange,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

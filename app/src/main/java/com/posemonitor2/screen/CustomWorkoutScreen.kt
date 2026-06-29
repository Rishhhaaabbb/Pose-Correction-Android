package com.posemonitor2.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.posemonitor2.R
import com.posemonitor2.loadAvailableGraphs
import kotlinx.coroutines.launch

private val HubBg = Color(0xFFF4EFE5)
private val HubDark = Color(0xFF0E3E40)
private val HubOrange = Color(0xFFE85A2E)
private val HubMuted = Color(0xFF7A7A7A)

@Composable
fun CustomWorkoutScreen(
    navController: NavController,
    viewModel: CustomWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val graphs = remember { loadAvailableGraphs(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var deleteTarget by remember { mutableStateOf<CustomWorkoutListItemUi?>(null) }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    Scaffold(
        containerColor = HubBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.CustomWorkout_hub_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = HubDark,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.CustomWorkout_hub_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HubMuted,
                    )
                }
                FilledTonalIconButton(
                    onClick = { navController.navigate(customWorkoutEditorRoute(NEW_WORKOUT_ID)) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.CustomWorkout_create),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = HubOrange)
                    }
                }

                uiState.CustomWorkouts.isEmpty() -> {
                    CustomWorkoutHubEmptyState(
                        onCreate = { navController.navigate(customWorkoutEditorRoute(NEW_WORKOUT_ID)) },
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.CustomWorkouts, key = { it.id }) { CustomWorkout ->
                            CustomWorkoutHubCard(
                                CustomWorkout = CustomWorkout,
                                onStart = {
                                    val exerciseNames = viewModel.getCustomWorkoutExerciseNames(CustomWorkout.id)
                                    val firstExercise = exerciseNames?.firstOrNull()
                                    
                                    if (firstExercise.isNullOrBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.CustomWorkout_no_exercise_selected)
                                            )
                                        }
                                        return@CustomWorkoutHubCard
                                    }

                                    val graphEntry = graphs.firstOrNull { it.fileName == "$firstExercise.json" }
                                    if (graphEntry == null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.CustomWorkout_missing_graph)
                                            )
                                        }
                                    } else {
                                        val listStr = exerciseNames.joinToString(",")
                                        navController.navigate(
                                            "exercise_detail/${graphEntry.fileName}/${graphEntry.isAsset}?CustomWorkoutId=${CustomWorkout.id}&index=0&exerciseList=$listStr"
                                        )
                                    }
                                },
                                onEdit = {
                                    navController.navigate(customWorkoutEditorRoute(CustomWorkout.id))
                                },
                                onDuplicate = {
                                    val duplicatedId = viewModel.duplicateCustomWorkout(CustomWorkout.id)
                                    if (duplicatedId != null) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.CustomWorkout_duplicated_message)
                                            )
                                        }
                                    }
                                },
                                onDelete = { deleteTarget = CustomWorkout },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { CustomWorkout ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.CustomWorkout_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.CustomWorkout_delete_message,
                        CustomWorkout.title,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedCustomWorkout = viewModel.deleteCustomWorkout(CustomWorkout.id)
                        deleteTarget = null
                        if (deletedCustomWorkout != null) {
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.CustomWorkout_deleted_message),
                                    actionLabel = context.getString(R.string.CustomWorkout_undo),
                                    withDismissAction = true,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete(deletedCustomWorkout)
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.CustomWorkout_card_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun CustomWorkoutHubEmptyState(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.CustomWorkout_empty_hub_title),
                style = MaterialTheme.typography.titleMedium,
                color = HubDark,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.CustomWorkout_empty_hub_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = HubMuted,
            )
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = HubOrange),
            ) {
                Text(stringResource(R.string.CustomWorkout_create_first))
            }
        }
    }
}

@Composable
private fun CustomWorkoutHubCard(
    CustomWorkout: CustomWorkoutListItemUi,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = CustomWorkout.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = HubDark,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (CustomWorkout.isDefault) {
                    Text(
                        text = stringResource(R.string.CustomWorkout_card_default_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = HubOrange,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Text(
                text = CustomWorkout.previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = HubMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.CustomWorkout_exercise_count, CustomWorkout.exerciseCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = HubOrange,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.CustomWorkout_estimated_time,
                        CustomWorkout.estimatedMinutes,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = HubDark,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDuplicate) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.CustomWorkout_card_duplicate))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = CustomWorkout.canStart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = HubOrange),
                ) {
                    Text(stringResource(R.string.CustomWorkout_card_start))
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.CustomWorkout_card_edit))
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.CustomWorkout_card_delete),
                        tint = Color(0xFF888888),
                    )
                }
            }
        }
    }
}

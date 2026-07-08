package com.posemonitor2.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.domain.model.Exercise
import com.posemonitor2.R
import com.posemonitor2.loadAvailableGraphs
import kotlinx.coroutines.launch
import java.util.Locale

private val EditorBg = Color(0xFFF4EFE5)
private val EditorDark = Color(0xFF0E3E40)
private val EditorOrange = Color(0xFFE85A2E)
private val EditorMuted = Color(0xFF7A7A7A)
private val EditorChipBorder = Color(0xFFD9D9D9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWorkoutEditorScreen(
    navController: NavController,
    viewModel: CustomWorkoutEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val graphs = remember { loadAvailableGraphs(context) }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(showAddSheet) {
        if (showAddSheet) {
            searchQuery = ""
            selectedDifficulty = null
            selectedCategory = null
        }
    }

    val addableExercises = remember(
        uiState.allExercises,
        uiState.selectedExercises,
        searchQuery,
        selectedDifficulty,
        selectedCategory,
    ) {
        viewModel.getAddableExercises(
            searchQuery = searchQuery,
            selectedDifficulty = selectedDifficulty,
            selectedCategory = selectedCategory,
        )
    }

    Scaffold(
        containerColor = EditorBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (uiState.isNewCustomWorkout) {
                        stringResource(R.string.CustomWorkout_editor_title_new)
                    } else {
                        stringResource(R.string.CustomWorkout_editor_title_edit)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = EditorDark,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = { showAddSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.CustomWorkout_add_exercise),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = EditorDark,
                    fontWeight = FontWeight.SemiBold,
                ),
                label = {
                    Text(
                        text = stringResource(R.string.CustomWorkout_name_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.CustomWorkout_name_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedBorderColor = EditorOrange,
                    unfocusedBorderColor = Color(0xFF928B98),
                    focusedLabelColor = EditorDark,
                    unfocusedLabelColor = EditorDark,
                    focusedPlaceholderColor = EditorMuted,
                    unfocusedPlaceholderColor = EditorMuted,
                    focusedTextColor = EditorDark,
                    unfocusedTextColor = EditorDark,
                    cursorColor = EditorOrange,
                ),
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(
                    R.string.CustomWorkout_editor_selected_count,
                    uiState.selectedExercises.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = EditorOrange,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(10.dp))

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = EditorOrange)
                    }
                }

                uiState.selectedExercises.isEmpty() -> {
                    EditorEmptyState(onAddExercise = { showAddSheet = true })
                }

                else -> {
                    uiState.selectedExercises.forEachIndexed { index, exercise ->
                        EditorSelectedExerciseCard(
                            index = index + 1,
                            exercise = exercise,
                            targetReps = uiState.selectedExerciseTargetReps[exercise.id] ?: 12,
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.selectedExercises.lastIndex,
                            onMoveUp = { viewModel.moveExerciseUp(exercise.id) },
                            onMoveDown = { viewModel.moveExerciseDown(exercise.id) },
                            onIncreaseTarget = { viewModel.increaseTargetReps(exercise.id) },
                            onDecreaseTarget = { viewModel.decreaseTargetReps(exercise.id) },
                            onTargetInputChange = {
                                viewModel.updateTargetRepsFromInput(exercise.id, it)
                            },
                            onRemove = { viewModel.removeExercise(exercise.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val savedId = viewModel.saveCustomWorkout()
                    
                    // If this was a new workout, navigate to the editor with the actual workout ID
                    // This ensures the URL reflects the real workout and reloading will work correctly
                    if (uiState.isNewCustomWorkout) {
                        navController.navigate(customWorkoutEditorRoute(savedId)) {
                            popUpTo(customWorkoutEditorRoute(NEW_WORKOUT_ID)) { inclusive = true }
                        }
                    }
                    
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.CustomWorkout_saved_message)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EditorDark),
            ) {
                Text(
                    text = stringResource(R.string.CustomWorkout_save_button),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    val exerciseName = viewModel.getStartExerciseName()
                    if (exerciseName.isNullOrBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.CustomWorkout_no_exercise_selected)
                            )
                        }
                        return@Button
                    }

                    val graphEntry = graphs.firstOrNull { it.fileName == "$exerciseName.json" }
                    if (graphEntry == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.CustomWorkout_missing_graph)
                            )
                        }
                    } else {
                        // Save the workout and get its ID
                        val workoutId = viewModel.saveCustomWorkout()
                        
                        // Get all exercise names for the sequence
                        val exerciseNames = uiState.selectedExercises.map { it.name }
                        val exerciseListStr = exerciseNames.joinToString(",")
                        
                        navController.navigate(
                            "exercise_detail/${graphEntry.fileName}/${graphEntry.isAsset}?workoutId=$workoutId&index=0&exerciseList=$exerciseListStr"
                        )
                    }
                },
                enabled = !uiState.isLoading && uiState.selectedExercises.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EditorOrange),
            ) {
                Text(
                    text = stringResource(R.string.CustomWorkout_start_button),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = Color.White,
        ) {
            EditorAddExerciseSheet(
                addableExercises = addableExercises,
                categories = uiState.categories,
                difficulties = uiState.difficulties,
                searchQuery = searchQuery,
                selectedDifficulty = selectedDifficulty,
                selectedCategory = selectedCategory,
                onSearchChange = { searchQuery = it },
                onSelectDifficulty = { selectedDifficulty = it },
                onSelectCategory = { selectedCategory = it },
                onAddExercise = viewModel::addExercise,
                onClose = { showAddSheet = false },
            )
        }
    }
}

@Composable
private fun EditorEmptyState(onAddExercise: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.CustomWorkout_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = EditorMuted,
            )
            TextButton(onClick = onAddExercise) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.CustomWorkout_add_exercise))
            }
        }
    }
}

@Composable
private fun EditorSelectedExerciseCard(
    index: Int,
    exercise: Exercise,
    targetReps: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onIncreaseTarget: () -> Unit,
    onDecreaseTarget: () -> Unit,
    onTargetInputChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFAE3DC)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = index.toString(),
                    color = EditorOrange,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatExerciseName(exercise.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = EditorDark,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = buildExerciseMeta(exercise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EditorMuted,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.CustomWorkout_target_input_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = EditorMuted,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF4F4F4),
                                border = BorderStroke(1.dp, EditorChipBorder),
                            ) {
                                IconButton(
                                    onClick = onDecreaseTarget,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = stringResource(R.string.CustomWorkout_target_decrease),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = targetReps.toString(),
                                onValueChange = { input ->
                                    if (input.isNotEmpty() && input.all(Char::isDigit)) {
                                        onTargetInputChange(input)
                                    }
                                },
                                modifier = Modifier.width(92.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleSmall.copy(
                                    color = EditorDark,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = EditorOrange,
                                    unfocusedBorderColor = Color(0xFFB3AEB8),
                                    focusedTextColor = EditorDark,
                                    unfocusedTextColor = EditorDark,
                                    cursorColor = EditorOrange,
                                ),
                            )

                            Text(
                                text = stringResource(R.string.CustomWorkout_target_input_suffix),
                                style = MaterialTheme.typography.labelLarge,
                                color = EditorDark,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF4F4F4),
                                border = BorderStroke(1.dp, EditorChipBorder),
                            ) {
                                IconButton(
                                    onClick = onIncreaseTarget,
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.CustomWorkout_target_increase),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.CustomWorkout_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.CustomWorkout_move_down),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(
                            R.string.CustomWorkout_remove_exercise,
                            formatExerciseName(exercise.name),
                        ),
                        tint = Color(0xFF8A8A8A),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorAddExerciseSheet(
    addableExercises: List<Exercise>,
    categories: List<String>,
    difficulties: List<String>,
    searchQuery: String,
    selectedDifficulty: String?,
    selectedCategory: String?,
    onSearchChange: (String) -> Unit,
    onSelectDifficulty: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onAddExercise: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.CustomWorkout_add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = EditorDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(50.dp),
            color = Color(0xFFF7F7F7),
            border = BorderStroke(1.dp, EditorChipBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = EditorMuted)
                Spacer(Modifier.size(8.dp))
                if (searchQuery.isBlank()) {
                    Text(
                        text = stringResource(R.string.CustomWorkout_search_hint),
                        color = EditorMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = EditorDark),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (difficulties.isNotEmpty()) {
            Text(
                text = stringResource(R.string.CustomWorkout_filter_difficulty),
                style = MaterialTheme.typography.labelMedium,
                color = EditorMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorFilterChip(
                    label = stringResource(R.string.CustomWorkout_filter_all),
                    selected = selectedDifficulty == null,
                    onClick = { onSelectDifficulty(null) },
                )
                difficulties.forEach { difficulty ->
                    EditorFilterChip(
                        label = difficulty,
                        selected = selectedDifficulty == difficulty,
                        onClick = { onSelectDifficulty(difficulty) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (categories.isNotEmpty()) {
            Text(
                text = stringResource(R.string.CustomWorkout_filter_category),
                style = MaterialTheme.typography.labelMedium,
                color = EditorMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorFilterChip(
                    label = stringResource(R.string.CustomWorkout_filter_all),
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                )
                categories.forEach { category ->
                    EditorFilterChip(
                        label = category,
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (addableExercises.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.CustomWorkout_no_more_exercises),
                    color = EditorMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(addableExercises, key = { it.id }) { exercise ->
                    EditorAddableExerciseCard(
                        exercise = exercise,
                        onAdd = { onAddExercise(exercise.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EditorFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        color = if (selected) EditorDark else Color.White,
        border = if (selected) null else BorderStroke(1.dp, EditorChipBorder),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color.White else EditorDark,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EditorAddableExerciseCard(
    exercise: Exercise,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatExerciseName(exercise.name),
                    style = MaterialTheme.typography.titleSmall,
                    color = EditorDark,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildExerciseMeta(exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorMuted,
                )
            }

            TextButton(onClick = onAdd) {
                Text(
                    text = stringResource(R.string.CustomWorkout_add_button),
                    color = EditorOrange,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatExerciseName(value: String): String {
    return value
        .split('-')
        .joinToString(" ") { token ->
            token.replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase(Locale.getDefault())
                } else {
                    char.toString()
                }
            }
        }
}

private fun buildExerciseMeta(exercise: Exercise): String {
    val difficulty = exercise.difficulty ?: "Beginner"
    val category = exercise.category ?: "Other"
    return "$difficulty - $category"
}

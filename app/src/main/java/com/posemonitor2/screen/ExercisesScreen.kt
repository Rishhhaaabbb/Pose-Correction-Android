package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import com.posemonitor2.R
import com.posemonitor2.UserRoute

// Accent colors — fixed regardless of theme
private val Orange = Color(0xFFE8622E)
private val Yellow = Color(0xFFE8B840)

@Composable
fun ExercisesScreen(
    navController: NavController,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedDifficulty  by remember { mutableStateOf<String?>(null) }
    var selectedCategory    by remember { mutableStateOf<String?>(null) }

    // Theme-aware colors derived from MaterialTheme — react to dark/light mode
    val isDark     = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor    = MaterialTheme.colorScheme.background
    val onBg       = MaterialTheme.colorScheme.onBackground
    val grayText   = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    val chipBorder = MaterialTheme.colorScheme.outline

    val greeting = getGreeting()
    val userProfile = uiState.userProfile

    val context = LocalContext.current
    val graphsByFileName = uiState.graphByFileName
    val drawableByExerciseId = uiState.drawableByExerciseId
    val preloadedThumbnails = uiState.preloadedThumbnails
    val filteredExercises by viewModel.filteredExercises.collectAsState()
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val navBackStackEntry = navController.currentBackStackEntry
    
    // Create a key that changes on every navigation to force refresh
    val refreshKey = remember {
        derivedStateOf {
            navBackStackEntry?.destination?.route
        }
    }.value
    
    // Refresh graphs when screen becomes visible (not just on creation)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            android.util.Log.d("ExercisesScreen", "Lifecycle event: $event")
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("ExercisesScreen", "ON_RESUME - calling refreshGraphs()")
                viewModel.refreshGraphs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Refresh when navigating to this screen or when route changes
    LaunchedEffect(refreshKey) {
        android.util.Log.d("ExercisesScreen", "LaunchedEffect triggered for route: $refreshKey")
        viewModel.refreshGraphs()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshGraphs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedDifficulty, selectedCategory) {
        viewModel.updateFilters(searchQuery, selectedDifficulty, selectedCategory)
    }

    var showSkeleton by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        showSkeleton = uiState.isLoading
    }

    // Delete confirmation state — only populated for generated/uploaded exercises
    var deleteTarget by remember { mutableStateOf<com.domain.model.Exercise?>(null) }

    deleteTarget?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Exercise") },
            text = {
                Text(
                    "Delete \"${exercise.name.replace("-", " ")}\"? " +
                    "This will remove it from your library and delete its graph file."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExercise(exercise)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.exercises_title),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = onBg,
                )

                if (userProfile.isAdmin) {
                    Surface(
                        onClick = { navController.navigate(UserRoute.Library.route) },
                        shape = CircleShape,
                        color = Color(0xFF0A3D3F),
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Exercise",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }

        // ── Search bar ────────────────────────────────────────────────────────
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Search exercises...", color = grayText, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = onBg,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = grayText,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = onBg,
                    unfocusedBorderColor = chipBorder,
                    cursorColor = onBg,
                    focusedTextColor = onBg,
                    unfocusedTextColor = onBg,
                ),
            )
        }

        item { Spacer(Modifier.height(20.dp)) }

        if (uiState.difficulties.isNotEmpty()) {
            item {
                Text("Difficulty", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = grayText, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipItem(
                        label = "All Levels",
                        selected = selectedDifficulty == null,
                        onClick = { selectedDifficulty = null }
                    )
                    uiState.difficulties.forEach { difficulty ->
                        FilterChipItem(
                            label = difficulty,
                            selected = selectedDifficulty == difficulty,
                            onClick = { selectedDifficulty = difficulty }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        if (uiState.categories.isNotEmpty()) {
            item {
                Text("Category", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = grayText, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipItem(
                        label = "All",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    uiState.categories.forEach { category ->
                        FilterChipItem(
                            label = category,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }

        if (showSkeleton) {
            items(6) {
                WorkoutCardSkeleton()
                Spacer(Modifier.height(12.dp))
            }
        } else if (filteredExercises.isEmpty() && searchQuery.isNotEmpty()) {
            // ── Empty state ───────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = grayText,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No exercises found for \"$searchQuery\"",
                        fontSize = 14.sp,
                        color = grayText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            items(filteredExercises, key = { it.id }) { exercise ->
                // Try multiple filename keys: exact name, normalised name with hyphens,
                // and the exercise id — generated exercises may use any of these forms
                val graphEntry = graphsByFileName["${exercise.name}.json"]
                    ?: graphsByFileName["${exercise.name.trim().replace(" ", "-").lowercase()}.json"]
                    ?: graphsByFileName["${exercise.id}.json"]
                // Only generated exercises (category == "Generated" or no drawable) can be deleted
                val isDeletable = exercise.category == "Generated" ||
                    (drawableByExerciseId[exercise.id] ?: 0) == 0
                WorkoutCard(
                    exerciseId = exercise.id,
                    name = exercise.name.replace("-", " ").uppercase(),
                    category = exercise.category ?: "Other",
                    difficulty = exercise.difficulty ?: "Beginner",
                    thumbBitmap = preloadedThumbnails[exercise.id],
                    hasGraph = graphEntry != null,
                    isDeletable = isDeletable,
                    onLongPress = { deleteTarget = exercise },
                    onClick = {
                        android.util.Log.d("ExercisesScreen", "Clicked exercise: ${exercise.name}, id: ${exercise.id}")
                        android.util.Log.d("ExercisesScreen", "Looking for graph: ${exercise.name}.json")
                        android.util.Log.d("ExercisesScreen", "Graph entry: $graphEntry")
                        android.util.Log.d("ExercisesScreen", "Available graphs: ${graphsByFileName.keys}")
                        
                        if (graphEntry != null) {
                            val cat = exercise.category?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
                            val diff = exercise.difficulty?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
                            navController.navigate(
                                "exercise_detail/${graphEntry.fileName}/${graphEntry.isAsset}?index=0&exerciseList=${exercise.name}&category=$cat&difficulty=$diff"
                            )
                        } else {
                            android.util.Log.e("ExercisesScreen", "Graph entry is NULL for exercise ${exercise.name}")
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val selectedBg  = MaterialTheme.colorScheme.onBackground
    val unselectedBg = MaterialTheme.colorScheme.surface
    val border      = MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        color = if (selected) selectedBg else unselectedBg,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, border),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WorkoutCard(
    exerciseId: String,
    name: String,
    category: String,
    difficulty: String,
    thumbBitmap: ImageBitmap?,
    hasGraph: Boolean,
    isDeletable: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val thumbBg = when (category) {
        "Legs"    -> Color(0xFFB8D4B8)
        "Chest"   -> Color(0xFFD4B8B8)
        "Core"    -> Color(0xFFB8C8D4)
        "Cardio"  -> Color(0xFFD4D4B8)
        "Stretch" -> Color(0xFFD4B8D4)
        else      -> Color(0xFFB0C8C0)
    }
    val cardBg       = MaterialTheme.colorScheme.surface
    val onCard       = MaterialTheme.colorScheme.onSurface
    val subText      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val disabledAlpha = if (hasGraph) 1f else 0.5f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        shadowElevation = if (hasGraph) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasGraph || isDeletable) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = if (isDeletable) onLongPress else null,
                    )
                } else Modifier
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(thumbBg.copy(alpha = disabledAlpha)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(disabledAlpha),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alignment = androidx.compose.ui.Alignment.BottomCenter,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Orange.copy(alpha = disabledAlpha),
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = onCard.copy(alpha = disabledAlpha),
                )
                Spacer(Modifier.height(4.dp))
                if (hasGraph) {
                    Text(
                        text = buildString {
                            append("$difficulty • AI Session")
                            if (isDeletable) append(" • Hold to delete")
                        },
                        fontSize = 12.sp,
                        color = subText,
                    )
                } else {
                    Text(
                        "Graph file not available",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                    if (isDeletable) {
                        Text(
                            "Hold to delete",
                            fontSize = 11.sp,
                            color = subText,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (hasGraph) Yellow else MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = if (hasGraph) stringResource(R.string.exercises_play_cd) else "Not available",
                    tint = Color.White.copy(alpha = disabledAlpha),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkoutCardSkeleton() {
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(shimmer),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer)
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(shimmer),
            )
        }
    }
}

private fun getGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "GOOD MORNING"
        hour < 17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
}

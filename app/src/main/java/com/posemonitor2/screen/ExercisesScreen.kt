package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import com.posemonitor2.R
import com.posemonitor2.UserRoute

private val Orange     = Color(0xFFE8622E)
private val Yellow     = Color(0xFFE8B840)

@Composable
private fun exercisesBgColor() = MaterialTheme.colorScheme.background
@Composable
private fun exercisesSurfaceColor() = MaterialTheme.colorScheme.surface
@Composable
private fun exercisesTextColor() = MaterialTheme.colorScheme.onSurface
@Composable
private fun exercisesMutedColor() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable
private fun exercisesBorderColor() = MaterialTheme.colorScheme.outline

@Composable
fun ExercisesScreen(
    navController: NavController,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedDifficulty  by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategory    by rememberSaveable { mutableStateOf<String?>(null) }
    
    val greeting = getGreeting()
    val userProfile = uiState.userProfile

    val context = LocalContext.current
    val graphsByFileName = uiState.graphByFileName
    val drawableByExerciseId = uiState.drawableByExerciseId
    val preloadedThumbnails = uiState.preloadedThumbnails
    val filteredExercises by viewModel.filteredExercises.collectAsState()
    
    val navBackStackEntry = navController.currentBackStackEntry

    LaunchedEffect(Unit) {
        viewModel.refreshGraphs()
    }

    LaunchedEffect(selectedDifficulty, selectedCategory) {
        viewModel.updateFilters(searchQuery, selectedDifficulty, selectedCategory)
    }

    var showSkeleton by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        showSkeleton = uiState.isLoading
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
                .background(exercisesBgColor())
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
                    color = MaterialTheme.colorScheme.primary,
                )

                if (userProfile.isAdmin) {
                    Surface(
                        onClick = { navController.navigate(UserRoute.Library.route) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary, // Dark green
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

        // ΓöÇΓöÇ Search bar ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("Search exercises...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = exercisesSurfaceColor(),
                    unfocusedContainerColor = exercisesSurfaceColor(),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = exercisesBorderColor(),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = exercisesTextColor(),
                    unfocusedTextColor = exercisesTextColor(),
                ),
            )
        }

        item { Spacer(Modifier.height(20.dp)) }

        if (uiState.difficulties.isNotEmpty()) {
            item {
                Text("Difficulty", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
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
                Text("Category", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
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
            // ΓöÇΓöÇ Empty state ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No exercises found for \"$searchQuery\"",
                        fontSize = 14.sp,
                        color = exercisesMutedColor(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            items(filteredExercises, key = { it.id }) { exercise ->
                val graphEntry = graphsByFileName["${exercise.name}.json"]
                WorkoutCard(
                    exerciseId = exercise.id,
                    name = exercise.name.replace("-", " ").uppercase(),
                    category = exercise.category ?: "Other",
                    difficulty = exercise.difficulty ?: "Beginner",
                    thumbBitmap = preloadedThumbnails[exercise.id],
                    onClick = {
                        android.util.Log.d("ExercisesScreen", "Clicked exercise: ${exercise.name}, id: ${exercise.id}")
                        android.util.Log.d("ExercisesScreen", "Looking for graph: ${exercise.name}.json")
                        android.util.Log.d("ExercisesScreen", "Graph entry: $graphEntry")
                        android.util.Log.d("ExercisesScreen", "Available graphs: ${graphsByFileName.keys}")
                        
                        if (graphEntry != null) {
                            navController.navigate(
                                "exercise_detail/${graphEntry.fileName}/${graphEntry.isAsset}?index=0&exerciseList=${exercise.name}"
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else exercisesSurfaceColor(),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, exercisesBorderColor()),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
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
    onClick: () -> Unit,
) {
    val thumbBg = when (category) {
        "Legs" -> Color(0xFFB8D4B8)
        "Chest" -> Color(0xFFD4B8B8)
        "Core" -> Color(0xFFB8C8D4)
        "Cardio" -> Color(0xFFD4D4B8)
        "Stretch" -> Color(0xFFD4B8D4)
        else -> Color(0xFFB0C8C0)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
                            color = exercisesSurfaceColor(),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(thumbBg),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumbBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        alignment = androidx.compose.ui.Alignment.BottomCenter
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Orange,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$difficulty - AI Session",
                    fontSize = 12.sp,
                    color = exercisesMutedColor(),
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Yellow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.exercises_play_cd),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkoutCardSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_x",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE8E8E8),
            Color(0xFFF5F5F5),
            Color(0xFFE8E8E8),
        ),
        start = Offset(shimmerX - 200f, 0f),
        end = Offset(shimmerX + 200f, 0f),
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = exercisesSurfaceColor(),
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
                    .background(shimmerBrush),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(shimmerBrush),
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
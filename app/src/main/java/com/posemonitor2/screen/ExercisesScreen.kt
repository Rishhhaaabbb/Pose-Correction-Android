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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import com.posemonitor2.R
import com.posemonitor2.UserRoute

private val BgColor    = Color(0xFFF4F6F4)
private val TealDark   = Color(0xFF0D3B34)
private val Orange     = Color(0xFFE8622E)
private val Yellow     = Color(0xFFE8B840)
private val GrayText   = Color(0xFF888888)
private val ChipBorder = Color(0xFFDDDDDD)
private val CardBg     = Color.White

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
            .background(BgColor)
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
                    color = TealDark,
                )

                if (userProfile.isAdmin) {
                    Surface(
                        onClick = { navController.navigate(UserRoute.Library.route) },
                        shape = CircleShape,
                        color = Color(0xFF0A3D3F), // Dark green / TealDark
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
                    Text("Search exercises...", color = GrayText, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TealDark,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = GrayText,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = TealDark,
                    unfocusedBorderColor = ChipBorder,
                    cursorColor = TealDark,
                    focusedTextColor = TealDark,
                    unfocusedTextColor = TealDark,
                ),
            )
        }

        item { Spacer(Modifier.height(20.dp)) }

        if (uiState.difficulties.isNotEmpty()) {
            item {
                Text("Difficulty", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrayText, letterSpacing = 1.sp)
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
                Text("Category", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrayText, letterSpacing = 1.sp)
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
                        tint = GrayText,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No exercises found for \"$searchQuery\"",
                        fontSize = 14.sp,
                        color = GrayText,
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
        color = if (selected) TealDark else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) Color.White else TealDark,
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
        color = CardBg,
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
                    color = TealDark,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$difficulty ΓÇó AI Session",
                    fontSize = 12.sp,
                    color = GrayText,
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
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
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
                    .background(Color(0xFFE0E0E0)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0E0E0))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0E0E0))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE0E0E0))
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0)),
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
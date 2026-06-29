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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
    var searchQuery         by remember { mutableStateOf("") }
    var selectedDifficulty  by remember { mutableStateOf<String?>(null) }
    var selectedCategory    by remember { mutableStateOf<String?>(null) }
    
    val greeting = getGreeting()
    val userProfile = uiState.userProfile

    val context = LocalContext.current
    val graphsByFileName = uiState.graphByFileName
    val drawableByExerciseId = uiState.drawableByExerciseId
    val preloadedThumbnails = uiState.preloadedThumbnails
    val filteredExercises by viewModel.filteredExercises.collectAsState()

    LaunchedEffect(searchQuery, selectedDifficulty, selectedCategory) {
        viewModel.updateFilters(searchQuery, selectedDifficulty, selectedCategory)
    }

    var showSkeleton by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            delay(200)
            showSkeleton = true
        } else {
            showSkeleton = false
        }
    }

    LazyColumn(
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

        item {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = GrayText, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    if (searchQuery.isEmpty()) {
                        Text(stringResource(R.string.exercises_search_hint), color = GrayText, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = TealDark),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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
                        if (graphEntry != null) {
                            navController.navigate(
                                "exercise_detail/${graphEntry.fileName}/${graphEntry.isAsset}?index=0&exerciseList=${exercise.name}"
                            )
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
                    "$difficulty • AI Session",
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

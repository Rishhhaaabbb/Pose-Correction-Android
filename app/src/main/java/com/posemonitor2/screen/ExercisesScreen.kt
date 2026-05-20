package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.posemonitor2.R
import com.posemonitor2.UserRoute
import com.posemonitor2.loadAvailableGraphs

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
    val graphs = remember { loadAvailableGraphs(context) }
    
    val filteredExercises = remember(uiState.exercises, searchQuery, selectedDifficulty, selectedCategory) {
        viewModel.getFilteredExercises(searchQuery, selectedDifficulty, selectedCategory)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(20.dp))

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

        Spacer(Modifier.height(20.dp))

        if (uiState.difficulties.isNotEmpty()) {
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

        Spacer(Modifier.height(16.dp))

        if (uiState.categories.isNotEmpty()) {
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

        Spacer(Modifier.height(20.dp))

        filteredExercises.forEachIndexed { index, exercise ->
            val graphEntry = graphs.find { it.fileName == "${exercise.name}.json" }
            WorkoutCard(
                exerciseId = exercise.id,
                name = exercise.name.replace("-", " ").uppercase(),
                category = exercise.category ?: "Other",
                difficulty = exercise.difficulty ?: "Beginner",
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

        Spacer(Modifier.height(8.dp))
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
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val drawableName = exerciseId.replace("-", "_")
    val resId = remember(exerciseId) {
        context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    }

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
                if (resId != 0) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(resId),
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

private fun getGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "GOOD MORNING"
        hour < 17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
}

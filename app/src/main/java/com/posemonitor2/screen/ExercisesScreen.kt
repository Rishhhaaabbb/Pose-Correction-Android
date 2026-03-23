package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ── Design tokens ─────────────────────────────────────────────────────────────
private val BgColor    = Color(0xFFF4F6F4)
private val TealDark   = Color(0xFF0D3B34)
private val Orange     = Color(0xFFE8622E)
private val Yellow     = Color(0xFFE8B840)
private val GrayText   = Color(0xFF888888)
private val ChipBorder = Color(0xFFDDDDDD)
private val CardBg     = Color.White

// ── Data ──────────────────────────────────────────────────────────────────────
private data class WorkoutItem(
    val category: String,
    val name: String,
    val level: String,
    val detail: String,
    val thumbBg: Color,
)

private val workouts = listOf(
    WorkoutItem("CORE STRENGTH",  "Static Plank",    "Int.",  "60s",    Color(0xFFF0EDE8)),
    WorkoutItem("HIGH INTENSITY", "Jumping Jacks",   "Basic", "45s",    Color(0xFFD4E8A0)),
    WorkoutItem("LOWER BODY",     "Deep Squats",     "Basic", "3 sets", Color(0xFF4AADAA)),
    WorkoutItem("UPPER BODY",     "Push Ups",        "Int.",  "40s",    Color(0xFFEAD8C0)),
    WorkoutItem("FULL BODY",      "Sun Salutation",  "Adv.",  "5 sets", Color(0xFFCCE0D5)),
)

private val difficultyChips = listOf("All Levels", "Beginner", "Intermediate", "Advanced")
private val targetChips     = listOf("Full Body", "Core", "Upper Body", "Lower Body")

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun ExercisesScreen(navController: NavController) {
    var searchQuery         by remember { mutableStateOf("") }
    var selectedDifficulty  by remember { mutableStateOf("All Levels") }
    var selectedTarget      by remember { mutableStateOf<String?>(null) }

    val filtered = workouts.filter { w ->
        (searchQuery.isBlank() || w.name.contains(searchQuery, ignoreCase = true) ||
                w.category.contains(searchQuery, ignoreCase = true)) &&
        (selectedDifficulty == "All Levels" || w.level.startsWith(selectedDifficulty.take(3), ignoreCase = true)) &&
        (selectedTarget == null || w.category.contains(selectedTarget!!, ignoreCase = true))
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

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB0C8C0)),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TealDark)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "GOOD MORNING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GrayText,
                    letterSpacing = 1.sp,
                )
                Text(
                    "Alex",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealDark,
                )
            }

            // Filter button
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Tune, contentDescription = "Filter", tint = TealDark, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Title ─────────────────────────────────────────────────────────────
        Text(
            "Workout Library",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TealDark,
        )

        Spacer(Modifier.height(16.dp))

        // ── Search bar ────────────────────────────────────────────────────────
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
                    Text("Search exercises...", color = GrayText, fontSize = 14.sp)
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

        // ── Difficulty chips ──────────────────────────────────────────────────
        Text("DIFFICULTY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrayText, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            difficultyChips.forEach { chip ->
                val selected = chip == selectedDifficulty
                FilterChipItem(
                    label    = chip,
                    selected = selected,
                    onClick  = { selectedDifficulty = chip },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Target area chips ─────────────────────────────────────────────────
        Text("TARGET AREA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrayText, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            targetChips.forEach { chip ->
                val selected = chip == selectedTarget
                FilterChipItem(
                    label    = chip,
                    selected = selected,
                    onClick  = { selectedTarget = if (selected) null else chip },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Workout cards ─────────────────────────────────────────────────────
        filtered.forEach { workout ->
            WorkoutCard(workout)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────
@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick    = onClick,
        shape      = RoundedCornerShape(50.dp),
        color      = if (selected) TealDark else Color.White,
        border     = if (selected) null else ButtonDefaults.outlinedButtonBorder(true).let {
            androidx.compose.foundation.BorderStroke(1.dp, ChipBorder)
        },
    ) {
        Text(
            label,
            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color     = if (selected) Color.White else TealDark,
            fontSize  = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Workout card ──────────────────────────────────────────────────────────────
@Composable
private fun WorkoutCard(workout: WorkoutItem) {
    Surface(
        shape         = RoundedCornerShape(16.dp),
        color         = CardBg,
        shadowElevation = 3.dp,
        modifier      = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(workout.thumbBg),
            )

            Spacer(Modifier.width(14.dp))

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workout.category,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Orange,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    workout.name,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TealDark,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Level: ${workout.level}  \u2022  ${workout.detail}",
                    fontSize = 12.sp,
                    color    = GrayText,
                )
            }

            // Play button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Yellow),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

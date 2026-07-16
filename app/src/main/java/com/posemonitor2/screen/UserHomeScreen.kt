package com.posemonitor2.screen

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.posemonitor2.MainActivity
import com.posemonitor2.UserRoute
import androidx.hilt.navigation.compose.hiltViewModel
import com.posemonitor2.R
import com.posemonitor2.onboarding.OnboardingActivity
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.SelfImprovement

private val Yellow      = Color(0xFFE8B840)
private val Orange      = Color(0xFFE85A2E)
private val TealChip    = Color(0xFF0D4A4E)

@Composable
private fun homeBgColor() = MaterialTheme.colorScheme.background
@Composable
private fun homeSurfaceColor() = MaterialTheme.colorScheme.surface
@Composable
private fun homeTextColor() = MaterialTheme.colorScheme.onBackground
@Composable
private fun homeMutedColor() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun UserHomeScreen(
    navController: NavController,
    onOpenHelp: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showHelpDialog by remember { mutableStateOf(false) }

    // Collect navigation events from HomeViewModel (recommended exercise tapped)
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { exercise ->
            navController.navigate(
                "exercise_detail/${exercise.name}.json/true?index=0&exerciseList=${exercise.name}"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBgColor())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        HomeHeader(
            userName = uiState.displayName,
            greetingSubtitle = uiState.greetingSubtitle,
            onOpenHelp = { showHelpDialog = true },
        )
        Spacer(Modifier.height(16.dp))

        HeroCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            onJoinSession = { navController.navigate(UserRoute.Exercises.route) },
        )
        Spacer(Modifier.height(12.dp))

        // ΓöÇΓöÇ 3A. Daily Goal Progress Card ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        DailyGoalProgressCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            dailyGoalMinutes = uiState.dailyGoalMinutes,
            todayMinutesDone = uiState.todayMinutesDone,
            dailyGoalProgress = uiState.dailyGoalProgress,
        )
        Spacer(Modifier.height(20.dp))

        // ΓöÇΓöÇ 3C. Recommended for You ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        if (uiState.recommendedExercises.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recommended for You",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Serif,
                )
                Text(
                    text = "See All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Orange,
                    modifier = Modifier.clickable {
                        navController.navigate(UserRoute.Exercises.route)
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.recommendedExercises) { exercise ->
                    RecommendedExerciseCard(
                        exercise = exercise,
                        onClick = { viewModel.onExerciseClicked(exercise) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ΓöÇΓöÇ 3D. Quick Actions Grid ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        Text(
            text = "Quick Actions",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        QuickActionsGrid(
            modifier = Modifier.padding(horizontal = 16.dp),
            onStartSession = { 
                navController.navigate(UserRoute.Exercises.route) {
                    popUpTo(UserRoute.Home.route) {
                        inclusive = false
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onMyProgress = { 
                navController.navigate(UserRoute.Progress.route) {
                    popUpTo(UserRoute.Home.route) {
                        inclusive = false
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onCustomWorkout = { 
                // Since Custom Workout is now a bottom nav item, navigate with proper back stack management
                navController.navigate(UserRoute.CustomWorkout.route) {
                    popUpTo(UserRoute.Home.route) {
                        inclusive = false
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onMyProfile = { 
                navController.navigate(UserRoute.Profile.route) {
                    popUpTo(UserRoute.Home.route) {
                        inclusive = false
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
        Spacer(Modifier.height(20.dp))

        SectionTitle(
            title = stringResource(R.string.home_body_vitals_title),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        VitalsCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            bmi = uiState.bmi,
            weightKg = uiState.weightKg,
            targetWeightKg = uiState.targetWeightKg,
            currentStreak = uiState.currentStreak,
        )
        Spacer(Modifier.height(20.dp))

        CustomWorkoutQuickCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            onOpenCustomWorkout = {
                // Navigate to Custom Workout with proper stack management
                navController.navigate(UserRoute.CustomWorkout.route) {
                    popUpTo(UserRoute.Home.route) {
                        inclusive = false
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )

        val isAdmin = remember(uiState.userEmail) {
            val normalized = uiState.userEmail?.trim()?.lowercase()
            normalized == "admin" || normalized == "admin@poseapp.com"
        }

        if (isAdmin) {
            Spacer(Modifier.height(20.dp))
            AdminStatsShortcutCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { navController.navigate(UserRoute.AdminStats.route) }
            )
        }

        Spacer(Modifier.height(16.dp))
    }
    
    if (showHelpDialog) {
        HelpOptionsDialog(
            onReplayOnboarding = {
                context.startActivity(
                    OnboardingActivity.createIntent(context, OnboardingActivity.MODE_INTRO)
                )
            },
            onFullTour = {
                context.startActivity(
                    OnboardingActivity.createIntent(context, OnboardingActivity.MODE_FULL_TOUR)
                )
            },
            onDismiss = { showHelpDialog = false }
        )
    }
}

// ΓöÇΓöÇ Header ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun HomeHeader(userName: String, greetingSubtitle: String = "", onOpenHelp: () -> Unit) {
    val greetingResId = getGreeting()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFFD4C4B0)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF8A7A6A), modifier = Modifier.size(28.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(greetingResId),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Orange,
                letterSpacing = 1.sp,
            )
            Text(
                text = userName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Serif,
            )
            // 3B. Personalized greeting subtitle
            if (greetingSubtitle.isNotEmpty()) {
                Text(
                    text = greetingSubtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Help icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clickable { onOpenHelp() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.HelpOutline, contentDescription = stringResource(R.string.home_help_title), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}

// ΓöÇΓöÇ Daily Goal Progress Card (3A) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun DailyGoalProgressCard(
    modifier: Modifier = Modifier,
    dailyGoalMinutes: Int,
    todayMinutesDone: Int,
    dailyGoalProgress: Float,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = homeSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Today's Goal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$dailyGoalMinutes min daily practice",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = "$todayMinutesDone / $dailyGoalMinutes min",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { dailyGoalProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Orange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

// ΓöÇΓöÇ Help Options Dialog ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun HelpOptionsDialog(
    onReplayOnboarding: () -> Unit,
    onFullTour: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onDismiss()
                        onReplayOnboarding()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8622E)),
                ) {
                    Text(stringResource(R.string.home_replay_onboarding))
                }
                Button(
                    onClick = {
                        onDismiss()
                        onFullTour()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33E8622E)),
                ) {
                    Text(stringResource(R.string.home_full_tour))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

// ΓöÇΓöÇ Hero card ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun HeroCard(modifier: Modifier = Modifier, onJoinSession: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left ΓÇö yellow, text + button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Yellow)
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                    // Chip
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            stringResource(R.string.home_ai_active),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    // Title
                    Text(
                        text = stringResource(R.string.home_hero_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 26.sp,
                    )

                    // Join button
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = Orange,
                        modifier = Modifier.clickable { onJoinSession() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(stringResource(R.string.home_join_session), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Right ΓÇö yoga photo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFC4A882)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.homepageyogapose),
                    contentDescription = stringResource(R.string.home_yoga_pose_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ΓöÇΓöÇ Quick Actions Grid (3D) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun QuickActionsGrid(
    modifier: Modifier = Modifier,
    onStartSession: () -> Unit,
    onMyProgress: () -> Unit,
    onCustomWorkout: () -> Unit,
    onMyProfile: () -> Unit,
) {
    val tiles = listOf(
        Triple("Start Session",   Icons.Default.PlayArrow,     MaterialTheme.colorScheme.primary),
        Triple("My Progress",     Icons.AutoMirrored.Filled.TrendingUp, Orange),
        Triple("Custom Workout",  Icons.Default.FitnessCenter, Color(0xFF1A646A)),
        Triple("My Profile",      Icons.Default.Person,        Color(0xFF4A3728)),
    )
    val actions = listOf(onStartSession, onMyProgress, onCustomWorkout, onMyProfile)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 2).forEach { rowStart ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(rowStart, rowStart + 1).forEach { idx ->
                    val (label, icon, bg) = tiles[idx]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .clickable { actions[idx]() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ΓöÇΓöÇ Recommended Exercise Card (3C) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun RecommendedExerciseCard(
    exercise: com.domain.model.Exercise,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val drawableResId = remember(exercise.id) {
        val name = exercise.id.replace("-", "_")
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }
    val difficultyColor = when (exercise.difficulty?.trim()) {
        "Beginner"     -> Color(0xFF4CAF50)
        "Intermediate" -> Color(0xFFE8622E)
        "Advanced"     -> Color(0xFFE85A2E)
        else           -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = homeSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            // Thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (drawableResId != 0) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(drawableResId),
                        contentDescription = exercise.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            // Info area
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = exercise.name.replace("-", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                val difficultyLabel = exercise.difficulty
                if (difficultyLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = difficultyColor,
                    ) {
                        Text(
                            text = difficultyLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

// ΓöÇΓöÇ Section title ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Orange, modifier = Modifier.size(22.dp))
    }
}

// ΓöÇΓöÇ Vitals card ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun VitalsCard(
    modifier: Modifier = Modifier,
    bmi: Double? = null,
    weightKg: Double? = null,
    targetWeightKg: Double? = null,
    currentStreak: Int = 0,
) {
    val hasData = bmi != null || weightKg != null || currentStreak > 0
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = homeSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        if (!hasData) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.home_no_stats_yet),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Person thumbnail
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEAEAEA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.SelfImprovement, contentDescription = null, tint = Color(0xFF999999), modifier = Modifier.size(32.dp))
                }

                // BMI + progress
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.home_current_status), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Orange, letterSpacing = 0.8.sp)
                    Text(
                        bmi?.let { String.format("%.1f", it) } ?: "--",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (bmi != null && targetWeightKg != null && weightKg != null) {
                        val progress = calculateProgress(weightKg, targetWeightKg)
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Orange,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                // Weight goal
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.home_weight_goal), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                    if (weightKg != null && targetWeightKg != null) {
                        Text(
                            "${weightKg.toInt()} / ${targetWeightKg.toInt()} kg",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text("--", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun calculateProgress(current: Double, target: Double): Float {
    return if (target > 0) (current / target).toFloat() else 0f
}

@Composable
private fun CustomWorkoutQuickCard(
    modifier: Modifier = Modifier,
    onOpenCustomWorkout: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0D4B50),
                        Color(0xFF1A646A),
                    )
                )
            ),
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
                .offset(x = 18.dp, y = (-10).dp)
                .clip(CircleShape)
                .background(Color(0x24FFE4A8)),
        )
        Box(
            modifier = Modifier
                .size(82.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-24).dp, y = 22.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF)),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0x2BFFFFFF),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = Yellow,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_custom_workout_badge),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.home_custom_workout_card_title),
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                lineHeight = 25.sp,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.home_custom_workout_card_subtitle),
                fontSize = 12.sp,
                color = Color(0xD9FFFFFF),
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color.White,
                modifier = Modifier.clickable { onOpenCustomWorkout() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_open_custom_workout_button),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ΓöÇΓöÇ Custom routine card ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun CustomRoutineCard(modifier: Modifier = Modifier, onStart: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors = listOf(Color(0xFF0A3D35), Color(0xFF1A5C40)))),
    ) {
        // Decorative sparkle shapes (background)
        Text("Γ£ª", fontSize = 60.sp, color = Color(0x221A8A5A), modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp))
        Text("Γ£ª", fontSize = 36.sp, color = Color(0x221A8A5A), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 28.dp, end = 50.dp))

        Column(modifier = Modifier.padding(18.dp)) {
            // Chip
            Surface(shape = RoundedCornerShape(6.dp), color = TealChip) {
                Text(stringResource(R.string.home_advanced), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Yellow, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }

            Spacer(Modifier.height(10.dp))

            Text(stringResource(R.string.home_custom), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif, lineHeight = 32.sp)
            Text(stringResource(R.string.home_routine), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Yellow, fontFamily = FontFamily.Serif, lineHeight = 32.sp)

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.home_custom_routine_desc),
                fontSize = 13.sp,
                color = Color(0xBBFFFFFF),
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar stack
                Box(modifier = Modifier.width(56.dp).height(28.dp)) {
                    listOf(Color(0xFF4CAF50), Color(0xFF2196F3)).forEachIndexed { i, color ->
                        Box(
                            modifier = Modifier.size(28.dp).offset(x = (i * 18).dp).clip(CircleShape).background(color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text("+12k", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_training_now), fontSize = 10.sp, color = Color(0xAAFFFFFF), letterSpacing = 0.5.sp)

                Spacer(Modifier.weight(1f))

                // Yellow arrow button
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Yellow).clickable { onStart() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ΓöÇΓöÇ Aura AI card ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
private fun AuraAiCard(modifier: Modifier = Modifier, onStart: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Orange),
    ) {
        // Decorative large circle right side
        Box(
            modifier = Modifier
                .size(130.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 30.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF)),
        )

        Column(modifier = Modifier.padding(18.dp)) {
            // Chip
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0x33000000)) {
                Text(stringResource(R.string.home_curated_for_you), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.home_aura_recommended),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                lineHeight = 32.sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.home_goal_oriented_desc),
                fontSize = 13.sp,
                color = Color(0xCCFFFFFF),
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Scientifically validated badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.home_scientifically_validated),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp,
                        lineHeight = 13.sp,
                    )
                }

                // Start Session dark pill
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onStart() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.home_start_session_multiline), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 14.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStatsShortcutCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0A3D3F), // Dark Green
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x1AFFFFFF),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Analytics,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_studio_analytics),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif
                )
                Text(
                    text = stringResource(R.string.home_studio_analytics_desc),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getGreeting(): Int {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> R.string.home_greeting_morning
        hour < 17 -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }
}
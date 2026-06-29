package com.posemonitor2.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.net.Uri
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.posemonitor2.LoginActivity
import com.posemonitor2.R
import com.posemonitor2.settings.AppLanguage
import com.posemonitor2.settings.ProfileSettingsPreferences
import com.posemonitor2.settings.UnitSystem
import com.posemonitor2.ui.theme.ThemeMode
import com.posemonitor2.ui.theme.ThemePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Theme-aware design tokens (map to Material color scheme)

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    // theme-aware colors
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val primaryVariant = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondary
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val textGray = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val dailyReminders by ProfileSettingsPreferences.observeDailyReminders(context).collectAsStateWithLifecycle(initialValue = true)
    val goalAchievements by ProfileSettingsPreferences.observeGoalAchievements(context).collectAsStateWithLifecycle(initialValue = true)
    val language by ProfileSettingsPreferences.observeLanguage(context).collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH_UK)
    val unitSystem by ProfileSettingsPreferences.observeUnitSystem(context).collectAsStateWithLifecycle(initialValue = UnitSystem.METRIC)

    var showEditDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editDialogSession by remember { mutableIntStateOf(0) }

    // image picker
    var profileImageUri by remember { mutableStateOf(uiState.profileImageUri) }
    val imagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        val str = uri?.toString()
        profileImageUri = str
        profileViewModel.setProfileImageUri(str)
    }

    LaunchedEffect(uiState.profileImageUri) {
        profileImageUri = uiState.profileImageUri
    }

    val openEditDialog = {
        editDialogSession += 1
        showEditDialog = true
    }

    LaunchedEffect(uiState.signOutCompleted) {
        if (uiState.signOutCompleted) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            (context as? Activity)?.finish()
            profileViewModel.clearSignOutSignal()
        }
    }

    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            showEditDialog = false
            profileViewModel.clearSaveSignal()
        }
    }

    if (showSettingsDialog) {
        SettingsQuickSheet(
            language = language,
            unitSystem = unitSystem,
            onDismiss = { showSettingsDialog = false },
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            sessionKey = editDialogSession,
            initialDisplayName = uiState.displayName,
            initialHeightCm = uiState.heightCm?.toString().orEmpty(),
            initialWeightKg = uiState.weightKg?.toString().orEmpty(),
            initialFocusArea = uiState.focusArea,
            initialTargetWeightKg = uiState.targetWeightKg?.toString().orEmpty(),
            initialWeeklyGoalSessions = uiState.weeklyGoalSessions.toString(),
            isSaving = uiState.isLoading,
            saveErrorMessage = uiState.saveErrorMessage,
            onPickImage = { imagePicker.launch("image/*") },
            onDismiss = {
                showEditDialog = false
                profileViewModel.clearSaveSignal()
            },
            onSave = { displayName, heightCm, weightKg, focusArea, targetWeightKg, weeklyGoalSessions ->
                profileViewModel.saveProfileEdits(
                    displayName = displayName,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    fitnessGoal = focusArea,
                    targetWeightKg = targetWeightKg,
                    weeklyGoalSessions = weeklyGoalSessions,
                )
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        // ── Header ────────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profile_title), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = onBackground, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .clickable { openEditDialog() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings), tint = primary, modifier = Modifier.size(22.dp)) }
        }

        Spacer(Modifier.height(20.dp))

        // ── User info card ────────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(68.dp)) {
                        if (profileImageUri != null) {
                            val imageUriValue = profileImageUri
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                                },
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                update = { view ->
                                    // Avoid re-loading the same URI on each recomposition (typing in dialog).
                                    if (view.tag != imageUriValue) {
                                        view.tag = imageUriValue
                                        try {
                                            view.setImageURI(Uri.parse(imageUriValue))
                                        } catch (_: Exception) {
                                            view.setImageResource(android.R.drawable.sym_def_app_icon)
                                        }
                                    }
                                },
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(secondary, Color(0xFF4AADAA)))),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(primary)
                                .clickable { openEditDialog() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp)) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(uiState.displayName, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = onBackground, fontFamily = FontFamily.Serif)
                        Text(uiState.memberSince, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textGray, letterSpacing = 1.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = surfaceVariant)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    val heightMetric = convertHeightForDisplay(uiState.heightCm, unitSystem)
                    val weightMetric = convertWeightForDisplay(uiState.weightKg, unitSystem)
                    BodyMetric(label = stringResource(R.string.profile_height_label), value = heightMetric.first, unit = heightMetric.second)
                    VerticalDivider(modifier = Modifier.height(40.dp), color = Color(0xFFF0F0F0))
                    BodyMetric(label = stringResource(R.string.profile_weight_label), value = weightMetric.first, unit = weightMetric.second)
                    VerticalDivider(modifier = Modifier.height(40.dp), color = surfaceVariant)
                    BodyMetric(label = stringResource(R.string.profile_bmi_label), value = uiState.bmi?.toString() ?: "--", unit = "")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Fitness goals card ────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(primary).padding(20.dp),
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_fitness_goals_title), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(50.dp), color = primary.copy(alpha = 0.2f)) {
                        Text(stringResource(R.string.profile_primary_chip), fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                GoalRow(label = stringResource(R.string.profile_focus_area_label), value = uiState.focusArea, valueColor = secondary)
                GoalRow(
                    label = stringResource(R.string.profile_target_weight_label),
                    value = convertWeightForDisplay(uiState.targetWeightKg, unitSystem).let { (value, unit) ->
                        if (value == "--") stringResource(R.string.profile_not_set) else "$value $unit"
                    },
                    valueColor = Color.White,
                )
                GoalRow(label = stringResource(R.string.profile_weekly_goal_label), value = stringResource(R.string.profile_sessions_value, uiState.weeklyGoalSessions), valueColor = Color.White)
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_progress_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Text("${uiState.goalProgressPercent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secondary)
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(primary.copy(alpha = 0.15f))) {
                    Box(modifier = Modifier.fillMaxWidth(uiState.goalProgressPercent / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(secondary))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Workout preferences card ──────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.profile_workout_preferences_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onBackground)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.preferredExercises.take(3).forEach { pref ->
                        Surface(shape = RoundedCornerShape(8.dp), color = surfaceVariant) {
                            Text(pref, fontSize = 13.sp, color = onBackground, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrefDetailBox(label = stringResource(R.string.profile_session_length_label), value = uiState.sessionLengthText, modifier = Modifier.weight(1f))
                    PrefDetailBox(label = stringResource(R.string.profile_difficulty_label), value = uiState.difficultyText, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Health information card ───────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(surfaceVariant).padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(secondary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = onSurface, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.profile_health_insights_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onBackground)
                }
                Spacer(Modifier.height(14.dp))
                uiState.healthInfo.forEachIndexed { index, value ->
                    HealthInfoRow(label = if (index == 0) stringResource(R.string.profile_status_label) else stringResource(R.string.profile_detail_label), value = value)
                    if (index < uiState.healthInfo.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Notifications card ────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.profile_notifications_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onBackground)
                Spacer(Modifier.height(14.dp))
                NotificationToggleRow(
                    label = stringResource(R.string.profile_daily_reminders_label),
                    checked = dailyReminders,
                    onToggle = { enabled ->
                        scope.launch { ProfileSettingsPreferences.setDailyReminders(context, enabled) }
                    },
                )
                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = Color(0xFFF5F5F5))
                Spacer(Modifier.height(4.dp))
                NotificationToggleRow(
                    label = stringResource(R.string.profile_goal_achievements_label),
                    checked = goalAchievements,
                    onToggle = { enabled ->
                        scope.launch { ProfileSettingsPreferences.setGoalAchievements(context, enabled) }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── App settings card ─────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.profile_app_settings_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onBackground)
                Spacer(Modifier.height(14.dp))
                LanguageSettingsRow(language = language, navController = navController)
                HorizontalDivider(color = Color(0xFFF5F5F5))
                UnitsSettingsRow(unitSystem = unitSystem)
                HorizontalDivider(color = Color(0xFFF5F5F5))
                ThemeSettingsRow(navController = navController)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Log out ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().clickable(enabled = !uiState.isLoading) { profileViewModel.signOut() }, contentAlignment = Alignment.Center) {
            Text(if (uiState.isLoading) stringResource(R.string.profile_signing_out) else stringResource(R.string.profile_log_out), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = secondary)
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Body metric ───────────────────────────────────────────────────────────────
@Composable
private fun BodyMetric(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), letterSpacing = 0.8.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            if (unit.isNotEmpty()) { Spacer(Modifier.width(2.dp)); Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 3.dp)) }
        }
    }
}

// ── Goal row ──────────────────────────────────────────────────────────────────
@Composable
private fun GoalRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f))
}

// ── Pref detail box ───────────────────────────────────────────────────────────
@Composable
private fun PrefDetailBox(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Column {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), letterSpacing = 0.8.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

// ── Health info row ───────────────────────────────────────────────────────────
@Composable
private fun HealthInfoRow(label: String, value: String) {
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 0.8.sp)
    Spacer(Modifier.height(2.dp))
    Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
}

// ── Notification toggle row ───────────────────────────────────────────────────
@Composable
private fun NotificationToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFCCCCCC)),
        )
    }
}

// ── Settings row ──────────────────────────────────────────────────────────────
@Composable
private fun LanguageSettingsRow(language: AppLanguage, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_language_title), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                Text(languageDisplayLabel(language), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(languageDisplayLabel(value)) },
                    onClick = {
                        expanded = false
                        scope.launch {
                            ProfileSettingsPreferences.setLanguage(context, value)
                            // A brief delay to allow the Dropdown dismissal to animate before any possible recreation
                            kotlinx.coroutines.delay(150)
                            // Since these are ComponentActivity, AppCompatDelegate might not auto-recreate.
                            // We manually recreate to be absolutely sure the change is applied immediately.
                            context.findActivity()?.let { act ->
                                // Use a clean restart intent to ensure new locales are picked up in attachBaseContext
                                val intent = act.intent
                                navController.currentDestination?.route?.let { route ->
                                    intent.putExtra("TARGET_ROUTE", route)
                                }
                                act.finish()
                                act.startActivity(intent)
                                act.overridePendingTransition(0, 0)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UnitsSettingsRow(unitSystem: UnitSystem) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_units_title), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                Text(unitSystemDisplayLabel(unitSystem), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UnitSystem.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(unitSystemDisplayLabel(value)) },
                    onClick = {
                        expanded = false
                        scope.launch { ProfileSettingsPreferences.setUnitSystem(context, value) }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsQuickSheet(
    language: AppLanguage,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.profile_language_summary, languageDisplayLabel(language)))
                Text(stringResource(R.string.profile_units_summary, unitSystemDisplayLabel(unitSystem)))
                Text(stringResource(R.string.profile_theme_summary))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun EditProfileDialog(
    sessionKey: Int,
    initialDisplayName: String,
    initialHeightCm: String,
    initialWeightKg: String,
    initialFocusArea: String,
    initialTargetWeightKg: String,
    initialWeeklyGoalSessions: String,
    isSaving: Boolean,
    saveErrorMessage: String?,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        heightCm: Int?,
        weightKg: Double?,
        focusArea: String,
        targetWeightKg: Double?,
        weeklyGoalSessions: Int?,
    ) -> Unit,
) {
    var displayName by rememberSaveable(sessionKey) { mutableStateOf(initialDisplayName) }
    var heightCm by rememberSaveable(sessionKey) { mutableStateOf(initialHeightCm) }
    var weightKg by rememberSaveable(sessionKey) { mutableStateOf(initialWeightKg) }
    var focusArea by rememberSaveable(sessionKey) { mutableStateOf(initialFocusArea) }
    var targetWeightKg by rememberSaveable(sessionKey) { mutableStateOf(initialTargetWeightKg) }
    var weeklyGoalSessions by rememberSaveable(sessionKey) { mutableStateOf(initialWeeklyGoalSessions) }
    var localSaveError by rememberSaveable(sessionKey) { mutableStateOf<String?>(null) }

    val isFormValid = (heightCm.toIntOrNull()?.let { it > 0 } == true)
        && (weightKg.toDoubleOrNull()?.let { it > 0.0 } == true)
        && (targetWeightKg.toDoubleOrNull()?.let { it > 0.0 } == true)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 8.dp, modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(520.dp)) {
            Column(modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(16.dp)
            ) {
                // Header: title + change photo
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_edit_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onPickImage) { Text(stringResource(R.string.profile_change_photo)) }
                }

                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

                // Scrollable body
                val scrollState = rememberScrollState()
                Column(modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it; localSaveError = null }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_edit_name)) })
                    OutlinedTextField(value = heightCm, onValueChange = { heightCm = it; localSaveError = null }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_edit_height_cm)) })
                    OutlinedTextField(value = weightKg, onValueChange = { weightKg = it; localSaveError = null }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_edit_weight_kg)) })
                    OutlinedTextField(value = targetWeightKg, onValueChange = { targetWeightKg = it; localSaveError = null }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_edit_target_weight_kg)) })
                    OutlinedTextField(value = weeklyGoalSessions, onValueChange = { weeklyGoalSessions = it; localSaveError = null }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_edit_weekly_goal_sessions)) })
                    OutlinedTextField(value = focusArea, onValueChange = { focusArea = it; localSaveError = null }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.profile_edit_focus_area)) })
                    val effectiveError = saveErrorMessage ?: localSaveError
                    if (!effectiveError.isNullOrBlank()) {
                        Text(effectiveError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }

                // Actions row (sticky at bottom of dialog)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!isFormValid) {
                                localSaveError = "Please enter valid Height, Weight and Target Weight"
                            } else {
                                localSaveError = null
                                onSave(
                                    displayName.trim(),
                                    heightCm.toIntOrNull(),
                                    weightKg.toDoubleOrNull(),
                                    focusArea.trim(),
                                    targetWeightKg.toDoubleOrNull(),
                                    weeklyGoalSessions.toIntOrNull(),
                                )
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    ) {
                        Text(if (isSaving) stringResource(R.string.profile_saving) else stringResource(R.string.common_save), color = Color(0xFF1A1A1A))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun languageDisplayLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH_UK -> stringResource(R.string.profile_language_en_uk)
    AppLanguage.HINDI -> stringResource(R.string.profile_language_hi)
    AppLanguage.KANNADA -> stringResource(R.string.profile_language_kn)
}

@Composable
private fun unitSystemDisplayLabel(unitSystem: UnitSystem): String = when (unitSystem) {
    UnitSystem.METRIC -> stringResource(R.string.profile_units_metric)
    UnitSystem.IMPERIAL -> stringResource(R.string.profile_units_imperial)
}

private fun convertWeightForDisplay(weightKg: Double?, unitSystem: UnitSystem): Pair<String, String> {
    if (weightKg == null) return "--" to if (unitSystem == UnitSystem.METRIC) "kg" else "lb"
    return if (unitSystem == UnitSystem.METRIC) {
        weightKg.roundToInt().toString() to "kg"
    } else {
        (weightKg * 2.20462).roundToInt().toString() to "lb"
    }
}

private fun convertHeightForDisplay(heightCm: Int?, unitSystem: UnitSystem): Pair<String, String> {
    if (heightCm == null) return "--" to if (unitSystem == UnitSystem.METRIC) "cm" else "ft/in"
    return if (unitSystem == UnitSystem.METRIC) {
        heightCm.toString() to "cm"
    } else {
        val totalInches = (heightCm / 2.54).roundToInt()
        val feet = totalInches / 12
        val inches = totalInches % 12
        "$feet'$inches\"" to ""
    }
}

@Composable
private fun ThemeSettingsRow(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by ThemePreferences.observeThemeMode(context).collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_theme_title), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                Text(themeModeDisplayLabel(themeMode), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(themeModeDisplayLabel(mode)) },
                    onClick = {
                        expanded = false
                        scope.launch {
                            ThemePreferences.setThemeMode(context, mode)
                            context.findActivity()?.let { act ->
                                val intent = act.intent
                                navController.currentDestination?.route?.let { route ->
                                    intent.putExtra("TARGET_ROUTE", route)
                                }
                                act.finish()
                                act.startActivity(intent)
                                act.overridePendingTransition(0, 0)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun themeModeDisplayLabel(themeMode: ThemeMode): String = when (themeMode) {
    ThemeMode.SYSTEM -> stringResource(R.string.profile_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.profile_theme_light)
    ThemeMode.DARK -> stringResource(R.string.profile_theme_dark)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
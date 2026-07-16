package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.posemonitor2.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ΓöÇΓöÇ Design tokens ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
private val Orange    = Color(0xFFE85A2E)
private val Yellow    = Color(0xFFE8B840)
private val GreenOk   = Color(0xFF4CAF82)
private val TealBar   = Color(0xFF0A3D3F)
private val TealLight = Color(0xFF4AADAA)

// ΓöÇΓöÇ Screen ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
fun ProgressScreen(
    navController: NavController,
    progressViewModel: ProgressViewModel = hiltViewModel(),
) {
    val uiState by progressViewModel.uiState.collectAsStateWithLifecycle()
    val weeklyActivity = if (uiState.weeklyActivity.isEmpty()) {
        listOf("M" to 0f, "T" to 0f, "W" to 0f, "T" to 0f, "F" to 0f, "S" to 0f, "S" to 0f)
    } else {
        uiState.weeklyActivity
    }

    var showStreakCalendar by remember { mutableStateOf(false) }
    var showAllSessions by remember { mutableStateOf(false) }

    // Theme-aware colors
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val tealColor = MaterialTheme.colorScheme.primary
    val textGrayColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        // ── Header ───────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.progress_title),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = tealColor,
                    fontFamily = FontFamily.Serif,
                )
                Text(
                    stringResource(R.string.progress_subtitle),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealColor,
                )
                Text(
                    stringResource(R.string.progress_description),
                    fontSize = 13.sp,
                    color = textGrayColor,
                    lineHeight = 18.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceColor)
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ShowChart, contentDescription = stringResource(R.string.progress_chart_cd), tint = tealColor, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsRange.entries.forEach { range ->
                val selected = uiState.selectedRange == range
                FilterChip(
                    selected = selected,
                    onClick = { progressViewModel.selectRange(range) },
                    label = { Text(range.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tealColor,
                        selectedLabelColor = if (isDark) Color.Black else Color.White,
                    ),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Stats summary card ─────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(
                        label = stringResource(R.string.progress_sessions_label),
                        value = uiState.sessionsCount.toString(),
                        sub = sessionsDeltaLabel(uiState.sessionsDeltaVsLastWeek, uiState.selectedRange),
                        subColor = if (uiState.sessionsDeltaVsLastWeek >= 0) GreenOk else Orange,
                        modifier = Modifier.weight(1f),
                        tealColor = tealColor,
                        textGrayColor = textGrayColor
                    )
                    StatCell(
                        label = stringResource(R.string.progress_total_time_label),
                        value = "${uiState.totalTimeMinutes} m",
                        sub = null,
                        subColor = textGrayColor,
                        modifier = Modifier.weight(1f),
                        tealColor = tealColor,
                        textGrayColor = textGrayColor
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(
                        label = stringResource(R.string.progress_avg_duration_label),
                        value = "${uiState.avgDurationMinutes} m",
                        sub = null,
                        subColor = textGrayColor,
                        modifier = Modifier.weight(1f),
                        tealColor = tealColor,
                        textGrayColor = textGrayColor
                    )
                    StatCell(
                        label = stringResource(R.string.progress_consistency_label),
                        value = "${uiState.consistencyPercent}%",
                        sub = null,
                        subColor = textGrayColor,
                        modifier = Modifier.weight(1f),
                        tealColor = tealColor,
                        textGrayColor = textGrayColor,
                        valueSuffix = { // orange dot after value
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Orange)
                                    .align(Alignment.CenterVertically),
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricPill(
                title = stringResource(R.string.progress_reps_label),
                value = uiState.totalReps.toString(),
                modifier = Modifier.weight(1f),
                surfaceColor = surfaceColor,
                tealColor = tealColor,
                textGrayColor = textGrayColor
            )
            MetricPill(
                title = stringResource(R.string.progress_accuracy_label),
                value = "${uiState.avgAccuracyPercent}%",
                modifier = Modifier.weight(1f),
                surfaceColor = surfaceColor,
                tealColor = tealColor,
                textGrayColor = textGrayColor
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Weekly activity card ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.progress_weekly_activity_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = tealColor,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.progress_last_7_days), fontSize = 12.sp, color = textGrayColor)
                }

                Spacer(Modifier.height(20.dp))

                // Bar chart
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weeklyActivity.forEach { (day, level) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            // Bar
                            val barHeight = if (level > 0f) (level * 72).dp else 4.dp
                            val barColor  = if (level > 0f) tealColor else MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(barColor),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(day, fontSize = 11.sp, color = textGrayColor)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Workout distribution card ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.progress_workout_distribution_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealColor,
                )
                Spacer(Modifier.height(16.dp))
                uiState.distribution.forEach { item ->
                    DistributionRow(item, tealColor)
                    Spacer(Modifier.height(14.dp))
                }
                if (uiState.distribution.isEmpty()) {
                    Text(stringResource(R.string.progress_no_workout_data), fontSize = 13.sp, color = textGrayColor)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Daily streak banner ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFFE85A2E), Color(0xFFE8803E)),
                    ),
                )
                .clickable { showStreakCalendar = true }
                .padding(20.dp),
        ) {
            // Decorative faint triangle / shape top right
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 20.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF)),
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 20.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.progress_daily_streak_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.progress_days_value, uiState.dailyStreakDays),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                )
                Text(
                    stringResource(R.string.progress_keep_streak_alive),
                    fontSize = 13.sp,
                    color = Color(0xCCFFFFFF),
                )
            }

            // Best badge — top right
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.progress_best_label),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xCCFFFFFF),
                    letterSpacing = 1.sp,
                )
                Text(
                    stringResource(R.string.progress_days_lower_value, uiState.bestStreakDays),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Recent sessions ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.progress_recent_sessions_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = tealColor,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.progress_see_all),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Orange,
                modifier = Modifier.clickable { showAllSessions = true },
            )
        }

        Spacer(Modifier.height(12.dp))

        uiState.recentSessions.forEach { session ->
            RecentSessionRow(session, tealColor, textGrayColor)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp)
        }

        if (uiState.recentSessions.isEmpty()) {
            Text(
                text = if (uiState.isLoading) stringResource(R.string.progress_loading_stats) else stringResource(R.string.progress_no_sessions_yet),
                fontSize = 13.sp,
                color = textGrayColor,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // ── Streak Calendar Bottom Sheet ────────────────────────────────────────
    if (showStreakCalendar) {
        StreakCalendarSheet(
            activeDays = uiState.activeDays,
            currentStreak = uiState.dailyStreakDays,
            bestStreak = uiState.bestStreakDays,
            onDismiss = { showStreakCalendar = false },
        )
    }

    // ── All Sessions Bottom Sheet ───────────────────────────────────────────
    if (showAllSessions) {
        AllSessionsSheet(
            sessions = uiState.allSessions,
            onDismiss = { showAllSessions = false },
        )
    }
}

@Composable
private fun sessionsDeltaLabel(delta: Int, range: StatsRange): String = when {
    delta > 0 -> stringResource(R.string.progress_delta_positive, delta, range.label)
    delta < 0 -> stringResource(R.string.progress_delta_negative, delta, range.label)
    else -> stringResource(R.string.progress_delta_none, range.label)
}

// ── Stat cell ──────────────────────────────────────────────────────────────────
@Composable
private fun StatCell(
    label: String,
    value: String,
    sub: String?,
    subColor: Color,
    modifier: Modifier = Modifier,
    tealColor: Color,
    textGrayColor: Color,
    valueSuffix: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textGrayColor, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = tealColor)
            valueSuffix?.invoke(this)
        }
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, color = subColor, fontWeight = FontWeight.Medium)
        }
    }
}

// ── Distribution row ───────────────────────────────────────────────────────────
@Composable
private fun DistributionRow(item: DistributionUiItem, tealColor: Color) {
    val color = when (item.colorIndex % 4) {
        0 -> TealBar
        1 -> Orange
        2 -> Yellow
        else -> TealLight
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.label,
                fontSize = 13.sp,
                color = tealColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${item.percent}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = tealColor,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(item.percent / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

// ── Recent session row ──────────────────────────────────────────────────────────
@Composable
private fun RecentSessionRow(session: RecentSessionUiItem, tealColor: Color, textGrayColor: Color) {
    val title = session.title ?: stringResource(R.string.progress_pose_session_fallback)
    val subtitle = remember(session.startedAtEpochMs, session.durationMinutes) {
        val todayStart = startOfDayMillis(System.currentTimeMillis())
        val dayStart = startOfDayMillis(session.startedAtEpochMs)
        val dayLabel = when (dayStart) {
            todayStart -> "__TODAY__"
            addDays(todayStart, -1) -> "__YESTERDAY__"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(session.startedAtEpochMs))
        }
        dayLabel
    }

    val resolvedDayLabel = when (subtitle) {
        "__TODAY__" -> stringResource(R.string.progress_today)
        "__YESTERDAY__" -> stringResource(R.string.progress_yesterday)
        else -> subtitle
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = tealColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tealColor)
            Text(
                stringResource(R.string.progress_session_subtitle, resolvedDayLabel, session.durationMinutes),
                fontSize = 12.sp,
                color = textGrayColor,
            )
        }
        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.progress_done_cd), tint = GreenOk, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    surfaceColor: Color,
    tealColor: Color,
    textGrayColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = surfaceColor,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textGrayColor, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = tealColor)
        }
    }
}

private fun startOfDayMillis(epochMs: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = epochMs }
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun addDays(epochMs: Long, days: Int): Long {
    val c = Calendar.getInstance().apply { timeInMillis = epochMs }
    c.add(Calendar.DAY_OF_YEAR, days)
    return c.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreakCalendarSheet(
    activeDays: Set<Long>,
    currentStreak: Int,
    bestStreak: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Streak Calendar",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Days you worked out are highlighted",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥 $currentStreak", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Orange)
                    Text("Current", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆 $bestStreak", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Orange)
                    Text("Best", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Calendar grid for the current month
            val calendar = remember { Calendar.getInstance() }
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            Text(
                text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            // Weekday headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Calendar days
            val firstDayCal = Calendar.getInstance().apply {
                set(currentYear, currentMonth, 1)
            }
            val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
            val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

            val rows = kotlin.math.ceil((firstDayOfWeek + daysInMonth) / 7.0).toInt()
            var dayCounter = 1 - firstDayOfWeek

            repeat(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val dayNum = dayCounter++
                        if (dayNum in 1..daysInMonth) {
                            val dayCal = Calendar.getInstance().apply {
                                set(currentYear, currentMonth, dayNum, 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val dayMs = dayCal.timeInMillis
                            val isActive = activeDays.contains(dayMs)
                            val isToday = dayMs == startOfDayMillis(System.currentTimeMillis())

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isActive -> Orange
                                            isToday -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$dayNum",
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isActive -> Color.White
                                        isToday -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllSessionsSheet(
    sessions: List<RecentSessionUiItem>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "All Sessions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${sessions.size} sessions total",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No sessions yet",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    items(sessions.size) { index ->
                        val session = sessions[index]
                        val title = session.title?.split('-')?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) } ?: "Workout"
                        val dateStr = dateFormat.format(Date(session.startedAtEpochMs))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = dateStr,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${session.durationMinutes}m",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (session.isPartial) {
                                    Text(
                                        text = "Partial",
                                        fontSize = 10.sp,
                                        color = Orange,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
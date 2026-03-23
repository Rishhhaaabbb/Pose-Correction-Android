package com.posemonitor2.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PageBg = Color(0xFFF2F1EE)
private val TitleColor = Color(0xFF0D3E40)
private val Muted = Color(0xFF8B959E)
private val CardBg = Color(0xFFF8F8F8)

@Composable
fun ProgressScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Header()

        Spacer(Modifier.height(20.dp))
        StatsSummaryCard()

        Spacer(Modifier.height(14.dp))
        WeeklyActivityCard()

        Spacer(Modifier.height(14.dp))
        WorkoutDistributionCard()

        Spacer(Modifier.height(14.dp))
        DailyStreakCard()

        Spacer(Modifier.height(20.dp))
        RecentSessionsHeader()
        Spacer(Modifier.height(10.dp))
        RecentSessionRow("Morning Mobility", "Today - 15 min", Icons.Outlined.FavoriteBorder, Color(0xFFECF8F5))
        Spacer(Modifier.height(10.dp))
        RecentSessionRow("Posture Core", "Yesterday - 20 min", Icons.Outlined.Insights, Color(0xFFFFF6E8))
        Spacer(Modifier.height(10.dp))
        RecentSessionRow("Evening Stretch", "2 days ago - 10 min", Icons.Outlined.Schedule, Color(0xFFFFFBEA))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Stats", fontSize = 40.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)
            Text("Your Progress", fontSize = 34.sp, color = Color(0xFF1A1B1D), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Track your workout consistency and improvements",
                fontSize = 19.sp,
                color = Muted,
                lineHeight = 26.sp,
            )
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Insights, contentDescription = "Stats", tint = TitleColor)
        }
    }
}

@Composable
private fun StatsSummaryCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatMetric("SESSIONS", "5", "+1 vs last week", highlight = Color(0xFF2CB46F), modifier = Modifier.weight(1f))
                StatMetric("TOTAL TIME", "120 m", "Target 150m", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatMetric("AVG DURATION", "24 m", "", modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                    Column {
                        Text("CONSISTENCY", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                        Text("85%", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TitleColor)
                    }
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFE76F2F), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMetric(
    label: String,
    value: String,
    footnote: String,
    modifier: Modifier = Modifier,
    highlight: Color = Muted,
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 34.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)
        if (footnote.isNotBlank()) {
            Text(footnote, fontSize = 12.sp, color = highlight, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WeeklyActivityCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly Activity", fontSize = 32.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("Last 7 days", fontSize = 14.sp, color = Muted)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(16.dp, 28.dp, 44.dp, 24.dp, 56.dp, 34.dp, 12.dp).forEach { barHeight ->
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(barHeight)
                            .background(Color(0xFF1E6B6C), RoundedCornerShape(8.dp)),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Text(day, color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun WorkoutDistributionCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("Workout Distribution", fontSize = 33.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))

            DistributionRow("Stretching", 0.60f, "60%", Color(0xFF1E6B6C))
            DistributionRow("Strength", 0.20f, "20%", Color(0xFFFF7A30))
            DistributionRow("Mobility", 0.15f, "15%", Color(0xFFF6BE38))
            DistributionRow("Balance", 0.05f, "5%", Color(0xFF87E4D6))
        }
    }
}

@Composable
private fun DistributionRow(label: String, progress: Float, percent: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = Color(0xFF111317), modifier = Modifier.weight(1f))
        Text(percent, fontSize = 16.sp, color = Color(0xFF111317), fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    ProgressBar(progress = progress, color = color)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ProgressBar(progress: Float, color: Color, height: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(0xFFECECEC), RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color, RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun DailyStreakCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF26750), RoundedCornerShape(30.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        Column {
            Text("Daily Streak", color = Color(0xFFFFE7DF), fontSize = 33.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("5 Days", color = Color.White, fontSize = 49.sp, fontWeight = FontWeight.ExtraBold)
            Text("Keep the streak alive!", color = Color(0xFFFFE7DF), fontSize = 21.sp)
        }

        Column(modifier = Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
            Text("BEST", color = Color(0xFFFFE7DF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("12 days", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        }

        Canvas(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.BottomEnd),
        ) {
            drawArc(
                color = Color(0x44FFD8CF),
                startAngle = 200f,
                sweepAngle = 120f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 36f, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun RecentSessionsHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Recent Sessions", color = TitleColor, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        Text(
            "See All",
            color = Color(0xFFE76F2F),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { },
        )
    }
}

@Composable
private fun RecentSessionRow(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconBg: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(iconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TitleColor)
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, color = Color(0xFF111317), fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 14.sp, color = Muted)
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .border(1.dp, Color(0xFFE7EFEA), CircleShape)
                .background(Color(0xFFF4FBF7), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Check, contentDescription = "Completed", tint = Color(0xFF37C98D))
        }
    }
}

package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PageBg = Color(0xFFF2F1EE)
private val TitleColor = Color(0xFF0D3E40)
private val Muted = Color(0xFF8B959E)
private val CardBg = Color(0xFFF8F8F8)
private val TealAccent = Color(0xFF1E6B6C)
private val Gold = Color(0xFFF6BE38)

@Composable
fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        HeaderWithSettings()

        Spacer(Modifier.height(20.dp))
        ProfileStatsCard()

        Spacer(Modifier.height(14.dp))
        FitnessGoalsCard()

        Spacer(Modifier.height(14.dp))
        WorkoutPreferencesCard()

        Spacer(Modifier.height(14.dp))
        HealthInformationCard()

        Spacer(Modifier.height(14.dp))
        NotificationsSection()

        Spacer(Modifier.height(14.dp))
        AppSettingsSection()

        Spacer(Modifier.height(20.dp))
        LogOutButton()

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeaderWithSettings() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Profile", fontSize = 40.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TitleColor, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ProfileStatsCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .border(4.dp, Color(0xFFD4A574), CircleShape)
                        .background(Color(0xFFCFC0A8), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.width(16.dp))

                Column {
                    Text("Alex", fontSize = 28.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)
                    Text("MEMBER SINCE 2023", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem("HEIGHT", "178 cm", modifier = Modifier.weight(1f))
                StatItem("WEIGHT", "70 kg", modifier = Modifier.weight(1f))
                StatItem("BMI", "22.4", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 22.sp, color = TitleColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FitnessGoalsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealAccent, RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fitness Goals", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(Color(0x44FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text("Primary", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            GoalRow("Focus Area", "Flexibility", Color.White)
            Spacer(Modifier.height(10.dp))
            GoalRow("Target Weight", "65 kg", Color.White)
            Spacer(Modifier.height(10.dp))
            GoalRow("Weekly Goal", "4 Sessions", Color.White)

            Spacer(Modifier.height(14.dp))

            Text("PROGRESS", fontSize = 11.sp, color = Color(0xFFD4E8C0), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            ProgressBar(progress = 0.75f, color = Gold, height = 8.dp)
            Spacer(Modifier.height(4.dp))
            Text("75%", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GoalRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 16.sp, color = Color(0xFFCBE3DF))
        Text(value, fontSize = 16.sp, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color, height: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color(0x44FFFFFF), RoundedCornerShape(50)),
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
private fun WorkoutPreferencesCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Workout Preferences", fontSize = 28.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)

            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreferenceTag("Yoga")
                PreferenceTag("Strength")
                PreferenceTag("Mobility")
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PreferenceSetting("SESSION LENGTH", "20 - 30 min", modifier = Modifier.weight(1f))
                PreferenceSetting("DIFFICULTY", "Intermediate", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreferenceTag(label: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFF0F0F0), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 14.sp, color = TitleColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PreferenceSetting(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 16.sp, color = TitleColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HealthInformationCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E6), RoundedCornerShape(24.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = "Health Info", tint = Color(0xFFF5A623), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Health Information", fontSize = 20.sp, color = TitleColor, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            HealthItem("INJURIES", "Minor lower back strain (recovering)")
            Spacer(Modifier.height(10.dp))
            HealthItem("MOBILITY LIMITATIONS", "Tight hamstrings")
            Spacer(Modifier.height(10.dp))
            HealthItem("POSTURE CONCERNS", "Forward head posture (tech neck)")
        }
    }
}

@Composable
private fun HealthItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 14.sp, color = TitleColor)
    }
}

@Composable
private fun NotificationsSection() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Notifications", fontSize = 28.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold)

            Spacer(Modifier.height(14.dp))

            var dailyReminders by remember { mutableStateOf(true) }
            NotificationToggleRow("Daily Reminders", dailyReminders) { dailyReminders = it }

            Spacer(Modifier.height(12.dp))

            var goalAchievements by remember { mutableStateOf(true) }
            NotificationToggleRow("Goal Achievements", goalAchievements) { goalAchievements = it }
        }
    }
}

@Composable
private fun NotificationToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = TitleColor, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TealAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Muted,
            ),
        )
    }
}

@Composable
private fun AppSettingsSection() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text("App Settings", fontSize = 28.sp, color = TitleColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))

            SettingRow("Language", "English (US)")
            SettingRow("Units", "Kilograms (kg), Centimeters (cm)")
            SettingRow("Dark Mode", "Follow system settings")
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = TitleColor, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 13.sp, color = Muted)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LogOutButton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFEBEE), RoundedCornerShape(16.dp))
            .clickable { }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Log Out", fontSize = 18.sp, color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold)
    }
}

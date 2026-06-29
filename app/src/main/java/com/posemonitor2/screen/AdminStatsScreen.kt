package com.posemonitor2.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

import androidx.compose.ui.res.stringResource
import com.posemonitor2.R

private val BgCream     = Color(0xFFF2EEE4)
private val TealDark    = Color(0xFF0A3D3F)
private val Orange      = Color(0xFFE85A2E)
private val Yellow      = Color(0xFFE8B840)
private val CardWhite   = Color.White
private val LightOrange = Color(0xFFFFF3E0)
private val LightRed    = Color(0xFFFFEBEE)

@Composable
fun AdminStatsScreen(
    navController: NavController,
    viewModel: AdminStatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCream)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = TealDark)
            }
            Text(
                stringResource(R.string.admin_stats_dashboard),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TealDark,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            stringResource(R.string.admin_stats_namaste),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TealDark,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp)
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealDark)
            }
        } else {
            StatCard(
                title = stringResource(R.string.admin_stats_total_users),
                value = uiState.totalUsers.toString(),
                badgeText = "",
                badgeColor = Color.Transparent,
                icon = Icons.Filled.Group,
                iconBg = Color(0xFFC8E6C9),
                iconTint = TealDark
            )

            StatCard(
                title = stringResource(R.string.admin_stats_new_members),
                value = uiState.newMembers.toString(),
                badgeText = "+${(uiState.newMembers * 0.3).toInt()}",
                badgeColor = Orange,
                icon = Icons.Filled.PersonAdd,
                iconBg = LightOrange,
                iconTint = Orange
            )

            StatCard(
                title = stringResource(R.string.admin_stats_live_users),
                value = uiState.liveUsers.toString(),
                badgeText = stringResource(R.string.admin_stats_live_now),
                badgeColor = Color(0xFFFF5252),
                icon = Icons.Filled.FlashOn,
                iconBg = LightRed,
                iconTint = Yellow
            )

            CommunityGrowthCard(uiState.growthData)
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    badgeText: String,
    badgeColor: Color,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color = TealDark
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 0.5.sp)
                Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TealDark, fontFamily = FontFamily.Serif)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (badgeText.isEmpty()) Color.Transparent else iconBg.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (badgeText.isNotEmpty()) {
                    Text(
                        badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityGrowthCard(growthData: List<Float>) {
    var isWeekly by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.admin_stats_community_growth), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TealDark, lineHeight = 24.sp)
                
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        ToggleOption(stringResource(R.string.admin_stats_week), isWeekly) { isWeekly = true }
                        ToggleOption(stringResource(R.string.admin_stats_month), !isWeekly) { isWeekly = false }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            
            GrowthGraph(growthData)
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val days = listOf(
                    stringResource(R.string.admin_stats_mon),
                    stringResource(R.string.admin_stats_tue),
                    stringResource(R.string.admin_stats_wed),
                    stringResource(R.string.admin_stats_thu),
                    stringResource(R.string.admin_stats_fri),
                    stringResource(R.string.admin_stats_sat),
                    stringResource(R.string.admin_stats_sun)
                )
                days.forEach { day ->
                    Text(day, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ToggleOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) TealDark else Color.Transparent,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color.Gray
        )
    }
}

@Composable
private fun GrowthGraph(points: List<Float>) {
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(140.dp)) {
        if (points.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        
        val path = Path()
        val segmentWidth = width / (points.size - 1)
        
        path.moveTo(0f, height * points[0])
        
        for (i in 1 until points.size) {
            val x1 = (i - 1) * segmentWidth
            val y1 = height * points[i - 1]
            val x2 = i * segmentWidth
            val y2 = height * points[i]
            
            path.cubicTo(
                x1 + segmentWidth / 2f, y1,
                x1 + segmentWidth / 2f, y2,
                x2, y2
            )
        }
        
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(TealDark.copy(alpha = 0.25f), Color.Transparent)
            )
        )
        
        drawPath(
            path = path,
            color = TealDark,
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

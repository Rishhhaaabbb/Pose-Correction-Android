package com.posemonitor2.screen

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.posemonitor2.MainActivity
import com.posemonitor2.R

// ── Design tokens ─────────────────────────────────────────────────────────────
private val BgCream     = Color(0xFFF2EEE4)
private val TealDark    = Color(0xFF0A3D3F)
private val TealMid     = Color(0xFF1A5C3A)
private val TealChip    = Color(0xFF0D4A4E)
private val Yellow      = Color(0xFFE8B840)
private val Orange      = Color(0xFFE85A2E)
private val TextDark    = Color(0xFF1A1A1A)
private val TextGray    = Color(0xFF888888)
private val CardWhite   = Color.White

// ── Screen ────────────────────────────────────────────────────────────────────
@Composable
fun UserHomeScreen(navController: NavController) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCream)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        HomeHeader(userName = "Alex")
        Spacer(Modifier.height(16.dp))

        HeroCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            onJoinSession = { context.startActivity(Intent(context, MainActivity::class.java)) },
        )
        Spacer(Modifier.height(20.dp))

        QuickActionsRow(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(24.dp))

        SectionTitle(
            title = "My Body Vitals",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        VitalsCard(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))

        CustomRoutineCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            onStart = { context.startActivity(Intent(context, MainActivity::class.java)) },
        )
        Spacer(Modifier.height(16.dp))

        AuraAiCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            onStart = { context.startActivity(Intent(context, MainActivity::class.java)) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
@Composable
private fun HomeHeader(userName: String) {
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
                text = "GOOD MORNING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Orange,
                letterSpacing = 1.sp,
            )
            Text(
                text = userName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TealDark,
                fontFamily = FontFamily.Serif,
            )
        }

        // Filter / sliders icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardWhite)
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Tune, contentDescription = "Settings", tint = TealDark, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────
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
            // Left — yellow, text + button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Yellow)
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                    // Chip
                    Surface(shape = RoundedCornerShape(6.dp), color = TealDark) {
                        Text(
                            "AI ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    // Title
                    Text(
                        text = "Your\nSmart\nSession",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TealDark,
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
                            Text("Join Session", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Right — yoga photo
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFC4A882)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.homepageyogapose),
                    contentDescription = "Yoga pose",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ── Quick actions ─────────────────────────────────────────────────────────────
private data class QAction(val label: String, val icon: ImageVector, val bg: Color, val iconTint: Color)

private val quickActions = listOf(
    QAction("FOCUS",    Icons.Outlined.CenterFocusWeak, TealDark,   Color.White),
    QAction("VITALS",   Icons.Outlined.FavoriteBorder,  CardWhite,  TealDark),
    QAction("ACTIVITY", Icons.AutoMirrored.Outlined.DirectionsRun, Orange, Color.White),
    QAction("AI COACH", Icons.Default.Bolt,             Yellow,     TealDark),
)

@Composable
private fun QuickActionsRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        quickActions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(if (action.bg == CardWhite) 3.dp else 0.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(action.bg)
                        .clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(action.icon, contentDescription = action.label, tint = action.iconTint, modifier = Modifier.size(26.dp))
                }
                Text(action.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextGray, letterSpacing = 0.5.sp)
            }
        }
    }
}

// ── Section title ─────────────────────────────────────────────────────────────
@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Orange, modifier = Modifier.size(22.dp))
    }
}

// ── Vitals card ───────────────────────────────────────────────────────────────
@Composable
private fun VitalsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
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
                Icon(Icons.Default.SelfImprovement, contentDescription = null, tint = Color(0xFF999999), modifier = Modifier.size(32.dp))
            }

            // BMI + progress
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("CURRENT STATUS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Orange, letterSpacing = 0.8.sp)
                Text("BMI: 22.4", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                LinearProgressIndicator(
                    progress = { 0.55f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Orange,
                    trackColor = Color(0xFFEEEEEE),
                )
            }

            // Weight goal
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("WEIGHT GOAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextGray, letterSpacing = 0.8.sp)
                Text("70kg / 65kg", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
            }
        }
    }
}

// ── Custom routine card ───────────────────────────────────────────────────────
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
        Text("✦", fontSize = 60.sp, color = Color(0x221A8A5A), modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp))
        Text("✦", fontSize = 36.sp, color = Color(0x221A8A5A), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 28.dp, end = 50.dp))

        Column(modifier = Modifier.padding(18.dp)) {
            // Chip
            Surface(shape = RoundedCornerShape(6.dp), color = TealChip) {
                Text("ADVANCED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Yellow, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }

            Spacer(Modifier.height(10.dp))

            Text("Custom", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif, lineHeight = 32.sp)
            Text("Routine", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Yellow, fontFamily = FontFamily.Serif, lineHeight = 32.sp)

            Spacer(Modifier.height(8.dp))

            Text(
                "Personalized AI-generated\nsequences based on your\nweekly posture data.",
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
                Text("TRAINING NOW", fontSize = 10.sp, color = Color(0xAAFFFFFF), letterSpacing = 0.5.sp)

                Spacer(Modifier.weight(1f))

                // Yellow arrow button
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Yellow).clickable { onStart() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TealDark, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ── Aura AI card ──────────────────────────────────────────────────────────────
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
                Text("CURATED FOR YOU", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Aura AI\nRecommended",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                lineHeight = 32.sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Goal-oriented sequences for\nyour current vitals.",
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
                        "SCIENTIFICALLY\nVALIDATED",
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
                    color = TealDark,
                    modifier = Modifier.clickable { onStart() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("START\nSESSION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 14.sp)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

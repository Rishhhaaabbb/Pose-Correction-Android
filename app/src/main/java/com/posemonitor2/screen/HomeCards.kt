package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.posemonitor2.R
import com.posemonitor2.ui.theme.LocalAppColors

@Composable
internal fun CustomWorkoutQuickCard(
    modifier: Modifier = Modifier,
    onOpenCustomWorkout: () -> Unit,
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(5.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        appColors.workoutTealDark,
                        appColors.workoutTeal,
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
                        tint = appColors.warning,
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

@Composable
internal fun CustomRoutineCard(modifier: Modifier = Modifier, onStart: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors = listOf(appColors.workoutGreenDark, appColors.workoutGreen))),
    ) {
        Text("\u2728", fontSize = 60.sp, color = Color(0x221A8A5A), modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp))
        Text("\u2728", fontSize = 36.sp, color = Color(0x221A8A5A), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 28.dp, end = 50.dp))

        Column(modifier = Modifier.padding(18.dp)) {
            Surface(shape = RoundedCornerShape(6.dp), color = appColors.workoutChip) {
                Text(stringResource(R.string.home_advanced), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = appColors.warning, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }

            Spacer(Modifier.height(10.dp))

            Text(stringResource(R.string.home_custom), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif, lineHeight = 32.sp)
            Text(stringResource(R.string.home_routine), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = appColors.warning, fontFamily = FontFamily.Serif, lineHeight = 32.sp)

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.home_custom_routine_desc),
                fontSize = 13.sp,
                color = Color(0xBBFFFFFF),
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(56.dp).height(28.dp)) {
                    listOf(appColors.success, appColors.info).forEachIndexed { i, color ->
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

                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(appColors.warning).clickable { onStart() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
internal fun AuraAiCard(modifier: Modifier = Modifier, onStart: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(appColors.accentVariant),
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 30.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF)),
        )

        Column(modifier = Modifier.padding(18.dp)) {
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
internal fun AdminStatsShortcutCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = appColors.primary,
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

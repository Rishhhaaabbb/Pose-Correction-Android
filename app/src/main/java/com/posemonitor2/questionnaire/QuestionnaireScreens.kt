package com.posemonitor2.questionnaire

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Design tokens ──────────────────────────────────────────────────────────────
val QBg           = Color(0xFFF0ECFA)
val QBgGradient   = Brush.verticalGradient(listOf(Color(0xFFEDE8F8), Color(0xFFF8F5FF)))
val QPurple       = Color(0xFF7B4FD8)
val QPurpleLight  = Color(0xFFEDE5FF)
val QPurpleBorder = Color(0xFF7B4FD8)
val QText         = Color(0xFF1A1036)
val QSubtext      = Color(0xFF888899)
val QCardBg       = Color.White
val QDivider      = Color(0xFFEEEEF2)

// ── QStepIndicator ─────────────────────────────────────────────────────────────
@Composable
fun QStepIndicator(currentPage: Int, modifier: Modifier = Modifier) {
    val milestones = listOf(
        Icons.Filled.TrackChanges,
        Icons.Filled.FitnessCenter,
        Icons.Filled.MonitorWeight,
        Icons.Filled.EmojiEvents,
    )
    val activeMilestone = when {
        currentPage <= 3  -> 0
        currentPage <= 6  -> 1
        currentPage <= 9  -> 2
        else              -> 3
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        milestones.forEachIndexed { index, icon ->
            val isCompleted = index < activeMilestone
            val isActive    = index == activeMilestone
            val bubbleColor by animateColorAsState(
                targetValue = if (isCompleted || isActive) QPurple else Color.Transparent,
                animationSpec = tween(300), label = "bubble$index"
            )
            val iconTint by animateColorAsState(
                targetValue = if (isCompleted || isActive) Color.White else QSubtext,
                animationSpec = tween(300), label = "tint$index"
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bubbleColor)
                    .then(
                        if (!isCompleted && !isActive)
                            Modifier.border(1.5.dp, QDivider, CircleShape)
                        else if (isActive)
                            Modifier.border(2.dp, QPurpleBorder, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Filled.Check else icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (index < milestones.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (isCompleted) QPurple else QDivider),
                )
            }
        }
    }
}

// ── QTipBox ────────────────────────────────────────────────────────────────────
@Composable
fun QTipBox(tip: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = QCardBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text("💡", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(tip, fontSize = 13.sp, color = QSubtext, lineHeight = 19.sp)
        }
    }
}

// ── QPageScaffold ──────────────────────────────────────────────────────────────
@Composable
fun QPageScaffold(
    currentPage: Int,
    title: String,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    tip: String? = null,
    onContinue: (() -> Unit)? = null,
    continueEnabled: Boolean = true,
    continueLabel: String = "CONTINUE",
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QBgGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = QText)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
                Spacer(Modifier.weight(1f))
                if (onSkip != null) {
                    TextButton(onClick = onSkip) {
                        Text("Skip", color = QSubtext, fontSize = 14.sp)
                    }
                }
            }

            // Step indicator
            QStepIndicator(currentPage = currentPage)

            Spacer(Modifier.height(8.dp))

            // Title
            Text(
                text = title.uppercase(),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = QText,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Optional tip
            if (tip != null) {
                QTipBox(tip = tip, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }

            Spacer(Modifier.height(12.dp))

            // Content slot
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                content()
            }

            Spacer(Modifier.height(16.dp))

            // Continue button
            if (onContinue != null) {
                Button(
                    onClick = onContinue,
                    enabled = continueEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QPurple,
                        disabledContainerColor = QPurple.copy(alpha = 0.35f),
                    ),
                ) {
                    Text(
                        continueLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── AgeGroupScreen ─────────────────────────────────────────────────────────────
private data class AgeOption(val key: String, val label: String, val emoji: String)

@Composable
fun AgeGroupScreen(
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    currentPage: Int,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    val options = listOf(
        AgeOption(">60",   "55+",    "👴"),
        AgeOption("51-60", "45–54",  "🧔"),
        AgeOption("41-50", "35–44",  "🧑"),
        AgeOption("31-40", "25–34",  "🙋"),
        AgeOption("20-30", "18–24",  "🧑‍🦱"),
        AgeOption("<20",   "Under 18","🧒"),
    )
    QPageScaffold(
        currentPage = currentPage,
        title = "How old are you?",
        onBack = onBack,
        onSkip = onSkip,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 600.dp),
        ) {
            items(options) { opt ->
                val isSelected = opt.key == selectedValue
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) QPurpleLight else QCardBg,
                    animationSpec = tween(200), label = "age_bg_${opt.key}"
                )
                Card(
                    onClick = { onSelect(opt.key); onNext() },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) QPurpleBorder else QDivider,
                    ),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(opt.emoji, fontSize = 28.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(opt.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = QText)
                        Text("Years old", fontSize = 11.sp, color = QSubtext)
                    }
                }
            }
        }
    }
}

// ── GenderScreen ───────────────────────────────────────────────────────────────
@Composable
fun GenderScreen(
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    currentPage: Int,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    QPageScaffold(
        currentPage = currentPage,
        title = "What is your gender?",
        onBack = onBack,
        onSkip = onSkip,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf("Male" to "🧔", "Female" to "🧕").forEach { (label, emoji) ->
                val isSelected = label == selectedValue
                val bgColor by animateColorAsState(
                    if (isSelected) QPurpleLight else QCardBg, tween(200), label = "gender_$label"
                )
                Card(
                    onClick = { onSelect(label); onNext() },
                    modifier = Modifier.weight(1f).height(130.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) QPurpleBorder else QDivider),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                                .background(if (isSelected) QPurple.copy(alpha = 0.1f) else QBg),
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 32.sp) }
                        Spacer(Modifier.height(6.dp))
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = QText)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val isPNS = selectedValue == "Prefer Not to Say"
        val pnsBg by animateColorAsState(if (isPNS) QPurpleLight else QCardBg, tween(200), label = "pns_bg")
        Card(
            onClick = { onSelect("Prefer Not to Say"); onNext() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(if (isPNS) 2.dp else 1.dp, if (isPNS) QPurpleBorder else QDivider),
            colors = CardDefaults.cardColors(containerColor = pnsBg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🤐", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text("Prefer Not to Say", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = QText)
            }
        }
    }
}

// ── GoalScreen ─────────────────────────────────────────────────────────────────
private data class GoalOption(val key: String, val label: String, val emoji: String)

@Composable
fun GoalScreen(
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    currentPage: Int,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    val options = listOf(
        GoalOption("Lose Weight",       "Lose Weight",      "🔥"),
        GoalOption("Build Strength",    "Build Strength",   "💪"),
        GoalOption("General Fitness",   "General Fitness",  "🏃"),
        GoalOption("Improve Flexibility","Improve\nFlexibility","🧘"),
        GoalOption("Increase Mobility", "Increase\nMobility","❤️"),
        GoalOption("Relieve Stress",    "Relieve Stress",   "😌"),
    )
    QPageScaffold(
        currentPage = currentPage,
        title = "What is your primary goal?",
        onBack = onBack,
        onSkip = onSkip,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 600.dp),
        ) {
            items(options) { opt ->
                val isSelected = opt.key == selectedValue
                val bgColor by animateColorAsState(
                    if (isSelected) QPurpleLight else QCardBg, tween(200), label = "goal_${opt.key}"
                )
                Card(
                    onClick = { onSelect(opt.key); onNext() },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) QPurpleBorder else QDivider),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(opt.emoji, fontSize = 28.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            opt.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = QText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// ── YogaStyleScreen ────────────────────────────────────────────────────────────
@Composable
fun YogaStyleScreen(
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    currentPage: Int,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    val options = listOf(
        "Hatha Yoga" to "🧘",
        "Vinyasa Yoga" to "🌊",
        "Yin Yoga" to "🌙",
        "Power Yoga" to "⚡",
        "Chair Yoga" to "🪑",
        "Meditation & Breathing" to "🌸",
    )
    QPageScaffold(
        currentPage = currentPage,
        title = "Which yoga style interests you?",
        onBack = onBack,
        onSkip = onSkip,
        onContinue = onNext,
        continueEnabled = selectedValue != null,
    ) {
        options.forEach { (label, emoji) ->
            val isSelected = label == selectedValue
            val bgColor by animateColorAsState(
                if (isSelected) QPurpleLight else QCardBg, tween(200), label = "yoga_$label"
            )
            Card(
                onClick = { onSelect(label) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) QPurpleBorder else QDivider),
                colors = CardDefaults.cardColors(containerColor = bgColor),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = QText)
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

// ── SingleChoiceQuestionScreen ─────────────────────────────────────────────────
@Composable
fun SingleChoiceQuestionScreen(
    title: String,
    subtitle: String = "",
    options: List<String>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    currentPage: Int = 0,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    showSkip: Boolean = false,
    autoAdvance: Boolean = false,
    icons: List<ImageVector>? = null,
) {
    QPageScaffold(
        currentPage = currentPage,
        title = title,
        onBack = onBack,
        onSkip = if (showSkip) onSkip else null,
        tip = if (subtitle.isNotBlank()) subtitle else null,
        onContinue = if (!autoAdvance) onNext else null,
        continueEnabled = selectedValue != null,
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selectedValue
            val bgColor by animateColorAsState(
                if (isSelected) QPurpleLight else QCardBg, tween(200), label = "sc_$option"
            )
            Card(
                onClick = {
                    onSelect(option)
                    if (autoAdvance) onNext()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) QPurpleBorder else QDivider),
                colors = CardDefaults.cardColors(containerColor = bgColor),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = icons?.getOrNull(index)
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) QPurple else QSubtext,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) QPurple else Color.Transparent)
                                .then(if (!isSelected) Modifier.border(1.5.dp, QSubtext, CircleShape) else Modifier),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(option, fontSize = 15.sp, color = QText, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// Backward-compat overload matching old signature
@Composable
fun SingleChoiceQuestionScreen(
    question: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) = SingleChoiceQuestionScreen(
    title = question,
    options = options,
    selectedValue = selected,
    onSelect = onSelect,
    onNext = { if (nextEnabled) onNext() },
    currentPage = 0,
)

// ── MultiSelectQuestionScreen ──────────────────────────────────────────────────
@Composable
fun MultiSelectQuestionScreen(
    title: String,
    subtitle: String = "",
    options: List<String>,
    selectedValues: Set<String>,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
    currentPage: Int = 0,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    showSkip: Boolean = false,
    icons: List<ImageVector>? = null,
) {
    QPageScaffold(
        currentPage = currentPage,
        title = title,
        onBack = onBack,
        onSkip = if (showSkip) onSkip else null,
        tip = if (subtitle.isNotBlank()) subtitle else null,
        onContinue = onNext,
        continueEnabled = selectedValues.isNotEmpty(),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option in selectedValues
            val bgColor by animateColorAsState(
                if (isSelected) QPurpleLight else QCardBg, tween(200), label = "ms_$option"
            )
            Card(
                onClick = { onToggle(option) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) QPurpleBorder else QDivider),
                colors = CardDefaults.cardColors(containerColor = bgColor),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = icons?.getOrNull(index)
                    if (icon != null) {
                        Icon(icon, contentDescription = null,
                            tint = if (isSelected) QPurple else QSubtext,
                            modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(option, fontSize = 15.sp, color = QText, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (isSelected) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = QPurple, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// Backward-compat overload
@Composable
fun MultiSelectQuestionScreen(
    question: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) = MultiSelectQuestionScreen(
    title = question,
    subtitle = "Select all that apply",
    options = options,
    selectedValues = selected,
    onToggle = onToggle,
    onNext = { if (nextEnabled) onNext() },
    currentPage = 0,
)

// ── HeightInputScreen ──────────────────────────────────────────────────────────
@Composable
fun HeightInputScreen(
    heightRawInput: String,
    isMetric: Boolean,
    onInputChange: (String) -> Unit,
    onToggleUnit: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    currentPage: Int = 0,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    // Parse display value
    val displayValue: Int = if (isMetric) {
        heightRawInput.toIntOrNull() ?: 170
    } else {
        val parts = heightRawInput.split(".")
        val ft = parts.getOrElse(0) { "5" }.toIntOrNull() ?: 5
        val inch = parts.getOrElse(1) { "7" }.toIntOrNull() ?: 7
        ft * 12 + inch
    }
    val step = if (isMetric) 1 else 1

    QPageScaffold(
        currentPage = currentPage,
        title = "What is your height?",
        onBack = onBack,
        onSkip = if (onSkip != null) onSkip else null,
        onContinue = onNext,
        continueEnabled = nextEnabled,
    ) {
        // Unit toggle pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(QDivider)
                .padding(4.dp),
        ) {
            listOf("cm" to true, "ft / in" to false).forEach { (label, metric) ->
                val active = isMetric == metric
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (active) QPurple else Color.Transparent)
                        .clickable { if (!active) onToggleUnit() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Color.White else QSubtext,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Large number display
        Text(
            text = if (isMetric) "$displayValue cm" else {
                val ft = displayValue / 12; val inch = displayValue % 12; "$ft' $inch\""
            },
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            color = QText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // Stepper row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Minus
            FilledIconButton(
                onClick = {
                    val newVal = (displayValue - step).coerceAtLeast(1)
                    onInputChange(if (isMetric) newVal.toString() else "${newVal / 12}.${newVal % 12}")
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = QPurpleLight),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = QPurple)
            }
            Spacer(Modifier.width(24.dp))
            // Tick ruler
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(3) {
                    Box(modifier = Modifier.width(2.dp).height(if (it == 1) 20.dp else 12.dp).background(QPurple.copy(alpha = 0.4f)))
                    if (it < 2) Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.width(24.dp))
            // Plus
            FilledIconButton(
                onClick = {
                    val newVal = displayValue + step
                    onInputChange(if (isMetric) newVal.toString() else "${newVal / 12}.${newVal % 12}")
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = QPurple),
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White)
            }
        }
    }
}

// ── WeightInputScreen ──────────────────────────────────────────────────────────
@Composable
fun WeightInputScreen(
    weightRawInput: String,
    isMetric: Boolean,
    onInputChange: (String) -> Unit,
    onToggleUnit: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    currentPage: Int = 0,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    heightCm: Int? = null,
) {
    val rawDouble = weightRawInput.toDoubleOrNull() ?: if (isMetric) 70.0 else 154.0
    val displayInt = rawDouble.toInt()
    val step = if (isMetric) 1 else 1

    // Compute BMI if height available
    val bmi: Double? = if (heightCm != null && heightCm > 0 && nextEnabled) {
        val kg = if (isMetric) rawDouble else rawDouble * 0.453592
        val hM = heightCm / 100.0
        kg / (hM * hM)
    } else null
    val bmiCategory = bmi?.let {
        when {
            it < 18.5 -> "Underweight"
            it < 25.0 -> "Normal"
            it < 30.0 -> "Overweight"
            else       -> "Obese"
        }
    }

    QPageScaffold(
        currentPage = currentPage,
        title = "What is your weight?",
        onBack = onBack,
        onSkip = if (onSkip != null) onSkip else null,
        onContinue = onNext,
        continueEnabled = nextEnabled,
    ) {
        // Unit toggle pill
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50.dp))
                .background(QDivider).padding(4.dp),
        ) {
            listOf("kg" to true, "lb" to false).forEach { (label, metric) ->
                val active = isMetric == metric
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50.dp))
                        .background(if (active) QPurple else Color.Transparent)
                        .clickable { if (!active) onToggleUnit() }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Color.White else QSubtext)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "$displayInt ${if (isMetric) "kg" else "lb"}",
            fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = QText,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = {
                    val newVal = (displayInt - step).coerceAtLeast(1)
                    onInputChange("$newVal")
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = QPurpleLight),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = QPurple) }
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                repeat(3) {
                    Box(modifier = Modifier.width(2.dp).height(if (it == 1) 20.dp else 12.dp).background(QPurple.copy(alpha = 0.4f)))
                    if (it < 2) Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.width(24.dp))
            FilledIconButton(
                onClick = { onInputChange("${displayInt + step}") },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = QPurple),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White) }
        }

        // BMI card
        if (bmi != null && bmiCategory != null) {
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = QCardBg),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("BMI", fontSize = 13.sp, color = QSubtext)
                        Text("%.1f · $bmiCategory".format(bmi), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = QText)
                    }
                    Spacer(Modifier.height(8.dp))
                    val fraction = ((bmi - 10.0) / 30.0).coerceIn(0.0, 1.0).toFloat()
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFF44336)))),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.Transparent))
                    }
                }
            }
        }
    }
}

// ── CameraPermissionScreen ─────────────────────────────────────────────────────
@Composable
fun CameraPermissionScreen(
    cameraPermissionGranted: Boolean?,
    onEnablePoseCorrection: () -> Unit,
    onContinueWithout: () -> Unit,
    onNext: () -> Unit,
    currentPage: Int = 0,
    onBack: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    QPageScaffold(
        currentPage = currentPage,
        title = "Enable AI Pose Correction",
        onBack = onBack,
        onSkip = onSkip,
        tip = "Your camera is used only on-device. Video is never uploaded or stored.",
        onContinue = onNext,
        continueEnabled = cameraPermissionGranted != null,
    ) {
        // Camera icon circle
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(QPurpleLight)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = QPurple, modifier = Modifier.size(48.dp))
        }

        Spacer(Modifier.height(20.dp))

        listOf(
            Triple(true, "Enable Pose Correction", "Get real-time AI form feedback"),
            Triple(false, "Continue Without Pose Correction", "Skip camera for now"),
        ).forEach { (value, label, sub) ->
            val isSelected = cameraPermissionGranted == value
            val bgColor by animateColorAsState(if (isSelected) QPurpleLight else QCardBg, tween(200), label = "cam_$value")
            Card(
                onClick = { if (value) onEnablePoseCorrection() else onContinueWithout() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) QPurpleBorder else QDivider),
                colors = CardDefaults.cardColors(containerColor = bgColor),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape)
                            .background(if (isSelected) QPurple else Color.Transparent)
                            .then(if (!isSelected) Modifier.border(1.5.dp, QSubtext, CircleShape) else Modifier),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = QText)
                        Text(sub, fontSize = 12.sp, color = QSubtext)
                    }
                }
            }
        }
    }
}

// Backward-compat overload
@Composable
fun CameraPermissionScreen(
    onEnablePoseCorrection: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) = CameraPermissionScreen(
    cameraPermissionGranted = null,
    onEnablePoseCorrection = onEnablePoseCorrection,
    onContinueWithout = onSkip,
    onNext = { if (nextEnabled) onNext() },
    currentPage = 0,
)

// ── QuestionnaireSummaryScreen ─────────────────────────────────────────────────
@Composable
fun QuestionnaireSummaryScreen(
    answers: AnswerState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    currentPage: Int = QPage.SUMMARY,
) {
    Box(modifier = Modifier.fillMaxSize().background(QBgGradient)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            QStepIndicator(currentPage = currentPage)
            Spacer(Modifier.height(12.dp))
            Text("YOU'RE ALL SET! 🎉", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = QText, letterSpacing = 0.5.sp)
            Text("Here's a summary of your profile.", fontSize = 14.sp, color = QSubtext, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))

            // Summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = QCardBg),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SummaryRow("Primary Goal", answers.goal ?: "—")
                    HorizontalDivider(color = QDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    SummaryRow("Yoga Style", answers.yogaStyle ?: "—")
                    HorizontalDivider(color = QDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    SummaryRow("Experience", answers.experienceLevel ?: "—")
                    HorizontalDivider(color = QDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    SummaryRow("Focus Areas", answers.focusAreas.joinToString(", ").ifEmpty { "—" })
                    HorizontalDivider(color = QDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    SummaryRow("Daily Practice", "${answers.dailyPracticeMinutes ?: "—"} min")
                    HorizontalDivider(color = QDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    SummaryRow("Height", answers.heightCm?.let { "$it cm" } ?: "—")
                    HorizontalDivider(color = QDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    SummaryRow("Weight", answers.weightKg?.let { "${"%.1f".format(it)} kg" } ?: "—")
                    HorizontalDivider(color = QDivider, modifier = Modifier.padding(horizontal = 16.dp))
                    SummaryRow("Injuries", answers.injuries.joinToString(", ").ifEmpty { "None" })
                }
            }

            Spacer(Modifier.height(16.dp))

            // Motivational card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = QPurple),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Your personalised plan is ready ✨", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Let's begin your yoga journey and track your progress every step of the way.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 19.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QPurple, disabledContainerColor = QPurple.copy(alpha = 0.35f)),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("START YOUR JOURNEY", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// Backward-compat overload
@Composable
fun QuestionnaireSummaryScreen(
    answers: AnswerState,
    isLoading: Boolean,
    saveError: String?,
    userId: String,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
) = QuestionnaireSummaryScreen(
    answers = answers,
    isLoading = isLoading,
    onSubmit = { onSubmit(userId) },
)

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = QSubtext)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = QText)
    }
}

// ── QuestionPageScaffold (backward compat) ─────────────────────────────────────
@Composable
fun QuestionPageScaffold(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(QBgGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            content = content,
        )
    }
}

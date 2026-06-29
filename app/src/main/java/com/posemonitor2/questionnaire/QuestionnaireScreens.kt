package com.posemonitor2.questionnaire

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Design tokens ─────────────────────────────────────────────────────────────
private val BgDark    = Color(0xFF0E1F20)
private val CardColor = Color(0xFF173436)
private val Orange    = Color(0xFFE8622E)
private val TextWhite = Color(0xFFFFFFFF)
private val TextGray  = Color(0xFFAACCCC)

// ── Shared scaffold ───────────────────────────────────────────────────────────
@Composable
fun QuestionPageScaffold(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
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

// ── Single-choice question screen ─────────────────────────────────────────────
@Composable
fun SingleChoiceQuestionScreen(
    question: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    QuestionPageScaffold {
        Text(
            text = question,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            lineHeight = 30.sp,
        )
        Spacer(Modifier.height(24.dp))
        options.forEach { option ->
            val isSelected = option == selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(option) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, Orange, RoundedCornerShape(14.dp))
                        else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Orange.copy(alpha = 0.18f) else CardColor,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option,
                        fontSize = 15.sp,
                        color = if (isSelected) Orange else TextWhite,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Orange,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                disabledContainerColor = Orange.copy(alpha = 0.3f),
            ),
        ) {
            Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        }
    }
}

// ── Multi-select question screen ──────────────────────────────────────────────
@Composable
fun MultiSelectQuestionScreen(
    question: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    QuestionPageScaffold {
        Text(
            text = question,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            lineHeight = 30.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select all that apply",
            fontSize = 13.sp,
            color = TextGray,
        )
        Spacer(Modifier.height(20.dp))
        options.forEach { option ->
            val isSelected = selected.contains(option)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggle(option) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, Orange, RoundedCornerShape(14.dp))
                        else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Orange.copy(alpha = 0.18f) else CardColor,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option,
                        fontSize = 15.sp,
                        color = if (isSelected) Orange else TextWhite,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Orange else CardColor)
                            .border(1.5.dp, if (isSelected) Orange else TextGray, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                disabledContainerColor = Orange.copy(alpha = 0.3f),
            ),
        ) {
            Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        }
    }
}

// ── Height input screen ───────────────────────────────────────────────────────
@Composable
fun HeightInputScreen(
    heightRawInput: String,
    isMetric: Boolean,
    onInputChange: (String) -> Unit,
    onToggleUnit: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    QuestionPageScaffold {
        Text(
            text = "What is your height?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
        )
        Spacer(Modifier.height(24.dp))

        // Unit toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(true to "cm", false to "ft/in").forEach { (metric, label) ->
                val selected = isMetric == metric
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Orange else CardColor)
                        .border(1.dp, if (selected) Orange else TextGray, RoundedCornerShape(10.dp))
                        .clickable { if (!selected) onToggleUnit() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (selected) TextWhite else TextGray,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = heightRawInput,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (isMetric) "e.g. 170" else "e.g. 5.7 (5 ft 7 in)",
                    color = TextGray,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedContainerColor = CardColor,
                unfocusedContainerColor = CardColor,
                focusedBorderColor = Orange,
                unfocusedBorderColor = TextGray,
            ),
            shape = RoundedCornerShape(12.dp),
        )

        if (!nextEnabled && heightRawInput.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Please enter a valid height", color = Orange.copy(alpha = 0.8f), fontSize = 12.sp)
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                disabledContainerColor = Orange.copy(alpha = 0.3f),
            ),
        ) {
            Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        }
    }
}

// ── Weight input screen ───────────────────────────────────────────────────────
@Composable
fun WeightInputScreen(
    weightRawInput: String,
    isMetric: Boolean,
    onInputChange: (String) -> Unit,
    onToggleUnit: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    QuestionPageScaffold {
        Text(
            text = "What is your weight?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(true to "kg", false to "lb").forEach { (metric, label) ->
                val selected = isMetric == metric
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Orange else CardColor)
                        .border(1.dp, if (selected) Orange else TextGray, RoundedCornerShape(10.dp))
                        .clickable { if (!selected) onToggleUnit() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (selected) TextWhite else TextGray,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = weightRawInput,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (isMetric) "e.g. 70.5" else "e.g. 155",
                    color = TextGray,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedContainerColor = CardColor,
                unfocusedContainerColor = CardColor,
                focusedBorderColor = Orange,
                unfocusedBorderColor = TextGray,
            ),
            shape = RoundedCornerShape(12.dp),
        )

        if (!nextEnabled && weightRawInput.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Please enter a valid weight", color = Orange.copy(alpha = 0.8f), fontSize = 12.sp)
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                disabledContainerColor = Orange.copy(alpha = 0.3f),
            ),
        ) {
            Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        }
    }
}

// ── Camera permission screen ──────────────────────────────────────────────────
@Composable
fun CameraPermissionScreen(
    onEnablePoseCorrection: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    QuestionPageScaffold {
        Text(
            text = "Enable AI-powered pose correction using your camera?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextWhite,
            lineHeight = 30.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Camera access lets the AI analyse your yoga poses in real time and give personalised corrections.",
            fontSize = 14.sp,
            color = TextGray,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(32.dp))

        // Enable option
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onEnablePoseCorrection() },
            colors = CardDefaults.cardColors(containerColor = Orange.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "📷  Enable Pose Correction",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Orange,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Get real-time AI feedback on your form",
                    fontSize = 13.sp,
                    color = TextGray,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Skip option
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onSkip() },
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Continue Without Pose Correction",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextWhite,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You can enable this later in Settings",
                    fontSize = 13.sp,
                    color = TextGray,
                )
            }
        }
    }
}

// ── Summary screen ────────────────────────────────────────────────────────────
@Composable
fun QuestionnaireSummaryScreen(
    answers: AnswerState,
    isLoading: Boolean,
    saveError: String?,
    userId: String,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
) {
    QuestionPageScaffold {
        Text(
            text = "You're all set! 🎉",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextWhite,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Here's a summary of your profile",
            fontSize = 14.sp,
            color = TextGray,
        )
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryRow("Primary Goal", answers.goal ?: "—")
                HorizontalDivider(color = Color(0xFF2A4A4C))
                SummaryRow("Experience", answers.experienceLevel ?: "—")
                HorizontalDivider(color = Color(0xFF2A4A4C))
                SummaryRow(
                    "Focus Areas",
                    if (answers.focusAreas.isNotEmpty()) answers.focusAreas.joinToString(", ") else "—"
                )
                HorizontalDivider(color = Color(0xFF2A4A4C))
                SummaryRow(
                    "Daily Practice",
                    answers.dailyPracticeMinutes?.let {
                        when (it) {
                            10 -> "10 Minutes"; 20 -> "20 Minutes"; 30 -> "30 Minutes"
                            45 -> "45 Minutes"; else -> "60+ Minutes"
                        }
                    } ?: "—"
                )
                HorizontalDivider(color = Color(0xFF2A4A4C))
                SummaryRow("Yoga Style", answers.yogaStyle ?: "—")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (saveError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1010)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = saveError,
                        color = Color(0xFFFF8080),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onRetry) {
                        Text("Retry", color = Orange)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = { onSubmit(userId) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                disabledContainerColor = Orange.copy(alpha = 0.4f),
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = TextWhite,
                )
            } else {
                Text(
                    "Start Your Journey",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = TextGray, fontWeight = FontWeight.Medium)
        Text(
            value,
            fontSize = 14.sp,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

package com.posemonitor2.questionnaire

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ΓöÇΓöÇ Design tokens ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
private val BgDark    = Color(0xFF0E1F20)
private val CardTeal  = Color(0xFF173436)
private val Orange    = Color(0xFFE8622E)
private val TextWhite = Color.White
private val TextGray  = Color(0xFFAAAAAA)

// Keep a local alias so the rest of the file still compiles (HeightInput, WeightInput etc. use CardColor)
private val CardColor = CardTeal

// ΓöÇΓöÇ Shared scaffold ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
fun QuestionPageScaffold(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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

// ΓöÇΓöÇ Single-choice question screen ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
fun SingleChoiceQuestionScreen(
    title: String,
    subtitle: String = "",
    options: List<String>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        options.forEach { option ->
            val isSelected = option == selectedValue
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(14.dp),
                onClick = { onSelect(option) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Circle indicator
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                                else Modifier
                                    .background(Color.Transparent)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            ),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(text = option, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = onNext,
                enabled = selectedValue != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                ),
            ) {
                Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary)
            }
        }
    }
}

// ΓöÇΓöÇ Backward-compat overload (used by QuestionnaireFlow.kt) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
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
    subtitle = "",
    options = options,
    selectedValue = selected,
    onSelect = onSelect,
    onNext = { if (nextEnabled) onNext() },
)

// ΓöÇΓöÇ Multi-select question screen ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
fun MultiSelectQuestionScreen(
    title: String,
    subtitle: String = "",
    options: List<String>,
    selectedValues: Set<String>,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        options.forEach { option ->
            val isSelected = option in selectedValues
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(14.dp),
                onClick = { onToggle(option) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(option, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = onNext,
                enabled = selectedValues.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                ),
            ) {
                Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary)
            }
        }
    }
}

// ΓöÇΓöÇ Backward-compat overload (used by QuestionnaireFlow.kt) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
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
)

// ΓöÇΓöÇ Height input screen ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
fun HeightInputScreen(
    heightRawInput: String,
    isMetric: Boolean,
    onInputChange: (String) -> Unit,
    onToggleUnit: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Text(
            text = "What is your height?",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
        )
        Text(
            text = "We use this to personalise your experience.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))

        // Unit toggle row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "cm" chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isMetric) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                    .clickable { onToggleUnit() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "cm",
                    fontSize = 14.sp,
                    color = if (isMetric) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isMetric) FontWeight.Bold else FontWeight.Normal,
                )
            }
            // "ft / in" chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isMetric) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                    .clickable { onToggleUnit() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ft / in",
                    fontSize = 14.sp,
                    color = if (!isMetric) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (!isMetric) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Input field(s)
        if (isMetric) {
            OutlinedTextField(
                value = heightRawInput,
                onValueChange = onInputChange,
                label = { Text("Height (cm)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            )
        } else {
            val parts = heightRawInput.split(".")
            val feetText = parts.getOrElse(0) { "" }
            val inchesText = parts.getOrElse(1) { "" }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = feetText,
                    onValueChange = { newFeet -> onInputChange("$newFeet.$inchesText") },
                    label = { Text("Feet", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = inchesText,
                    onValueChange = { newInches -> onInputChange("$feetText.$newInches") },
                    label = { Text("Inches", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        // Inline validation
        if (heightRawInput.isNotEmpty() && !nextEnabled) {
            Text(
                text = "Please enter a valid height.",
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange,
                    disabledContainerColor = Orange.copy(alpha = 0.4f),
                ),
            ) {
                Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            }
        }
    }
}

// ΓöÇΓöÇ Weight input screen ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
@Composable
fun WeightInputScreen(
    weightRawInput: String,
    isMetric: Boolean,
    onInputChange: (String) -> Unit,
    onToggleUnit: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Text(
            text = "What is your weight?",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
        )
        Text(
            text = "We use this to personalise your experience.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))

        // Unit toggle row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // "kg" chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isMetric) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                    .clickable { onToggleUnit() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "kg",
                    fontSize = 14.sp,
                    color = if (isMetric) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isMetric) FontWeight.Bold else FontWeight.Normal,
                )
            }
            // "lb" chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isMetric) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                    .clickable { onToggleUnit() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "lb",
                    fontSize = 14.sp,
                    color = if (!isMetric) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (!isMetric) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Single input field (weight never splits into sub-fields)
        OutlinedTextField(
            value = weightRawInput,
            onValueChange = onInputChange,
            label = { Text(if (isMetric) "Weight (kg)" else "Weight (lb)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(12.dp),
        )

        // Inline validation
        if (weightRawInput.isNotEmpty() && !nextEnabled) {
            Text(
                text = "Please enter a valid weight.",
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange,
                    disabledContainerColor = Orange.copy(alpha = 0.4f),
                ),
            ) {
                Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            }
        }
    }
}

// ΓöÇΓöÇ Camera permission screen ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

// Primary composable
@Composable
fun CameraPermissionScreen(
    cameraPermissionGranted: Boolean?,
    onEnablePoseCorrection: () -> Unit,
    onContinueWithout: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))

        // Camera icon
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Enable AI Pose Correction",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Your camera is used only on-device for real-time pose correction. Video is never uploaded or stored.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(28.dp))

        // Option card 1 — Enable Pose Correction
        val isEnableSelected = cameraPermissionGranted == true
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isEnableSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
            border = if (isEnableSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(14.dp),
            onClick = { onEnablePoseCorrection() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .then(
                            if (isEnableSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier
                                .background(Color.Transparent)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        ),
                )
                Spacer(Modifier.width(16.dp))
                Text("Enable Pose Correction", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(4.dp))

        // Option card 2 — Continue Without Pose Correction
        val isSkipSelected = cameraPermissionGranted == false
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSkipSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
            border = if (isSkipSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(14.dp),
            onClick = { onContinueWithout() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSkipSelected) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier
                                .background(Color.Transparent)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        ),
                )
                Spacer(Modifier.width(16.dp))
                Text("Continue Without Pose Correction", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Next button
        Box(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = onNext,
                enabled = cameraPermissionGranted != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                ),
            ) {
                Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary)
            }
        }
    }
}

// Backward-compat overload (used by QuestionnaireFlow.kt)
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
)

// ΓöÇΓöÇ Summary screen ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

// Primary composable
@Composable
fun QuestionnaireSummaryScreen(
    answers: AnswerState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Text(
            text = "You're all set! 🎉",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = FontFamily.Serif,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Here's a summary of your profile.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(24.dp))

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                SummaryRow("Primary Goal", answers.goal ?: "—")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SummaryRow("Experience", answers.experienceLevel ?: "—")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SummaryRow("Focus Areas", answers.focusAreas.joinToString(", ").ifEmpty { "—" })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SummaryRow("Daily Practice", "${answers.dailyPracticeMinutes ?: "—"} min")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SummaryRow("Height", answers.heightCm?.let { "$it cm" } ?: "—")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SummaryRow("Weight", answers.weightKg?.let { "${"%.1f".format(it)} kg" } ?: "—")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Start Your Journey button
        Box(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                } else {
                    Text("Start Your Journey", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary)
                }
            }
        }
    }
}

// Backward-compat overload (used by QuestionnaireFlow.kt)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
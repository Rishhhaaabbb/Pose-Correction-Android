package com.posemonitor2

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domain.generator.GeneratorConfig

@Composable
fun SettingsContent(
    videoUri: String,
    onConfirm: (GeneratorConfig, String) -> Unit,
    onBack: () -> Unit,
) {
    val defaults = GeneratorConfig.DEFAULT
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    var graphName           by remember { mutableStateOf("") }
    var stabilityThreshold  by remember { mutableStateOf(defaults.stabilityThreshold.toString()) }
    var minHoldFrames       by remember { mutableStateOf(defaults.minHoldFrames.toString()) }
    var skipFrames          by remember { mutableStateOf(defaults.skipFrames.toString()) }
    var smoothingWindow     by remember { mutableStateOf(defaults.smoothingWindow.toString()) }
    var transitionBuffer    by remember { mutableStateOf(defaults.transitionBuffer.toString()) }
    var toleranceMultiplier by remember { mutableStateOf(defaults.toleranceMultiplier.toString()) }
    var confidenceThreshold by remember { mutableStateOf(defaults.confidenceThreshold.toString()) }
    var validationError     by remember { mutableStateOf<String?>(null) }
    val validationErrorMessage = stringResource(R.string.gg_validation_error)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = primary,
        unfocusedBorderColor = outline,
        focusedTextColor = onSurface,
        unfocusedTextColor = onSurface,
        cursorColor = primary,
        focusedPlaceholderColor = onSurfaceVariant.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = onSurfaceVariant.copy(alpha = 0.5f),
    )

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.gg_settings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = onSurface)
        Text(
            "Video: ${Uri.parse(videoUri).lastPathSegment ?: videoUri}",
            fontSize = 13.sp, color = onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.gg_graph_name), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = onSurface)
            Text(
                stringResource(R.string.gg_graph_name_hint),
                fontSize = 12.sp, color = onSurfaceVariant,
            )
            OutlinedTextField(
                value = graphName,
                onValueChange = { graphName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.gg_graph_name_placeholder)) },
                colors = textFieldColors,
                shape = RoundedCornerShape(8.dp),
            )
        }

        HorizontalDivider(color = outline.copy(alpha = 0.3f))

        ParamField("Stability Threshold", "Max feature distance to stay in same segment (default: 0.12)",
            stabilityThreshold, { stabilityThreshold = it }, KeyboardType.Decimal)
        ParamField("Min Hold Frames", "Minimum frames a pose must be held (default: 18)",
            minHoldFrames, { minHoldFrames = it }, KeyboardType.Number)
        ParamField("Skip Frames", "Process every Nth frame \u2014 1 = all frames (default: 1)",
            skipFrames, { skipFrames = it }, KeyboardType.Number)
        ParamField("Smoothing Window", "Rolling average window size (default: 7)",
            smoothingWindow, { smoothingWindow = it }, KeyboardType.Number)
        ParamField("Transition Buffer", "Frames skipped at segment boundaries (default: 3)",
            transitionBuffer, { transitionBuffer = it }, KeyboardType.Number)
        ParamField("Tolerance Multiplier", "Feature tolerance = std \u00d7 this value (default: 2.0)",
            toleranceMultiplier, { toleranceMultiplier = it }, KeyboardType.Decimal)
        ParamField("Confidence Threshold", "Min keypoint confidence 0.0\u20131.0 (default: 0.6)",
            confidenceThreshold, { confidenceThreshold = it }, KeyboardType.Decimal)

        if (validationError != null) {
            Text(validationError!!, fontSize = 13.sp, color = error)
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text(stringResource(R.string.common_back), fontSize = 15.sp) }

            Button(
                onClick = {
                    val config = buildConfig(
                        stabilityThreshold, minHoldFrames, skipFrames,
                        smoothingWindow, transitionBuffer, toleranceMultiplier, confidenceThreshold,
                    )
                    if (config == null) {
                        validationError = validationErrorMessage
                    } else {
                        validationError = null
                        onConfirm(config, graphName.trim())
                    }
                },
                modifier = Modifier.weight(2f).height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
            ) { Text(stringResource(R.string.gg_start_generation), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ParamField(
    label: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = onSurface)
        Text(description, fontSize = 12.sp, color = onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primary,
                unfocusedBorderColor = outline,
                focusedTextColor = onSurface,
                unfocusedTextColor = onSurface,
                cursorColor = primary,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

private fun buildConfig(
    stabilityThreshold: String, minHoldFrames: String, skipFrames: String,
    smoothingWindow: String, transitionBuffer: String, toleranceMultiplier: String,
    confidenceThreshold: String,
): GeneratorConfig? = try {
    GeneratorConfig(
        stabilityThreshold  = stabilityThreshold.toDouble().also  { require(it > 0.0) },
        minHoldFrames       = minHoldFrames.toInt().also           { require(it >= 1) },
        skipFrames          = skipFrames.toInt().also              { require(it >= 1) },
        smoothingWindow     = smoothingWindow.toInt().also         { require(it >= 1) },
        transitionBuffer    = transitionBuffer.toInt().also        { require(it >= 0) },
        toleranceMultiplier = toleranceMultiplier.toDouble().also  { require(it > 0.0) },
        confidenceThreshold = confidenceThreshold.toDouble().also  { require(it in 0.0..1.0) },
    )
} catch (e: Exception) { null }

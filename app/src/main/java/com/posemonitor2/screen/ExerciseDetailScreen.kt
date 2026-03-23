package com.posemonitor2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Placeholder — replace with Figma design when shared
@Composable
fun ExerciseDetailScreen(
    fileName: String,
    isAsset: Boolean,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A2B2E)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Exercise: $fileName\n(detail coming soon)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

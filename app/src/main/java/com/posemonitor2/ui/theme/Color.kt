package com.posemonitor2.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Brand palette ──────────────────────────────────────────────────────────────

@Immutable
data class AppColors(
    // Primary
    val primary: Color = Color(0xFF0D3B34),
    val primaryLight: Color = Color(0xFF80CBC4),
    val primaryContainer: Color = Color(0xFF9BD2D8),
    val onPrimary: Color = Color.White,

    // Accent
    val accent: Color = Color(0xFFE8622E),
    val accentVariant: Color = Color(0xFFE85A2E),

    // Surfaces
    val background: Color = Color(0xFFF2EEE4),
    val surface: Color = Color.White,
    val surfaceVariant: Color = Color(0xFFF5F5F5),
    val surfaceMuted: Color = Color(0xFFD9E8E9),
    val onSurface: Color = Color(0xFF1A1A1A),
    val onSurfaceVariant: Color = Color(0xFF888888),

    // Semantic
    val success: Color = Color(0xFF4CAF50),
    val error: Color = Color(0xFFCC3333),
    val warning: Color = Color(0xFFE8B840),
    val info: Color = Color(0xFF2196F3),

    // Neutrals
    val gray: Color = Color(0xFF9E9E9E),
    val grayLight: Color = Color(0xFFB0B0B0),
    val grayDark: Color = Color(0xFF444444),
    val grayMuted: Color = Color(0xFF999999),

    // Dark theme surfaces
    val darkBackground: Color = Color(0xFF121212),
    val darkSurface: Color = Color(0xFF1E1E1E),
    val darkOnSurface: Color = Color.White,

    // Yogi warm palette (Hero card, profile)
    val warmAccent: Color = Color(0xFFC4A882),
    val warmMuted: Color = Color(0xFFD4C4B0),
    val warmText: Color = Color(0xFF8A7A6A),

    // Custom workout gradients
    val workoutTealDark: Color = Color(0xFF0D4B50),
    val workoutTeal: Color = Color(0xFF1A646A),
    val workoutGreenDark: Color = Color(0xFF0A3D35),
    val workoutGreen: Color = Color(0xFF1A5C40),
    val workoutChip: Color = Color(0xFF0D4A4E),

    // Aura AI card
    val auraOrange: Color = Color(0xFFE85A2E),
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }

// ── Pre-built light/dark schemes ───────────────────────────────────────────────

val LightAppColors = AppColors()

val DarkAppColors = AppColors(
    primary = Color(0xFF80CBC4),
    primaryLight = Color(0xFF0D3B34),
    primaryContainer = Color(0xFF0A3D3F),
    onPrimary = Color(0xFF121212),

    accent = Color(0xFFE8622E),
    accentVariant = Color(0xFFE85A2E),

    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    surfaceMuted = Color(0xFF333333),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAAAAAA),

    success = Color(0xFF66BB6A),
    error = Color(0xFFEF5350),
    warning = Color(0xFFFFB74D),
    info = Color(0xFF42A5F5),

    gray = Color(0xFF757575),
    grayLight = Color(0xFF616161),
    grayDark = Color(0xFFBDBDBD),
    grayMuted = Color(0xFF757575),

    darkBackground = Color(0xFF000000),
    darkSurface = Color(0xFF121212),
    darkOnSurface = Color.White,

    warmAccent = Color(0xFFC4A882),
    warmMuted = Color(0xFF8A7A6A),
    warmText = Color(0xFFD4C4B0),

    workoutTealDark = Color(0xFF0D4B50),
    workoutTeal = Color(0xFF1A646A),
    workoutGreenDark = Color(0xFF0A3D35),
    workoutGreen = Color(0xFF1A5C40),
    workoutChip = Color(0xFF0D4A4E),

    auraOrange = Color(0xFFE85A2E),
)

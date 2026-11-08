package com.momosubtitle.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Momo Subtitle is dark-first (professional media utility, docs/DESIGN_SYSTEM.md).
 * The light scheme lands with the Phase 2 user-preference support.
 */
private val DarkColors = darkColorScheme(
    primary = Aqua,
    onPrimary = Color(0xFF003639),
    primaryContainer = Color(0xFF004F55),
    onPrimaryContainer = Color(0xFFC8F7FB),
    secondary = Periwinkle,
    onSecondary = Color(0xFF232B55),
    secondaryContainer = Color(0xFF333C63),
    onSecondaryContainer = Color(0xFFE3E5FF),
    tertiary = OkGreen,
    onTertiary = Color(0xFF00391C),
    background = InkBg,
    onBackground = TextPrimary,
    surface = InkSurface,
    onSurface = TextPrimary,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = InkSurfaceHighest,
    outline = InkOutline,
    error = ErrRed,
    onError = Color(0xFF3B0A0A)
)

/**
 * Typography override (UI rehaul): weight-contrast hierarchy — SemiBold
 * titles with slightly tightened tracking, Normal body, Medium labels.
 * Monospace stays reserved for SRT timestamps and engine output.
 */
val MomoTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 20.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium)
    )
}

/** Corner system: cards 16 dp, sheets/drop-zones 20 dp (design study). */
val MomoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
)

/** Monospace styles for timestamps, codecs and counts (docs/DESIGN_SYSTEM.md). */
val MonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
val MonoBody = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
fun MomoSubtitleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MomoTypography,
        shapes = MomoShapes,
        content = content
    )
}

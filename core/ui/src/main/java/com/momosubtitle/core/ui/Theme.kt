package com.momosubtitle.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

/** M3 typography defaults; no custom font bundles in Milestone 0.1. */
val MomoTypography = Typography()

/** Monospace styles for timestamps, codecs and counts (docs/DESIGN_SYSTEM.md). */
val MonoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
val MonoBody = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
fun MomoSubtitleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MomoTypography,
        content = content
    )
}

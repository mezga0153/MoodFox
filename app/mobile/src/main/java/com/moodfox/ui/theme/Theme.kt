package com.moodfox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Theme mode ────────────────────────────────────────────
enum class ThemeMode { DARK, LIGHT }

// ── Color presets ─────────────────────────────────────────
enum class ThemePreset(val label: String, val accentHue: Float, val mode: ThemeMode) {
    PURPLE_DARK("Purple Dark",   250f, ThemeMode.DARK),
    PURPLE_LIGHT("Purple Light", 250f, ThemeMode.LIGHT),
    BLUE_DARK("Blue Dark",       220f, ThemeMode.DARK),
    BLUE_LIGHT("Blue Light",     220f, ThemeMode.LIGHT),
    TEAL_DARK("Teal Dark",       170f, ThemeMode.DARK),
    TEAL_LIGHT("Teal Light",     170f, ThemeMode.LIGHT),
    PINK_DARK("Pink Dark",       330f, ThemeMode.DARK),
    PINK_LIGHT("Pink Light",     330f, ThemeMode.LIGHT),
    ORANGE_DARK("Orange Dark",    25f, ThemeMode.DARK),
    ORANGE_LIGHT("Orange Light",  25f, ThemeMode.LIGHT),
}

/**
 * All semantic app colors, resolved per theme.
 * Every composable that needs colors reads from [LocalAppColors].
 */
@Stable
data class AppColors(
    val surface: Color,
    val surfaceVariant: Color,
    val cardSurface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,   // positive mood / above-band
    val tertiary: Color,    // negative mood / below-band
    val accent: Color,      // helper glow, target band shading
    val error: Color,
    val outline: Color,
    val isDark: Boolean,
)

val LocalAppColors = staticCompositionLocalOf { buildDarkColors(250f) }

// ── Generator ─────────────────────────────────────────────
fun buildAppColors(accentHue: Float, mode: ThemeMode): AppColors =
    if (mode == ThemeMode.DARK) buildDarkColors(accentHue) else buildLightColors(accentHue)

fun buildDarkColors(hue: Float): AppColors {
    val primary = Color.hsl(hue, 0.70f, 0.71f)
    val accent   = Color.hsl((hue + 60f) % 360f, 0.55f, 0.81f)
    val tertiary = Color.hsl((hue + 140f) % 360f, 0.85f, 0.71f)
    return AppColors(
        surface          = Color.hsl(hue, 0.15f, 0.05f),
        surfaceVariant   = Color.hsl(hue, 0.20f, 0.08f),
        cardSurface      = Color.hsl(hue, 0.22f, 0.14f),
        onSurface        = Color(0xFFECECF4),
        onSurfaceVariant = Color(0xFF8B8BA3),
        primary          = primary,
        primaryContainer = Color.hsl(hue, 0.40f, 0.20f),
        secondary        = Color(0xFF59D8A0),
        tertiary         = tertiary,
        accent           = accent,
        error            = Color(0xFFFF6B6B),
        outline          = Color.hsl(hue, 0.15f, 0.16f),
        isDark           = true,
    )
}

fun buildLightColors(hue: Float): AppColors {
    val primary  = Color.hsl(hue, 0.65f, 0.45f)
    val accent   = Color.hsl((hue + 60f) % 360f, 0.50f, 0.55f)
    val tertiary = Color.hsl((hue + 140f) % 360f, 0.70f, 0.45f)
    return AppColors(
        surface          = Color(0xFFF5F5FA),
        surfaceVariant   = Color(0xFFEEEEF5),
        cardSurface      = Color.White,
        onSurface        = Color(0xFF1A1A2E),
        onSurfaceVariant = Color(0xFF6B6B80),
        primary          = primary,
        primaryContainer = Color.hsl(hue, 0.30f, 0.88f),
        secondary        = Color(0xFF2E9E6E),
        tertiary         = tertiary,
        accent           = accent,
        error            = Color(0xFFD32F2F),
        outline          = Color(0xFFD5D5E0),
        isDark           = false,
    )
}

// ── Pre-built default palette ─────────────────────────────
val purpleDarkColors = buildDarkColors(250f)

// ── Convenience accessors ─────────────────────────────────
val Surface: Color @Composable get() = LocalAppColors.current.surface
val CardSurface: Color @Composable get() = LocalAppColors.current.cardSurface
val OnSurface: Color @Composable get() = LocalAppColors.current.onSurface
val OnSurfaceVariant: Color @Composable get() = LocalAppColors.current.onSurfaceVariant
val Primary: Color @Composable get() = LocalAppColors.current.primary
val Secondary: Color @Composable get() = LocalAppColors.current.secondary
val Tertiary: Color @Composable get() = LocalAppColors.current.tertiary
val Accent: Color @Composable get() = LocalAppColors.current.accent

// ── Typography ────────────────────────────────────────────
private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

// ── Theme composable ──────────────────────────────────────
@Composable
fun MoodFoxTheme(
    appColors: AppColors = purpleDarkColors,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (appColors.isDark) {
        darkColorScheme(
            primary          = appColors.primary,
            onPrimary        = Color.Black,
            primaryContainer = appColors.primaryContainer,
            secondary        = appColors.secondary,
            tertiary         = appColors.tertiary,
            background       = appColors.surface,
            surface          = appColors.surfaceVariant,
            surfaceVariant   = appColors.cardSurface,
            onBackground     = appColors.onSurface,
            onSurface        = appColors.onSurface,
            onSurfaceVariant = appColors.onSurfaceVariant,
            error            = appColors.error,
            outline          = appColors.outline,
        )
    } else {
        lightColorScheme(
            primary          = appColors.primary,
            onPrimary        = Color.White,
            primaryContainer = appColors.primaryContainer,
            secondary        = appColors.secondary,
            tertiary         = appColors.tertiary,
            background       = appColors.surface,
            surface          = appColors.surfaceVariant,
            surfaceVariant   = appColors.cardSurface,
            onBackground     = appColors.onSurface,
            onSurface        = appColors.onSurface,
            onSurfaceVariant = appColors.onSurfaceVariant,
            error            = appColors.error,
            outline          = appColors.outline,
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AppTypography,
            content     = content,
        )
    }
}

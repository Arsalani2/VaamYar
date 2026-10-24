package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val titleFa: String) {
    SYSTEM("پیروی از سیستم"),
    LIGHT("حالت روشن"),
    DARK("حالت تاریک")
}

enum class ThemePreset(val titleFa: String, val previewColor: Color) {
    EMERALD_GOLD("زمردی و زرین", Color(0xFF10B981)),
    COBALT_BLUE("نیلی و لاجوردی", Color(0xFF2563EB)),
    ROYAL_PURPLE("ارغوانی سلطنتی", Color(0xFF9333EA)),
    RUBY_ROSE("یاقوتی و کهربایی", Color(0xFFE11D48)),
    OBSIDIAN_SLATE("آبنوسی مینیمال", Color(0xFF475569))
}

private fun getDarkColorScheme(preset: ThemePreset): ColorScheme {
    return when (preset) {
        ThemePreset.EMERALD_GOLD -> darkColorScheme(
            primary = EmeraldPrimaryDark,
            onPrimary = EmeraldOnPrimaryDark,
            primaryContainer = EmeraldPrimaryContainerDark,
            secondary = EmeraldSecondaryDark,
            background = EmeraldBackgroundDark,
            surface = EmeraldSurfaceDark,
            surfaceVariant = EmeraldSurfaceVariantDark,
            onBackground = Color(0xFFE2E8F0),
            onSurface = Color(0xFFF1F5F9),
            outline = Color(0xFF2E4D41)
        )
        ThemePreset.COBALT_BLUE -> darkColorScheme(
            primary = CobaltPrimaryDark,
            onPrimary = Color(0xFF002244),
            primaryContainer = Color(0xFF1E3A8A),
            secondary = CobaltSecondaryDark,
            background = CobaltBackgroundDark,
            surface = CobaltSurfaceDark,
            surfaceVariant = CobaltSurfaceVariantDark,
            onBackground = Color(0xFFE2E8F0),
            onSurface = Color(0xFFF1F5F9),
            outline = Color(0xFF1E3A5F)
        )
        ThemePreset.ROYAL_PURPLE -> darkColorScheme(
            primary = PurplePrimaryDark,
            onPrimary = Color(0xFF2E0854),
            primaryContainer = Color(0xFF581C87),
            secondary = PurpleSecondaryDark,
            background = PurpleBackgroundDark,
            surface = PurpleSurfaceDark,
            surfaceVariant = PurpleSurfaceVariantDark,
            onBackground = Color(0xFFF3E8FF),
            onSurface = Color(0xFFFAF5FF),
            outline = Color(0xFF4C1D95)
        )
        ThemePreset.RUBY_ROSE -> darkColorScheme(
            primary = RubyPrimaryDark,
            onPrimary = Color(0xFF4C0519),
            primaryContainer = Color(0xFF881337),
            secondary = RubySecondaryDark,
            background = RubyBackgroundDark,
            surface = RubySurfaceDark,
            surfaceVariant = RubySurfaceVariantDark,
            onBackground = Color(0xFFFFE4E6),
            onSurface = Color(0xFFFFF1F2),
            outline = Color(0xFF581C28)
        )
        ThemePreset.OBSIDIAN_SLATE -> darkColorScheme(
            primary = ObsidianPrimaryDark,
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF334155),
            secondary = ObsidianSecondaryDark,
            background = ObsidianBackgroundDark,
            surface = ObsidianSurfaceDark,
            surfaceVariant = ObsidianSurfaceVariantDark,
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF1F5F9),
            outline = Color(0xFF475569)
        )
    }
}

private fun getLightColorScheme(preset: ThemePreset): ColorScheme {
    return when (preset) {
        ThemePreset.EMERALD_GOLD -> lightColorScheme(
            primary = EmeraldPrimaryLight,
            onPrimary = EmeraldOnPrimaryLight,
            primaryContainer = EmeraldPrimaryContainerLight,
            secondary = EmeraldSecondaryLight,
            background = EmeraldBackgroundLight,
            surface = EmeraldSurfaceLight,
            surfaceVariant = EmeraldSurfaceVariantLight,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B),
            outline = Color(0xFFCBD5E1)
        )
        ThemePreset.COBALT_BLUE -> lightColorScheme(
            primary = CobaltPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            secondary = CobaltSecondaryLight,
            background = CobaltBackgroundLight,
            surface = CobaltSurfaceLight,
            surfaceVariant = CobaltSurfaceVariantLight,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B),
            outline = Color(0xFFBFDBFE)
        )
        ThemePreset.ROYAL_PURPLE -> lightColorScheme(
            primary = PurplePrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF3E8FF),
            secondary = PurpleSecondaryLight,
            background = PurpleBackgroundLight,
            surface = PurpleSurfaceLight,
            surfaceVariant = PurpleSurfaceVariantLight,
            onBackground = Color(0xFF1F1033),
            onSurface = Color(0xFF2D164B),
            outline = Color(0xFFE9D5FF)
        )
        ThemePreset.RUBY_ROSE -> lightColorScheme(
            primary = RubyPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE4E6),
            secondary = RubySecondaryLight,
            background = RubyBackgroundLight,
            surface = RubySurfaceLight,
            surfaceVariant = RubySurfaceVariantLight,
            onBackground = Color(0xFF2A0A12),
            onSurface = Color(0xFF3B101A),
            outline = Color(0xFFFECDD3)
        )
        ThemePreset.OBSIDIAN_SLATE -> lightColorScheme(
            primary = ObsidianPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E8F0),
            secondary = ObsidianSecondaryLight,
            background = ObsidianBackgroundLight,
            surface = ObsidianSurfaceLight,
            surfaceVariant = ObsidianSurfaceVariantLight,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B),
            outline = Color(0xFFCBD5E1)
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    preset: ThemePreset = ThemePreset.EMERALD_GOLD,
    fontStyle: AppFontStyle = AppFontStyle.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) {
        getDarkColorScheme(preset)
    } else {
        getLightColorScheme(preset)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = getAppTypography(fontStyle),
        content = content
    )
}

package com.aistudio.vamyar.ui.theme

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

// فیکس bug.md/UI-02: نقش‌های رنگی ناقص (secondaryContainer/onSecondaryContainer/tertiary/tertiaryContainer/
// onSurfaceVariant/outlineVariant/error*) باعث fallback به رنگ‌های بنفشِ پیش‌فرض M3 می‌شدند که با
// پالت اسلیت/زمردی هماهنگ نیستند؛ برای هر ۵ پریست (روشن + تاریک) مجموعهٔ کامل نقش‌ها تعریف شد.
private fun getDarkColorScheme(preset: ThemePreset): ColorScheme {
    // نقش‌های خطا — مشترک بین پریست‌ها (قرمز هارمونیزه‌شده، نه بنفش M3)
    val darkError = Color(0xFFF87171)
    val darkOnError = Color(0xFF450A0A)
    val darkErrorContainer = Color(0xFF7F1D1D)
    val darkOnErrorContainer = Color(0xFFFECACA)
    return when (preset) {
        ThemePreset.EMERALD_GOLD -> darkColorScheme(
            primary = EmeraldPrimaryDark,
            onPrimary = EmeraldOnPrimaryDark,
            primaryContainer = EmeraldPrimaryContainerDark,
            secondary = EmeraldSecondaryDark,
            onSecondary = Color(0xFFA7F3D0),
            secondaryContainer = Color(0xFF065F46),
            onSecondaryContainer = Color(0xFFD1FAE5),
            tertiary = Color(0xFF5EEAD4),
            onTertiary = Color(0xFF003731),
            tertiaryContainer = Color(0xFF00504A),
            onTertiaryContainer = Color(0xFF99F6E4),
            error = darkError,
            onError = darkOnError,
            errorContainer = darkErrorContainer,
            onErrorContainer = darkOnErrorContainer,
            background = EmeraldBackgroundDark,
            surface = EmeraldSurfaceDark,
            surfaceVariant = EmeraldSurfaceVariantDark,
            onBackground = Color(0xFFE2E8F0),
            onSurface = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF2E4D41),
            outlineVariant = Color(0xFF24403A)
        )
        ThemePreset.COBALT_BLUE -> darkColorScheme(
            primary = CobaltPrimaryDark,
            onPrimary = Color(0xFF002244),
            primaryContainer = Color(0xFF1E3A8A),
            secondary = CobaltSecondaryDark,
            onSecondary = Color(0xFFBFDBFE),
            secondaryContainer = Color(0xFF1E3A8A),
            onSecondaryContainer = Color(0xFFDBEAFE),
            tertiary = Color(0xFFA5B4FC),
            onTertiary = Color(0xFF1E1B4B),
            tertiaryContainer = Color(0xFF3730A3),
            onTertiaryContainer = Color(0xFFE0E7FF),
            error = darkError,
            onError = darkOnError,
            errorContainer = darkErrorContainer,
            onErrorContainer = darkOnErrorContainer,
            background = CobaltBackgroundDark,
            surface = CobaltSurfaceDark,
            surfaceVariant = CobaltSurfaceVariantDark,
            onBackground = Color(0xFFE2E8F0),
            onSurface = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF1E3A5F),
            outlineVariant = Color(0xFF16304F)
        )
        ThemePreset.ROYAL_PURPLE -> darkColorScheme(
            primary = PurplePrimaryDark,
            onPrimary = Color(0xFF2E0854),
            primaryContainer = Color(0xFF581C87),
            secondary = PurpleSecondaryDark,
            onSecondary = Color(0xFFE9D5FF),
            secondaryContainer = Color(0xFF581C87),
            onSecondaryContainer = Color(0xFFF3E8FF),
            tertiary = Color(0xFFE879F9),
            onTertiary = Color(0xFF4A044E),
            tertiaryContainer = Color(0xFF86198F),
            onTertiaryContainer = Color(0xFFFAE8FF),
            error = darkError,
            onError = darkOnError,
            errorContainer = darkErrorContainer,
            onErrorContainer = darkOnErrorContainer,
            background = PurpleBackgroundDark,
            surface = PurpleSurfaceDark,
            surfaceVariant = PurpleSurfaceVariantDark,
            onBackground = Color(0xFFF3E8FF),
            onSurface = Color(0xFFFAF5FF),
            onSurfaceVariant = Color(0xFFC4B5FD),
            outline = Color(0xFF4C1D95),
            outlineVariant = Color(0xFF3B1676)
        )
        ThemePreset.RUBY_ROSE -> darkColorScheme(
            primary = RubyPrimaryDark,
            onPrimary = Color(0xFF4C0519),
            primaryContainer = Color(0xFF881337),
            secondary = RubySecondaryDark,
            onSecondary = Color(0xFFFECDD3),
            secondaryContainer = Color(0xFF881337),
            onSecondaryContainer = Color(0xFFFFE4E6),
            tertiary = Color(0xFFFB923C),
            onTertiary = Color(0xFF431407),
            tertiaryContainer = Color(0xFF7C2D12),
            onTertiaryContainer = Color(0xFFFFEDD5),
            error = darkError,
            onError = darkOnError,
            errorContainer = darkErrorContainer,
            onErrorContainer = darkOnErrorContainer,
            background = RubyBackgroundDark,
            surface = RubySurfaceDark,
            surfaceVariant = RubySurfaceVariantDark,
            onBackground = Color(0xFFFFE4E6),
            onSurface = Color(0xFFFFF1F2),
            onSurfaceVariant = Color(0xFFFDA4AF),
            outline = Color(0xFF581C28),
            outlineVariant = Color(0xFF431521)
        )
        ThemePreset.OBSIDIAN_SLATE -> darkColorScheme(
            primary = ObsidianPrimaryDark,
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF334155),
            secondary = ObsidianSecondaryDark,
            onSecondary = Color(0xFFCBD5E1),
            secondaryContainer = Color(0xFF334155),
            onSecondaryContainer = Color(0xFFE2E8F0),
            tertiary = Color(0xFF38BDF8),
            onTertiary = Color(0xFF082F49),
            tertiaryContainer = Color(0xFF0C4A6E),
            onTertiaryContainer = Color(0xFFE0F2FE),
            error = darkError,
            onError = darkOnError,
            errorContainer = darkErrorContainer,
            onErrorContainer = darkOnErrorContainer,
            background = ObsidianBackgroundDark,
            surface = ObsidianSurfaceDark,
            surfaceVariant = ObsidianSurfaceVariantDark,
            onBackground = Color(0xFFF8FAFC),
            onSurface = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF475569),
            outlineVariant = Color(0xFF334155)
        )
    }
}

private fun getLightColorScheme(preset: ThemePreset): ColorScheme {
    // نقش‌های خطا — مشترک بین پریست‌ها (قرمز تیره‌تر برای کنتراست متن در حالت روشن)
    val lightError = Color(0xFFDC2626)
    val lightOnError = Color.White
    val lightErrorContainer = Color(0xFFFEE2E2)
    val lightOnErrorContainer = Color(0xFF7F1D1D)
    return when (preset) {
        ThemePreset.EMERALD_GOLD -> lightColorScheme(
            primary = EmeraldPrimaryLight,
            onPrimary = EmeraldOnPrimaryLight,
            primaryContainer = EmeraldPrimaryContainerLight,
            onPrimaryContainer = Color(0xFF003822),
            secondary = EmeraldSecondaryLight,
            onSecondary = Color(0xFF065F46),
            secondaryContainer = Color(0xFFD1FAE5),
            onSecondaryContainer = Color(0xFF064E3B),
            tertiary = Color(0xFF0F766E),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFCCFBF1),
            onTertiaryContainer = Color(0xFF134E4A),
            error = lightError,
            onError = lightOnError,
            errorContainer = lightErrorContainer,
            onErrorContainer = lightOnErrorContainer,
            background = EmeraldBackgroundLight,
            surface = EmeraldSurfaceLight,
            surfaceVariant = EmeraldSurfaceVariantLight,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        ThemePreset.COBALT_BLUE -> lightColorScheme(
            primary = CobaltPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E3A8A),
            secondary = CobaltSecondaryLight,
            onSecondary = Color(0xFF1E3A8A),
            secondaryContainer = Color(0xFFDBEAFE),
            onSecondaryContainer = Color(0xFF1E3A8A),
            tertiary = Color(0xFF4F46E5),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0E7FF),
            onTertiaryContainer = Color(0xFF312E81),
            error = lightError,
            onError = lightOnError,
            errorContainer = lightErrorContainer,
            onErrorContainer = lightOnErrorContainer,
            background = CobaltBackgroundLight,
            surface = CobaltSurfaceLight,
            surfaceVariant = CobaltSurfaceVariantLight,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFBFDBFE),
            outlineVariant = Color(0xFFE2E8F0)
        )
        ThemePreset.ROYAL_PURPLE -> lightColorScheme(
            primary = PurplePrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF3E8FF),
            onPrimaryContainer = Color(0xFF581C87),
            secondary = PurpleSecondaryLight,
            onSecondary = Color(0xFF6B21A8),
            secondaryContainer = Color(0xFFF3E8FF),
            onSecondaryContainer = Color(0xFF581C87),
            tertiary = Color(0xFFC026D3),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFAE8FF),
            onTertiaryContainer = Color(0xFF701A75),
            error = lightError,
            onError = lightOnError,
            errorContainer = lightErrorContainer,
            onErrorContainer = lightOnErrorContainer,
            background = PurpleBackgroundLight,
            surface = PurpleSurfaceLight,
            surfaceVariant = PurpleSurfaceVariantLight,
            onBackground = Color(0xFF1F1033),
            onSurface = Color(0xFF2D164B),
            onSurfaceVariant = Color(0xFF584C66),
            outline = Color(0xFFE9D5FF),
            outlineVariant = Color(0xFFF1E4FA)
        )
        ThemePreset.RUBY_ROSE -> lightColorScheme(
            primary = RubyPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE4E6),
            onPrimaryContainer = Color(0xFF881337),
            secondary = RubySecondaryLight,
            onSecondary = Color(0xFF9F1239),
            secondaryContainer = Color(0xFFFFE4E6),
            onSecondaryContainer = Color(0xFF881337),
            tertiary = Color(0xFFEA580C),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFEDD5),
            onTertiaryContainer = Color(0xFF7C2D12),
            error = lightError,
            onError = lightOnError,
            errorContainer = lightErrorContainer,
            onErrorContainer = lightOnErrorContainer,
            background = RubyBackgroundLight,
            surface = RubySurfaceLight,
            surfaceVariant = RubySurfaceVariantLight,
            onBackground = Color(0xFF2A0A12),
            onSurface = Color(0xFF3B101A),
            onSurfaceVariant = Color(0xFF5E494E),
            outline = Color(0xFFFECDD3),
            outlineVariant = Color(0xFFFCE3E8)
        )
        ThemePreset.OBSIDIAN_SLATE -> lightColorScheme(
            primary = ObsidianPrimaryLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E8F0),
            onPrimaryContainer = Color(0xFF1E293B),
            secondary = ObsidianSecondaryLight,
            onSecondary = Color(0xFF334155),
            secondaryContainer = Color(0xFFE2E8F0),
            onSecondaryContainer = Color(0xFF1E293B),
            tertiary = Color(0xFF0EA5E9),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0F2FE),
            onTertiaryContainer = Color(0xFF075985),
            error = lightError,
            onError = lightOnError,
            errorContainer = lightErrorContainer,
            onErrorContainer = lightOnErrorContainer,
            background = ObsidianBackgroundLight,
            surface = ObsidianSurfaceLight,
            surfaceVariant = ObsidianSurfaceVariantLight,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    preset: ThemePreset = ThemePreset.EMERALD_GOLD,
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
        typography = Typography,
        content = content
    )
}

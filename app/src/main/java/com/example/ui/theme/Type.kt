package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppFontStyle(val titleFa: String, val fontFamily: FontFamily) {
    SYSTEM("فونت استاندارد سیستم", FontFamily.Default),
    SANS_SERIF("سنس مدرن و روان", FontFamily.SansSerif),
    SERIF("قلم کلاسیک و نسخ", FontFamily.Serif),
    MONOSPACE("اعداد دیجیتال و مالی", FontFamily.Monospace),
    CURSIVE("صمیمی و گرد", FontFamily.Cursive)
}

fun getAppTypography(fontStyle: AppFontStyle): Typography {
    val family = fontStyle.fontFamily
    return Typography(
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    )
}

val Typography = getAppTypography(AppFontStyle.SYSTEM)

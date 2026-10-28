package com.aistudio.vamyar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design Tokens متمرکز فرم افزودن/ویرایش وام (AddLoanScreen).
 *
 * منبع واحد رنگ، ابعاد و تایپوگرافی این فرم — هیچ کامپوننتی نباید مقدار پراکنده
 * یا Magic Number داشته باشد؛ همه‌چیز از همین سه object خوانده می‌شود.
 * (تطابق با سند Design Specification فرم وام؛ بازطراحی ۲۰۲۶-۱۰-۲۸)
 */
object LoanFormColors {
    val Primary = Color(0xFF7045A4)            // دکمه اصلی، انتخاب فعال و آیکون اصلی
    val PrimaryDark = Color(0xFF553080)       // حالت فشرده و تأکید متنی
    val PrimaryContainer = Color(0xFFEDE4F7)  // پس‌زمینه انتخاب‌شده
    val SecondaryContainer = Color(0xFFF7F1FC) // پس‌زمینه بخش‌ها
    val ScreenBackground = Color(0xFFF8F7FB)  // پس‌زمینه صفحه
    val Surface = Color(0xFFFFFFFF)           // کارت‌ها و ورودی‌ها
    val Outline = Color(0xFFDED5E8)            // حاشیه ورودی‌ها
    val FocusedOutline = Color(0xFF7045A4)     // حاشیه ورودی فعال
    val TextPrimary = Color(0xFF211A2E)        // متن اصلی
    val TextSecondary = Color(0xFF756B80)      // توضیحات و برچسب‌ها
    val DisabledContainer = Color(0xFFE8E5EB) // پس‌زمینه دکمه غیرفعال
    val DisabledContent = Color(0xFFA39BAA)    // متن دکمه غیرفعال
    val Error = Color(0xFFB3261E)             // خطای واقعی اعتبارسنجی
    val Paid = Color(0xFF1976D2)               // نشانگر پرداخت‌شده
    val Overdue = Color(0xFFF8DED7)           // پس‌زمینه وضعیت معوق
    val Upcoming = Color(0xFF7045A4)           // نشانگر سررسید / ماه جاری
    val Unpaid = Color(0xFFF0E5F8)            // وضعیت پرداخت‌نشده (آینده)
    val TopBarDivider = Color(0xFFEDE7F3)      // Divider بسیار ظریف هدر
}

object LoanFormDimensions {
    val ScreenHorizontalPadding = 16.dp
    val SectionSpacing = 12.dp
    val CardPadding = 16.dp
    val FieldSpacing = 12.dp
    val SectionTitleSpacing = 16.dp
    val HeaderHeight = 64.dp
    val InputHeight = 52.dp
    val PrimaryButtonHeight = 52.dp
    val SecondaryButtonHeight = 52.dp
    val ChipHeight = 40.dp
    val MonthChipMinHeight = 56.dp
    val CardRadius = 20.dp
    val SectionCardRadius = 16.dp
    val InputRadius = 12.dp
    val ButtonRadius = 16.dp
    val ChipRadius = 12.dp
    val MonthChipRadius = 16.dp
    val IconSize = 24.dp
    val SmallIconSize = 20.dp
    val TinyIconSize = 16.dp
    val TouchTargetSize = 48.dp
    val IconOptionSize = 44.dp
    val ColorDotSize = 24.dp
    val ColorRingSize = 32.dp
    val ColorDotSpacing = 12.dp
    val IconTextSpacing = 8.dp
    val BottomBarVerticalPadding = 12.dp
    val ScrollBottomPadding = 16.dp
}

object LoanFormTypography {
    val ScreenTitle = 20.sp
    val SectionTitle = 16.sp
    val ImportantAmount = 20.sp
    val InputText = 16.sp
    val ButtonText = 15.sp
    val FieldLabel = 13.sp
    val ChipText = 13.sp
    val HelperText = 12.sp
    val Subtitle = 12.sp
    val MonthName = 14.sp
    val StatusText = 12.sp
    val SummaryValue = 15.sp
}

/**
 * TextStyles آمادهٔ فرم — ارتفاع خط متناسب با فارسی تنظیم شده تا متن بریده نشود.
 * مقادیر sp از LoanFormTypography خوانده می‌شوند (منبع واحد).
 */
object LoanFormTextStyles {
    val ScreenTitle = TextStyle(
        fontSize = LoanFormTypography.ScreenTitle,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        color = LoanFormColors.TextPrimary
    )
    val TopBarSubtitle = TextStyle(
        fontSize = LoanFormTypography.Subtitle,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        color = LoanFormColors.TextSecondary
    )
    val SectionTitle = TextStyle(
        fontSize = LoanFormTypography.SectionTitle,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = LoanFormColors.TextPrimary
    )
    val FieldLabel = TextStyle(
        fontSize = LoanFormTypography.FieldLabel,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        color = LoanFormColors.TextSecondary
    )
    val HelperText = TextStyle(
        fontSize = LoanFormTypography.HelperText,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        color = LoanFormColors.TextSecondary
    )
    val ErrorText = TextStyle(
        fontSize = LoanFormTypography.HelperText,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        color = LoanFormColors.Error
    )
    val InputText = TextStyle(
        fontSize = LoanFormTypography.InputText,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        color = LoanFormColors.TextPrimary
    )
    val SummaryValue = TextStyle(
        fontSize = LoanFormTypography.SummaryValue,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        color = LoanFormColors.TextPrimary
    )
    val ImportantAmount = TextStyle(
        fontSize = LoanFormTypography.ImportantAmount,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        color = LoanFormColors.Primary
    )
    val ButtonText = TextStyle(
        fontSize = LoanFormTypography.ButtonText,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    )
    val ChipText = TextStyle(
        fontSize = LoanFormTypography.ChipText,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    )
    val MonthName = TextStyle(
        fontSize = LoanFormTypography.MonthName,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        color = LoanFormColors.TextPrimary
    )
    val MonthYear = TextStyle(
        fontSize = LoanFormTypography.HelperText,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        color = LoanFormColors.TextSecondary
    )
}

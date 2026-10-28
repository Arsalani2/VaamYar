package com.aistudio.vamyar.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.ui.components.PriceInputField
import com.aistudio.vamyar.ui.theme.LoanFormColors
import com.aistudio.vamyar.ui.theme.LoanFormDimensions
import com.aistudio.vamyar.ui.theme.LoanFormTextStyles
import com.aistudio.vamyar.ui.theme.LoanFormTypography
import com.aistudio.vamyar.util.LoanIconDisplay
import com.aistudio.vamyar.util.LoanIconHelper
import com.aistudio.vamyar.util.NumberFormatter

/**
 * کامپوننت‌های مستقل فرم افزودن/ویرایش وام (بازطراحی AddLoanScreen).
 * هر بخش فرم یک کامپوننت مستقل است و کل state آن از طریق پارامترها از
 * AddEditLoanDialog (منبع واحد state واقعی) تزریق می‌شود — هیچ دادهٔ
 * ساختگی یا موقت اینجا ساخته نمی‌شود.
 */

/** وضعیت پرداخت هر ماهِ تقویم — از رکوردهای واقعی LoanInstallment محاسبه می‌شود. */
enum class LoanMonthStatus {
    /** در مجموعهٔ پرداخت‌شده‌ها ثبت شده است. */
    PAID,
    /** سررسید گذشته و پرداخت نشده (ماه جاری نیست). */
    OVERDUE,
    /** ماه جاری تقویم (سررسید رسیده، هنوز پرداخت نشده). */
    CURRENT,
    /** آینده — هنوز سررسید نرسیده. */
    UPCOMING
}

/** دادهٔ نمایشی هر چیپ ماه در تقویم شمسی فرم. */
data class LoanMonthChipData(
    val installmentNumber: Int,
    val year: Int,
    val month: Int,
    val monthName: String,
    val status: LoanMonthStatus
)

// ─────────────────────────────────────────────────────────────────────────────
// ۳.۱ AddLoanTopBar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * هدر ثابت فرم: دکمه بستن، عنوان + زیرعنوان و آیکون نوع وام.
 * ارتفاع پایه ۶۴dp؛ هدر با تغییر state فیلدها دوباره ساخته نمی‌شود
 * (پارامترها فقط با تغییرِ واقعی آیکون/عنوان تغییر می‌کنند).
 */
@Composable
internal fun LoanFormTopBar(
    title: String,
    subtitle: String,
    iconName: String,
    iconShape: String,
    customIconPath: String?,
    tintColor: Color,
    onClose: () -> Unit
) {
    Surface(color = LoanFormColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LoanFormDimensions.HeaderHeight)
                    .padding(horizontal = LoanFormDimensions.ScreenHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.IconTextSpacing)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = LoanFormColors.TextPrimary,
                        modifier = Modifier.size(LoanFormDimensions.IconSize)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(text = title, style = LoanFormTextStyles.ScreenTitle, maxLines = 1)
                    Text(text = subtitle, style = LoanFormTextStyles.TopBarSubtitle, maxLines = 1)
                }
                LoanIconDisplay(
                    iconName = iconName,
                    iconShape = iconShape,
                    customIconPath = customIconPath,
                    tintColor = tintColor,
                    size = 40.dp,
                    iconSize = LoanFormDimensions.IconSize
                )
            }
            HorizontalDivider(thickness = 1.dp, color = LoanFormColors.TopBarDivider)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ورودی متنی هم‌سبک فرم — برچسب مستقل بالای فیلد (13sp) + بدنه 52dp + شعاع 12dp
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LoanFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: (@Composable () -> Unit)? = null,
    onFieldClick: (() -> Unit)? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = LoanFormTextStyles.FieldLabel)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(LoanFormDimensions.InputHeight)
                .let { m -> if (onFieldClick != null) m.clickable(onClick = onFieldClick) else m },
            placeholder = placeholder?.let { { Text(it, style = LoanFormTextStyles.InputText.copy(fontWeight = FontWeight.Normal)) } },
            readOnly = readOnly,
            isError = isError,
            singleLine = true,
            shape = RoundedCornerShape(LoanFormDimensions.InputRadius),
            textStyle = LoanFormTextStyles.InputText,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LoanFormColors.FocusedOutline,
                unfocusedBorderColor = LoanFormColors.Outline,
                focusedContainerColor = LoanFormColors.Surface,
                unfocusedContainerColor = LoanFormColors.Surface,
                errorBorderColor = LoanFormColors.Error,
                focusedTextColor = LoanFormColors.TextPrimary,
                unfocusedTextColor = LoanFormColors.TextPrimary,
                cursorColor = LoanFormColors.FocusedOutline
            ),
            trailingIcon = trailingIcon
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = if (isError) LoanFormTextStyles.ErrorText else LoanFormTextStyles.HelperText
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ۳.۲ LoanIdentityCard — مشخصات و ظاهر وام
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LoanIdentityCard(
    title: String,
    onTitleChange: (String) -> Unit,
    titleError: String?,
    colorOptions: List<Long>,
    selectedColor: Long,
    onColorSelected: (Long) -> Unit,
    selectedIconName: String,
    onIconSelected: (String) -> Unit,
    selectedIconShape: String,
    onShapeChange: (String) -> Unit,
    customIconPath: String?,
    onPickCustomImage: () -> Unit,
    onRemoveCustomImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LoanFormDimensions.CardRadius),
        colors = CardDefaults.cardColors(containerColor = LoanFormColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanFormDimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
        ) {
            Text(text = "مشخصات وام", style = LoanFormTextStyles.SectionTitle)

            // فیلد نام وام — فاصله عنوان تا اولین فیلد 16dp
            Spacer(modifier = Modifier.height(LoanFormDimensions.SectionTitleSpacing - LoanFormDimensions.FieldSpacing))

            LoanFormTextField(
                value = title,
                onValueChange = onTitleChange,
                label = "نام وام",
                placeholder = "مثلاً: وام بانک ملت",
                isError = titleError != null,
                supportingText = titleError
            )

            // ── نماد و ظاهر وام (آیکون و فرم نمایش) ──
            Text(text = "نماد و ظاهر وام", style = LoanFormTextStyles.FieldLabel)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
            ) {
                FilterChip(
                    selected = selectedIconShape == "ROUNDED_SQUARE",
                    onClick = { onShapeChange("ROUNDED_SQUARE") },
                    label = { Text("مربع گرد", style = LoanFormTextStyles.ChipText) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CropSquare,
                            contentDescription = null,
                            modifier = Modifier.size(LoanFormDimensions.TinyIconSize)
                        )
                    },
                    shape = RoundedCornerShape(LoanFormDimensions.ChipRadius),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LoanFormColors.PrimaryContainer,
                        selectedLabelColor = LoanFormColors.PrimaryDark,
                        containerColor = LoanFormColors.ScreenBackground
                    )
                )
                FilterChip(
                    selected = selectedIconShape == "CIRCLE",
                    onClick = { onShapeChange("CIRCLE") },
                    label = { Text("دایره", style = LoanFormTextStyles.ChipText) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(LoanFormDimensions.TinyIconSize)
                        )
                    },
                    shape = RoundedCornerShape(LoanFormDimensions.ChipRadius),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LoanFormColors.PrimaryContainer,
                        selectedLabelColor = LoanFormColors.PrimaryDark,
                        containerColor = LoanFormColors.ScreenBackground
                    )
                )
            }

            // آیکون‌های پیش‌فرض — هدف لمسی ۴۸dp، ظرف ۴۴dp، فاصله ۸dp
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.IconTextSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(LoanIconHelper.PRESET_ICONS) { item ->
                    val isSelected = selectedIconName == item.id && customIconPath == null
                    val optionShape =
                        if (selectedIconShape == "CIRCLE") CircleShape
                        else RoundedCornerShape(LoanFormDimensions.ChipRadius)
                    Box(
                        modifier = Modifier
                            .size(LoanFormDimensions.TouchTargetSize)
                            .clip(optionShape)
                            .clickable {
                                onIconSelected(item.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = optionShape,
                            color = if (isSelected) LoanFormColors.PrimaryContainer else LoanFormColors.ScreenBackground,
                            border = if (isSelected) BorderStroke(2.dp, LoanFormColors.Primary) else BorderStroke(1.dp, LoanFormColors.Outline),
                            modifier = Modifier.size(LoanFormDimensions.IconOptionSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.titleFa,
                                    tint = if (isSelected) Color(selectedColor) else LoanFormColors.TextSecondary,
                                    modifier = Modifier.size(LoanFormDimensions.IconSize)
                                )
                            }
                        }
                    }
                }
            }

            // تصویر اختصاصی از گالری (قابلیت موجود حفظ می‌شود)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPickCustomImage,
                    shape = RoundedCornerShape(LoanFormDimensions.ChipRadius),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(LoanFormDimensions.TinyIconSize)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (customIconPath != null) "تغییر تصویر از گالری" else "انتخاب لوگو/عکس از گالری",
                        style = LoanFormTextStyles.HelperText
                    )
                }
                if (customIconPath != null) {
                    TextButton(
                        onClick = onRemoveCustomImage,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = LoanFormColors.Error,
                            modifier = Modifier.size(LoanFormDimensions.TinyIconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف تصویر", color = LoanFormColors.Error, style = LoanFormTextStyles.HelperText)
                    }
                }
            }

            // ── انتخاب رنگ — نشانگر ۲۴dp، حلقه انتخاب ۳۲dp، فاصله ۱۲dp ──
            Text(text = "رنگ وام", style = LoanFormTextStyles.FieldLabel)
            Row(
                horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.ColorDotSpacing),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                colorOptions.forEach { colorVal ->
                    val isSelected = selectedColor == colorVal
                    Box(
                        modifier = Modifier
                            .size(LoanFormDimensions.ColorRingSize)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color(colorVal), CircleShape)
                                else Modifier
                            )
                            .clickable { onColorSelected(colorVal) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(LoanFormDimensions.ColorDotSize)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ۳.۳ PaymentScheduleInputCard — مبلغ قسط و زمان‌بندی
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaymentScheduleCard(
    monthlyAmountDigits: String,
    onMonthlyAmountChange: (String) -> Unit,
    amountErrorText: String?,
    startDateText: String,
    onPickStartDate: () -> Unit,
    dueDayHint: String,
    totalMonthsText: String,
    onTotalMonthsChange: (String) -> Unit,
    durationError: String?,
    quickDurationOptions: List<Int>,
    selectedDuration: Int,
    onQuickDurationSelected: (Int) -> Unit,
    usePersianDigits: Boolean,
    compactThousands: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LoanFormDimensions.CardRadius),
        colors = CardDefaults.cardColors(containerColor = LoanFormColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanFormDimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
        ) {
            Text(text = "مبلغ و زمان‌بندی", style = LoanFormTextStyles.SectionTitle)
            Spacer(modifier = Modifier.height(LoanFormDimensions.SectionTitleSpacing - LoanFormDimensions.FieldSpacing))

            // مبلغ هر قسط — برچسب بالای فیلد، واحد تومان، قالب‌بندی هزارگان در پیش‌نمایش
            PriceInputField(
                rawDigitsValue = monthlyAmountDigits,
                onRawDigitsChange = onMonthlyAmountChange,
                label = "مبلغ هر قسط (تومان)",
                placeholder = "مثلاً: ۲,۵۰۰,۰۰۰",
                usePersianDigits = usePersianDigits,
                compactThousands = compactThousands,
                isError = amountErrorText != null,
                errorText = amountErrorText,
                labelAbove = true,
                fieldHeight = LoanFormDimensions.InputHeight,
                unfocusedBorderColor = LoanFormColors.Outline,
                focusedBorderColor = LoanFormColors.FocusedOutline,
                labelStyle = LoanFormTextStyles.FieldLabel
            )

            // تاریخ شروع + تعداد اقساط — دو ستون هم‌عرض با فاصله ۱۲dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
            ) {
                LoanFormTextField(
                    value = startDateText,
                    onValueChange = {},
                    label = "تاریخ شروع وام",
                    readOnly = true,
                    supportingText = dueDayHint,
                    onFieldClick = onPickStartDate,
                    trailingIcon = {
                        IconButton(onClick = onPickStartDate) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "انتخاب تاریخ",
                                tint = LoanFormColors.Primary,
                                modifier = Modifier.size(LoanFormDimensions.SmallIconSize)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                LoanFormTextField(
                    value = totalMonthsText,
                    onValueChange = onTotalMonthsChange,
                    label = "تعداد اقساط (ماه)",
                    isError = durationError != null,
                    supportingText = durationError,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            // انتخاب سریع تعداد اقساط — چیپ ۴۰dp، شعاع ۱۲dp؛ در صفحهٔ باریک
            // به ردیف بعد می‌رود (FlowRow) و متن بریده نمی‌شود.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.IconTextSpacing),
                verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.IconTextSpacing)
            ) {
                quickDurationOptions.forEach { duration ->
                    val isSelected = selectedDuration == duration
                    Surface(
                        shape = RoundedCornerShape(LoanFormDimensions.ChipRadius),
                        color = if (isSelected) LoanFormColors.PrimaryContainer else LoanFormColors.Surface,
                        border = if (isSelected) BorderStroke(2.dp, LoanFormColors.Primary) else BorderStroke(1.dp, LoanFormColors.Outline),
                        modifier = Modifier
                            .height(LoanFormDimensions.ChipHeight)
                            .clip(RoundedCornerShape(LoanFormDimensions.ChipRadius))
                            .clickable { onQuickDurationSelected(duration) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "${NumberFormatter.formatNumber(duration, usePersianDigits)} ماه",
                                style = LoanFormTextStyles.ChipText,
                                color = if (isSelected) LoanFormColors.PrimaryDark else LoanFormColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ۳.۴ RepaymentSummaryCard — خلاصه مالی
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun RepaymentSummaryCard(
    perInstallmentAmount: Long,
    monthsCount: Int,
    totalAmount: Long,
    usePersianDigits: Boolean,
    compactThousands: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LoanFormDimensions.SectionCardRadius),
        color = LoanFormColors.SecondaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanFormDimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
        ) {
            Text(text = "خلاصه بازپرداخت", style = LoanFormTextStyles.SectionTitle)
            Spacer(modifier = Modifier.height(4.dp))

            SummaryRow(
                label = "مبلغ هر قسط",
                value = NumberFormatter.formatPrice(perInstallmentAmount, usePersian = usePersianDigits, compactThousands = compactThousands)
            )
            SummaryRow(
                label = "تعداد اقساط",
                value = "${NumberFormatter.formatNumber(monthsCount, usePersianDigits)} ماه"
            )

            // ردیف نهایی — پس‌زمینهٔ کمی متمایز، بدون کارت/حاشیه/سایه اضافه
            Surface(
                shape = RoundedCornerShape(LoanFormDimensions.ChipRadius),
                color = LoanFormColors.PrimaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "جمع کل اقساط",
                        style = LoanFormTextStyles.FieldLabel,
                        color = LoanFormColors.TextSecondary
                    )
                    Text(
                        text = NumberFormatter.formatPrice(totalAmount, usePersian = usePersianDigits, compactThousands = compactThousands),
                        style = LoanFormTextStyles.ImportantAmount
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = LoanFormTextStyles.FieldLabel, color = LoanFormColors.TextSecondary)
        Text(text = value, style = LoanFormTextStyles.SummaryValue, color = LoanFormColors.TextPrimary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ۳.۵ MonthlyInstallmentCalendarCard — تقویم ماه‌های شمسی و وضعیت پرداخت
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonthlyInstallmentCalendarCard(
    monthChips: List<LoanMonthChipData>,
    paidCount: Int,
    totalMonths: Int,
    arrivedCount: Int,
    currentMonthLabel: String,
    unpaidArrivedCount: Int,
    onQuickSyncArrived: () -> Unit,
    onToggleMonth: (Int) -> Unit,
    usePersianDigits: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LoanFormDimensions.CardRadius),
        color = LoanFormColors.SecondaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanFormDimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
        ) {
            // ۱) عنوان + توضیح وضعیت اقساط (فاصله عنوان تا توضیح ۴dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "تقویم ماه‌های شمسی", style = LoanFormTextStyles.SectionTitle)
                    Text(
                        text = "وضعیت پرداخت اقساط به تفکیک ماه شمسی",
                        style = LoanFormTextStyles.HelperText
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LoanFormColors.PrimaryContainer
                ) {
                    Text(
                        text = "${NumberFormatter.formatNumber(paidCount, usePersianDigits)} از ${NumberFormatter.formatNumber(totalMonths, usePersianDigits)}",
                        style = LoanFormTextStyles.HelperText.copy(fontWeight = FontWeight.Bold),
                        color = LoanFormColors.PrimaryDark,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // ۲) وضعیت اقساط رسیده (فقط محاسبهٔ سررسید — ثبتِ پرداخت جداگانه است)
            Text(
                text = "اقساط رسیده تا امروز: ${NumberFormatter.formatNumber(arrivedCount, usePersianDigits)} قسط ($currentMonthLabel) — صرفاً محاسبهٔ سررسید؛ پرداخت‌شده‌ها را جداگانه ثبت کنید",
                style = LoanFormTextStyles.HelperText,
                color = LoanFormColors.Primary
            )

            // ۳) عملیات گروهی موجود: ثبت یک‌جای همهٔ اقساط رسیدهٔ پرداخت‌نشده
            if (unpaidArrivedCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LoanFormColors.PrimaryContainer,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onQuickSyncArrived)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoMode,
                            contentDescription = null,
                            tint = LoanFormColors.PrimaryDark,
                            modifier = Modifier.size(LoanFormDimensions.TinyIconSize)
                        )
                        Text(
                            text = "ثبت همهٔ ${NumberFormatter.formatNumber(unpaidArrivedCount, usePersianDigits)} قسط رسیدهٔ پرداخت‌نشده",
                            style = LoanFormTextStyles.HelperText,
                            color = LoanFormColors.PrimaryDark
                        )
                    }
                }
            }

            // ۴) راهنمای رنگ‌ها — دقیقاً مطابق وضعیت واقعی چیپ‌ها
            StatusLegend()

            // ۵) تقویم ماه‌های شمسی — Grid واکنش‌گرا بر اساس عرض واقعی
            MonthGrid(
                monthChips = monthChips,
                usePersianDigits = usePersianDigits,
                onToggleMonth = onToggleMonth
            )

            // ۶) توضیح کوتاه تعامل با ماه‌ها
            Text(
                text = "💡 با لمس هر ماه، وضعیت پرداخت همان قسط را کم یا زیاد کنید.",
                style = LoanFormTextStyles.HelperText
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusLegend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendItem(label = "پرداخت‌شده", color = LoanFormColors.Paid, outlined = false)
        LegendItem(label = "عقب‌افتاده", color = LoanFormColors.Overdue, outlined = true)
        LegendItem(label = "ماه جاری", color = LoanFormColors.Upcoming, outlined = false)
        LegendItem(label = "در انتظار", color = LoanFormColors.Unpaid, outlined = true)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, outlined: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (outlined) Modifier.border(1.dp, LoanFormColors.TextSecondary.copy(alpha = 0.5f), CircleShape)
                    else Modifier
                )
        )
        Text(text = label, style = LoanFormTextStyles.HelperText)
    }
}

@Composable
private fun MonthGrid(
    monthChips: List<LoanMonthChipData>,
    usePersianDigits: Boolean,
    onToggleMonth: (Int) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // تعداد ستون‌ها از عرض واقعی محاسبه می‌شود — هیچ عرض ثابتی باعث
        // بیرون‌زدگی چیپ‌ها نمی‌شود.
        val columns = (maxWidth / 92.dp).toInt().coerceIn(3, 6)
        val rows = monthChips.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.IconTextSpacing)) {
            rows.forEach { rowChips ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.IconTextSpacing)
                ) {
                    rowChips.forEach { chipData ->
                        MonthChip(
                            data = chipData,
                            usePersianDigits = usePersianDigits,
                            onClick = { onToggleMonth(chipData.installmentNumber) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * چیپ هر ماه: حداقل ۵۶dp ارتفاع، شعاع ۱۶dp، نام ماه ۱۴sp و سال ۱۲sp؛
 * وضعیت پرداخت هم با رنگ و هم با نشانگر نقطه + متن قابل تشخیص است
 * (الزام Design Tokens: وضعیت فقط با رنگ communicating نمی‌شود).
 */
@Composable
private fun MonthChip(
    data: LoanMonthChipData,
    usePersianDigits: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (data.status) {
        LoanMonthStatus.PAID -> LoanFormColors.Surface
        LoanMonthStatus.OVERDUE -> LoanFormColors.Overdue
        LoanMonthStatus.CURRENT -> LoanFormColors.Surface
        LoanMonthStatus.UPCOMING -> LoanFormColors.Unpaid
    }
    val chipBorder: BorderStroke? = when (data.status) {
        // ماه جاری با حاشیهٔ ۲dp بنفش از بقیهٔ وضعیت‌ها تفکیک می‌شود
        LoanMonthStatus.CURRENT -> BorderStroke(2.dp, LoanFormColors.Primary)
        LoanMonthStatus.PAID -> BorderStroke(1.dp, LoanFormColors.Paid)
        else -> null
    }
    val dotColor = when (data.status) {
        LoanMonthStatus.PAID -> LoanFormColors.Paid
        LoanMonthStatus.OVERDUE -> LoanFormColors.Error
        LoanMonthStatus.CURRENT -> LoanFormColors.Upcoming
        LoanMonthStatus.UPCOMING -> LoanFormColors.TextSecondary
    }
    val statusLabel = when (data.status) {
        LoanMonthStatus.PAID -> "پرداخت‌شده"
        LoanMonthStatus.OVERDUE -> "عقب‌افتاده"
        LoanMonthStatus.CURRENT -> "ماه جاری"
        LoanMonthStatus.UPCOMING -> "در انتظار"
    }
    Surface(
        shape = RoundedCornerShape(LoanFormDimensions.MonthChipRadius),
        color = backgroundColor,
        border = chipBorder,
        modifier = modifier
            .heightIn(min = LoanFormDimensions.MonthChipMinHeight)
            .clip(RoundedCornerShape(LoanFormDimensions.MonthChipRadius))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = data.monthName, style = LoanFormTextStyles.MonthName)
            Text(
                text = NumberFormatter.formatNumber(data.year, usePersianDigits),
                style = LoanFormTextStyles.MonthYear
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(text = statusLabel, style = LoanFormTextStyles.HelperText)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ۳.۶ LoanPartnersCard — شرکا و سهم‌بندی
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LoanPartnersCard(
    shares: List<DebtorShare>,
    onRemoveShare: (Int) -> Unit,
    onAddPartner: () -> Unit,
    myShareAmount: Long,
    othersTotalShare: Long,
    monthlyAmount: Long,
    sharesExceedMonthly: Boolean,
    usePersianDigits: Boolean,
    compactThousands: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LoanFormDimensions.CardRadius),
        color = LoanFormColors.SecondaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LoanFormDimensions.CardPadding),
            verticalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
        ) {
            Text(text = "شراکت در قسط", style = LoanFormTextStyles.SectionTitle)
            Text(
                text = "افزودن سهم دوستان یا خانواده",
                style = LoanFormTextStyles.FieldLabel,
                color = LoanFormColors.TextSecondary
            )

            // دکمهٔ افزودن شریک — هدف لمسی ≥ ۴۸dp، شعاع ۱۲dp،
            // پس‌زمینه PrimaryContainer و متن/آیکون PrimaryDark
            Surface(
                shape = RoundedCornerShape(LoanFormDimensions.ChipRadius),
                color = LoanFormColors.PrimaryContainer,
                modifier = Modifier
                    .heightIn(min = LoanFormDimensions.TouchTargetSize)
                    .clip(RoundedCornerShape(LoanFormDimensions.ChipRadius))
                    .clickable(onClick = onAddPartner)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.IconTextSpacing)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = LoanFormColors.PrimaryDark,
                        modifier = Modifier.size(LoanFormDimensions.SmallIconSize)
                    )
                    Text(
                        text = "افزودن شریک",
                        style = LoanFormTextStyles.ButtonText.copy(color = LoanFormColors.PrimaryDark)
                    )
                }
            }

            // لیست شرکا یا Empty State
            if (shares.isEmpty()) {
                Text(
                    text = "این وام بدون شریک است و کل قسط متعلق به کاربر مبنا است.",
                    style = LoanFormTextStyles.HelperText
                )
            } else {
                shares.forEachIndexed { index, share ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${share.personName}: ${NumberFormatter.formatPrice(share.shareAmount, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                                style = LoanFormTextStyles.SummaryValue
                            )
                            if (share.shareDescription.isNotBlank()) {
                                Text(
                                    text = share.shareDescription,
                                    style = LoanFormTextStyles.HelperText
                                )
                            }
                        }
                        IconButton(onClick = { onRemoveShare(index) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف",
                                tint = LoanFormColors.Error,
                                modifier = Modifier.size(LoanFormDimensions.TinyIconSize)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = LoanFormColors.Outline.copy(alpha = 0.6f))

            // خلاصهٔ سهم کاربر اصلی — از دادهٔ واقعی محاسبه می‌شود (بدون مقدار ثابت)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سهم کاربر مبنا:",
                    style = LoanFormTextStyles.FieldLabel,
                    color = LoanFormColors.TextSecondary
                )
                Text(
                    text = NumberFormatter.formatPrice(myShareAmount, usePersian = usePersianDigits, compactThousands = compactThousands),
                    style = LoanFormTextStyles.ImportantAmount
                )
            }

            // فیکس bug.md/P1-8(الف): سهم صفرِ بی‌صدا ممکن نیست — ذخیره مسدود می‌شود
            if (sharesExceedMonthly) {
                Text(
                    text = "⚠️ مجموع سهم شرکا (${NumberFormatter.formatPrice(othersTotalShare, usePersian = usePersianDigits, compactThousands = compactThousands)}) از مبلغ کل قسط ماهانه (${NumberFormatter.formatPrice(monthlyAmount, usePersian = usePersianDigits, compactThousands = compactThousands)}) بیشتر است؛ مبلغ قسط را افزایش دهید یا سهم شرکا را کم/حذف کنید.",
                    style = LoanFormTextStyles.ErrorText
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ۳.۷ FixedBottomActionBar — ذخیره و انصراف
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LoanFormBottomBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean
) {
    Surface(color = LoanFormColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = LoanFormDimensions.ScreenHorizontalPadding,
                    vertical = LoanFormDimensions.BottomBarVerticalPadding
                ),
            horizontalArrangement = Arrangement.spacedBy(LoanFormDimensions.FieldSpacing)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(LoanFormDimensions.ButtonRadius),
                border = BorderStroke(1.dp, LoanFormColors.Outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LoanFormColors.Primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(LoanFormDimensions.SecondaryButtonHeight)
            ) {
                Text(text = "انصراف", style = LoanFormTextStyles.ButtonText, color = LoanFormColors.Primary)
            }
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                shape = RoundedCornerShape(LoanFormDimensions.ButtonRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LoanFormColors.Primary,
                    contentColor = LoanFormColors.Surface,
                    disabledContainerColor = LoanFormColors.DisabledContainer,
                    disabledContentColor = LoanFormColors.DisabledContent
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(LoanFormDimensions.PrimaryButtonHeight)
            ) {
                Text(text = "ذخیره وام", style = LoanFormTextStyles.ButtonText)
            }
        }
    }
}

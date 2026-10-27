package com.aistudio.vamyar.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.vamyar.util.InputLimits
import com.aistudio.vamyar.util.NumberFormatter

/**
 * Rock-solid price input field designed specifically for RTL Persian environments.
 * Avoids any internal comma transformations that cause BiDi cursor jumping or flipped digits.
 * Digits are entered purely in LTR direction, with an instant live Persian words & formatted
 * currency display underneath.
 *
 * فیکس bug.md/NB-40: خطای بیرونی ([isError] + [errorText]) زیر همین فیلد و با قرمزشدن
 * آن نمایش داده می‌شود تا هر خطا کنار فیلد خودش دیده شود (نه انتهای دیالوگ)؛ سقف
 * ارقام هم از InputLimits خوانده می‌شود و رسیدن به سقف بازخورد صریح دارد (بند د).
 */
@Composable
fun PriceInputField(
    rawDigitsValue: String,
    onRawDigitsChange: (String) -> Unit,
    label: String,
    placeholder: String = "مثلاً: ۲۵۰۰۰۰۰",
    modifier: Modifier = Modifier,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    enabled: Boolean = true,
    // فیکس bug.md/NB-40: خطای زندهٔ صدا‌زننده (مثلاً «مبلغ قسط باید بزرگ‌تر از صفر باشد»)
    isError: Boolean = false,
    errorText: String? = null
) {
    val amountLong = rawDigitsValue.toLongOrNull() ?: 0L

    var hasInvalidSeparator by remember { mutableStateOf(false) }
    // فیکس bug.md/NB-40 (بند د): رسیدن به سقف ارقام بی‌صدا کپ نمی‌شود — بازخورد صریح دارد
    var hitMaxDigits by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = rawDigitsValue,
            onValueChange = { input ->
                // فیکس bug.md/NB-5: بررسی وجود ممیز/اعشار برای هشدار فوری به کاربر
                // فیکس bug.md/NB-26: خطا در هر تغییر دوباره ارزیابی می‌شود؛ اصلاحِ ورودی،
                // خطا را بدون خالی‌کردن فیلد برمی‌دارد (قبلاً فقط با پاک‌شدن کامل فیلد ریست می‌شد)
                hasInvalidSeparator = input.any { it == '.' || it == '/' || it == '٫' }

                // Clean input: extract only numeric digits, convert Persian/Arabic digits to ASCII 0-9
                val digitsOnly = input.filter { it.isDigit() }.map { c ->
                    when (c) {
                        in '۰'..'۹' -> (c - 0x06F0 + 0x0030)
                        in '٠'..'٩' -> (c - 0x0660 + 0x0030)
                        else -> c
                    }
                }.joinToString("")

                // Cap at MAX_AMOUNT_DIGITS — فیکس bug.md/NB-40: سقف متمرکز در InputLimits
                hitMaxDigits = digitsOnly.length > InputLimits.MAX_AMOUNT_DIGITS
                val capped = if (hitMaxDigits) digitsOnly.take(InputLimits.MAX_AMOUNT_DIGITS) else digitsOnly
                onRawDigitsChange(capped)
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = enabled,
            isError = hasInvalidSeparator || isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textDirection = TextDirection.Ltr,
                fontWeight = FontWeight.Bold
            ),
            trailingIcon = {
                if (rawDigitsValue.isNotEmpty()) {
                    IconButton(
                        onClick = { 
                            hasInvalidSeparator = false
                            onRawDigitsChange("") 
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "پاک کردن",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (hasInvalidSeparator) {
            Text(
                text = "جداکننده اعشار مجاز نیست؛ لطفاً مبلغ را کامل و به تومان وارد کنید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // فیکس bug.md/NB-40: خطای زندهٔ صدا‌زننده زیر همین فیلد (نه انتهای دیالوگ)
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // فیکس bug.md/NB-40 (بند د): بازخورد صریح رسیدن به سقف ارقام (به‌جای کپ بی‌صدا)
        if (hitMaxDigits) {
            Text(
                text = "سقف مبلغ ${InputLimits.MAX_AMOUNT_DIGITS} رقم است؛ رقم‌های بیشتر نادیده گرفته می‌شوند.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Live formatted price and Persian words preview
        if (amountLong > 0L) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مبلغ:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = NumberFormatter.formatPrice(amountLong, usePersian = usePersianDigits, compactThousands = compactThousands),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = NumberFormatter.toPersianWords(amountLong),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

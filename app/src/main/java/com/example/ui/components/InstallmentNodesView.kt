package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.NumberFormatter
import com.example.util.ShamsiDateHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallmentNodesView(
    totalMonths: Int,
    paidMonths: Int,
    startYear: Int,
    startMonth: Int,
    accentColor: Color,
    usePersianDigits: Boolean = true,
    // فیکس bug.md/F1+NB-31: نودها از رکوردهای واقعی رنگ می‌گیرند، نه از شمارندهٔ ترتیبی —
    // paidYearMonths = مجموعهٔ (سال، ماه) اقساط پرداخت‌شده (null = fallback ترتیبیِ قدیمی)،
    // overdueYearMonths = ماه‌های سررسیدگذشتهٔ بدون رکورد، currentYearMonth = ماه جاریِ تقویم.
    paidYearMonths: Set<Pair<Int, Int>>? = null,
    overdueYearMonths: Set<Pair<Int, Int>> = emptySet(),
    currentYearMonth: Pair<Int, Int>? = null,
    onNodeClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // قسطِ متناظر با ماهِ جاریِ تقویم (اگر داخل بازهٔ وام باشد) — نه «قسط بعدیِ ترتیبی»
    val currentMonthIndex = currentYearMonth?.let { (cy, cm) ->
        val n = (cy * 12 + cm - 1) - (startYear * 12 + startMonth - 1) + 1
        if (n in 1..totalMonths) n else null
    } ?: (paidMonths + 1).coerceAtMost(totalMonths)

    // Pulsing animation for the current active month
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Legend indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تقویم ماه‌های اقساط (به تفکیک ماه شمسی):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Paid
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text("پرداخت شده", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }

                // Current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text("قسط پیش‌رو", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }

                // فیکس bug.md/F1+NB-31: راهنمای وضعیت عقب‌افتاده
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                    )
                    Text("عقب‌افتاده", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }

                // Remaining
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Text("مانده", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
            }
        }

        // Nodes Grid (FlowRow) with Shamsi Month Names
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (k in 1..totalMonths) {
                // Calculate exact Shamsi month and year for this installment
                val monthFromZero = (startMonth - 1) + (k - 1)
                val year = startYear + (monthFromZero / 12)
                val month = (monthFromZero % 12) + 1
                val monthName = ShamsiDateHelper.getMonthName(month)
                // فیکس bug.md/NB-23: متغیر مردهٔ shortYear (بدون هیچ استفاده‌ای) حذف شد.

                // فیکس bug.md/F1+NB-31: پرداخت‌بودن از رکوردهای واقعی (ماه شمسی)؛
                // ماهِ سررسیدگذشتهٔ بدون رکورد = عقب‌افتاده
                val isPaid = paidYearMonths?.contains(year to month) ?: (k <= paidMonths)
                val isOverdue = !isPaid && overdueYearMonths.contains(year to month)
                val isCurrent = k == currentMonthIndex && !isPaid && paidMonths < totalMonths

                val nodeBgColor by animateColorAsState(
                    targetValue = when {
                        isPaid -> accentColor
                        isOverdue -> MaterialTheme.colorScheme.errorContainer
                        isCurrent -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    label = "nodeColor_$k"
                )

                Surface(
                    modifier = Modifier
                        .widthIn(min = 54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isCurrent) Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                shape = RoundedCornerShape(12.dp)
                            )
                            else Modifier
                        )
                        .then(
                            if (onNodeClick != null) Modifier.clickable { onNodeClick(k) }
                            else Modifier
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = nodeBgColor,
                    tonalElevation = if (isCurrent) 4.dp else 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (isPaid) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = monthName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent || isPaid || isOverdue) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                color = when {
                                    isPaid -> Color.White
                                    isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                    isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        // Year and installment number
                        Text(
                            text = "${NumberFormatter.formatNumber(year, usePersian = usePersianDigits)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = when {
                                isPaid -> Color.White.copy(alpha = 0.85f)
                                isOverdue -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

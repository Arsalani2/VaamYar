package com.aistudio.vamyar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
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
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.ShamsiDateHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallmentNodesView(
    totalMonths: Int,
    paidMonths: Int,
    startYear: Int,
    startMonth: Int,
    accentColor: Color,
    usePersianDigits: Boolean = true,
    paidYearMonths: Set<Pair<Int, Int>>? = null,
    overdueYearMonths: Set<Pair<Int, Int>> = emptySet(),
    currentYearMonth: Pair<Int, Int>? = null,
    onNodeClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val brandViolet = Color(0xFF6C27DF)

    // Current month installment index
    val currentMonthIndex = currentYearMonth?.let { (cy, cm) ->
        val n = (cy * 12 + cm - 1) - (startYear * 12 + startMonth - 1) + 1
        if (n in 1..totalMonths) n else null
    } ?: (paidMonths + 1).coerceAtMost(totalMonths)

    // Pulsing animation for the current active month
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-box Headline & Description
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = brandViolet,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "تقویم ماه‌های اقساط",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "(به تفکیک ماه شمسی)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF94A3B8)
            )
        }

        // Visual Legend / Filter Guide Card matching Stitch prototype
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0).copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item 1: Paid
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text("پرداخت شده", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                }

                // Item 2: Upcoming
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, brandViolet, CircleShape)
                            .background(Color.White)
                    )
                    Text("قسط پیش‌رو", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                }

                // Item 3: Overdue
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE11D48))
                    )
                    Text("عقب‌افتاده", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
                }

                // Item 4: Remaining
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFCBD5E1))
                    )
                    Text("مانده", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF94A3B8))
                }
            }
        }

        // Horizontal Scrollable / FlowRow Grid with Responsive Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (k in 1..totalMonths) {
                val monthFromZero = (startMonth - 1) + (k - 1)
                val year = startYear + (monthFromZero / 12)
                val month = (monthFromZero % 12) + 1
                val monthName = ShamsiDateHelper.getMonthName(month)

                val isPaid = paidYearMonths?.contains(year to month) ?: (k <= paidMonths)
                val isOverdue = !isPaid && overdueYearMonths.contains(year to month)
                val isCurrent = k == currentMonthIndex && !isPaid && paidMonths < totalMonths

                val nodeBgColor by animateColorAsState(
                    targetValue = when {
                        isPaid -> accentColor
                        isOverdue -> Color(0xFFFFF1F2)
                        isCurrent -> Color.White
                        else -> Color(0xFFF8FAFC)
                    },
                    label = "nodeColor_$k"
                )

                Surface(
                    modifier = Modifier
                        .widthIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isCurrent) Modifier.border(
                                width = 2.dp,
                                color = brandViolet.copy(alpha = pulseAlpha),
                                shape = RoundedCornerShape(12.dp)
                            )
                            else if (isOverdue) Modifier.border(
                                width = 1.dp,
                                color = Color(0xFFFECDD3),
                                shape = RoundedCornerShape(12.dp)
                            )
                            else if (!isPaid) Modifier.border(
                                width = 1.dp,
                                color = Color(0xFFE2E8F0),
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
                    shadowElevation = if (isPaid) 2.dp else if (isCurrent) 1.dp else 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (isPaid) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            } else if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(brandViolet)
                                )
                            }
                            Text(
                                text = monthName,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent || isPaid || isOverdue) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isPaid -> Color.White
                                    isOverdue -> Color(0xFFBE123C)
                                    isCurrent -> brandViolet
                                    else -> Color(0xFF475569)
                                }
                            )
                        }

                        Text(
                            text = NumberFormatter.formatNumber(year, usePersian = usePersianDigits),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Normal,
                            color = when {
                                isPaid -> Color.White.copy(alpha = 0.85f)
                                isOverdue -> Color(0xFFE11D48).copy(alpha = 0.8f)
                                isCurrent -> brandViolet.copy(alpha = 0.8f)
                                else -> Color(0xFF94A3B8)
                            }
                        )
                    }
                }
            }
        }
    }
}

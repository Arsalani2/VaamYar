package com.aistudio.vamyar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.vamyar.ui.FinancialOverview
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.ShamsiDate

/**
 * کارت خلاصه وضعیت مالی ماهانه (Modern Persian Fintech)
 * بازطراحی‌شده منطبق بر پروتوتایپ استیچ:
 * - عنوان و بج ماه جاری (مثلاً «آبان ماه»)
 * - مبلغ شاخص مانده اقساط پرداخت‌نشده
 * - تفکیک ۲ ستونه با خط جداکننده (پرداخت‌شده این ماه و طلب از شرکا)
 * - نوار پیشرفت گرادیانی با درصد تسویه
 */
@Composable
fun FinancialSummaryCard(
    overview: FinancialOverview,
    currentShamsiDate: ShamsiDate? = null,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = overview.overallProgress,
        animationSpec = tween(durationMillis = 900),
        label = "summaryProgress"
    )

    val isAllMonthPaid = overview.arsalanUnpaidRemainingThisMonth == 0L && overview.activeLoansCount > 0
    val monthLabel = currentShamsiDate?.let { "${it.monthName} ماه" } ?: "ماه جاری"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF8F9FD)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            // Ambient Soft Violet Glow in corner
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.06f))
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Row: Label & Month Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مانده اقساط پرداخت‌نشده",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF5F3FF),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Main Headline Amount: Big & Bold
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = NumberFormatter.formatNumber(
                            number = overview.arsalanUnpaidRemainingThisMonth,
                            usePersian = usePersianDigits
                        ),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isAllMonthPaid) Color(0xFF10B981) else Color(0xFF0F172A),
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "تومان",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )

                    if (isAllMonthPaid) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "تسویه این ماه",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                    }
                }

                // Dashed divider separating headline from breakdown
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE2E8F0))
                )

                // 2-Column Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Col 1: Paid This Month
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "پرداخت‌شده این ماه",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = NumberFormatter.formatNumber(overview.arsalanPaidThisMonth, usePersianDigits),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = " از ${NumberFormatter.formatNumber(overview.arsalanMonthlyShare, usePersianDigits)}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(Color(0xFFF1F5F9))
                    )

                    // Col 2: Receivables from Partners
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "طلب باقیمانده از شرکا",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF43F5E)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (overview.othersRemainingUnpaid == 0L) "وصول شد ✓"
                                else NumberFormatter.formatNumber(overview.othersRemainingUnpaid, usePersianDigits),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (overview.othersRemainingUnpaid == 0L) Color(0xFF10B981) else Color(0xFFE11D48)
                            )
                            if (overview.othersRemainingUnpaid > 0L) {
                                Text(
                                    text = " تومان",
                                    fontSize = 10.sp,
                                    color = Color(0xFFF43F5E).copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Bottom Progress Bar
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "پیشرفت تسویه اقساط",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "${NumberFormatter.formatNumber((animatedProgress * 100).toInt(), usePersian = usePersianDigits)}٪",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9)
                        )
                    }

                    // Track & Gradient Fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF6D28D9),
                                            Color(0xFF4F46E5)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

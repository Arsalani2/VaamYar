package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Loan
import com.example.util.NumberFormatter
import kotlin.math.max

@Composable
fun DebtDonutChart(
    totalPaid: Long,
    totalRemaining: Long,
    modifier: Modifier = Modifier
) {
    val total = (totalPaid + totalRemaining).coerceAtLeast(1L)
    val paidRatio = (totalPaid.toFloat() / total).coerceIn(0f, 1f)

    val animatedPaidRatio by animateFloatAsState(
        targetValue = paidRatio,
        animationSpec = tween(durationMillis = 1200),
        label = "donutPaid"
    )

    val paidColor = MaterialTheme.colorScheme.primary
    val remainingColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "نسبت کل پرداخت شده به بدهی مانده",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(190.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 24.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(
                        (size.width - diameter) / 2,
                        (size.height - diameter) / 2
                    )
                    val arcSize = Size(diameter, diameter)

                    // Background remaining arc (full circle)
                    drawArc(
                        color = remainingColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Paid arc
                    if (animatedPaidRatio > 0.01f) {
                        drawArc(
                            color = paidColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedPaidRatio,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${NumberFormatter.toPersianDigits((animatedPaidRatio * 100).toInt())}٪",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "تسویه شده",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(paidColor)
                    )
                    Column {
                        Text(
                            text = "پرداخت شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = NumberFormatter.formatPrice(totalPaid),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(remainingColor)
                    )
                    Column {
                        Text(
                            text = "مانده بدهی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = NumberFormatter.formatPrice(totalRemaining),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyInstallmentsBarChart(
    loans: List<Loan>,
    modifier: Modifier = Modifier
) {
    if (loans.isEmpty()) return

    val maxAmount = loans.maxOfOrNull { it.monthlyAmount } ?: 1L
    var selectedLoan by remember { mutableStateOf<Loan?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مقایسه اقساط ماهانه وام‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                selectedLoan?.let { sel ->
                    Text(
                        text = NumberFormatter.formatPrice(sel.monthlyAmount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Bar Chart Canvas / Rows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                loans.forEach { loan ->
                    val ratio = (loan.monthlyAmount.toFloat() / maxAmount).coerceIn(0.1f, 1f)
                    val animatedHeightRatio by animateFloatAsState(
                        targetValue = ratio,
                        animationSpec = tween(durationMillis = 800),
                        label = "bar_${loan.id}"
                    )
                    val isSelected = selectedLoan?.id == loan.id
                    val barColor = Color(loan.colorHex)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                selectedLoan = if (isSelected) null else loan
                            }
                            .padding(horizontal = 4.dp)
                    ) {
                        // Top due day indicator
                        Text(
                            text = "${NumberFormatter.toPersianDigits(loan.dueDay)}ام",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Animated Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isSelected) 0.85f else 0.7f)
                                .fillMaxHeight(animatedHeightRatio * 0.75f)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (isSelected) barColor else barColor.copy(alpha = 0.85f)
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Loan Short Name
                        Text(
                            text = loan.title.replace("بانک ", "").take(5),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "💡 برای دیدن مبلغ دقیق هر وام، روی ستون آن ضربه بزنید.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

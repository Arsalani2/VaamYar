package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.util.NumberFormatter
import com.example.util.ShamsiDateHelper

enum class LoanViewMode(val titleFa: String) {
    RECTANGLE("مستطیلی"),
    SQUARE("مربعی"),
    CHART("نموداری")
}

@Composable
fun LoanItemCard(
    loan: Loan,
    viewMode: LoanViewMode = LoanViewMode.RECTANGLE,
    debtorShares: List<DebtorShare> = emptyList(),
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onTogglePaidThisMonth: () -> Unit,
    onIncrementPaid: () -> Unit,
    onDecrementPaid: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = loan.progress,
        label = "loanProgress"
    )

    val currentShamsi = remember { ShamsiDateHelper.getCurrentShamsiDate() }
    val daysRemaining = remember(loan.dueDay, currentShamsi.day) {
        loan.dueDay - currentShamsi.day
    }

    val loanAccentColor = Color(loan.colorHex)
    val myShare = loan.effectiveMyShare
    val hasPartners = loan.monthlyAmount > myShare || debtorShares.isNotEmpty()
    val partnersTotalShare = (loan.monthlyAmount - myShare).coerceAtLeast(0L)

    // Clear paid border / tint
    val cardBorder = if (loan.isCurrentMonthPaid) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
    } else {
        Modifier
    }

    when (viewMode) {
        LoanViewMode.RECTANGLE -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .then(cardBorder)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (loan.isCurrentMonthPaid) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Row: Title, Due / Paid Chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(loanAccentColor)
                            )
                            Column {
                                Text(
                                    text = loan.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${NumberFormatter.formatNumber(loan.dueDay, usePersian = usePersianDigits)} هر ماه",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Prominent Paid Status Badge
                        if (loan.isCurrentMonthPaid) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "قسط این ماه پرداخت شد ✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    loan.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                                    daysRemaining == 0 -> MaterialTheme.colorScheme.errorContainer
                                    daysRemaining in 1..3 -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = when {
                                        loan.isCompleted -> "تسویه کامل شده"
                                        daysRemaining == 0 -> "سررسید امروز!"
                                        daysRemaining > 0 -> "در انتظار پرداخت (${NumberFormatter.formatNumber(daysRemaining, usePersian = usePersianDigits)} روز مانده)"
                                        else -> "در انتظار پرداخت"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        loan.isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                                        daysRemaining == 0 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // PRIMARY AMOUNT: Arsalan's Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "قسط سهم شما (من)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = NumberFormatter.formatPrice(
                                    amount = myShare,
                                    usePersian = usePersianDigits,
                                    compactThousands = compactThousands
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Secondary if shared
                        if (hasPartners) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "کل قسط بانک: ${NumberFormatter.formatPrice(loan.monthlyAmount, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "سهم شرکا: ${NumberFormatter.formatPrice(partnersTotalShare, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Progress bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${NumberFormatter.formatNumber(loan.paidMonths, usePersian = usePersianDigits)} از ${NumberFormatter.formatNumber(loan.totalMonths, usePersian = usePersianDigits)} قسط پرداخت شده",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${NumberFormatter.formatNumber((loan.progress * 100).toInt(), usePersian = usePersianDigits)}٪",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = loanAccentColor
                            )
                        }

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (loan.isCurrentMonthPaid) MaterialTheme.colorScheme.primary else loanAccentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Bottom Row: Clear Payment Toggle & Details Prompt
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expanded) "بستن تقویم ماه‌ها ▲" else "نمایش ماه‌های پرداخت شده ▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Clear Payment Status Button
                        Button(
                            onClick = onTogglePaidThisMonth,
                            shape = RoundedCornerShape(10.dp),
                            colors = if (loan.isCurrentMonthPaid) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = if (loan.isCurrentMonthPaid) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (loan.isCurrentMonthPaid) "پرداخت شده ✓" else "پرداخت این ماه",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Expanded Section with Shamsi Month Names Nodes
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Installment Nodes Visualization with Shamsi Month Names
                            InstallmentNodesView(
                                totalMonths = loan.totalMonths,
                                paidMonths = loan.paidMonths,
                                startYear = loan.startYear,
                                startMonth = loan.startMonth,
                                accentColor = loanAccentColor,
                                usePersianDigits = usePersianDigits
                            )

                            // Clear Shamsi Calendar Schedule Card
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "تقویم دقیق بازپرداخت به شمسی:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = ShamsiDateHelper.formatScheduleExplanation(
                                            startYear = loan.startYear,
                                            startMonth = loan.startMonth,
                                            totalMonths = loan.totalMonths,
                                            paidMonths = loan.paidMonths,
                                            usePersianDigits = usePersianDigits
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 20.sp
                                    )
                                }
                            }

                            // Partner breakdown if shared
                            if (debtorShares.isNotEmpty()) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "تفکیک سهم افراد از کل قسط (${NumberFormatter.formatPrice(loan.monthlyAmount, usePersian = usePersianDigits, compactThousands = compactThousands)}):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("• سهم شما (من):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                NumberFormatter.formatPrice(myShare, usePersian = usePersianDigits, compactThousands = compactThousands),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        debtorShares.forEach { share ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• سهم ${share.personName} (${share.shareDescription}):", style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    NumberFormatter.formatPrice(share.shareAmount, usePersian = usePersianDigits, compactThousands = compactThousands),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = onDelete,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حذف")
                                }

                                TextButton(onClick = onEdit) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ویرایش وام و سهم‌ها")
                                }
                            }
                        }
                    }
                }
            }
        }

        LoanViewMode.SQUARE -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .then(cardBorder)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (loan.isCurrentMonthPaid) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(loanAccentColor)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (loan.isCurrentMonthPaid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (loan.isCurrentMonthPaid) "پرداخت شد ✓" else "${NumberFormatter.formatNumber(loan.dueDay, usePersian = usePersianDigits)}ام",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (loan.isCurrentMonthPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = loan.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    // Arsalan's Share as main amount
                    Column {
                        Text(
                            text = "سهم من",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = NumberFormatter.formatPrice(myShare, usePersian = usePersianDigits, compactThousands = compactThousands),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (loan.isCurrentMonthPaid) MaterialTheme.colorScheme.primary else loanAccentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${NumberFormatter.formatNumber(loan.paidMonths, usePersian = usePersianDigits)}/${NumberFormatter.formatNumber(loan.totalMonths, usePersian = usePersianDigits)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FilledTonalIconButton(
                            onClick = onTogglePaidThisMonth,
                            modifier = Modifier.size(28.dp),
                            colors = if (loan.isCurrentMonthPaid) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors()
                            }
                        ) {
                            Icon(
                                imageVector = if (loan.isCurrentMonthPaid) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            InstallmentNodesView(
                                totalMonths = loan.totalMonths,
                                paidMonths = loan.paidMonths,
                                startYear = loan.startYear,
                                startMonth = loan.startMonth,
                                accentColor = loanAccentColor,
                                usePersianDigits = usePersianDigits
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        LoanViewMode.CHART -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .then(cardBorder)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (loan.isCurrentMonthPaid) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Mini Donut Chart
                        val progressColor = if (loan.isCurrentMonthPaid) MaterialTheme.colorScheme.primary else loanAccentColor
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 8.dp.toPx()
                                val diameter = size.minDimension - strokeWidth
                                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                                val arcSize = Size(diameter, diameter)

                                drawArc(
                                    color = Color.LightGray.copy(alpha = 0.3f),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )

                                drawArc(
                                    color = progressColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "${NumberFormatter.formatNumber((animatedProgress * 100).toInt(), usePersian = usePersianDigits)}٪",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Info with Arsalan's share prominent
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = loan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (loan.isCurrentMonthPaid) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "سهم من: ${NumberFormatter.formatPrice(myShare, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (hasPartners) {
                                Text(
                                    text = "کل قسط بانک: ${NumberFormatter.formatPrice(loan.monthlyAmount, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = onTogglePaidThisMonth,
                            colors = if (loan.isCurrentMonthPaid) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors()
                            }
                        ) {
                            Icon(
                                imageVector = if (loan.isCurrentMonthPaid) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null
                            )
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            InstallmentNodesView(
                                totalMonths = loan.totalMonths,
                                paidMonths = loan.paidMonths,
                                startYear = loan.startYear,
                                startMonth = loan.startMonth,
                                accentColor = loanAccentColor,
                                usePersianDigits = usePersianDigits
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onEdit) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ویرایش وام")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

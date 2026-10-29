package com.aistudio.vamyar.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
import com.aistudio.vamyar.model.LoanShareCalculator
import com.aistudio.vamyar.util.LoanIconDisplay
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.ShamsiDate
import com.aistudio.vamyar.util.ShamsiDateHelper

enum class LoanViewMode(val titleFa: String) {
    RECTANGLE("مستطیلی"),
    SQUARE("مربعی"),
    CHART("نموداری")
}

/**
 * کارت نمایش وام در سه حالت بصری فین‌تک مدرن (Modern Persian Fintech):
 * ۱. مستطیلی (RECTANGLE): کارت کامل با جزئیات، تفکیک سهم شرکا، پروگرس‌بار خطی و اکشن‌های سریع
 * ۲. مربعی (SQUARE): کارت شبکه ۲ستونه با تمرکز بر مبلغ سهم شما، بج وضعیت و دکمه دایره‌ای پرداخت
 * ۳. نموداری (CHART): چیدمان تک‌ستونه فشرده با گیج دایره‌ای درصدی (Donut Gauge) و کلید پرداخت
 */
@Composable
fun LoanItemCard(
    loan: Loan,
    currentShamsiDate: ShamsiDate,
    viewMode: LoanViewMode = LoanViewMode.RECTANGLE,
    debtorShares: List<DebtorShare> = emptyList(),
    installments: List<LoanInstallment> = emptyList(),
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

    val daysRemaining = remember(loan.dueDay, currentShamsiDate.year, currentShamsiDate.month, currentShamsiDate.day) {
        ShamsiDateHelper.daysUntilDue(loan.dueDay, currentShamsiDate)
    }

    val daysToDueThisMonth = remember(loan.dueDay, currentShamsiDate.year, currentShamsiDate.month, currentShamsiDate.day) {
        ShamsiDateHelper.daysUntilDueThisMonth(loan.dueDay, currentShamsiDate)
    }

    val paidYearMonths = remember(installments) { installments.map { it.year to it.month }.toSet() }
    val currentYearMonth = remember(currentShamsiDate.year, currentShamsiDate.month) {
        currentShamsiDate.year to currentShamsiDate.month
    }
    val overdueYearMonths = remember(
        loan.id, loan.totalMonths, loan.startYear, loan.startMonth,
        installments, currentShamsiDate.year, currentShamsiDate.month
    ) {
        val curTotal = currentShamsiDate.year * 12 + (currentShamsiDate.month - 1)
        (1..loan.totalMonths).mapNotNull { k ->
            val m = ShamsiDateHelper.installmentMonth(loan.startYear, loan.startMonth, k)
            val t = m.year * 12 + (m.month - 1)
            if (t < curTotal && (m.year to m.month) !in paidYearMonths) m.year to m.month else null
        }.toSet()
    }

    val loanAccentColor = Color(loan.colorHex)
    val partnersTotalShare = if (debtorShares.isNotEmpty()) {
        debtorShares.sumOf { it.shareAmount }
    } else {
        loan.othersShareAmount
    }
    val myShare = LoanShareCalculator.computeMyShare(loan, debtorShares)
    val hasPartners = partnersTotalShare > 0L || loan.hasPartners

    val brandViolet = Color(0xFF6D28D9)

    when (viewMode) {
        // ۱. حالت مستطیلی (RECTANGLE / List Mode)
        LoanViewMode.RECTANGLE -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(
                    width = if (loan.isCurrentMonthPaid) 1.5.dp else 1.dp,
                    color = if (loan.isCurrentMonthPaid) brandViolet.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Row: Brand Icon & Title & Status Badge
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(loanAccentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LoanIconDisplay(
                                    iconName = loan.iconName,
                                    iconShape = loan.iconShape,
                                    customIconPath = loan.customIconPath,
                                    tintColor = loanAccentColor,
                                    size = 40.dp,
                                    iconSize = 22.dp
                                )
                            }

                            Column {
                                Text(
                                    text = loan.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "${NumberFormatter.formatNumber(loan.dueDay, usePersian = usePersianDigits)}ام هر ماه",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Status Badge
                        if (loan.isCurrentMonthPaid) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFECFDF5),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "پرداخت شد",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = when {
                                    loan.isCompleted -> Color(0xFFF1F5F9)
                                    daysToDueThisMonth < 0 -> Color(0xFFFEF2F2)
                                    daysRemaining == 0 -> Color(0xFFFFFBEB)
                                    else -> Color(0xFFFFFBEB)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        loan.isCompleted -> Color(0xFFCBD5E1)
                                        daysToDueThisMonth < 0 -> Color(0xFFFECDD3)
                                        daysRemaining == 0 -> Color(0xFFFDE68A)
                                        else -> Color(0xFFFDE68A)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (daysToDueThisMonth >= 0 && !loan.isCompleted) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFD97706))
                                        )
                                    }
                                    Text(
                                        text = when {
                                            loan.isCompleted -> "تسویه کامل"
                                            daysToDueThisMonth < 0 -> "عقب‌افتاده (${NumberFormatter.formatNumber(-daysToDueThisMonth, usePersian = usePersianDigits)} روز)"
                                            daysRemaining == 0 -> "سررسید امروز"
                                            daysRemaining > 0 -> "در انتظار پرداخت (${NumberFormatter.formatNumber(daysRemaining, usePersian = usePersianDigits)} روز)"
                                            else -> "در انتظار پرداخت"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            loan.isCompleted -> Color(0xFF64748B)
                                            daysToDueThisMonth < 0 -> Color(0xFFDC2626)
                                            daysRemaining == 0 -> Color(0xFFB45309)
                                            else -> Color(0xFFB45309)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Multi-party breakdown block (if shared)
                    if (hasPartners) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("کل قسط:", fontSize = 11.sp, color = Color(0xFF64748B))
                                    Text(
                                        "${NumberFormatter.formatNumber(loan.monthlyAmount, usePersianDigits)} تومان",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("سهم شرکا:", fontSize = 11.sp, color = Color(0xFFF43F5E))
                                    Text(
                                        "${NumberFormatter.formatNumber(partnersTotalShare, usePersianDigits)} تومان",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE11D48)
                                    )
                                }
                            }
                        }
                    }

                    // User Share Amount Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "قسط سهم شما:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = NumberFormatter.formatNumber(myShare, usePersianDigits),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (loan.isCurrentMonthPaid) Color(0xFF0F172A) else brandViolet
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "تومان",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Linear Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${NumberFormatter.formatNumber(loan.paidMonths, usePersianDigits)} از ${NumberFormatter.formatNumber(loan.totalMonths, usePersianDigits)} قسط پرداخت شده",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "${NumberFormatter.formatNumber((loan.progress * 100).toInt(), usePersianDigits)}٪",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandViolet
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFF1F5F9))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(brandViolet)
                            )
                        }
                    }

                    // Bottom Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expanded) "بستن تقویم اقساط ▲" else "مشاهده جدول اقساط ▼",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandViolet
                        )

                        // Quick pay button
                        Button(
                            onClick = onTogglePaidThisMonth,
                            shape = RoundedCornerShape(12.dp),
                            colors = if (loan.isCurrentMonthPaid) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFECFDF5),
                                    contentColor = Color(0xFF059669)
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = brandViolet,
                                    contentColor = Color.White
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = if (loan.isCurrentMonthPaid) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (loan.isCurrentMonthPaid) "پرداخت شد" else "پرداخت سریع قسط",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Expanded Calendar Nodes View
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            InstallmentNodesView(
                                totalMonths = loan.totalMonths,
                                paidMonths = loan.paidMonths,
                                startYear = loan.startYear,
                                startMonth = loan.startMonth,
                                accentColor = loanAccentColor,
                                usePersianDigits = usePersianDigits,
                                paidYearMonths = paidYearMonths,
                                overdueYearMonths = overdueYearMonths,
                                currentYearMonth = currentYearMonth,
                                onNodeClick = { clickedInstallment ->
                                    val m = ShamsiDateHelper.installmentMonth(loan.startYear, loan.startMonth, clickedInstallment)
                                    val isPaid = (m.year to m.month) in paidYearMonths
                                    if (isPaid) onDecrementPaid() else onIncrementPaid()
                                }
                            )

                            // Edit and Delete buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = onDelete,
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حذف وام", fontSize = 11.sp)
                                }

                                TextButton(onClick = onEdit) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = brandViolet)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ویرایش وام و سهم‌ها", fontSize = 11.sp, color = brandViolet)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ۲. حالت مربعی (SQUARE / Grid Mode)
        LoanViewMode.SQUARE -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(5.dp)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(
                    width = if (loan.isCurrentMonthPaid) 1.5.dp else 1.dp,
                    color = if (loan.isCurrentMonthPaid) brandViolet.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top: Brand Icon & Status Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(loanAccentColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            LoanIconDisplay(
                                iconName = loan.iconName,
                                iconShape = loan.iconShape,
                                customIconPath = loan.customIconPath,
                                tintColor = loanAccentColor,
                                size = 30.dp,
                                iconSize = 18.dp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (loan.isCurrentMonthPaid) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                            border = BorderStroke(
                                1.dp,
                                if (loan.isCurrentMonthPaid) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Text(
                                text = if (loan.isCurrentMonthPaid) "پرداخت شد" else "${NumberFormatter.formatNumber(loan.dueDay, usePersian = usePersianDigits)}ام",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (loan.isCurrentMonthPaid) Color(0xFF059669) else Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Title & Amount
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = loan.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "سهم شما",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = NumberFormatter.formatNumber(myShare, usePersianDigits),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (loan.isCurrentMonthPaid) Color(0xFF0F172A) else brandViolet
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("تومان", fontSize = 9.sp, color = Color(0xFF64748B))
                        }
                    }

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(brandViolet)
                        )
                    }

                    // Bottom Row: Counter & Quick Pay Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${NumberFormatter.formatNumber(loan.paidMonths, usePersianDigits)}/${NumberFormatter.formatNumber(loan.totalMonths, usePersianDigits)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (loan.isCurrentMonthPaid) Color(0xFF10B981)
                                    else Color(0xFFF5F3FF)
                                )
                                .border(
                                    1.dp,
                                    if (loan.isCurrentMonthPaid) Color(0xFF059669) else Color(0xFFDDD6FE),
                                    CircleShape
                                )
                                .clickable { onTogglePaidThisMonth() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (loan.isCurrentMonthPaid) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                tint = if (loan.isCurrentMonthPaid) Color.White else brandViolet,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Expanded Section
                    AnimatedVisibility(visible = expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            InstallmentNodesView(
                                totalMonths = loan.totalMonths,
                                paidMonths = loan.paidMonths,
                                startYear = loan.startYear,
                                startMonth = loan.startMonth,
                                accentColor = loanAccentColor,
                                usePersianDigits = usePersianDigits,
                                paidYearMonths = paidYearMonths,
                                overdueYearMonths = overdueYearMonths,
                                currentYearMonth = currentYearMonth
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = brandViolet)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ۳. حالت نموداری (CHART / Compact Circular Gauge Mode)
        LoanViewMode.CHART -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(
                    width = if (loan.isCurrentMonthPaid) 1.5.dp else 1.dp,
                    color = if (loan.isCurrentMonthPaid) brandViolet.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Right: Brand Icon + Title + Amount
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(loanAccentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LoanIconDisplay(
                                    iconName = loan.iconName,
                                    iconShape = loan.iconShape,
                                    customIconPath = loan.customIconPath,
                                    tintColor = loanAccentColor,
                                    size = 40.dp,
                                    iconSize = 22.dp
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = loan.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = NumberFormatter.formatNumber(myShare, usePersianDigits),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (loan.isCurrentMonthPaid) Color(0xFF0F172A) else brandViolet
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("تومان", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }

                        // Left: Circular Progress Gauge + Quick Action Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Donut Progress Gauge
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 3.5.dp.toPx()
                                    val diameter = size.minDimension - strokeWidth
                                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                                    val arcSize = Size(diameter, diameter)

                                    drawArc(
                                        color = Color(0xFFF1F5F9),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )

                                    drawArc(
                                        color = if (loan.isCurrentMonthPaid) Color(0xFF10B981) else brandViolet,
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
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }

                            // Quick Action Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (loan.isCurrentMonthPaid) brandViolet
                                        else Color(0xFFF1F5F9)
                                    )
                                    .clickable { onTogglePaidThisMonth() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (loan.isCurrentMonthPaid) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (loan.isCurrentMonthPaid) Color.White else Color(0xFF475569),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Expanded Section
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            InstallmentNodesView(
                                totalMonths = loan.totalMonths,
                                paidMonths = loan.paidMonths,
                                startYear = loan.startYear,
                                startMonth = loan.startMonth,
                                accentColor = loanAccentColor,
                                usePersianDigits = usePersianDigits,
                                paidYearMonths = paidYearMonths,
                                overdueYearMonths = overdueYearMonths,
                                currentYearMonth = currentYearMonth
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onEdit) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = brandViolet)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ویرایش وام", fontSize = 11.sp, color = brandViolet)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

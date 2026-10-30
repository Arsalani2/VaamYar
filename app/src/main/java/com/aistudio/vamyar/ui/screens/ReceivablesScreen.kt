package com.aistudio.vamyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.ui.PersonReceivableSummary
import com.aistudio.vamyar.ui.theme.SuccessGreen
import com.aistudio.vamyar.ui.dialogs.ReminderShareDialog
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.ShamsiDate

@Composable
fun ReceivablesScreen(
    personSummaries: List<PersonReceivableSummary>,
    activeUserName: String = "شما",
    currentShamsiDate: ShamsiDate,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onToggleSharePaid: (Long, Boolean) -> Unit,
    onAddShare: ((DebtorShare) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var personToRemind by remember { mutableStateOf<PersonReceivableSummary?>(null) }

    val totalReceivableAmount = remember(personSummaries) {
        personSummaries.sumOf { it.totalMonthlyShare }
    }

    val totalCollectedThisMonth = remember(personSummaries) {
        personSummaries.flatMap { it.shares }
            .filter { it.isPaidForCurrentMonth }
            .sumOf { it.shareAmount }
    }

    val remainingUnpaid = (totalReceivableAmount - totalCollectedThisMonth).coerceAtLeast(0L)

    val collectionProgress = if (totalReceivableAmount > 0) {
        (totalCollectedThisMonth.toFloat() / totalReceivableAmount).coerceIn(0f, 1f)
    } else 0f

    val brandViolet = MaterialTheme.colorScheme.primary
    val brandIndigo = MaterialTheme.colorScheme.secondary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "مطالبات",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "سهم واریزی شرکای وام به حساب $activeUserName",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Minimal Summary Hero Card (matching Stitch Modern Persian Fintech prototype)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receivables_summary_hero"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top row: Label + Received Progress Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "کل مانده این ماه:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = brandViolet.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, brandViolet.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(brandViolet)
                                )
                                Text(
                                    text = "${NumberFormatter.formatNumber((collectionProgress * 100).toInt(), usePersian = usePersianDigits)}٪ دریافت شده",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = brandViolet
                                )
                            }
                        }
                    }

                    // Key Focus Metric: Big Bold Remaining Unpaid
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = NumberFormatter.formatPrice( // فیکس bug.md/NB-46: عدد پولی + احترام به compactThousands
                                remainingUnpaid,
                                withUnit = false,
                                usePersian = usePersianDigits,
                                compactThousands = compactThousands
                            ),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = if (remainingUnpaid == 0L && totalReceivableAmount > 0) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "تومان",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Ultra-thin 6dp Progress Line with Violet Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(collectionProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(brandViolet, brandIndigo)
                                    )
                                )
                        )
                    }

                    // Bottom Row: Breakdown Counters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "دریافت‌شده: ${NumberFormatter.formatPrice(totalCollectedThisMonth, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "باقی‌مانده: ${NumberFormatter.formatPrice(remainingUnpaid, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingUnpaid == 0L) SuccessGreen else brandViolet
                        )
                    }
                }
            }
        }

        // Zero-State when no shared receivables exist
        if (personSummaries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(brandViolet.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = brandViolet,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = "هیچ شریکی برای وام‌ها ثبت نشده است",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "اگر وامی را با دوستان یا اعضای خانواده به اشتراک بگذارید، سهم پرداختی ماهانه آنها در این بخش دسته‌بندی و پیگیری خواهد شد.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        } else {
            // Partner Cards matching Stitch prototype
            items(personSummaries, key = { it.personName }) { summary ->
                PartnerReceivableCard(
                    summary = summary,
                    usePersianDigits = usePersianDigits,
                    compactThousands = compactThousands,
                    onToggleShare = onToggleSharePaid,
                    onRemindClick = { personToRemind = summary }
                )
            }
        }
    }

    // Modal: Reminder Share Dialog
    personToRemind?.let { person ->
        ReminderShareDialog(
            debtorName = person.personName,
            currentShamsiDate = currentShamsiDate,
            shares = person.shares,
            senderName = activeUserName,
            usePersianDigits = usePersianDigits,
            compactThousands = compactThousands,
            onDismiss = { personToRemind = null }
        )
    }
}

@Composable
fun PartnerReceivableCard(
    summary: PersonReceivableSummary,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onToggleShare: (Long, Boolean) -> Unit,
    onRemindClick: () -> Unit
) {
    val brandViolet = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("partner_card_${summary.personName}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Partner Header: Avatar + Name + Subtitle (Right) and Total Amount (Left)
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(brandViolet.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = summary.personName.take(1),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = brandViolet
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = summary.personName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${NumberFormatter.formatNumber(summary.loansCount, usePersian = usePersianDigits)} سهم اقساط این ماه",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Left: Total Partner Amount / Status
                Column(horizontalAlignment = Alignment.End) {
                    if (summary.isAllPaidCurrentMonth) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SuccessGreen.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = "تسویه کامل این ماه ✓",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = NumberFormatter.formatPrice(summary.remainingUnpaidThisMonth, withUnit = false, usePersian = usePersianDigits, compactThousands = compactThousands), // فیکس bug.md/NB-46
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "تومان طلب",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Indented & Guided Sub-items Section (Stitch prototype: right border guide line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                // Vertical Guide Line
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(brandViolet.copy(alpha = 0.2f))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // List of sub-shares
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    summary.shares.forEach { share ->
                        ReceivableShareRow(
                            share = share,
                            usePersianDigits = usePersianDigits,
                            compactThousands = compactThousands,
                            onToggle = { isChecked -> onToggleShare(share.id, isChecked) }
                        )
                    }
                }
            }

            // Sleek Reminder Action Button
            Surface(
                onClick = onRemindClick,
                shape = RoundedCornerShape(16.dp),
                color = brandViolet.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remind_button_${summary.personName}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = brandViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ارسال پیام یادآوری",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = brandViolet
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivableShareRow(
    share: DebtorShare,
    usePersianDigits: Boolean,
    compactThousands: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val brandViolet = MaterialTheme.colorScheme.primary
    val isPaid = share.isPaidForCurrentMonth

    Surface(
        onClick = { onToggle(!isPaid) },
        shape = RoundedCornerShape(14.dp),
        color = if (isPaid) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Right: Custom Rounded Checkbox + Loan Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Custom Rounded 22dp Checkbox
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isPaid) brandViolet else MaterialTheme.colorScheme.surface) // فیکس bug.md/NB-45
                        .border(
                            width = 1.5.dp,
                            color = if (isPaid) brandViolet else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(7.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPaid) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "پرداخت شده",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = share.loanTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${NumberFormatter.formatNumber(share.dueDay, usePersian = usePersianDigits)}م ماه",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (share.shareDescription.isNotBlank()) {
                            Text(
                                text = "• ${share.shareDescription}",
                                fontSize = 11.sp,
                                color = brandViolet.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Left: Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormatter.formatPrice(share.shareAmount, withUnit = false, usePersian = usePersianDigits, compactThousands = compactThousands), // فیکس bug.md/NB-46
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "تومان",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

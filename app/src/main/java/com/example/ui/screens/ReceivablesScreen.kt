package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DebtorShare
import com.example.ui.PersonReceivableSummary
import com.example.ui.dialogs.ReminderShareDialog
import com.example.util.NumberFormatter
import com.example.util.ShamsiDateHelper

@Composable
fun ReceivablesScreen(
    personSummaries: List<PersonReceivableSummary>,
    activeUserName: String = "شما",
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onToggleSharePaid: (Long, Boolean) -> Unit,
    onResetMonthlyStatus: () -> Unit,
    onAddShare: ((DebtorShare) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var personToRemind by remember { mutableStateOf<PersonReceivableSummary?>(null) }
    val currentShamsi = remember { ShamsiDateHelper.getCurrentShamsiDate() }

    val totalReceivableAmount = remember(personSummaries) {
        personSummaries.sumOf { it.totalMonthlyShare }
    }

    val totalCollectedThisMonth = remember(personSummaries) {
        personSummaries.flatMap { it.shares }.filter { it.isPaidForCurrentMonth }.sumOf { it.shareAmount }
    }

    val collectionProgress = if (totalReceivableAmount > 0) {
        (totalCollectedThisMonth.toFloat() / totalReceivableAmount).coerceIn(0f, 1f)
    } else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & Persona Header
        item {
            Column {
                Text(
                    text = "مدیریت پیشرفته مطالبات و طلب‌ها",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "سهم شرکایی که باید مبالغ اقساط را به حساب $activeUserName واریز کنند",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Summary Card with Collection Progress
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "کل طلب ماهانه $activeUserName:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = NumberFormatter.formatPrice(
                                    amount = totalReceivableAmount,
                                    usePersian = usePersianDigits,
                                    compactThousands = compactThousands
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "${NumberFormatter.formatNumber((collectionProgress * 100).toInt(), usePersian = usePersianDigits)}٪ دریافت شده",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Progress
                    LinearProgressIndicator(
                        progress = { collectionProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "واریز شده: ${NumberFormatter.formatPrice(totalCollectedThisMonth, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "باقی‌مانده: ${NumberFormatter.formatPrice((totalReceivableAmount - totalCollectedThisMonth).coerceAtLeast(0L), usePersian = usePersianDigits, compactThousands = compactThousands)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Action Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onResetMonthlyStatus) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("شروع ماه جدید (صفر کردن واریزها)")
                }
            }
        }

        // Person Cards (Zahra, Mohammadreza, Amirali)
        items(personSummaries, key = { it.personName }) { summary ->
            PersonShareCard(
                summary = summary,
                usePersianDigits = usePersianDigits,
                compactThousands = compactThousands,
                onToggleShare = onToggleSharePaid,
                onRemindClick = { personToRemind = summary }
            )
        }
    }

    personToRemind?.let { person ->
        ReminderShareDialog(
            debtorName = person.personName,
            shares = person.shares,
            senderName = activeUserName,
            onDismiss = { personToRemind = null }
        )
    }
}

@Composable
fun PersonShareCard(
    summary: PersonReceivableSummary,
    usePersianDigits: Boolean = true,
    compactThousands: Boolean = false,
    onToggleShare: (Long, Boolean) -> Unit,
    onRemindClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Name & Total
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
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = summary.personName.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column {
                        Text(
                            text = summary.personName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${NumberFormatter.formatNumber(summary.loansCount, usePersian = usePersianDigits)} سهم وام",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (summary.isAllPaidCurrentMonth) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "تسویه کامل این ماه ✓",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "مانده این ماه:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = NumberFormatter.formatPrice(
                                amount = summary.remainingUnpaidThisMonth,
                                usePersian = usePersianDigits,
                                compactThousands = compactThousands
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = "کل سهم: ${NumberFormatter.formatPrice(summary.totalMonthlyShare, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Sub-shares for this person
            summary.shares.forEach { share ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = share.loanTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "موعد: ${NumberFormatter.formatNumber(share.dueDay, usePersian = usePersianDigits)}ام هر ماه",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (share.shareDescription.isNotBlank()) {
                                Text(
                                    text = "(${share.shareDescription})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = NumberFormatter.formatPrice(
                                amount = share.shareAmount,
                                usePersian = usePersianDigits,
                                compactThousands = compactThousands
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Checkbox for paid this month
                        Checkbox(
                            checked = share.isPaidForCurrentMonth,
                            onCheckedChange = { isChecked ->
                                onToggleShare(share.id, isChecked)
                            }
                        )
                    }
                }
            }

            // Remind Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onRemindClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ارسال پیام یادآوری")
                }
            }
        }
    }
}

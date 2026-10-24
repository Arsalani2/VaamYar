package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.UserProfile
import com.example.ui.FinancialOverview
import com.example.ui.SmartFinancialTip
import com.example.ui.components.FinancialSummaryCard
import com.example.ui.components.LoanItemCard
import com.example.ui.components.LoanViewMode
import com.example.ui.components.SmartFinancialAdvisorCard
import com.example.ui.dialogs.AddEditLoanDialog
import com.example.util.ShamsiDate

@Composable
fun HomeScreen(
    loans: List<Loan>,
    allShares: List<DebtorShare>,
    allUsers: List<UserProfile>,
    currentShamsiDate: ShamsiDate,
    overview: FinancialOverview,
    smartTips: List<SmartFinancialTip>,
    viewMode: LoanViewMode,
    onViewModeChange: (LoanViewMode) -> Unit,
    usePersianDigits: Boolean,
    compactThousands: Boolean,
    onTogglePaidThisMonth: (Loan) -> Unit,
    onIncrementPaid: (Loan) -> Unit,
    onDecrementPaid: (Loan) -> Unit,
    onSaveLoan: (Loan, List<DebtorShare>) -> Unit,
    onDeleteLoan: (Long) -> Unit,
    onAddNewUser: (UserProfile) -> Unit,
    onTipAction: ((SmartFinancialTip) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var loanToEdit by remember { mutableStateOf<Loan?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (viewMode == LoanViewMode.SQUARE && loans.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // Summary Card with Remaining Unpaid Hero Amount
                item(span = { GridItemSpan(2) }) {
                    FinancialSummaryCard(
                        overview = overview,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands
                    )
                }

                // Smart Advisor Card
                if (smartTips.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        SmartFinancialAdvisorCard(
                            tips = smartTips,
                            onActionClick = onTipAction
                        )
                    }
                }

                // View Mode Switcher
                item(span = { GridItemSpan(2) }) {
                    ViewModeSelectorRow(
                        currentMode = viewMode,
                        onModeChange = onViewModeChange
                    )
                }

                items(loans, key = { it.id }) { loan ->
                    val loanShares = remember(allShares, loan.id) {
                        allShares.filter { it.loanId == loan.id }
                    }
                    LoanItemCard(
                        loan = loan,
                        viewMode = LoanViewMode.SQUARE,
                        debtorShares = loanShares,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands,
                        onTogglePaidThisMonth = { onTogglePaidThisMonth(loan) },
                        onIncrementPaid = { onIncrementPaid(loan) },
                        onDecrementPaid = { onDecrementPaid(loan) },
                        onEdit = { loanToEdit = loan },
                        onDelete = { onDeleteLoan(loan.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // Summary Card
                item {
                    FinancialSummaryCard(
                        overview = overview,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands
                    )
                }

                // Smart Financial Advisor Card
                if (smartTips.isNotEmpty()) {
                    item {
                        SmartFinancialAdvisorCard(
                            tips = smartTips,
                            onActionClick = onTipAction
                        )
                    }
                }

                // View Mode Switcher Row (shown when there are loans)
                if (loans.isNotEmpty()) {
                    item {
                        ViewModeSelectorRow(
                            currentMode = viewMode,
                            onModeChange = onViewModeChange
                        )
                    }
                }

                // Clean, welcoming Zero-State for new users
                if (loans.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Text(
                                    text = "خوش آمدید به وام‌یار!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "هنوز وامی در برنامه ثبت نشده است. برای مدیریت اقساط ماهانه، سررسیدها و تفکیک سهم شرکا، اولین وام خود را ثبت نمایید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ثبت اولین وام")
                                }
                            }
                        }
                    }
                } else {
                    items(loans, key = { it.id }) { loan ->
                        val loanShares = remember(allShares, loan.id) {
                            allShares.filter { it.loanId == loan.id }
                        }
                        LoanItemCard(
                            loan = loan,
                            viewMode = viewMode,
                            debtorShares = loanShares,
                            usePersianDigits = usePersianDigits,
                            compactThousands = compactThousands,
                            onTogglePaidThisMonth = { onTogglePaidThisMonth(loan) },
                            onIncrementPaid = { onIncrementPaid(loan) },
                            onDecrementPaid = { onDecrementPaid(loan) },
                            onEdit = { loanToEdit = loan },
                            onDelete = { onDeleteLoan(loan.id) }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_loan_fab")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن وام")
        }
    }

    // Redesigned Add/Edit Loan Dialog
    if (showAddDialog) {
        AddEditLoanDialog(
            loanToEdit = null,
            initialShares = emptyList(),
            allUsers = allUsers,
            currentShamsiDate = currentShamsiDate,
            usePersianDigits = usePersianDigits,
            compactThousands = compactThousands,
            onDismiss = { showAddDialog = false },
            onAddNewUser = onAddNewUser,
            onSave = { newLoan, shares ->
                onSaveLoan(newLoan, shares)
                showAddDialog = false
            }
        )
    }

    loanToEdit?.let { loan ->
        val existingShares = remember(allShares, loan.id) {
            allShares.filter { it.loanId == loan.id }
        }
        AddEditLoanDialog(
            loanToEdit = loan,
            initialShares = existingShares,
            allUsers = allUsers,
            currentShamsiDate = currentShamsiDate,
            usePersianDigits = usePersianDigits,
            compactThousands = compactThousands,
            onDismiss = { loanToEdit = null },
            onAddNewUser = onAddNewUser,
            onSave = { updatedLoan, shares ->
                onSaveLoan(updatedLoan, shares)
                loanToEdit = null
            }
        )
    }
}

@Composable
fun ViewModeSelectorRow(
    currentMode: LoanViewMode,
    onModeChange: (LoanViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "فهرست اقساط",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = currentMode == LoanViewMode.RECTANGLE,
                onClick = { onModeChange(LoanViewMode.RECTANGLE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = {
                    Icon(
                        imageVector = Icons.Default.ViewStream,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            ) {
                Text(LoanViewMode.RECTANGLE.titleFa, style = MaterialTheme.typography.labelSmall)
            }

            SegmentedButton(
                selected = currentMode == LoanViewMode.SQUARE,
                onClick = { onModeChange(LoanViewMode.SQUARE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            ) {
                Text(LoanViewMode.SQUARE.titleFa, style = MaterialTheme.typography.labelSmall)
            }

            SegmentedButton(
                selected = currentMode == LoanViewMode.CHART,
                onClick = { onModeChange(LoanViewMode.CHART) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            ) {
                Text(LoanViewMode.CHART.titleFa, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

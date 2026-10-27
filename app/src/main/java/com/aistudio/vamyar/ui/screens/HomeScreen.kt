package com.aistudio.vamyar.ui.screens

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
import androidx.compose.ui.unit.sp
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.ui.ArrearsPrompt
import com.aistudio.vamyar.ui.FinancialOverview
import com.aistudio.vamyar.ui.SmartFinancialTip
import com.aistudio.vamyar.ui.TipType
import com.aistudio.vamyar.ui.components.FinancialSummaryCard
import com.aistudio.vamyar.ui.components.LoanItemCard
import com.aistudio.vamyar.ui.components.LoanViewMode
import com.aistudio.vamyar.ui.components.SmartFinancialAdvisorCard
import com.aistudio.vamyar.ui.dialogs.AddEditLoanDialog
import com.aistudio.vamyar.util.LiquidityForecast
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.ShamsiDate

@Composable
fun HomeScreen(
    loans: List<Loan>,
    allShares: List<DebtorShare>,
    allUsers: List<UserProfile>,
    // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط (رنگ نودها) + دیالوگ عقب‌ماندگی
    allInstallments: List<LoanInstallment>,
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
    arrearsPrompt: ArrearsPrompt?,
    onPayOnlyCurrentMonth: (Loan) -> Unit,
    onPayAllArrears: (Loan) -> Unit,
    onDismissArrearsPrompt: () -> Unit,
    onSaveLoan: (Loan, List<DebtorShare>, Set<Pair<Int, Int>>) -> Unit,
    onDeleteLoan: (Long) -> Unit,
    onAddNewUser: (UserProfile) -> Unit,
    onTipAction: ((SmartFinancialTip) -> Unit)? = null,
    // فیکس bug.md/F7: پیش‌بینی نقدینگی ۳/۶ ماه آینده (تفکیک ماه‌ها در دیالوگ)
    liquidityForecast: LiquidityForecast? = null,
    modifier: Modifier = Modifier
) {
    var loanToEdit by remember { mutableStateOf<Loan?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    // فیکس bug.md/NB-22: حذف وام فقط پس از تأیید صریح کاربر انجام می‌شود؛
    // ضربهٔ اول فقط این دیالوگ را باز می‌کند و انصراف هیچ تغییری نمی‌کند.
    var loanToDelete by remember { mutableStateOf<Loan?>(null) }

    // فیکس bug.md/F7: دیالوگ تفکیک ماه‌های پیش‌بینی نقدینگی — با اکشنِ نکتهٔ
    // «پیش‌بینی نقدینگی» در دستیار هوشمند باز می‌شود.
    var showLiquidityDialog by remember { mutableStateOf(false) }

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
                            onActionClick = { tip ->
                                // فیکس bug.md/F7: اکشن نکتهٔ نقدینگی، دیالوگ تفکیک ماه‌ها را باز می‌کند
                                if (tip.type == TipType.LIQUIDITY_FORECAST) {
                                    showLiquidityDialog = true
                                } else {
                                    onTipAction?.invoke(tip)
                                }
                            }
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
                    val loanInstallments = remember(allInstallments, loan.id) {
                        allInstallments.filter { it.loanId == loan.id }
                    }
                    LoanItemCard(
                        loan = loan,
                        viewMode = LoanViewMode.SQUARE,
                        currentShamsiDate = currentShamsiDate,
                        debtorShares = loanShares,
                        installments = loanInstallments,
                        usePersianDigits = usePersianDigits,
                        compactThousands = compactThousands,
                        onTogglePaidThisMonth = { onTogglePaidThisMonth(loan) },
                        onIncrementPaid = { onIncrementPaid(loan) },
                        onDecrementPaid = { onDecrementPaid(loan) },
                        onEdit = { loanToEdit = loan },
                        onDelete = { loanToDelete = loan }
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
                            onActionClick = { tip ->
                                // فیکس bug.md/F7: اکشن نکتهٔ نقدینگی، دیالوگ تفکیک ماه‌ها را باز می‌کند
                                if (tip.type == TipType.LIQUIDITY_FORECAST) {
                                    showLiquidityDialog = true
                                } else {
                                    onTipAction?.invoke(tip)
                                }
                            }
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
                        val loanInstallments = remember(allInstallments, loan.id) {
                            allInstallments.filter { it.loanId == loan.id }
                        }
                        LoanItemCard(
                            loan = loan,
                            viewMode = viewMode,
                            currentShamsiDate = currentShamsiDate,
                            debtorShares = loanShares,
                            installments = loanInstallments,
                            usePersianDigits = usePersianDigits,
                            compactThousands = compactThousands,
                            onTogglePaidThisMonth = { onTogglePaidThisMonth(loan) },
                            onIncrementPaid = { onIncrementPaid(loan) },
                            onDecrementPaid = { onDecrementPaid(loan) },
                            onEdit = { loanToEdit = loan },
                            onDelete = { loanToDelete = loan }
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

    // فیکس bug.md/F1+NB-31: دیالوگ «فقط این ماه / همهٔ عقب‌مانده‌ها» — فقط وقتی
    // عقب‌ماندگی هست پرسیده می‌شود (تصمیم کاربر، بند ۵ متد فیکس NB-31).
    arrearsPrompt?.let { prompt ->
        val monthsText = prompt.arrearsMonths.joinToString("، ") { it.monthName }
        val countText = NumberFormatter.formatNumber(prompt.arrearsMonths.size, usePersian = true)
        AlertDialog(
            onDismissRequest = onDismissArrearsPrompt,
            title = { Text("عقب‌ماندگی اقساط: ${prompt.loan.title}") },
            text = {
                Text(
                    "این وام $countText ماهِ سررسیدگذشتهٔ پرداخت‌نشده دارد ($monthsText).\n" +
                        "فقط قسط همین ماه ثبت شود یا همهٔ عقب‌مانده‌ها تا الان؟"
                )
            },
            confirmButton = {
                TextButton(onClick = { onPayAllArrears(prompt.loan) }) {
                    Text("همهٔ عقب‌مانده‌ها")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onPayOnlyCurrentMonth(prompt.loan) }) {
                        Text("فقط این ماه")
                    }
                    TextButton(onClick = onDismissArrearsPrompt) {
                        Text("انصراف")
                    }
                }
            }
        )
    }

    // Redesigned Add/Edit Loan Dialog
    if (showAddDialog) {
        AddEditLoanDialog(
            loanToEdit = null,
            initialShares = emptyList(),
            allUsers = allUsers,
            // فیکس bug.md/NB-36: چک زندهٔ عنوان تکراری
            allLoanTitles = loans.map { it.title },
            currentShamsiDate = currentShamsiDate,
            usePersianDigits = usePersianDigits,
            compactThousands = compactThousands,
            onDismiss = { showAddDialog = false },
            onAddNewUser = onAddNewUser,
            onSave = { newLoan, shares, paidYearMonths ->
                onSaveLoan(newLoan, shares, paidYearMonths)
                showAddDialog = false
            }
        )
    }

    loanToEdit?.let { loan ->
        val existingShares = remember(allShares, loan.id) {
            allShares.filter { it.loanId == loan.id }
        }
        // فیکس bug.md/NB-37: رکوردهای واقعی اقساط وام — منبع تیک‌های دیالوگ ویرایش
        val existingInstallments = remember(allInstallments, loan.id) {
            allInstallments.filter { it.loanId == loan.id }
        }
        AddEditLoanDialog(
            loanToEdit = loan,
            initialShares = existingShares,
            initialInstallments = existingInstallments,
            allUsers = allUsers,
            // فیکس bug.md/NB-36: چک زندهٔ عنوان تکراری (خودِ وامِ در حال ویرایش مستثناست)
            allLoanTitles = loans.map { it.title },
            currentShamsiDate = currentShamsiDate,
            usePersianDigits = usePersianDigits,
            compactThousands = compactThousands,
            onDismiss = { loanToEdit = null },
            onAddNewUser = onAddNewUser,
            onSave = { updatedLoan, shares, paidYearMonths ->
                onSaveLoan(updatedLoan, shares, paidYearMonths)
                loanToEdit = null
            }
        )
    }

    // فیکس bug.md/NB-22: دیالوگ تأیید حذف وام — حذف دائمی است و سابقهٔ پرداخت‌ها،
    // اقساط و آیکون اختصاصی وام هم پاک می‌شود؛ فقط پس از تأیید صریح انجام می‌شود.
    loanToDelete?.let { loan ->
        AlertDialog(
            onDismissRequest = { loanToDelete = null },
            title = { Text("حذف وام") },
            text = { Text("وام «${loan.title}» برای همیشه حذف شود؟ سابقهٔ پرداخت‌ها، اقساط و آیکون اختصاصی این وام هم پاک می‌شود و این عمل قابل بازگشت نیست (فقط با فایل پشتیبان قابل بازیابی است).") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLoan(loan.id)
                        loanToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("بله، حذف شود")
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // فیکس bug.md/F7 (تأیید کاربر — بازبینی ۴.۵): دیالوگ تفکیک ماه‌های پیش‌بینی نقدینگی —
    // سهم خودِ کاربر در هر ماه (ماه‌های پرداخت‌شده ۰ هستند)، مجموع ۳ و ۶ ماهه + مقایسه
    // با میانگین پرداخت‌های واقعی خودِ او. بدون فیلد درآمد.
    if (showLiquidityDialog && liquidityForecast != null) {
        AlertDialog(
            onDismissRequest = { showLiquidityDialog = false },
            title = { Text("پیش‌بینی نقدینگی ۶ ماه آینده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "سهم ماهانهٔ خود شما در اقساطِ پیشِ رو (از ماه جاری؛ قسطِ پرداخت‌شده حساب نمی‌شود):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    liquidityForecast.months.forEachIndexed { index, row ->
                        val monthName = ShamsiDate(row.year, row.month, 1).monthName
                        val yearStr = NumberFormatter.formatNumber(row.year, usePersianDigits)
                        val amountStr = NumberFormatter.formatPrice(row.amount, usePersian = usePersianDigits, compactThousands = compactThousands)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (row.loansCount > 0) {
                                    "$monthName $yearStr (${NumberFormatter.formatNumber(row.loansCount, usePersianDigits)} وام)"
                                } else {
                                    "$monthName $yearStr"
                                },
                                fontSize = 13.sp
                            )
                            Text(
                                text = amountStr,
                                fontSize = 13.sp,
                                fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        "مجموع ۳ ماه آینده (شامل این ماه): ${NumberFormatter.formatPrice(liquidityForecast.next3MonthsTotal, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "مجموع ۶ ماه آینده (شامل این ماه): ${NumberFormatter.formatPrice(liquidityForecast.next6MonthsTotal, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                        fontWeight = FontWeight.Bold
                    )
                    val avg = liquidityForecast.historyMonthlyAverage
                    if (avg != null && liquidityForecast.historyMonthsCount > 0) {
                        Text(
                            "میانگین پرداخت ماهانهٔ شما از ${NumberFormatter.formatNumber(liquidityForecast.historyMonthsCount, usePersianDigits)} ماه تاریخچهٔ واقعی: ${NumberFormatter.formatPrice(avg, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "هنوز تاریخچهٔ پرداختی واقعی ثبت نشده است تا میانگین با آن مقایسه شود.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLiquidityDialog = false }) {
                    Text("بستن")
                }
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

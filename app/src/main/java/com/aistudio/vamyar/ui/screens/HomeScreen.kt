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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
    // فیکس bug.md/NB-47: toggle ماهِ واقعیِ (سال، ماه) کلیک‌شده روی نود اقساط
    onToggleInstallmentMonth: (Loan, year: Int, month: Int) -> Unit,
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
                        currentShamsiDate = currentShamsiDate,
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
                        onToggleInstallmentMonth = { year, month ->
                            onToggleInstallmentMonth(loan, year, month)
                        },
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
                        currentShamsiDate = currentShamsiDate,
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

                // Modern Gamified Zero-State matching Stitch Prototype
                if (loans.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Month Snapshot Preview Card
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "وضعیت ماهانه",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xFFDCFCE7),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC).copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(Color(0xFF10B981))
                                                )
                                                Text(
                                                    text = "آماده برنامه‌ریزی",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "مجموع اقساط سررسید شده",
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.Bottom,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = NumberFormatter.formatNumber(0, usePersianDigits),
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = "تومان",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B),
                                                    modifier = Modifier.padding(bottom = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Hero Motivational Empty State Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDE9FE))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // 3D/Illustrated Icon Graphic with Micro Badges
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(26.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF6D28D9), Color(0xFF4F46E5), Color(0xFF818CF8))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(38.dp)
                                            )
                                        }

                                        // Badge: 0% penalty
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xFFFBBF24),
                                            shadowElevation = 2.dp,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 10.dp, y = (-6).dp)
                                        ) {
                                            Text(
                                                text = "٪۰ جریمه دیرکرد",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF0F172A),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Badge: Smart Reminder
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xFF10B981),
                                            shadowElevation = 2.dp,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .offset(x = (-10).dp, y = 6.dp)
                                        ) {
                                            Text(
                                                text = "یادآور هوشمند ✓",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Persuasive Headlines
                                    Text(
                                        text = "شروع مدیریت بی‌دغدغه اقساط",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF0F172A),
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = "با ثبت اولین وام، دیگر سررسیدها را فراموش نمی‌کنید؛ سهم شرکا خودکار محاسبه شده و از استرس دیرکرد خلاص می‌شوید.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )

                                    // Primary High-Conversion Action Button
                                    Surface(
                                        onClick = { showAddDialog = true },
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color.Transparent,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF6D28D9), Color(0xFF4F46E5))
                                                    )
                                                )
                                                .padding(vertical = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "ثبت اولین وام در ۱ دقیقه",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    // Micro Feature Benefit Tags
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⚡ سریع و کاربردی",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(text = "•", color = Color(0xFFCBD5E1))
                                        Text(
                                            text = "🔔 افزودن هشدار",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(text = "•", color = Color(0xFFCBD5E1))
                                        Text(
                                            text = "👥 تفکیک سهم شرکا",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            // 3. Starter Templates Section
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "یا با یک لمس انتخاب کنید: الگوهای آماده",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Template 1: Bank Loan
                                    Surface(
                                        onClick = { showAddDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFEDE9FE)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AccountBalance,
                                                    contentDescription = null,
                                                    tint = Color(0xFF6D28D9),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = "بانکی و قرض‌الحسنه",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "مهر، رسالت، مسکن",
                                                fontSize = 9.sp,
                                                color = Color(0xFF94A3B8),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    // Template 2: BNPL Shopping
                                    Surface(
                                        onClick = { showAddDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFE0E7FF)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ShoppingBag,
                                                    contentDescription = null,
                                                    tint = Color(0xFF4338CA),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = "خرید اقساطی",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "اسنپ‌پی، دیجی‌پی",
                                                fontSize = 9.sp,
                                                color = Color(0xFF94A3B8),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }

                                    // Template 3: Family/Circle
                                    Surface(
                                        onClick = { showAddDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFFCE7F3)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.People,
                                                    contentDescription = null,
                                                    tint = Color(0xFFBE185D),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = "خانگی و دوره‌ای",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = "صندوق‌های فامیلی",
                                                fontSize = 9.sp,
                                                color = Color(0xFF94A3B8),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
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
                            onToggleInstallmentMonth = { year, month ->
                                onToggleInstallmentMonth(loan, year, month)
                            },
                            onEdit = { loanToEdit = loan },
                            onDelete = { loanToDelete = loan }
                        )
                    }
                }
            }
        }

        // Floating Action Button with Brand Gradient Squircle
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Surface(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("add_loan_fab")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "افزودن وام",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
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

    // Modern Loan Deletion Dialog matching Stitch Prototype
    loanToDelete?.let { loan ->
        AlertDialog(
            onDismissRequest = { loanToDelete = null },
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Modern Danger Icon Badge
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFFFFF1F2))
                            .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFE11D48),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Modal Title
                    Text(
                        text = "حذف وام",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )

                    // Loan Snapshot Summary Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = loan.title.take(2),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                    Text(
                                        text = loan.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (loan.isCompleted) Color(0xFFEDE9FE) else Color(0xFFDCFCE7),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (loan.isCompleted) Color(0xFFDDD6FE) else Color(0xFF86EFAC).copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = if (loan.isCompleted) "تسویه شده" else "فعال",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (loan.isCompleted) Color(0xFF6D28D9) else Color(0xFF15803D),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0).copy(alpha = 0.6f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text("مبلغ کل وام:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            text = NumberFormatter.formatPrice(loan.totalLoanAmount, usePersianDigits),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text("اقساط مانده:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                        val remaining = loan.remainingMonths
                                        Text(
                                            text = "${NumberFormatter.formatNumber(remaining, usePersianDigits)} از ${NumberFormatter.formatNumber(loan.totalMonths, usePersianDigits)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // High contrast safety text
                    Text(
                        text = "این وام و همه داده‌های مرتبط برای همیشه حذف می‌شوند.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Surface(
                    onClick = {
                        onDeleteLoan(loan.id)
                        loanToDelete = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE11D48),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "حذف",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            },
            dismissButton = {
                Surface(
                    onClick = { loanToDelete = null },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "بازگشت",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        )
    }

    // فیکس bug.md/F7 + SB-09 (= NB-39): جدول سادهٔ پیش‌بینی نقدینگی — سهم خودِ
    // کاربر در هر ماه + مجموع ۳ و ۶ ماهه. گزارش کاربر: «الکی پیچیده است» →
    // متن کوتاه یک‌خطی + ردیف‌های ساده (بدون پرانتز تعداد وام، بدون پاراگراف
    // مقایسهٔ تاریخچه). ماه‌های پرداخت‌شده ۰ هستند. بدون فیلد درآمد.
    if (showLiquidityDialog && liquidityForecast != null) {
        AlertDialog(
            onDismissRequest = { showLiquidityDialog = false },
            title = { Text("پیش‌بینی نقدینگی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "سهم شما در اقساط ماه‌های پیشِ رو:",
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
                                text = "$monthName $yearStr",
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
                        "مجموع ۳ ماه آینده: ${NumberFormatter.formatPrice(liquidityForecast.next3MonthsTotal, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "مجموع ۶ ماه آینده: ${NumberFormatter.formatPrice(liquidityForecast.next6MonthsTotal, usePersian = usePersianDigits, compactThousands = compactThousands)}",
                        fontWeight = FontWeight.Bold
                    )
                    val avg = liquidityForecast.historyMonthlyAverage
                    if (avg != null && liquidityForecast.historyMonthsCount > 0) {
                        Text(
                            "میانگین پرداخت ماهانهٔ شما: ${NumberFormatter.formatPrice(avg, usePersian = usePersianDigits, compactThousands = compactThousands)}",
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "فهرست اقساط",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Stitch-style segmented pill container
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewModePillButton(
                    selected = currentMode == LoanViewMode.RECTANGLE,
                    icon = Icons.Default.ViewStream,
                    label = "فهرست",
                    onClick = { onModeChange(LoanViewMode.RECTANGLE) }
                )
                ViewModePillButton(
                    selected = currentMode == LoanViewMode.SQUARE,
                    icon = Icons.Default.GridView,
                    label = "مربعی",
                    onClick = { onModeChange(LoanViewMode.SQUARE) }
                )
                ViewModePillButton(
                    selected = currentMode == LoanViewMode.CHART,
                    icon = Icons.Default.PieChart,
                    label = "نموداری",
                    onClick = { onModeChange(LoanViewMode.CHART) }
                )
            }
        }
    }
}

@Composable
private fun ViewModePillButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            if (selected) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

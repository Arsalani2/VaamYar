package com.aistudio.vamyar.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.vamyar.BuildConfig
import com.aistudio.vamyar.VamyarApp
import com.aistudio.vamyar.data.CurrentMonthPayOutcome
import com.aistudio.vamyar.data.UserDeleteCheckResult
import com.aistudio.vamyar.data.matchExistingLoan
import com.aistudio.vamyar.data.matchExistingShare
import com.aistudio.vamyar.data.matchExistingUser
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
import com.aistudio.vamyar.model.LoanShareCalculator
import com.aistudio.vamyar.model.PaymentRecord
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.notification.InstallmentAlarmReceiver
import com.aistudio.vamyar.ui.components.LoanViewMode
import com.aistudio.vamyar.ui.theme.AppThemeMode
import com.aistudio.vamyar.ui.theme.ThemePreset
import com.aistudio.vamyar.util.AppPreferences
import com.aistudio.vamyar.util.LiquidityForecast
import com.aistudio.vamyar.util.LiquidityLoanInput
import com.aistudio.vamyar.util.LiquidityPaidRecord
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.PersianNameNormalizer
import com.aistudio.vamyar.util.ShamsiDate
import com.aistudio.vamyar.util.ShamsiDateHelper
import com.aistudio.vamyar.util.computeLiquidityForecast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FinancialOverview(
    val activeUserName: String = "کاربر من",
    val totalMonthlyDue: Long = 0L,
    val arsalanMonthlyShare: Long = 0L,
    val arsalanPaidThisMonth: Long = 0L,
    val arsalanUnpaidRemainingThisMonth: Long = 0L, // مانده پرداخت نشده این ماه کاربر مبنا
    val othersMonthlyReceivables: Long = 0L,
    val othersReceivedThisMonth: Long = 0L,
    val othersRemainingUnpaid: Long = 0L, // مانده طلب وصول نشده از شرکا
    val totalDebtRemaining: Long = 0L,
    val arsalanDebtRemaining: Long = 0L,
    val totalPaidSoFar: Long = 0L,
    val overallProgress: Float = 0f,
    val activeLoansCount: Int = 0,
    val completedLoansCount: Int = 0,
    val totalLoansCount: Int = 0
)

data class PersonReceivableSummary(
    val personName: String,
    val totalMonthlyShare: Long,
    val paidThisMonth: Long,
    val remainingUnpaidThisMonth: Long,
    val loansCount: Int,
    val isAllPaidCurrentMonth: Boolean,
    val shares: List<DebtorShare>
)

data class SmartFinancialTip(
    val title: String,
    val description: String,
    val type: TipType,
    val actionText: String? = null
)

enum class TipType {
    URGENT_DUE,
    COLLECTION_REMINDER,
    SNOWBALL_PAYOFF,
    LIQUIDITY_PLAN,
    // فیکس bug.md/F7: نکتهٔ پیش‌بینی نقدینگی ۳/۶ ماه آینده
    LIQUIDITY_FORECAST,
    BUDGET_ADVICE
}

/**
 * فیکس bug.md/F1+NB-31: دادهٔ دیالوگ «فقط این ماه / همهٔ عقب‌مانده‌ها» — وقتی کاربر
 * «پرداخت این ماه» را می‌زند و وام ماه‌های سررسیدگذشتهٔ پرداخت‌نشده دارد.
 */
data class ArrearsPrompt(
    val loan: Loan,
    val arrearsMonths: List<com.aistudio.vamyar.data.ArrearsMonth>
)

/**
 * فیکس bug.md/NB-34: حالت اجرای ایمپورت — «جایگزینی کامل» (رفتار قبلی) یا «ادغام»
 * با دو استراتژی برای موارد تکراری (به‌روزرسانی یا رد).
 */
enum class ImportMode(val titleFa: String) {
    REPLACE_ALL("جایگزینی کامل"),
    MERGE_UPDATE_DUPLICATES("ادغام — به‌روزرسانی موارد تکراری"),
    MERGE_SKIP_DUPLICATES("ادغام — رد کردن موارد تکراری")
}

/**
 * فیکس bug.md/NB-34+NB-29: پیش‌نمایش ایمپورت — قبل از هر تغییری به کاربر نشان داده
 * می‌شود: شمارش‌های بکاپ + فهرست موارد جدید در برابر موجود (با همان کلید تطبیق merge).
 */
data class ImportPreview(
    val userCount: Int,
    val loanCount: Int,
    val shareCount: Int,
    val recordCount: Int,
    val installmentCount: Int,
    val iconCount: Int,
    val newUsers: List<String>,
    val existingUsers: List<String>,
    val newLoans: List<String>,
    val existingLoans: List<String>,
    val newShareCount: Int,
    val exportedDate: String,
    val legacyMatch: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as VamyarApp).repository
    // فیکس bug.md/NB-18: برای نوشتن فایل آیکون‌های بازسازی‌شده از بکاپ لازم است
    private val app = application as VamyarApp
    private val appPreferences = AppPreferences(application)

    val allLoans: StateFlow<List<Loan>> = repository.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allShares: StateFlow<List<DebtorShare>> = repository.allShares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecords: StateFlow<List<PaymentRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط — منبع رنگ‌آمیزی نودها و تشخیص عقب‌ماندگی
    val allInstallments: StateFlow<List<LoanInstallment>> = repository.allInstallments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserProfile>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val primaryUser: StateFlow<UserProfile?> = repository.primaryUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // فیکس bug.md/P3-9: فلگ مردهٔ showOnboarding (همیشه false، بدون هیچ فراخوانی)
    // حذف شد؛ راهنمای کاربر جدید همان AppGuideDialog و دادهٔ راهنمای seed است.

    // Persistent View Mode
    private val _loanViewMode = MutableStateFlow(appPreferences.loanViewMode)
    val loanViewMode: StateFlow<LoanViewMode> = _loanViewMode.asStateFlow()

    // First Launch Unobtrusive Guide Tour
    private val _hasSeenAppGuide = MutableStateFlow(appPreferences.hasSeenAppGuide)
    val hasSeenAppGuide: StateFlow<Boolean> = _hasSeenAppGuide.asStateFlow()

    // Current Shamsi Date (System date or custom override)
    private val _currentShamsiDate = MutableStateFlow(
        if (appPreferences.isCustomDateEnabled) {
            ShamsiDate(appPreferences.customYear, appPreferences.customMonth, appPreferences.customDay)
        } else {
            ShamsiDateHelper.getSystemShamsiDate()
        }
    )
    val currentShamsiDate: StateFlow<ShamsiDate> = _currentShamsiDate.asStateFlow()

    // فیکس bug.md/F1+NB-31: دیالوگ انتخاب «فقط این ماه / همهٔ عقب‌مانده‌ها»
    private val _arrearsPrompt = MutableStateFlow<ArrearsPrompt?>(null)
    val arrearsPrompt: StateFlow<ArrearsPrompt?> = _arrearsPrompt.asStateFlow()

    // فیکس bug.md/P2-12: این فلو قبلاً یک val فریز‌شده در لحظهٔ ساختِ ViewModel بود؛
    // چون exportBackupJson از آن می‌خواند، خروجیِ بکاپ در همان نشستی که کاربر تاریخ
    // سفارشی را تغییر می‌داد، پرچم کهنه می‌نوشت. حالا در syncDateWithSystem و
    // setCustomShamsiDate به‌روز می‌شود و همیشه با AppPreferences هم‌گام است.
    private val _isCustomDateActive = MutableStateFlow(appPreferences.isCustomDateEnabled)
    val isCustomDateActive: StateFlow<Boolean> = _isCustomDateActive.asStateFlow()

    init {
        // فیکس bug.md/P2-8: تاریخ جاری قبلاً فقط یک‌بار در ساختِ ViewModel مقدار می‌گرفت؛ با
        // باز ماندن اپ، عبور از نیمه‌شب/مرز ماه شمسی در محاسبات منعکس نمی‌شد و «امروز»
        // همان دیروز می‌ماند. این چک دوره‌ای «تاریخ مؤثر» را دوباره می‌خواند و در صورت تغییر،
        // StateFlow را تازه می‌کند تا SmartTips و همه‌ی مصرف‌کنندگان تاریخ دوباره محاسبه شوند.
        // (تاریخ سفارشی ثابت است و تغییری نمی‌کند؛ تغییر دستی ساعت سیستم هم پوشش داده می‌شود.)
        viewModelScope.launch {
            while (isActive) {
                delay(60_000) // چک سبکِ هر دقیقه — فقط خواندن SharedPreferences از حافظه
                refreshCurrentShamsiDate()
            }
        }
    }

    // Persistent Theme Mode (default SYSTEM)
    private val _themeMode = MutableStateFlow(appPreferences.themeMode)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // Persistent Theme Preset
    private val _themePreset = MutableStateFlow(appPreferences.themePreset)
    val themePreset: StateFlow<ThemePreset> = _themePreset.asStateFlow()

    // Persistent Digit Display Setting (Persian vs English)
    private val _usePersianDigits = MutableStateFlow(appPreferences.usePersianDigits)
    val usePersianDigits: StateFlow<Boolean> = _usePersianDigits.asStateFlow()

    private val _compactThousands = MutableStateFlow(appPreferences.compactThousands)
    val compactThousands: StateFlow<Boolean> = _compactThousands.asStateFlow()

    private fun getUserShareForLoan(loan: Loan, shares: List<DebtorShare>, activeUser: String, primaryUserName: String = activeUser): Long {
        val loanShares = shares.filter { it.loanId == loan.id }
        // فیکس bug.md/P3-11: حذف شرط‌های خاص «من» و «شما»؛ مبنا فقط تطبیق استاندارد نام است
        val isPrimary = PersianNameNormalizer.namesMatch(activeUser, primaryUserName)

        return if (isPrimary) {
            // فیکس bug.md/P3-2: منبع واحد محاسبهٔ سهم من
            LoanShareCalculator.computeMyShare(loan, loanShares)
        } else {
            val userDebtorShare = loanShares.find { PersianNameNormalizer.namesMatch(it.personName, activeUser) }
            userDebtorShare?.shareAmount ?: 0L
        }
    }

    val financialOverview: StateFlow<FinancialOverview> = combine(
        allLoans,
        allShares,
        primaryUser
    ) { loans, shares, primary ->
        val activeUserName = primary?.name ?: "کاربر من"
        val activeLoans = loans.filter { !it.isCompleted }
        val activeLoanIds = activeLoans.map { it.id }.toSet()
        val totalMonthlyDue = activeLoans.sumOf { it.monthlyAmount }

        val userMonthlyShare = activeLoans.sumOf { getUserShareForLoan(it, shares, activeUserName, activeUserName) }
        val userPaidThisMonth = activeLoans.filter { it.isCurrentMonthPaid }.sumOf { getUserShareForLoan(it, shares, activeUserName, activeUserName) }
        val userUnpaidRemainingThisMonth = (userMonthlyShare - userPaidThisMonth).coerceAtLeast(0L)

        // فیکس bug.md/P3-3 و P3-1: مطالبات فقط از وام‌های تسویه‌نشده و با کلید استاندارد نام محاسبه می‌شوند
        val relevantShares = shares.filter { it.loanId in activeLoanIds && !PersianNameNormalizer.namesMatch(it.personName, activeUserName) }

        val othersMonthlyReceivables = relevantShares.sumOf { it.shareAmount }
        val othersReceivedThisMonth = relevantShares.filter { it.isPaidForCurrentMonth }.sumOf { it.shareAmount }
        val othersRemainingUnpaid = (othersMonthlyReceivables - othersReceivedThisMonth).coerceAtLeast(0L)

        val totalDebtRemaining = activeLoans.sumOf { it.remainingTotalDebt }
        val userDebtRemaining = activeLoans.sumOf { loan ->
            loan.remainingMonths.toLong() * getUserShareForLoan(loan, shares, activeUserName, activeUserName)
        }
        val totalPaidSoFar = loans.sumOf { it.totalPaidAmount }
        val totalAmountAll = loans.sumOf { it.totalLoanAmount }

        val overallProgress = if (totalAmountAll > 0) {
            (totalPaidSoFar.toFloat() / totalAmountAll).coerceIn(0f, 1f)
        } else 0f

        FinancialOverview(
            activeUserName = activeUserName,
            totalMonthlyDue = totalMonthlyDue,
            arsalanMonthlyShare = userMonthlyShare,
            arsalanPaidThisMonth = userPaidThisMonth,
            arsalanUnpaidRemainingThisMonth = userUnpaidRemainingThisMonth,
            othersMonthlyReceivables = othersMonthlyReceivables,
            othersReceivedThisMonth = othersReceivedThisMonth,
            othersRemainingUnpaid = othersRemainingUnpaid,
            totalDebtRemaining = totalDebtRemaining,
            arsalanDebtRemaining = userDebtRemaining,
            totalPaidSoFar = totalPaidSoFar,
            overallProgress = overallProgress,
            activeLoansCount = activeLoans.size,
            completedLoansCount = loans.count { it.isCompleted },
            totalLoansCount = loans.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialOverview())

    val personSummaries: StateFlow<List<PersonReceivableSummary>> = combine(
        allShares,
        allLoans,
        primaryUser
    ) { shares, loans, primary ->
        val activeUserName = primary?.name ?: "کاربر من"
        val activeLoanIds = loans.filter { !it.isCompleted }.map { it.id }.toSet()
        // فیکس bug.md/P3-3 و P3-1: سهم‌های مربوط به وام‌های تسویه‌شده از مطالبات حذف می‌شوند
        val otherShares = shares.filter { it.loanId in activeLoanIds && !PersianNameNormalizer.namesMatch(it.personName, activeUserName) }
        otherShares.groupBy { PersianNameNormalizer.canonicalName(it.personName) }.map { (person, personShares) ->
            val totalShare = personShares.sumOf { it.shareAmount }
            val paidThisMonth = personShares.filter { it.isPaidForCurrentMonth }.sumOf { it.shareAmount }
            val remainingUnpaid = (totalShare - paidThisMonth).coerceAtLeast(0L)
            PersonReceivableSummary(
                personName = person,
                totalMonthlyShare = totalShare,
                paidThisMonth = paidThisMonth,
                remainingUnpaidThisMonth = remainingUnpaid,
                loansCount = personShares.size,
                isAllPaidCurrentMonth = personShares.all { it.isPaidForCurrentMonth },
                shares = personShares
            )
        }.sortedByDescending { it.remainingUnpaidThisMonth }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Advanced, professional smart financial suggestions with proper formatted numbers
    // فیکس bug.md/NB-8: استفاده از فلوهای usePersianDigits و compactThousands به جای .value
    /**
     * فیکس bug.md/F7 (تأیید کاربر — بازبینی ۴.۵): پیش‌بینی نقدینگی ۳ و ۶ ماه آینده —
     * مجموع اقساطِ سهم خودِ کاربر در ماه‌های پیشِ رو (به تفکیک ماه + مجموع کل ۳/۶
     * ماهه، از ماه جاری؛ قسطِ پرداخت‌شدهٔ ماه جاری حساب نمی‌شود) + میانگین پرداخت‌های
     * خودِ او از تاریخچهٔ واقعی (رکوردهای اقساط) — بدون فیلد درآمد (تصمیم کاربر: فعلاً نه).
     * محاسبهٔ خالص در util/LiquidityForecast.kt است؛ اینجا فقط داده وصل می‌شود.
     */
    val liquidityForecast: StateFlow<LiquidityForecast?> = combine(
        allLoans,
        allShares,
        allInstallments,
        primaryUser,
        currentShamsiDate
    ) { loans, shares, installments, primary, curDate ->
        val userName = primary?.name ?: "کاربر من"
        // سهم ماهانهٔ فعلیِ خود کاربر برای همهٔ وام‌ها — هم برای پنجرهٔ آینده، هم برای
        // میانگین تاریخچه (سهمِ تاریخی شرکا ثبت نمی‌شود؛ تقریبِ استاندارد = سهم فعلی)
        val myShareByLoan = loans.associate { loan ->
            loan.id to getUserShareForLoan(loan, shares, userName, userName)
        }
        val activeInputs = loans
            .filter { !it.isCompleted && !it.isArchived }
            .map { loan ->
                LiquidityLoanInput(
                    loanId = loan.id,
                    startYear = loan.startYear,
                    startMonth = loan.startMonth,
                    totalMonths = loan.totalMonths,
                    myMonthlyShare = myShareByLoan[loan.id] ?: 0L
                )
            }
        val paidMonthsByLoan = installments
            .groupBy({ it.loanId }, { it.year to it.month })
            .mapValues { entry -> entry.value.toSet() }
        computeLiquidityForecast(
            currentYear = curDate.year,
            currentMonth = curDate.month,
            loans = activeInputs,
            paidMonthsByLoan = paidMonthsByLoan,
            myShareByLoan = myShareByLoan,
            paidRecords = installments.map {
                LiquidityPaidRecord(loanId = it.loanId, year = it.year, month = it.month)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val smartFinancialTips: StateFlow<List<SmartFinancialTip>> = combine(
        combine(allLoans, allShares, ::Pair),
        combine(primaryUser, currentShamsiDate, ::Pair),
        combine(usePersianDigits, compactThousands, ::Pair),
        liquidityForecast
    ) { (loans, shares), (primary, curDate), (usePersian, compact), forecast ->
        val tips = mutableListOf<SmartFinancialTip>()
        val activeUserName = primary?.name ?: "کاربر من"
        val activeLoans = loans.filter { !it.isCompleted }

        // 1. Next upcoming unpaid loan
        val unpaidLoans = activeLoans.filter { !it.isCurrentMonthPaid }.sortedBy { it.dueDay }
        if (unpaidLoans.isNotEmpty()) {
            val nextLoan = unpaidLoans.first()
            val shareAmount = getUserShareForLoan(nextLoan, shares, activeUserName)
            val formattedShare = NumberFormatter.formatPrice(shareAmount, usePersian = usePersian, compactThousands = compact)
            val dueDayFormatted = NumberFormatter.formatNumber(nextLoan.dueDay, usePersian)
            // فیکس bug.md/P2-6: فاصله تا سررسید با تقویم شمسیِ واقعی محاسبه می‌شود
            // (ماه‌های ۳۱روزه، اسفند ۲۹/۳۰روزه و سررسیدِ افتاده در ماه بعد) — قبلاً فقط
            // dueDay - day بود و روزهای منفی یا شمارشِ اشتباه می‌داد.
            // فیکس bug.md/NB-16: عقب‌افتادگیِ سررسیدِ همین ماه هم دیده می‌شود؛
            // daysUntilDue برای سررسیدِ سپری‌شده به ماهِ بعد می‌پرد و وضعیت عقب‌ماندن
            // را از UI پنهان می‌کرد؛ حالا «X روز سپری شده» صریح اعلام می‌شود.
            val daysThisMonth = ShamsiDateHelper.daysUntilDueThisMonth(nextLoan.dueDay, curDate)

            val timingText = when {
                daysThisMonth == 0 -> "امروز موعد پرداخت است!"
                daysThisMonth > 0 -> "${NumberFormatter.formatNumber(daysThisMonth, usePersian)} روز دیگر"
                else -> "موعد این ماه ${NumberFormatter.formatNumber(-daysThisMonth, usePersian)} روز سپری شده است"
            }

            tips.add(
                SmartFinancialTip(
                    title = "سررسید پیش‌رو: ${nextLoan.title}",
                    description = "روز ${dueDayFormatted}ام هر ماه ($timingText) • سهم ${activeUserName}: ${formattedShare}.",
                    type = TipType.URGENT_DUE
                )
            )
        }

        // 2. Collection Reminder from debtors with clean formatted numbers
        // فیکس bug.md/P3-1: تشخیص «سهمِ خودم یا دیگران» با کلید استاندارد نام
        val unpaidShares = shares.filter { !PersianNameNormalizer.namesMatch(it.personName, activeUserName) && !it.isPaidForCurrentMonth }
        if (unpaidShares.isNotEmpty()) {
            val totalPending = unpaidShares.sumOf { it.shareAmount }
            val formattedPending = NumberFormatter.formatPrice(totalPending, usePersian = usePersian, compactThousands = compact)
            val topDebtor = unpaidShares.groupBy { it.personName }.maxByOrNull { it.value.sumOf { s -> s.shareAmount } }
            val topDebtorName = topDebtor?.key ?: "شرکا"
            val topDebtorAmount = topDebtor?.value?.sumOf { it.shareAmount } ?: 0L
            val formattedTopAmount = NumberFormatter.formatPrice(topDebtorAmount, usePersian = usePersian, compactThousands = compact)

            tips.add(
                SmartFinancialTip(
                    title = "پیگیری مطالبات معوق این ماه",
                    description = "مجموعاً ${formattedPending} از شرکا دریافت نشده است • بیشترین سهم مربوط به ${topDebtorName} با مبلغ ${formattedTopAmount} است.",
                    type = TipType.COLLECTION_REMINDER,
                    actionText = "ارسال یادآوری"
                )
            )
        }

        // 3. Snowball early payoff strategy
        val quickestPayoffLoan = activeLoans.minByOrNull { it.remainingMonths }
        if (quickestPayoffLoan != null && quickestPayoffLoan.remainingMonths <= 6) {
            val shareAmount = getUserShareForLoan(quickestPayoffLoan, shares, activeUserName)
            val formattedShare = NumberFormatter.formatPrice(shareAmount, usePersian = usePersian, compactThousands = compact)
            val monthsFormatted = NumberFormatter.formatNumber(quickestPayoffLoan.remainingMonths, usePersian)

            tips.add(
                SmartFinancialTip(
                    title = "استراتژی تسویه زودهنگام: ${quickestPayoffLoan.title}",
                    description = "تنها ${monthsFormatted} قسط از این وام باقی مانده است • با تسویه آن، ماهانه ${formattedShare} از تعهدات شما آزاد می‌شود.",
                    type = TipType.SNOWBALL_PAYOFF
                )
            )
        }

        // 4. Cashflow balance (first half vs second half of the month)
        if (activeLoans.isNotEmpty()) {
            val firstHalfAmount = activeLoans.filter { it.dueDay <= 15 }.sumOf { getUserShareForLoan(it, shares, activeUserName) }
            val secondHalfAmount = activeLoans.filter { it.dueDay > 15 }.sumOf { getUserShareForLoan(it, shares, activeUserName) }
            val formattedFirstHalf = NumberFormatter.formatPrice(firstHalfAmount, usePersian = usePersian, compactThousands = compact)
            val formattedSecondHalf = NumberFormatter.formatPrice(secondHalfAmount, usePersian = usePersian, compactThousands = compact)

            tips.add(
                SmartFinancialTip(
                    title = "توزیع بار مالی نقدینگی در ماه",
                    description = "نیمه اول ماه (روز ۱ تا ۱۵): ${formattedFirstHalf} • نیمه دوم ماه (روز ۱۶ تا ۳۱): ${formattedSecondHalf}.",
                    type = TipType.LIQUIDITY_PLAN
                )
            )
        }

        // فیکس bug.md/F7 (تأیید کاربر — بازبینی ۴.۵): پیش‌بینی نقدینگی ۳ و ۶ ماه آینده —
        // مجموع اقساطِ سهم کاربر (از ماه جاری؛ قسطِ پرداخت‌شده حساب نمی‌شود) + مقایسه با
        // میانگین پرداخت‌های خودِ او از تاریخچهٔ واقعی. بدون فیلد درآمد (تصمیم کاربر).
        if (forecast != null && activeLoans.isNotEmpty()) {
            val f3 = NumberFormatter.formatPrice(forecast.next3MonthsTotal, usePersian = usePersian, compactThousands = compact)
            val f6 = NumberFormatter.formatPrice(forecast.next6MonthsTotal, usePersian = usePersian, compactThousands = compact)
            val avg = forecast.historyMonthlyAverage
            val historyText = if (avg != null && forecast.historyMonthsCount > 0) {
                val avgFormatted = NumberFormatter.formatPrice(avg, usePersian = usePersian, compactThousands = compact)
                val monthsCountFormatted = NumberFormatter.formatNumber(forecast.historyMonthsCount, usePersian)
                val diff3 = forecast.next3MonthsTotal - avg * 3
                val comparison = when {
                    diff3 > 0 -> "بار ۳ماههٔ آینده ${NumberFormatter.formatPrice(diff3, usePersian = usePersian, compactThousands = compact)} بیشتر از میانگین پرداخت‌های شماست."
                    diff3 < 0 -> "بار ۳ماههٔ آینده ${NumberFormatter.formatPrice(-diff3, usePersian = usePersian, compactThousands = compact)} کمتر از میانگین پرداخت‌های شماست."
                    else -> "بار ۳ماههٔ آینده هم‌تراز میانگین پرداخت‌های شماست."
                }
                "میانگین پرداخت ماهانهٔ شما (از $monthsCountFormatted ماه تاریخچهٔ واقعی): $avgFormatted • $comparison"
            } else {
                "هنوز تاریخچهٔ پرداختی واقعی ثبت نشده است تا میانگین با آن مقایسه شود."
            }

            tips.add(
                SmartFinancialTip(
                    title = "پیش‌بینی نقدینگی ۳ و ۶ ماه آینده",
                    description = "مجموع اقساط سهم شما در ۳ ماه آینده (شامل این ماه): $f3 • در ۶ ماه آینده: $f6 • $historyText",
                    type = TipType.LIQUIDITY_FORECAST,
                    actionText = "جزئیات ماه‌ها"
                )
            )
        }

        tips
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * فیکس bug.md/P2-8: «تاریخ مؤثر» اپ (تاریخ سفارشی تنظیمات اگر فعال باشد، وگرنده
     * ساعت سیستم) را دوباره می‌خواند و در صورت تغییر، [currentShamsiDate] را تازه می‌کند.
     * توسط چک دوره‌ای init بالا فراخوانی می‌شود تا عبور از نیمه‌شب/مرز ماه، وقتی اپ باز
     * است، در همه‌ی محاسبات (SmartTips، قسط رسیده، روزهای مانده و …) منعکس شود.
     */
    fun refreshCurrentShamsiDate() {
        val effective = ShamsiDateHelper.getEffectiveShamsiDate(appPreferences)
        if (effective != _currentShamsiDate.value) {
            _currentShamsiDate.value = effective
        }
    }

    // Date Sync & Custom Date Override Methods
    fun syncDateWithSystem() {
        appPreferences.clearCustomDate()
        _isCustomDateActive.value = false
        _currentShamsiDate.value = ShamsiDateHelper.getSystemShamsiDate()
    }

    fun setCustomShamsiDate(date: ShamsiDate) {
        appPreferences.setCustomDate(date.year, date.month, date.day)
        _isCustomDateActive.value = true
        _currentShamsiDate.value = date
    }

    // Persistent View Mode
    fun setLoanViewMode(mode: LoanViewMode) {
        _loanViewMode.value = mode
        appPreferences.loanViewMode = mode
    }

    // User Management Functions
    fun setPrimaryUser(userId: Long) {
        viewModelScope.launch {
            repository.setPrimaryUser(userId)
        }
    }

    fun saveUser(user: UserProfile) {
        viewModelScope.launch {
            repository.saveUser(user)
        }
    }

    suspend fun checkCanDeleteUser(userId: Long): UserDeleteCheckResult {
        return repository.checkCanDeleteUser(userId)
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            repository.deleteUser(userId)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        appPreferences.themeMode = mode
    }

    fun setThemePreset(preset: ThemePreset) {
        _themePreset.value = preset
        appPreferences.themePreset = preset
    }

    fun setUsePersianDigits(usePersian: Boolean) {
        _usePersianDigits.value = usePersian
        appPreferences.usePersianDigits = usePersian
    }

    fun setCompactThousands(compact: Boolean) {
        _compactThousands.value = compact
        appPreferences.compactThousands = compact
    }

    fun dismissAppGuide() {
        _hasSeenAppGuide.value = true
        appPreferences.hasSeenAppGuide = true
    }

    fun toggleLoanCurrentMonthPaid(loan: Loan) {
        viewModelScope.launch {
            val cur = _currentShamsiDate.value
            // فیکس bug.md/F1+NB-31: «پرداخت این ماه» به ماه جاریِ واقعیِ تقویم وصل است؛
            // اگر عقب‌ماندگی باشد، طبق تصمیم کاربر (بند ۵ NB-31) دیالوگ انتخاب نشان داده می‌شود.
            when (val outcome = repository.payCurrentMonthSmart(loan, cur)) {
                is CurrentMonthPayOutcome.NeedsArrearsChoice -> {
                    _arrearsPrompt.value = ArrearsPrompt(loan, outcome.arrears)
                }
                else -> {}
            }
        }
    }

    /** گزینهٔ «فقط این ماه» از دیالوگ عقب‌ماندگی */
    fun payOnlyCurrentMonth(loan: Loan) {
        viewModelScope.launch {
            val cur = _currentShamsiDate.value
            repository.payInstallmentMonth(loan, cur.year, cur.month, cur)
            _arrearsPrompt.value = null
        }
    }

    /** گزینهٔ «همهٔ عقب‌مانده‌ها تا الان» از دیالوگ عقب‌ماندگی */
    fun payAllArrearsAndCurrent(loan: Loan) {
        viewModelScope.launch {
            repository.payArrearsAndCurrentMonth(loan, _currentShamsiDate.value)
            _arrearsPrompt.value = null
        }
    }

    fun dismissArrearsPrompt() {
        _arrearsPrompt.value = null
    }

    fun incrementPaidInstallment(loan: Loan) {
        viewModelScope.launch {
            repository.recordInstallmentPayment(loan, isIncrement = true, currentDate = _currentShamsiDate.value)
        }
    }

    fun decrementPaidInstallment(loan: Loan) {
        viewModelScope.launch {
            repository.recordInstallmentPayment(loan, isIncrement = false, currentDate = _currentShamsiDate.value)
        }
    }

    fun saveLoan(loan: Loan, shares: List<DebtorShare>) {
        viewModelScope.launch {
            repository.saveLoanWithShares(loan, shares)
        }
    }

    fun deleteLoan(loanId: Long) {
        viewModelScope.launch {
            repository.deleteLoan(loanId)
        }
    }

    fun toggleDebtorPaid(shareId: Long, isPaid: Boolean) {
        viewModelScope.launch {
            val monthName = if (isPaid) _currentShamsiDate.value.monthName else ""
            repository.updateDebtorPaidStatus(shareId, isPaid, monthName)
        }
    }

    fun resetMonthlyDebtorStatus() {
        viewModelScope.launch {
            repository.resetMonthlyDebtorStatus()
        }
    }

    // فیکس bug.md/NB-23: توابع مردهٔ clearAllDataAndStartFresh و resetToDefaults (بدون هیچ
    // فراخواننده‌ای در UI؛ دوباره‌کاریِ همین مسیر) حذف شدند — این تنها نقطهٔ ورود
    // «پاکسازی تمام وام‌ها» است و clearAllDataAndResetِ بدون فراخواننده هم از ریپو حذف شد.
    fun clearAllLoansAndStartFresh() {
        viewModelScope.launch {
            repository.clearAllLoansAndReset()
        }
    }

    fun exportBackupJson(): String {
        val currentLoans = allLoans.value
        val currentShares = allShares.value
        val currentRecords = allRecords.value
        val currentUsers = allUsers.value

        // فیکس bug.md/NB-18: فایل آیکون‌های سفارشی به‌صورت base64 داخل خودِ بکاپ
        // embed می‌شوند تا روی دستگاه/نصب دیگر بی‌صدا به vector برنگردند؛ اگر فایل
        // روی دیسک نباشد، همان وام بدون آیکون embed می‌شود و فیلد path هم حذف می‌شود.
        val currentIcons = currentLoans.mapNotNull { l ->
            val path = l.customIconPath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val iconFile = java.io.File(path)
            if (!iconFile.exists()) return@mapNotNull null
            com.aistudio.vamyar.util.BackupIcon(
                loanId = l.id,
                fileName = iconFile.name,
                dataBase64 = android.util.Base64.encodeToString(iconFile.readBytes(), android.util.Base64.NO_WRAP)
            )
        }

        val prefs = com.aistudio.vamyar.util.BackupPreferences(
            themeMode = _themeMode.value.name,
            themePreset = _themePreset.value.name,
            usePersianDigits = _usePersianDigits.value,
            compactThousands = _compactThousands.value,
            customYear = _currentShamsiDate.value.year,
            customMonth = _currentShamsiDate.value.month,
            customDay = _currentShamsiDate.value.day,
            isCustomDateEnabled = isCustomDateActive.value
        )

        return com.aistudio.vamyar.util.BackupHelper.createBackupJson(
            users = currentUsers,
            loans = currentLoans,
            shares = currentShares,
            records = currentRecords,
            // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط در بکاپ ذخیره می‌شوند تا
            // ماه‌های پرداخت‌شدهٔ واقعی بعد از restore عیناً برگردند
            installments = allInstallments.value,
            // فیکس bug.md/NB-18: خودِ فایل آیکون‌های سفارشی هم داخل بکاپ می‌رود
            icons = currentIcons,
            preferences = prefs,
            // فیکس bug.md/P2-13: نسخهٔ درج‌شده در فایل بکاپ دیگر هاردکد نیست؛ از
            // BuildConfig.VERSION_NAME خوانده می‌شود که در زمان بیلد از version.properties
            // (تنها منبع حقیقت نسخهٔ اپ) تزریق می‌شود.
            appVersion = BuildConfig.VERSION_NAME,
            shamsiDateFormatted = _currentShamsiDate.value.toStandardFormat(usePersianDigits = false)
        )
    }

    /**
     * فیکس bug.md/NB-34+NB-29: پیش‌نمایش ایمپورت بدون هیچ تغییری در داده — فایل فقط
     * پارس و با دادهٔ فعلی مقایسه می‌شود (همان کلید تطبیق merge: publicId با fallback
     * نام کانونی/عنوان+مبلغ+ماه شروع) تا UI بتواند «موارد جدید» و «تکراری‌ها» را قبل
     * از اجرا نشان دهد و کاربر صریحاً حالت را انتخاب و تأیید کند.
     */
    suspend fun prepareImportPreview(jsonString: String): Result<ImportPreview> {
        return try {
            val parseResult = com.aistudio.vamyar.util.BackupHelper.parseAndValidateBackup(jsonString)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("فایل پشتیبان نامعتبر است."))
            }
            val data = parseResult.getOrThrow()

            val curUsers = allUsers.value
            val curLoans = allLoans.value
            val curShares = allShares.value

            var legacyMatch = false
            val newUsers = mutableListOf<String>()
            val existingUsers = mutableListOf<String>()
            for (u in data.users) {
                val matched = matchExistingUser(u, curUsers)
                if (matched == null) {
                    newUsers.add(u.name)
                } else {
                    existingUsers.add(u.name)
                    // تطبیق با کلید fallback (نه publicId) → کاربر باید بداند
                    if (!(com.aistudio.vamyar.util.PublicId.isValidUserId(u.publicId) && matched.publicId == u.publicId)) {
                        legacyMatch = true
                    }
                }
            }
            val newLoans = mutableListOf<String>()
            val existingLoans = mutableListOf<String>()
            for (l in data.loans) {
                val matched = matchExistingLoan(l, curLoans)
                if (matched == null) {
                    newLoans.add(l.title)
                } else {
                    existingLoans.add(l.title)
                    if (!(com.aistudio.vamyar.util.PublicId.isValidLoanId(l.publicId) && matched.publicId == l.publicId)) {
                        legacyMatch = true
                    }
                }
            }
            // سهم‌ها فقط شمارش می‌شوند (فهرست‌شان در دیالوگ طولانی می‌شود)
            val newShareCount = data.shares.count { s ->
                val matchedLoan = matchExistingLoan(data.loans.firstOrNull { it.id == s.loanId } ?: return@count false, curLoans)
                val localLoanId = matchedLoan?.id ?: -1L
                matchExistingShare(s, curShares, localLoanId) == null
            }

            Result.success(
                ImportPreview(
                    userCount = data.users.size,
                    loanCount = data.loans.size,
                    shareCount = data.shares.size,
                    recordCount = data.records.size,
                    installmentCount = data.installments.size,
                    iconCount = data.icons.size,
                    newUsers = newUsers,
                    existingUsers = existingUsers,
                    newLoans = newLoans,
                    existingLoans = existingLoans,
                    newShareCount = newShareCount,
                    exportedDate = data.exportedDate,
                    legacyMatch = legacyMatch
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackupJson(jsonString: String, mode: ImportMode = ImportMode.REPLACE_ALL): Result<String> {
        return try {
            val parseResult = com.aistudio.vamyar.util.BackupHelper.parseAndValidateBackup(jsonString)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("فایل پشتیبان نامعتبر است."))
            }

            val data = parseResult.getOrThrow()

            // فیکس bug.md/NB-18: فایل آیکون‌های embedشدهٔ بکاپ اول روی دیسکِ همین
            // دستگاه نوشته می‌شوند و مسیر محلیِ تازه به restore داده می‌شود؛ اگر
            // restore با خطا rolback شود، همین فایل‌های تازه دوباره پاک می‌شوند
            // تا فایل یتیم نماند.
            val writtenIconPaths = data.icons.mapNotNull { bi ->
                val bytes = android.util.Base64.decode(bi.dataBase64, android.util.Base64.NO_WRAP)
                val savedPath = com.aistudio.vamyar.util.LoanIconHelper.saveCustomIconBytes(app, bytes)
                if (savedPath != null) bi.loanId to savedPath else null
            }.toMap()

            // Restore Database — فیکس bug.md/NB-34: دو حالت ایمپورت:
            // - REPLACE_ALL: رفتار قبلی (جایگزینی کامل + هشدار پیش‌نمایش NB-29)
            // - MERGE_*: ادغام بدون پاک‌شدن دادهٔ فعلی (فقط موارد جدید درج؛
            //   تکراری‌ها به‌روزرسانی یا رد) — idempotent برای ایمپورت دوبارِ همان فایل
            val restoreResult = runCatching {
                if (mode == ImportMode.REPLACE_ALL) {
                    repository.restoreDatabaseSnapshot(
                        users = data.users,
                        loans = data.loans,
                        shares = data.shares,
                        records = data.records,
                        // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط از بکاپ بازیابی می‌شوند
                        // (بکاپ قدیمی → خالی → بازسازی ترتیبی در خود restore)
                        installments = data.installments,
                        // فیکس bug.md/NB-18: مسیرهای محلیِ تازهٔ آیکون‌های بازنویسی‌شده
                        iconPaths = writtenIconPaths
                    )
                } else {
                    repository.mergeFromBackup(
                        users = data.users,
                        loans = data.loans,
                        shares = data.shares,
                        records = data.records,
                        installments = data.installments,
                        iconPaths = writtenIconPaths,
                        updateDuplicates = mode == ImportMode.MERGE_UPDATE_DUPLICATES
                    )
                }
            }
            if (restoreResult.isFailure) {
                writtenIconPaths.values.forEach { com.aistudio.vamyar.util.LoanIconHelper.deleteCustomIconFile(it) }
                return Result.failure(restoreResult.exceptionOrNull() ?: Exception("بازیابی ناموفق بود."))
            }

            // Restore preferences
            // فیکس bug.md/P2-12: تنظیماتِ بازیابی‌شده قبلاً فقط در StateFlowها نوشته می‌شد
            // و در AppPreferences ذخیره نمی‌شد؛ بعد از restart به حالت قبل برمی‌گشت. اکنون
            // همان setterهای دائمی (که هم StateFlow و هم AppPreferences را می‌نویسند)
            // فراخوانی می‌شوند. enumها هم دفاعی parse می‌شوند تا مقدار نامعتبر در فایل
            // بکاپ، بازیابی بقیهٔ تنظیمات را نیمه‌کاره رها نکند (قبلاً یک رشتهٔ خرابِ
            // themeMode، ارقام و فشرده‌سازی را هم از پا درمی‌آورد).
            try {
                setThemeMode(runCatching { AppThemeMode.valueOf(data.preferences.themeMode) }.getOrDefault(_themeMode.value))
                setThemePreset(runCatching { ThemePreset.valueOf(data.preferences.themePreset) }.getOrDefault(_themePreset.value))
                setUsePersianDigits(data.preferences.usePersianDigits)
                setCompactThousands(data.preferences.compactThousands)

                if (data.preferences.isCustomDateEnabled) {
                    setCustomShamsiDate(ShamsiDate(data.preferences.customYear, data.preferences.customMonth, data.preferences.customDay))
                } else {
                    syncDateWithSystem()
                }
            } catch (_: Exception) {}


            // فیکس bug.md/NB-34: پیام موفقیت بر اساس حالتِ انتخابی کاربر —
            // در ادغام، خلاصهٔ MergeSummary (جدید/به‌روزرسانی/رد) گزارش می‌شود
            val summary = if (mode == ImportMode.REPLACE_ALL) {
                "پشتیبان با موفقیت بازیابی شد: ${data.loans.size} وام، ${data.users.size} کاربر، ${data.shares.size} سهم شراکت."
            } else {
                val ms = restoreResult.getOrNull() as? com.aistudio.vamyar.data.LoanRepository.MergeSummary
                if (ms != null) {
                    "ادغام انجام شد — کاربران: ${ms.newUsers} جدید، ${ms.updatedUsers} به‌روزرسانی، ${ms.skippedUsers} رد؛ وام‌ها: ${ms.newLoans} جدید، ${ms.updatedLoans} به‌روزرسانی، ${ms.skippedLoans} رد؛ سهم‌ها: ${ms.newShares} جدید، ${ms.updatedShares} به‌روزرسانی، ${ms.skippedShares} رد."
                } else {
                    "ادغام انجام شد."
                }
            }
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendTestNotification(context: Context) {
        val intent = Intent(context, InstallmentAlarmReceiver::class.java).apply {
            action = InstallmentAlarmReceiver.ACTION_TEST_NOTIFICATION
        }
        context.sendBroadcast(intent)
    }
}

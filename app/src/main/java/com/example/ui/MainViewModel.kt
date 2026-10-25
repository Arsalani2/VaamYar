package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.VamyarApp
import com.example.data.UserDeleteCheckResult
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile
import com.example.notification.InstallmentAlarmReceiver
import com.example.ui.components.LoanViewMode
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemePreset
import com.example.util.AppPreferences
import com.example.util.NumberFormatter
import com.example.util.ShamsiDate
import com.example.util.ShamsiDateHelper
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
    BUDGET_ADVICE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as VamyarApp).repository
    private val appPreferences = AppPreferences(application)

    val allLoans: StateFlow<List<Loan>> = repository.allLoans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allShares: StateFlow<List<DebtorShare>> = repository.allShares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecords: StateFlow<List<PaymentRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserProfile>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val primaryUser: StateFlow<UserProfile?> = repository.primaryUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Onboarding dialog removed per user request (primary user defaults to "من")
    val showOnboarding: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

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
        val isPrimary = activeUser.trim().equals(primaryUserName.trim(), ignoreCase = true) || activeUser == "من" || activeUser == "شما"

        return if (isPrimary) {
            if (loanShares.isNotEmpty()) {
                val othersTotal = loanShares.sumOf { it.shareAmount }
                (loan.monthlyAmount - othersTotal).coerceAtLeast(0L)
            } else if (loan.hasPartners) {
                loan.myShareAmount
            } else {
                loan.monthlyAmount
            }
        } else {
            val userDebtorShare = loanShares.find { it.personName.trim().equals(activeUser.trim(), ignoreCase = true) }
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
        val totalMonthlyDue = activeLoans.sumOf { it.monthlyAmount }

        val userMonthlyShare = activeLoans.sumOf { getUserShareForLoan(it, shares, activeUserName, activeUserName) }
        val userPaidThisMonth = activeLoans.filter { it.isCurrentMonthPaid }.sumOf { getUserShareForLoan(it, shares, activeUserName, activeUserName) }
        val userUnpaidRemainingThisMonth = (userMonthlyShare - userPaidThisMonth).coerceAtLeast(0L)

        val relevantShares = shares.filter { it.personName != activeUserName }

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
        primaryUser
    ) { shares, primary ->
        val activeUserName = primary?.name ?: "کاربر من"
        val otherShares = shares.filter { it.personName != activeUserName }
        otherShares.groupBy { it.personName }.map { (person, personShares) ->
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
    val smartFinancialTips: StateFlow<List<SmartFinancialTip>> = combine(
        allLoans,
        allShares,
        primaryUser,
        currentShamsiDate
    ) { loans, shares, primary, curDate ->
        val usePersian = _usePersianDigits.value
        val compact = _compactThousands.value
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
            val daysDiff = ShamsiDateHelper.daysUntilDue(nextLoan.dueDay, curDate)

            val timingText = when {
                daysDiff == 0 -> "امروز موعد پرداخت است!"
                daysDiff > 0 -> "${NumberFormatter.formatNumber(daysDiff, usePersian)} روز دیگر"
                else -> "موعد این ماه سپری شده"
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
        val unpaidShares = shares.filter { it.personName != activeUserName && !it.isPaidForCurrentMonth }
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
            val curDateStr = _currentShamsiDate.value.formatted()
            repository.toggleCurrentMonthPaid(loan, curDateStr)
        }
    }

    fun incrementPaidInstallment(loan: Loan) {
        viewModelScope.launch {
            val curDateStr = _currentShamsiDate.value.formatted()
            repository.recordInstallmentPayment(loan, isIncrement = true, curShamsiFormatted = curDateStr)
        }
    }

    fun decrementPaidInstallment(loan: Loan) {
        viewModelScope.launch {
            val curDateStr = _currentShamsiDate.value.formatted()
            repository.recordInstallmentPayment(loan, isIncrement = false, curShamsiFormatted = curDateStr)
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

    fun completeOnboarding(userName: String, phoneNumber: String = "") {
        viewModelScope.launch {
            val cleanName = userName.trim().ifEmpty { "من" }
            // فیکس bug.md/P1-7: saveUser اکنون پرچم مبنای قبلی را در همان تراکنش پاک می‌کند؛
            // دیگر ممکن نیست دو کاربر مبنا هم‌زمان وجود داشته باشند.
            repository.saveUser(
                com.example.model.UserProfile(
                    id = 0L,
                    name = cleanName,
                    phoneNumber = phoneNumber.trim(),
                    isPrimary = true,
                    avatarColorHex = 0xFF10B981L,
                    notes = "کاربر مبنا"
                )
            )
        }
    }

    fun clearAllDataAndStartFresh() {
        viewModelScope.launch {
            repository.clearAllDataAndReset()
        }
    }

    fun clearAllLoansAndStartFresh() {
        viewModelScope.launch {
            repository.clearAllLoansAndReset()
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.clearAllLoansAndReset()
        }
    }

    fun exportBackupJson(): String {
        val currentLoans = allLoans.value
        val currentShares = allShares.value
        val currentRecords = allRecords.value
        val currentUsers = allUsers.value

        val prefs = com.example.util.BackupPreferences(
            themeMode = _themeMode.value.name,
            themePreset = _themePreset.value.name,
            usePersianDigits = _usePersianDigits.value,
            compactThousands = _compactThousands.value,
            customYear = _currentShamsiDate.value.year,
            customMonth = _currentShamsiDate.value.month,
            customDay = _currentShamsiDate.value.day,
            isCustomDateEnabled = isCustomDateActive.value
        )

        return com.example.util.BackupHelper.createBackupJson(
            users = currentUsers,
            loans = currentLoans,
            shares = currentShares,
            records = currentRecords,
            preferences = prefs,
            // فیکس bug.md/P2-13: نسخهٔ درج‌شده در فایل بکاپ دیگر هاردکد نیست؛ از
            // BuildConfig.VERSION_NAME خوانده می‌شود که در زمان بیلد از version.properties
            // (تنها منبع حقیقت نسخهٔ اپ) تزریق می‌شود.
            appVersion = BuildConfig.VERSION_NAME,
            shamsiDateFormatted = _currentShamsiDate.value.toStandardFormat(usePersianDigits = false)
        )
    }

    suspend fun importBackupJson(jsonString: String): Result<String> {
        return try {
            val parseResult = com.example.util.BackupHelper.parseAndValidateBackup(jsonString)
            if (parseResult.isFailure) {
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("فایل پشتیبان نامعتبر است."))
            }

            val data = parseResult.getOrThrow()

            // Restore Database
            repository.restoreDatabaseSnapshot(
                users = data.users,
                loans = data.loans,
                shares = data.shares,
                records = data.records
            )

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


            val summary = "پشتیبان با موفقیت بازیابی شد: ${data.loans.size} وام، ${data.users.size} کاربر، ${data.shares.size} سهم شراکت."
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

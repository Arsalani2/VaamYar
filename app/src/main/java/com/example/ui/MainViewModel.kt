package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VamyarApp
import com.example.data.UserDeleteCheckResult
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile
import com.example.notification.InstallmentAlarmReceiver
import com.example.ui.components.LoanViewMode
import com.example.ui.theme.AppFontStyle
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemePreset
import com.example.util.AppPreferences
import com.example.util.NumberFormatter
import com.example.util.ShamsiDate
import com.example.util.ShamsiDateHelper
import com.example.util.SoundHelper
import kotlinx.coroutines.flow.*
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

    val showOnboarding: StateFlow<Boolean> = allUsers
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Persistent View Mode
    private val _loanViewMode = MutableStateFlow(appPreferences.loanViewMode)
    val loanViewMode: StateFlow<LoanViewMode> = _loanViewMode.asStateFlow()

    // Current Shamsi Date (System date or custom override)
    private val _currentShamsiDate = MutableStateFlow(
        if (appPreferences.isCustomDateEnabled) {
            ShamsiDate(appPreferences.customYear, appPreferences.customMonth, appPreferences.customDay)
        } else {
            ShamsiDateHelper.getSystemShamsiDate()
        }
    )
    val currentShamsiDate: StateFlow<ShamsiDate> = _currentShamsiDate.asStateFlow()

    val isCustomDateActive: StateFlow<Boolean> = MutableStateFlow(appPreferences.isCustomDateEnabled)

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _themePreset = MutableStateFlow(ThemePreset.EMERALD_GOLD)
    val themePreset: StateFlow<ThemePreset> = _themePreset.asStateFlow()

    private val _appFontStyle = MutableStateFlow(AppFontStyle.SYSTEM)
    val appFontStyle: StateFlow<AppFontStyle> = _appFontStyle.asStateFlow()

    private val _usePersianDigits = MutableStateFlow(true)
    val usePersianDigits: StateFlow<Boolean> = _usePersianDigits.asStateFlow()

    private val _compactThousands = MutableStateFlow(false)
    val compactThousands: StateFlow<Boolean> = _compactThousands.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private fun getUserShareForLoan(loan: Loan, shares: List<DebtorShare>, activeUser: String, primaryUserName: String = activeUser): Long {
        return if (activeUser == primaryUserName || activeUser == "من") {
            loan.effectiveMyShare
        } else {
            val userDebtorShare = shares.find { it.loanId == loan.id && it.personName == activeUser }
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
            val daysDiff = nextLoan.dueDay - curDate.day

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

    // Date Sync & Custom Date Override Methods
    fun syncDateWithSystem() {
        appPreferences.clearCustomDate()
        _currentShamsiDate.value = ShamsiDateHelper.getSystemShamsiDate()
        SoundHelper.playClick(getApplication(), _soundEnabled.value)
    }

    fun setCustomShamsiDate(date: ShamsiDate) {
        appPreferences.setCustomDate(date.year, date.month, date.day)
        _currentShamsiDate.value = date
        SoundHelper.playClick(getApplication(), _soundEnabled.value)
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
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun saveUser(user: UserProfile) {
        viewModelScope.launch {
            repository.saveUser(user)
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    suspend fun checkCanDeleteUser(userId: Long): UserDeleteCheckResult {
        return repository.checkCanDeleteUser(userId)
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            repository.deleteUser(userId)
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun setThemePreset(preset: ThemePreset) {
        _themePreset.value = preset
    }

    fun setAppFontStyle(fontStyle: AppFontStyle) {
        _appFontStyle.value = fontStyle
    }

    fun setUsePersianDigits(usePersian: Boolean) {
        _usePersianDigits.value = usePersian
    }

    fun setCompactThousands(compact: Boolean) {
        _compactThousands.value = compact
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
    }

    fun toggleLoanCurrentMonthPaid(loan: Loan) {
        viewModelScope.launch {
            val curDateStr = _currentShamsiDate.value.formatted()
            repository.toggleCurrentMonthPaid(loan, curDateStr)
            if (!loan.isCurrentMonthPaid) {
                SoundHelper.playPaymentSuccess(getApplication(), _soundEnabled.value)
            } else {
                SoundHelper.playClick(getApplication(), _soundEnabled.value)
            }
        }
    }

    fun incrementPaidInstallment(loan: Loan) {
        viewModelScope.launch {
            val curDateStr = _currentShamsiDate.value.formatted()
            repository.recordInstallmentPayment(loan, isIncrement = true, curShamsiFormatted = curDateStr)
            SoundHelper.playPaymentSuccess(getApplication(), _soundEnabled.value)
        }
    }

    fun decrementPaidInstallment(loan: Loan) {
        viewModelScope.launch {
            val curDateStr = _currentShamsiDate.value.formatted()
            repository.recordInstallmentPayment(loan, isIncrement = false, curShamsiFormatted = curDateStr)
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun saveLoan(loan: Loan, shares: List<DebtorShare>) {
        viewModelScope.launch {
            repository.saveLoanWithShares(loan, shares)
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun deleteLoan(loanId: Long) {
        viewModelScope.launch {
            repository.deleteLoan(loanId)
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun toggleDebtorPaid(shareId: Long, isPaid: Boolean) {
        viewModelScope.launch {
            val monthName = if (isPaid) _currentShamsiDate.value.monthName else ""
            repository.updateDebtorPaidStatus(shareId, isPaid, monthName)
            if (isPaid) {
                SoundHelper.playPaymentSuccess(getApplication(), _soundEnabled.value)
            } else {
                SoundHelper.playClick(getApplication(), _soundEnabled.value)
            }
        }
    }

    fun resetMonthlyDebtorStatus() {
        viewModelScope.launch {
            repository.resetMonthlyDebtorStatus()
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun completeOnboarding(userName: String, phoneNumber: String = "") {
        viewModelScope.launch {
            val cleanName = userName.trim().ifEmpty { "کاربر اصلی" }
            repository.saveUser(
                com.example.model.UserProfile(
                    id = 0L,
                    name = cleanName,
                    phoneNumber = phoneNumber.trim(),
                    isPrimary = true,
                    avatarColorHex = 0xFF10B981L,
                    notes = "کاربر اصلی برنامه"
                )
            )
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun clearAllDataAndStartFresh() {
        viewModelScope.launch {
            repository.clearAllDataAndReset()
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun clearAllLoansAndStartFresh() {
        viewModelScope.launch {
            repository.clearAllLoansAndReset()
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.clearAllLoansAndReset()
            SoundHelper.playClick(getApplication(), _soundEnabled.value)
        }
    }

    fun sendTestNotification(context: Context) {
        val intent = Intent(context, InstallmentAlarmReceiver::class.java).apply {
            action = InstallmentAlarmReceiver.ACTION_TEST_NOTIFICATION
        }
        context.sendBroadcast(intent)
    }
}

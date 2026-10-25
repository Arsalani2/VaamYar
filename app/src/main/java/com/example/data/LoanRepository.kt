package com.example.data

import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile
import com.example.util.ShamsiDateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class UserDeleteCheckResult(
    val canDelete: Boolean,
    val blockingLoanTitles: List<String> = emptyList()
)

class LoanRepository(
    private val loanDao: LoanDao,
    private val debtorShareDao: DebtorShareDao,
    private val paymentRecordDao: PaymentRecordDao,
    private val userProfileDao: UserProfileDao
) {
    val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
    val allActiveLoans: Flow<List<Loan>> = loanDao.getAllActiveLoans()
    val allShares: Flow<List<DebtorShare>> = debtorShareDao.getAllShares()
    val allRecords: Flow<List<PaymentRecord>> = paymentRecordDao.getAllRecords()
    val allUsers: Flow<List<UserProfile>> = userProfileDao.getAllUsers()
    val primaryUser: Flow<UserProfile?> = userProfileDao.getPrimaryUser()

    suspend fun checkAndSeedInitialData() = withContext(Dispatchers.IO) {
        // 1. Ensure baseline primary user exists with default name "من" without prompting
        if (userProfileDao.getCount() == 0) {
            userProfileDao.insertUser(
                UserProfile(
                    id = 0L,
                    name = "من",
                    phoneNumber = "",
                    isPrimary = true,
                    avatarColorHex = 0xFF10B981L,
                    notes = "کاربر مبنا"
                )
            )
            userProfileDao.insertUser(
                UserProfile(
                    id = 0L,
                    name = "علی",
                    phoneNumber = "",
                    isPrimary = false,
                    avatarColorHex = 0xFF3B82F6L,
                    notes = "شریک وام"
                )
            )
        }

        // 2. Only seed 3 attractive guide loans if the loan database is completely empty
        if (loanDao.getCount() == 0) {
            // Loan 1: Personal loan - وام قرض‌الحسنه مهر ایران
            val loan1 = Loan(
                id = 0L,
                title = "وام قرض‌الحسنه مهر ایران",
                dueDay = 10,
                monthlyAmount = 2_100_000L,
                totalMonths = 24,
                paidMonths = 8,
                startYear = 1403,
                startMonth = 4,
                endYear = 1405,
                endMonth = 4,
                category = "قرض‌الحسنه",
                colorHex = 0xFF10B981L,
                notes = "وام انفرادی جهت پس‌انداز و گردش حساب",
                myShareAmount = 2_100_000L,
                hasPartners = false,
                isCurrentMonthPaid = false,
                iconName = "bank"
            )
            loanDao.insertLoan(loan1)

            // Loan 2: Shared loan - تسهیلات خودرو (شراکتی)
            val loan2 = Loan(
                id = 0L,
                title = "تسهیلات خودرو (شراکتی)",
                dueDay = 20,
                monthlyAmount = 4_000_000L,
                totalMonths = 36,
                paidMonths = 12,
                startYear = 1402,
                startMonth = 11,
                endYear = 1405,
                endMonth = 11,
                category = "خودرو",
                colorHex = 0xFF2563EBL,
                notes = "خرید خودرو مشترک با علی (سهم ۵۰-۵۰)",
                myShareAmount = 2_000_000L,
                hasPartners = true,
                isCurrentMonthPaid = false,
                iconName = "car"
            )
            val loan2Id = loanDao.insertLoan(loan2)
            debtorShareDao.insertShare(
                DebtorShare(
                    id = 0L,
                    loanId = loan2Id,
                    loanTitle = "تسهیلات خودرو (شراکتی)",
                    personName = "علی",
                    shareAmount = 2_000_000L,
                    isPaidForCurrentMonth = false
                )
            )

            // Loan 3: Consumer goods loan - خرید کالا (کالا کارت)
            val loan3 = Loan(
                id = 0L,
                title = "خرید کالا (کالا کارت)",
                dueDay = 5,
                monthlyAmount = 2_300_000L,
                totalMonths = 12,
                paidMonths = 4,
                startYear = 1403,
                startMonth = 8,
                endYear = 1404,
                endMonth = 8,
                category = "کالا",
                colorHex = 0xFFF59E0BL,
                notes = "اقساط خرید لوازم خانگی",
                myShareAmount = 2_300_000L,
                hasPartners = false,
                isCurrentMonthPaid = true,
                iconName = "shopping_cart"
            )
            loanDao.insertLoan(loan3)
        }
    }

    suspend fun clearAllLoansAndReset() = withContext(Dispatchers.IO) {
        loanDao.clearAllLoans()
        debtorShareDao.clearAllShares()
    }

    suspend fun clearAllDataAndReset() = withContext(Dispatchers.IO) {
        loanDao.clearAllLoans()
        debtorShareDao.clearAllShares()
        paymentRecordDao.clearAllRecords()
        userProfileDao.clearAllUsers()
    }

    suspend fun restoreDatabaseSnapshot(
        users: List<UserProfile>,
        loans: List<Loan>,
        shares: List<DebtorShare>,
        records: List<PaymentRecord>
    ) = withContext(Dispatchers.IO) {
        loanDao.clearAllLoans()
        debtorShareDao.clearAllShares()
        paymentRecordDao.clearAllRecords()
        userProfileDao.clearAllUsers()

        users.forEach { userProfileDao.insertUser(it) }
        loans.forEach { loanDao.insertLoan(it) }
        shares.forEach { debtorShareDao.insertShare(it) }
        records.forEach { paymentRecordDao.insertRecord(it) }
    }

    // User Management Methods
    suspend fun setPrimaryUser(userId: Long) = withContext(Dispatchers.IO) {
        userProfileDao.setPrimaryUser(userId)
    }

    suspend fun saveUser(user: UserProfile) = withContext(Dispatchers.IO) {
        if (user.id == 0L) {
            userProfileDao.insertUser(user)
        } else {
            val existing = userProfileDao.getUserById(user.id)
            if (existing != null && existing.name != user.name) {
                // Synchronize name change in all debtor shares!
                debtorShareDao.renamePersonInShares(existing.name, user.name)
            }
            userProfileDao.updateUser(user)
        }
    }

    /**
     * Checks if a user is safely deletable.
     * If user is partner in any loans, deletion is blocked and loan titles are returned.
     */
    suspend fun checkCanDeleteUser(userId: Long): UserDeleteCheckResult = withContext(Dispatchers.IO) {
        val user = userProfileDao.getUserById(userId) ?: return@withContext UserDeleteCheckResult(canDelete = true)
        val shares = debtorShareDao.getSharesForPerson(user.name)
        if (shares.isNotEmpty()) {
            val loanTitles = shares.map { it.loanTitle }.distinct()
            UserDeleteCheckResult(canDelete = false, blockingLoanTitles = loanTitles)
        } else {
            UserDeleteCheckResult(canDelete = true)
        }
    }

    suspend fun deleteUser(userId: Long) = withContext(Dispatchers.IO) {
        userProfileDao.deleteUserById(userId)
    }

    suspend fun saveLoanWithShares(
        loan: Loan,
        shares: List<DebtorShare>
    ): Long = withContext(Dispatchers.IO) {
        val loanId = if (loan.id == 0L) {
            loanDao.insertLoan(loan)
        } else {
            loanDao.updateLoan(loan)
            debtorShareDao.deleteSharesForLoan(loan.id)
            loan.id
        }

        shares.forEach { share ->
            debtorShareDao.insertShare(
                share.copy(
                    loanId = loanId,
                    loanTitle = loan.title,
                    dueDay = loan.dueDay
                )
            )
        }
        loanId
    }

    suspend fun deleteLoan(loanId: Long) = withContext(Dispatchers.IO) {
        debtorShareDao.deleteSharesForLoan(loanId)
        loanDao.deleteLoanById(loanId)
    }

    /**
     * Toggles whether the installment for the current month is paid or unpaid.
     */
    suspend fun toggleCurrentMonthPaid(loan: Loan, curShamsiFormatted: String) = withContext(Dispatchers.IO) {
        val newPaidStatus = !loan.isCurrentMonthPaid
        val newPaidMonths = if (newPaidStatus) {
            (loan.paidMonths + 1).coerceAtMost(loan.totalMonths)
        } else {
            (loan.paidMonths - 1).coerceAtLeast(0)
        }

        val updated = loan.copy(
            isCurrentMonthPaid = newPaidStatus,
            paidMonths = newPaidMonths
        )
        loanDao.updateLoan(updated)

        if (newPaidStatus) {
            paymentRecordDao.insertRecord(
                PaymentRecord(
                    loanId = loan.id,
                    loanTitle = loan.title,
                    installmentNumber = newPaidMonths,
                    amountPaid = loan.effectiveMyShare,
                    paymentDateShamsi = curShamsiFormatted
                )
            )
        }
    }

    suspend fun recordInstallmentPayment(
        loan: Loan,
        isIncrement: Boolean,
        curShamsiFormatted: String
    ) = withContext(Dispatchers.IO) {
        val newPaidMonths = if (isIncrement) {
            (loan.paidMonths + 1).coerceAtMost(loan.totalMonths)
        } else {
            (loan.paidMonths - 1).coerceAtLeast(0)
        }
        val isPaidNow = if (isIncrement) true else (newPaidMonths > 0)
        val updated = loan.copy(
            paidMonths = newPaidMonths,
            isCurrentMonthPaid = isPaidNow
        )
        loanDao.updateLoan(updated)

        if (isIncrement) {
            paymentRecordDao.insertRecord(
                PaymentRecord(
                    loanId = loan.id,
                    loanTitle = loan.title,
                    installmentNumber = newPaidMonths,
                    amountPaid = loan.monthlyAmount,
                    paymentDateShamsi = curShamsiFormatted
                )
            )
        }
    }

    suspend fun updateDebtorPaidStatus(shareId: Long, isPaid: Boolean, monthName: String) = withContext(Dispatchers.IO) {
        debtorShareDao.updatePaidStatus(shareId, isPaid, monthName)
    }

    suspend fun resetMonthlyDebtorStatus() = withContext(Dispatchers.IO) {
        debtorShareDao.resetMonthlyPaidStatus()
    }
}

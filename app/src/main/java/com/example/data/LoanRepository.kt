package com.example.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile
import com.example.util.PersianNameNormalizer
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
    private val userProfileDao: UserProfileDao,
    // فیکس bug.md/P1-4: برای اجرای عملیات چندمرحله‌ای (restore، ویرایش وام، ثبت پرداخت) به‌صورت اتمیک
    private val database: RoomDatabase
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
        database.withTransaction {
            loanDao.clearAllLoans()
            debtorShareDao.clearAllShares()
        }
    }

    suspend fun clearAllDataAndReset() = withContext(Dispatchers.IO) {
        database.withTransaction {
            loanDao.clearAllLoans()
            debtorShareDao.clearAllShares()
            paymentRecordDao.clearAllRecords()
            userProfileDao.clearAllUsers()
        }
    }

    /**
     * فیکس bug.md/P1-4:
     * پاک‌سازی چهار جدول و درج داده‌های پشتیبان قبلاً بدون تراکنش انجام می‌شد؛ کشته شدن اپ یا
     * استثنا وسط کار = دیتابیس نصفه (مثلاً وام‌ها پاک و سهم‌ها درج‌نشده). اکنون کل عملیات یک
     * تراکنش اتمیک است: یا کاملاً اعمال می‌شود یا (در صورت هر خطا) rollback شده و داده‌ی قبلی
     * کاربر دست‌نخورده می‌ماند. استثنا عمداً به فراخواننده منتقل می‌شود تا importBackupJson
     * آن را به‌صورت Result.failure به کاربر گزارش کند.
     */
    suspend fun restoreDatabaseSnapshot(
        users: List<UserProfile>,
        loans: List<Loan>,
        shares: List<DebtorShare>,
        records: List<PaymentRecord>
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            loanDao.clearAllLoans()
            debtorShareDao.clearAllShares()
            paymentRecordDao.clearAllRecords()
            userProfileDao.clearAllUsers()

            // فیکس bug.md/P3-1: نام‌های ورودی از بکاپ/داده‌های قدیمی هم هنگام بازیابی در
            // شکل استاندارد ذخیره می‌شوند تا داده‌های قدیمی با قاعدهٔ واحد تطبیق همگرا شوند.
            users.forEach {
                userProfileDao.insertUser(it.copy(name = PersianNameNormalizer.canonicalName(it.name)))
            }
            loans.forEach { loanDao.insertLoan(it) }
            shares.forEach {
                debtorShareDao.insertShare(
                    it.copy(personName = PersianNameNormalizer.canonicalName(it.personName))
                )
            }
            records.forEach { paymentRecordDao.insertRecord(it) }
        }
    }

    // User Management Methods
    suspend fun setPrimaryUser(userId: Long) = withContext(Dispatchers.IO) {
        userProfileDao.setPrimaryUser(userId)
    }

    /**
     * فیکس bug.md/P1-7:
     * درج/ویرایش کاربرِ دارای پرچمِ مبنا (مسیر completeOnboarding و ویرایش کاربر مبنا)
     * قبلاً پرچم کاربر مبنای قبلی را پاک نمی‌کرد → دو کاربر مبنا + انتخابِ غیرقطعی
     * getPrimaryUser. اکنون پاک‌سازی پرچم‌های قبلی و ثبت/به‌روزرسانی کاربر درون یک
     * تراکنش اتمیک انجام می‌شود تا همیشه حداکثر یک کاربر مبنا وجود داشته باشد.
     */
    suspend fun saveUser(user: UserProfile) = withContext(Dispatchers.IO) {
        // فیکس bug.md/P3-1: نام کاربر همیشه در شکل استاندارد ذخیره می‌شود (ي/ك عربی،
        // فاصله‌های اضافه) و هم‌گام‌سازی تغییر نام در سهم‌ها با کلیدِ تطبیقِ استاندارد
        // انجام می‌شود تا سهم‌های شرکا هرگز از صاحبشان جدا نمانند.
        val canonicalUser = user.copy(name = PersianNameNormalizer.canonicalName(user.name))
        if (canonicalUser.isPrimary) {
            database.withTransaction {
                userProfileDao.clearPrimaryFlags()
                if (canonicalUser.id == 0L) {
                    userProfileDao.insertUser(canonicalUser)
                } else {
                    val existing = userProfileDao.getUserById(canonicalUser.id)
                    if (existing != null && !PersianNameNormalizer.namesMatch(existing.name, canonicalUser.name)) {
                        // Synchronize name change in all debtor shares!
                        syncPersonNameInShares(existing.name, canonicalUser.name)
                    }
                    userProfileDao.updateUser(canonicalUser)
                }
            }
        } else {
            if (canonicalUser.id == 0L) {
                userProfileDao.insertUser(canonicalUser)
            } else {
                val existing = userProfileDao.getUserById(canonicalUser.id)
                if (existing != null && !PersianNameNormalizer.namesMatch(existing.name, canonicalUser.name)) {
                    // Synchronize name change in all debtor shares!
                    syncPersonNameInShares(existing.name, canonicalUser.name)
                }
                userProfileDao.updateUser(canonicalUser)
            }
        }
    }

    /**
     * فیکس bug.md/P3-1: هم‌گام‌سازی تغییر نامِ شخص در سهم‌های شرکا قبلاً یک UPDATE با
     * تطبیق خامِ رشته‌ای در SQL بود (renamePersonInShares)؛ نام قدیمی با ي/ك عربی،
     * فاصله‌های اضافه یا نیم‌فاصله هرگز مچ نمی‌شد و سهم‌های آن شخص بی‌صدا از صاحبشان
     * جدا می‌ماندند. اکنون تطبیق با کلید استاندارد نام (PersianNameNormalizer) انجام
     * می‌شود و نام جدید هم در همان شکل استاندارد نوشته می‌شود. باید درون تراکنشِ
     * فراخواننده صدا زده شود.
     */
    private suspend fun syncPersonNameInShares(oldName: String, canonicalNewName: String) {
        debtorShareDao.getAllSharesOnce().forEach { share ->
            if (PersianNameNormalizer.namesMatch(share.personName, oldName)) {
                debtorShareDao.updateShare(share.copy(personName = canonicalNewName))
            }
        }
    }

    /**
     * Checks if a user is safely deletable.
     * If user is partner in any loans, deletion is blocked and loan titles are returned.
     *
     * فیکس bug.md/P3-1: تطبیقِ «این کاربر شریکِ کدام وام‌هاست» قبلاً تطبیق خامِ رشته‌ای
     * بود؛ نام با ي/ك عربی یا فاصلهٔ اضافه مچ نمی‌شد و حذفِ کاربری که هنوز سهم دارد
     * مجاز می‌شد (سهم یتیم می‌ماند). اکنون تطبیق با کلید استاندارد نام انجام می‌شود.
     */
    suspend fun checkCanDeleteUser(userId: Long): UserDeleteCheckResult = withContext(Dispatchers.IO) {
        val user = userProfileDao.getUserById(userId) ?: return@withContext UserDeleteCheckResult(canDelete = true)
        val shares = debtorShareDao.getAllSharesOnce()
            .filter { PersianNameNormalizer.namesMatch(it.personName, user.name) }
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

    /**
     * فیکس bug.md/P1-2:
     * در مسیر ویرایش، قبل از حذف سهم‌های قدیمی، وضعیت پرداخت ماه جاری شرکای پابرجا
     * (isPaidForCurrentMonth و lastPaidMonthName) بر اساس نام شخص استخراج و روی
     * سهم جدید همان شخص بازگردانده می‌شود تا ویرایش وام (مثلاً تغییر عنوان یا مبلغ)
     * وضعیت پرداخت شرکا را پاک نکند. شرکای حذف‌شده/تازه‌اضافه‌شده حالت اولیه را می‌گیرند.
     */
    suspend fun saveLoanWithShares(
        loan: Loan,
        shares: List<DebtorShare>
    ): Long = withContext(Dispatchers.IO) {
        // ذخیره‌ی وام + شرکا یک تراکنش اتمیک است (حذف و درج دوباره‌ی سهم‌ها نباید وسط کار قطع شود)
        database.withTransaction {
            // فیکس bug.md/P1-9 (دفاع در عمق): هیچ مسیری — حتی فراخوانی‌های آینده — نباید بتواند
            // وامی با paidMonths > totalMonths ذخیره کند؛ UI همین قاعده را با هشدار و مسدودسازیِ
            // دکمه‌ی ذخیره اعمال می‌کند و اینجا صرفاً تضمین نهایی است. وامِ تسویه‌شده همیشه
            // پرچم «پرداخت ماه جاری» = true می‌گیرد و رکوردهای تاریخچه‌ی فراتر از کل اقساط پاک می‌شوند.
            val safeTotalMonths = loan.totalMonths.coerceIn(1, 120)
            val safePaidMonths = loan.paidMonths.coerceIn(0, safeTotalMonths)
            val settled = safePaidMonths >= safeTotalMonths
            val safeLoan = loan.copy(
                totalMonths = safeTotalMonths,
                paidMonths = safePaidMonths,
                isCurrentMonthPaid = if (settled) true else loan.isCurrentMonthPaid
            )
            if (safeLoan.id == 0L) {
                // وام جدید: سهم‌ها همان‌طور که از دیالوگ آماده‌اند درج می‌شوند
                val newLoanId = loanDao.insertLoan(safeLoan)
                shares.forEach { share ->
                    debtorShareDao.insertShare(
                        share.copy(
                            // فیکس bug.md/P3-1: نام شریک در شکل استاندارد ذخیره می‌شود
                            personName = PersianNameNormalizer.canonicalName(share.personName),
                            loanId = newLoanId,
                            loanTitle = safeLoan.title,
                            dueDay = safeLoan.dueDay
                        )
                    )
                }
                newLoanId
            } else {
                loanDao.updateLoan(safeLoan)
                // رکوردهای تاریخچه با شماره‌ی بالاتر از کل اقساط (باقی‌مانده از داده‌ی قدیمی
                // یا کاهشِ مدت وام) معنایی ندارند و حذف می‌شوند تا تاریخچه با وام سازگار بماند.
                paymentRecordDao.deleteRecordsAboveInstallment(safeLoan.id, safeTotalMonths)
                // فیکس bug.md/P3-1: بازگرداندن وضعیت پرداخت شرکای پابرجا حالا با کلید
                // استاندارد نام مچ می‌شود (ي/ك عربی، فاصله/نیم‌فاصله) و نامِ شخص هم در
                // شکل استاندارد ذخیره می‌شود.
                val existingByName = debtorShareDao
                    .getSharesForLoanOnce(safeLoan.id)
                    .associateBy { PersianNameNormalizer.matchKey(it.personName) }
                debtorShareDao.deleteSharesForLoan(safeLoan.id)

                shares.forEach { newShare ->
                    val matched = existingByName[PersianNameNormalizer.matchKey(newShare.personName)]
                    val toInsert = if (matched != null) {
                        newShare.copy(
                            isPaidForCurrentMonth = matched.isPaidForCurrentMonth,
                            lastPaidMonthName = matched.lastPaidMonthName
                        )
                    } else {
                        newShare
                    }
                    debtorShareDao.insertShare(
                        toInsert.copy(
                            personName = PersianNameNormalizer.canonicalName(toInsert.personName),
                            loanId = safeLoan.id,
                            loanTitle = safeLoan.title,
                            dueDay = safeLoan.dueDay
                        )
                    )
                }
                safeLoan.id
            }
        }
    }

    suspend fun deleteLoan(loanId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            debtorShareDao.deleteSharesForLoan(loanId)
            loanDao.deleteLoanById(loanId)
        }
    }

    /**
     * رکورد تاریخچه‌ی یک قسط. منبع واحد برای مبلغ ثبت‌شده در تاریخچه (فیکس bug.md/P1-3).
     *
     * تصمیم: مبلغ رکورد همیشه «کل قسط ماهانه‌ی وام» (monthlyAmount) است، چون هر دو مسیر
     * ثبت پرداخت (تیک ماه جاری و دکمه‌ی +/−) خودِ قسط وام را به وام‌دهنده پرداخت‌شده علامت
     * می‌زنند و Loan.totalPaidAmount هم بر پایه‌ی monthlyAmount محاسبه می‌شود. سهم هر شریک جدا
     * در debtor_shares نگه‌داری می‌شود. اگر روزی تاریخچه‌ی «سهم من» لازم شد، فقط همین تابع عوض شود.
     */
    private fun buildPaymentRecord(
        loan: Loan,
        installmentNumber: Int,
        curShamsiFormatted: String
    ) = PaymentRecord(
        loanId = loan.id,
        loanTitle = loan.title,
        installmentNumber = installmentNumber,
        amountPaid = loan.monthlyAmount,
        paymentDateShamsi = curShamsiFormatted
    )

    /**
     * ثبت رکورد قسط شماره‌ی [installmentNumber]؛ idempotent است: اگر رکوردی با همین شماره
     * از قبل وجود داشته باشد (مثلاً تکراریِ باقی‌مانده از باگ قدیمی) اول پاک می‌شود تا هرگز
     * دو رکورد برای یک قسط نماند.
     */
    private suspend fun upsertInstallmentRecord(
        loan: Loan,
        installmentNumber: Int,
        curShamsiFormatted: String
    ) {
        paymentRecordDao.deleteRecordsForInstallment(loan.id, installmentNumber)
        paymentRecordDao.insertRecord(buildPaymentRecord(loan, installmentNumber, curShamsiFormatted))
    }

    /**
     * فیکس bug.md/P1-3:
     * 1) برداشتن تیک، رکورد تاریخچه‌ی همان قسط را حذف می‌کند (قبلاً می‌ماند و تیک دوباره
     *    رکورد تکراری می‌ساخت).
     * 2) مبلغ رکورد در هر دو مسیر پرداخت یکسان است (buildPaymentRecord).
     * 3) وضعیت وام داخل تراکنش از دیتابیس خوانده می‌شود، نه از نسخه‌ی احتمالاً کهنه‌ی UI؛
     *    پس دوبار ضربه‌ی سریع هم رکورد تکراری یا شمارنده‌ی غلط نمی‌سازد.
     *
     * Toggles whether the installment for the current month is paid or unpaid.
     */
    suspend fun toggleCurrentMonthPaid(loan: Loan, curShamsiFormatted: String) = withContext(Dispatchers.IO) {
        database.withTransaction<Unit> {
            val current = loanDao.getLoanByIdOnce(loan.id) ?: return@withTransaction
            val newPaidStatus = !current.isCurrentMonthPaid
            val newPaidMonths = if (newPaidStatus) {
                (current.paidMonths + 1).coerceAtMost(current.totalMonths)
            } else {
                (current.paidMonths - 1).coerceAtLeast(0)
            }

            loanDao.updateLoan(
                current.copy(
                    isCurrentMonthPaid = newPaidStatus,
                    paidMonths = newPaidMonths
                )
            )

            if (newPaidStatus) {
                // فقط وقتی واقعاً یک قسط جدید اضافه شده رکورد ثبت می‌شود (وام تسویه‌شده clamp می‌شود)
                if (newPaidMonths > current.paidMonths) {
                    upsertInstallmentRecord(current, newPaidMonths, curShamsiFormatted)
                }
            } else {
                // قسطی که تا اینجا آخرین پرداخت‌شده بوده برگردانده شد؛ رکوردش باید پاک شود
                paymentRecordDao.deleteRecordsForInstallment(current.id, current.paidMonths)
            }
        }
    }

    /**
     * افزایش/کاهش دستی تعداد اقساط پرداخت‌شده (دکمه‌های +/−).
     * فیکس bug.md/P1-3: کاهش قسط هم اکنون رکورد همان قسط را از تاریخچه حذف می‌کند و
     * افزایش، رکورد تکراری نمی‌سازد.
     */
    suspend fun recordInstallmentPayment(
        loan: Loan,
        isIncrement: Boolean,
        curShamsiFormatted: String
    ) = withContext(Dispatchers.IO) {
        database.withTransaction<Unit> {
            val current = loanDao.getLoanByIdOnce(loan.id) ?: return@withTransaction
            val newPaidMonths = if (isIncrement) {
                (current.paidMonths + 1).coerceAtMost(current.totalMonths)
            } else {
                (current.paidMonths - 1).coerceAtLeast(0)
            }
            val isPaidNow = if (isIncrement) true else (newPaidMonths > 0)
            loanDao.updateLoan(
                current.copy(
                    paidMonths = newPaidMonths,
                    isCurrentMonthPaid = isPaidNow
                )
            )

            if (isIncrement) {
                if (newPaidMonths > current.paidMonths) {
                    upsertInstallmentRecord(current, newPaidMonths, curShamsiFormatted)
                }
            } else if (newPaidMonths < current.paidMonths) {
                paymentRecordDao.deleteRecordsForInstallment(current.id, current.paidMonths)
            }
        }
    }

    suspend fun updateDebtorPaidStatus(shareId: Long, isPaid: Boolean, monthName: String) = withContext(Dispatchers.IO) {
        debtorShareDao.updatePaidStatus(shareId, isPaid, monthName)
    }

    /**
     * رِسِت دستی «شروع ماه جدید» — فیکس bug.md/P1-6:
     * قبلاً فقط پرچم سهم شرکا صفر می‌شد و پرچم ماه جاری خود وام‌ها تیک‌خورده ماند می‌شد.
     * اکنون هر دو درون یک تراکنش اتمیک رِسِت می‌شوند.
     */
    suspend fun resetMonthlyDebtorStatus() = withContext(Dispatchers.IO) {
        database.withTransaction {
            loanDao.resetCurrentMonthPaidFlags()
            debtorShareDao.resetMonthlyPaidStatus()
        }
    }
}

package com.example.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile
import com.example.util.AppPreferences
import com.example.util.LoanIconHelper
import com.example.util.PersianNameNormalizer
import com.example.util.PublicId
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
    private val database: RoomDatabase,
    // فیکس bug.md/P3-10: مخزن مالکِ قاعدهٔ «seed فقط یک‌بار در هر نصب» است؛
    // پرچم persisted در AppPreferences نگه داشته می‌شود تا حذف وام‌های دمو
    // توسط کاربر هرگز به seedِ دوباره منجر نشود.
    private val appPreferences: AppPreferences
) {
    val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
    val allActiveLoans: Flow<List<Loan>> = loanDao.getAllActiveLoans()
    val allShares: Flow<List<DebtorShare>> = debtorShareDao.getAllShares()
    val allRecords: Flow<List<PaymentRecord>> = paymentRecordDao.getAllRecords()
    val allUsers: Flow<List<UserProfile>> = userProfileDao.getAllUsers()
    val primaryUser: Flow<UserProfile?> = userProfileDao.getPrimaryUser()

    /**
     * فیکس bug.md/P3-10 (تصمیم A1 — باگ گزارش‌شده توسط کاربر):
     * قبلاً شرط seed «خالی بودن جدول وام‌ها» بود؛ کاربر وام‌های دمو را برای ثبت
     * وام‌های واقعی‌اش پاک می‌کرد و در اجرای بعدیِ اپ دوباره ساخته می‌شدند.
     * اکنون seed دقیقاً یک‌بار در هر نصب انجام می‌شود:
     * ۱) اگر پرچم persisted `initialDataSeeded` set باشد، هیچ کاری انجام نمی‌شود —
     *    حذف وام‌های دمو (تکی یا «پاکسازی کامل وام‌ها») دیگر هرگز به seedِ دوباره
     *    منجر نمی‌شود.
     * ۲) چکِ خالی‌بودن جداول حفظ شد تا کاربرِ قدیمیِ دارندهٔ داده (که پرچم هنوز
     *    ندارد) هم داده‌ی دمو دریافت نکند؛ در آن صورت فقط پرچم set می‌شود.
     * ۳) کل seed داخل یک تراکنش اتمیک است و پرچم فقط بعد از موفقیت set می‌شود؛
     *    اگر وسط کار اپ کشته شود، rollback می‌شود و اجرای بعدی دوباره تلاش می‌کند.
     */
    suspend fun checkAndSeedInitialData() = withContext(Dispatchers.IO) {
        if (appPreferences.initialDataSeeded) return@withContext

        database.withTransaction {
            // 1. Ensure baseline primary user exists with default name "من" without prompting
            if (userProfileDao.getCount() == 0) {
                userProfileDao.insertUser(
                    UserProfile(
                        id = 0L,
                        name = "من",
                        isPrimary = true,
                        avatarColorHex = 0xFF10B981L,
                        notes = "کاربر مبنا"
                    )
                )
                userProfileDao.insertUser(
                    UserProfile(
                        id = 0L,
                        name = "علی",
                        isPrimary = false,
                        avatarColorHex = 0xFF3B82F6L,
                        notes = "شریک وام"
                    )
                )
            }

            // 2. Only seed 3 attractive guide loans if the loan database is completely empty
            if (loanDao.getCount() == 0) {
                // فیکس bug.md/NB-25: پایان هر وام seed با همان calculateEndDate محاسبه می‌شود
                // که payoffCountdownText و بقیهٔ اپ استفاده می‌کنند. ثابت‌های دستیِ قبلی یک
                // ماه جلوتر بودند (قسط اول = ماه شروع؛ قسط آخر = ماه شروع + مدت − ۱) و
                // «پایان وام» را در کارت‌ها غلط نشان می‌دادند.
                val end1 = ShamsiDateHelper.calculateEndDate(1403, 4, 24)
                val end2 = ShamsiDateHelper.calculateEndDate(1402, 11, 36)
                val end3 = ShamsiDateHelper.calculateEndDate(1403, 8, 12)
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
                    endYear = end1.year,
                    endMonth = end1.month,
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
                    endYear = end2.year,
                    endMonth = end2.month,
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
                    endYear = end3.year,
                    endMonth = end3.month,
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

        // پرچم فقط بعد از seedِ موفق (یا تشخیصِ داده‌ی موجود) set می‌شود؛
        // اگر وسط راه استثنایی رخ دهد، دفعهٔ بعد دوباره تلاش می‌شود.
        appPreferences.initialDataSeeded = true
    }

    suspend fun clearAllLoansAndReset() = withContext(Dispatchers.IO) {
        // فیکس bug.md/NB-19: مسیر آیکون‌های سفارشیِ موجود پیش از تراکنش جمع می‌شود و پس از
        // commit موفق، فایل‌ها از دیسک پاک می‌شوند تا هیچ فایل آیکونِ یتیمی نماند
        // (عیناً الگوی NB-6 در deleteLoan — فقط بعد از commit، تا در صورت rollback چیزی از دست نرود).
        val orphanIconPaths = loanDao.getLoansWithCustomIcon().mapNotNull { it.customIconPath }
        database.withTransaction {
            // فیکس bug.md/NB-14: رکوردهای تاریخچهٔ پرداخت وابستهٔ وام‌ها هستند؛ پیش از حذفِ
            // خودِ وام‌ها پاک می‌شوند تا هیچ رکورد یتیمی در دیتابیس — و در نتیجه در هر
            // بکاپِ بعدی — باقی نماند («پاکسازی تمام وام‌ها» اکنون واقعاً کامل است).
            paymentRecordDao.clearAllRecords()
            loanDao.clearAllLoans()
            debtorShareDao.clearAllShares()
        }
        orphanIconPaths.forEach { LoanIconHelper.deleteCustomIconFile(it) }
        appPreferences.initialDataSeeded = true
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
        // فیکس bug.md/NB-19: آیکون‌های سفارشیِ دادهٔ قبلی که در دادهٔ ورودی هیچ مرجعی به آن‌ها
        // نیست، پس از commit موفق از دیسک پاک می‌شوند (فایل یتیم نماند)؛ آیکون‌هایی که خودِ
        // بکاپ به آن‌ها اشاره می‌کند دست‌نخورده می‌مانند. فقط بعد از commit حذف می‌شود تا در
        // صورت rollback، دادهٔ قبلی و فایل‌هایش سرِ جای سالمشان بمانند.
        val importedIconPaths = loans.mapNotNull { it.customIconPath?.takeIf(String::isNotBlank) }.toSet()
        val staleIconPaths = loanDao.getLoansWithCustomIcon()
            .mapNotNull { it.customIconPath }
            .filterNot { it in importedIconPaths }
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
        staleIconPaths.forEach { LoanIconHelper.deleteCustomIconFile(it) }
    }

    // User Management Methods
    suspend fun setPrimaryUser(userId: Long) = withContext(Dispatchers.IO) {
        userProfileDao.setPrimaryUser(userId)
    }

    // ---------------------------------------------------------------- فیکس bug.md/F6 (شناسهٔ یکتای عمومی)

    /**
     * فیکس bug.md/F6: مولد شناسهٔ یکتا با چک برخورد در دیتابیس. فضای ۶۲^۶/۶۲^۷ نویسه
     * برخورد را عملاً ناممکن می‌کند؛ چک دفاعی است و بعد از ۱۰ تلاش، آخرین کاندیدا
     * برگردانده می‌شود (برخورد باقی‌مانده در ادغام NB-34 با منبع واحد مدیریت می‌شود).
     */
    private suspend fun uniqueLoanPublicId(): String {
        var candidate = PublicId.newLoanId()
        var tries = 0
        while (loanDao.getLoanByPublicId(candidate) != null && tries < 10) {
            candidate = PublicId.newLoanId()
            tries++
        }
        return candidate
    }

    private suspend fun uniqueUserPublicId(): String {
        var candidate = PublicId.newUserId()
        var tries = 0
        while (userProfileDao.getUserByPublicId(candidate) != null && tries < 10) {
            candidate = PublicId.newUserId()
            tries++
        }
        return candidate
    }

    private suspend fun uniqueSharePublicId(): String {
        var candidate = PublicId.newShareId()
        var tries = 0
        while (debtorShareDao.getShareByPublicId(candidate) != null && tries < 10) {
            candidate = PublicId.newShareId()
            tries++
        }
        return candidate
    }

    /** پیوند سهم به کاربرِ صاحبش؛ فقط با کلید استاندارد نام (fallback بکاپ‌های قدیمی). */
    private fun resolveOwnerPublicId(personName: String, users: List<UserProfile>): String =
        users.firstOrNull { PersianNameNormalizer.namesMatch(it.name, personName) }
            ?.publicId?.takeIf { it.isNotBlank() } ?: ""

    /**
     * فیکس bug.md/F6: تخصیص شناسهٔ عمومی به رکوردهای قدیمی/مهاجرت‌خورده (publicId خالی).
     * idempotent است — اجرای دوباره هیچ رکوردی را تغییر نمی‌دهد — و در startup اپ
     * (پس از rollover و seed) فراخوانی می‌شود. سهم‌ها هم ownerPublicId می‌گیرند
     * (کاربر هم‌نامِ موجود) تا پیوند هویتی سهم↔کاربر برقرار شود.
     */
    suspend fun backfillMissingPublicIds() = withContext(Dispatchers.IO) {
        userProfileDao.getUsersWithoutPublicId().forEach { user ->
            userProfileDao.updateUser(user.copy(publicId = uniqueUserPublicId()))
        }
        loanDao.getLoansWithoutPublicId().forEach { loan ->
            loanDao.updateLoan(loan.copy(publicId = uniqueLoanPublicId()))
        }
        val users = userProfileDao.getAllUsersOnce()
        debtorShareDao.getSharesWithoutPublicId().forEach { share ->
            debtorShareDao.updateShare(
                share.copy(
                    publicId = uniqueSharePublicId(),
                    ownerPublicId = share.ownerPublicId.ifBlank {
                        resolveOwnerPublicId(share.personName, users)
                    }
                )
            )
        }
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
        // فیکس bug.md/NB-23: دو شاخهٔ if/else با بدنهٔ یکسان ادغام شدند؛ تنها تفاوت واقعی —
        // پاک‌کردن پرچم‌های کاربر مبنای قبلی — فقط برای کاربرِ مبنا انجام می‌شود.
        database.withTransaction {
            if (canonicalUser.isPrimary) {
                userProfileDao.clearPrimaryFlags()
            }
            if (canonicalUser.id == 0L) {
                // فیکس bug.md/F6: کاربر جدید همیشه شناسهٔ عمومی می‌گیرد
                userProfileDao.insertUser(
                    if (canonicalUser.publicId.isBlank()) {
                        canonicalUser.copy(publicId = uniqueUserPublicId())
                    } else {
                        canonicalUser
                    }
                )
            } else {
                val existing = userProfileDao.getUserById(canonicalUser.id)
                if (existing != null && !PersianNameNormalizer.namesMatch(existing.name, canonicalUser.name)) {
                    // Synchronize name change in all debtor shares!
                    syncPersonNameInShares(existing, canonicalUser.name)
                }
                userProfileDao.updateUser(
                    // فیکس bug.md/F6: شناسهٔ عمومیِ رکورد موجود هرگز از بیرون بازنویسی
                    // نمی‌شود؛ ورودی بدون شناسه (UI قدیمی) شناسهٔ موجود را نگه می‌دارد.
                    if (canonicalUser.publicId.isBlank() && existing?.publicId?.isNotBlank() == true) {
                        canonicalUser.copy(publicId = existing.publicId)
                    } else {
                        canonicalUser
                    }
                )
            }
        }
    }

    /**
     * فیکس bug.md/P3-1 و ریشه‌حل NB-21 با F6: هم‌گام‌سازی تغییر نام کاربر در سهم‌های
     * شرکا. پیوند هویتی اول با ownerPublicId مچ می‌شود (دقیق و شکست‌ناپذیر)؛ سهم‌های
     * قدیمیِ بدون پیوند هنوز با کلید استاندارد نام مچ می‌شوند و همین یک‌بار
     * ownerPublicId می‌گیرند تا از این پس هویتی باشند. باید درون تراکنش فراخواننده صدا شود.
     */
    private suspend fun syncPersonNameInShares(existing: UserProfile, canonicalNewName: String) {
        val ownerPublicId = existing.publicId
        debtorShareDao.getAllSharesOnce().forEach { share ->
            val linkedById = ownerPublicId.isNotBlank() && share.ownerPublicId == ownerPublicId
            val legacyNameMatch = share.ownerPublicId.isBlank() &&
                PersianNameNormalizer.namesMatch(share.personName, existing.name)
            if (linkedById || legacyNameMatch) {
                debtorShareDao.updateShare(
                    share.copy(
                        personName = canonicalNewName,
                        ownerPublicId = share.ownerPublicId.ifBlank { ownerPublicId }
                    )
                )
            }
        }
    }

    /**
     * Checks if a user is safely deletable.
     * If user is partner in any loans, deletion is blocked and loan titles are returned.
     *
     * فیکس bug.md/P3-1 و ریشه‌حل NB-20 با F6: پیوند «این کاربر شریکِ کدام وام‌هاست»
     * اول با ownerPublicId مچ می‌شود (هویتی و دقیق)؛ سهم‌های قدیمیِ بدون پیوند با
     * کلید استاندارد نام fallback می‌شوند تا هیچ سهمی بی‌صدا یتیم نماند.
     */
    suspend fun checkCanDeleteUser(userId: Long): UserDeleteCheckResult = withContext(Dispatchers.IO) {
        val user = userProfileDao.getUserById(userId) ?: return@withContext UserDeleteCheckResult(canDelete = true)
        val shares = debtorShareDao.getAllSharesOnce()
        val blocking = shares.filter { share ->
            if (user.publicId.isNotBlank() && share.ownerPublicId.isNotBlank()) {
                share.ownerPublicId == user.publicId
            } else {
                PersianNameNormalizer.namesMatch(share.personName, user.name)
            }
        }
        if (blocking.isNotEmpty()) {
            UserDeleteCheckResult(canDelete = false, blockingLoanTitles = blocking.map { it.loanTitle }.distinct())
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
            // فیکس bug.md/F6: شناسهٔ عمومی وام — رکورد جدید شناسهٔ تازه می‌گیرد؛
            // رکورد موجود همیشه شناسهٔ قبلی‌اش را نگه می‌دارد (هویت با edit عوض نمی‌شود).
            val existingLoanForId = if (safeLoan.id == 0L) null else loanDao.getLoanByIdOnce(safeLoan.id)
            val loanWithId = when {
                safeLoan.publicId.isNotBlank() -> safeLoan
                existingLoanForId?.publicId?.isNotBlank() == true -> safeLoan.copy(publicId = existingLoanForId.publicId)
                else -> safeLoan.copy(publicId = uniqueLoanPublicId())
            }
            val usersForOwners = userProfileDao.getAllUsersOnce()
            if (loanWithId.id == 0L) {
                // وام جدید: سهم‌ها همان‌طور که از دیالوگ آماده‌اند شناسه و پیوند مالک می‌گیرند
                val newLoanId = loanDao.insertLoan(loanWithId)
                shares.forEach { share ->
                    debtorShareDao.insertShare(
                        share.copy(
                            // فیکس bug.md/P3-1: نام شریک در شکل استاندارد ذخیره می‌شود
                            personName = PersianNameNormalizer.canonicalName(share.personName),
                            // فیکس bug.md/F6: شناسهٔ سهم + پیوند به publicId کاربرِ صاحبش
                            publicId = share.publicId.ifBlank { uniqueSharePublicId() },
                            ownerPublicId = share.ownerPublicId.ifBlank {
                                resolveOwnerPublicId(share.personName, usersForOwners)
                            },
                            loanId = newLoanId,
                            loanTitle = loanWithId.title,
                            dueDay = loanWithId.dueDay
                        )
                    )
                }
                newLoanId
            } else {
                loanDao.updateLoan(loanWithId)
                // رکوردهای تاریخچه با شماره‌ی بالاتر از کل اقساط (باقی‌مانده از داده‌ی قدیمی
                // یا کاهشِ مدت وام) معنایی ندارند و حذف می‌شوند تا تاریخچه با وام سازگار بماند.
                paymentRecordDao.deleteRecordsAboveInstallment(loanWithId.id, loanWithId.totalMonths)
                // فیکس bug.md/F6: تطبیق شرکای پابرجا اول با publicId است (هویتی) و برای
                // سهم‌های بدون شناسه با کلید استاندارد نام (P3-1)؛ در هر دو حالت شناسهٔ
                // سهمِ قدیمی حفظ می‌شود تا edit هرگز هویت را نبُرد.
                val existingShares = debtorShareDao.getSharesForLoanOnce(loanWithId.id)
                val existingByPublicId = existingShares
                    .filter { it.publicId.isNotBlank() }
                    .associateBy { it.publicId }
                val existingByName = existingShares
                    .associateBy { PersianNameNormalizer.matchKey(it.personName) }
                debtorShareDao.deleteSharesForLoan(loanWithId.id)

                shares.forEach { newShare ->
                    val matched = if (newShare.publicId.isNotBlank()) {
                        existingByPublicId[newShare.publicId]
                    } else {
                        existingByName[PersianNameNormalizer.matchKey(newShare.personName)]
                    }
                    val toInsert = if (matched != null) {
                        newShare.copy(
                            publicId = matched.publicId.ifBlank { uniqueSharePublicId() },
                            ownerPublicId = matched.ownerPublicId.ifBlank {
                                resolveOwnerPublicId(newShare.personName, usersForOwners)
                            },
                            isPaidForCurrentMonth = matched.isPaidForCurrentMonth,
                            lastPaidMonthName = matched.lastPaidMonthName
                        )
                    } else {
                        newShare.copy(
                            publicId = uniqueSharePublicId(),
                            ownerPublicId = newShare.ownerPublicId.ifBlank {
                                resolveOwnerPublicId(newShare.personName, usersForOwners)
                            }
                        )
                    }
                    debtorShareDao.insertShare(
                        toInsert.copy(
                            personName = PersianNameNormalizer.canonicalName(toInsert.personName),
                            loanId = loanWithId.id,
                            loanTitle = loanWithId.title,
                            dueDay = loanWithId.dueDay
                        )
                    )
                }
                loanWithId.id
            }
        }
    }

    suspend fun deleteLoan(loanId: Long): Unit = withContext(Dispatchers.IO) {
        val iconPath = loanDao.getLoanByIdOnce(loanId)?.customIconPath
        database.withTransaction<Unit> {
            debtorShareDao.deleteSharesForLoan(loanId)
            loanDao.deleteLoanById(loanId)
        }
        // فیکس bug.md/NB-6: حذف فایل تصویر اختصاصی آیکون از دیسک پس از commit موفق تراکنش
        LoanIconHelper.deleteCustomIconFile(iconPath)
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
            // وام تسویه‌شده تمام اقساطش پرداخت شده است و نمی‌تواند تیک ماه جاری را تغییر دهد یا رکورد شبحی بسازد
            if (current.isCompleted && current.isCurrentMonthPaid) {
                return@withTransaction
            }
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
            // فیکس bug.md/NB-3: با کاهش شمارنده («−»)، آخرین قسط (که همان قسط ماه جاری بوده)
            // پس گرفته می‌شود؛ پس پرچم isCurrentMonthPaid باید false شود نه اینکه چون newPaidMonths > 0
            // است به غلط true بماند.
            val isPaidNow = if (isIncrement) true else false
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

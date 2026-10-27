package com.aistudio.vamyar.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
import com.aistudio.vamyar.model.PaymentRecord
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.util.AppPreferences
import com.aistudio.vamyar.util.LoanIconHelper
import com.aistudio.vamyar.util.PersianNameNormalizer
import com.aistudio.vamyar.util.PublicId
import com.aistudio.vamyar.util.ShamsiDate
import com.aistudio.vamyar.util.ShamsiDateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * فیکس bug.md/NB-36 (اینوارینت #۱۲): نام کاربر/عنوان وام یکتا هستند — دفاع نهایی در
 * Repository، مستقل از UI. فقط مسیر ثبت/ویرایش دستی؛ تطبیق و ادغام ایمپورت بکاپ
 * (NB-34) استثنای مستند است و از DAO مستقیم می‌گذرد، نه از این مسیر.
 */
class DuplicateNameException(message: String) : IllegalStateException(message)

/**
 * فیکس bug.md/NB-35 (اینوارینت #۱۱): سهمِ شریکِ بدون مالکِ تعریف‌شده قابل ثبت نیست —
 * نام آزاد دیگر مجاز نیست؛ شریک باید کاربر تعریف‌شده باشد. resolveOwnerPublicId
 * فقط fallback هویت است (پیوند سهمِ مشروع به کاربر هم‌نام)، نه مجوز نام آزاد.
 */
class OwnerlessShareException(message: String) : IllegalStateException(message)

data class UserDeleteCheckResult(
    val canDelete: Boolean,
    val blockingLoanTitles: List<String> = emptyList()
)

/**
 * فیکس bug.md/F1+NB-31: یک ماهِ عقب‌ماندهٔ واقعی (سررسیدشده ولی بدون رکورد پرداخت).
 */
data class ArrearsMonth(
    val installmentNumber: Int,
    val year: Int,
    val month: Int
) {
    val monthName: String get() = ShamsiDateHelper.getMonthName(month)
}

/**
 * فیکس bug.md/F1+NB-31: نتیجهٔ ضربه به دکمهٔ «پرداخت این ماه» —
 * - [PaidDirect]: عقب‌ماندگی نبود؛ قسط ماهِ جاریِ تقویم مستقیم ثبت شد.
 * - [Unpaid]: ماه جاری قبلاً پرداخت بود؛ رکوردش پس گرفته شد (رفتار toggle حفظ شد).
 * - [NeedsArrearsChoice]: عقب‌ماندگی هست؛ طبق تصمیم کاربر (بند ۵ متد فیکس NB-31)
 *   UI باید دیالوگ «فقط این ماه / همهٔ عقب‌مانده‌ها» نشان دهد.
 * - [Noop]: خارج از بازهٔ وام (ماه جاری بعد از قسط آخر) — کاری نمی‌شود کرد.
 */
sealed class CurrentMonthPayOutcome {
    object PaidDirect : CurrentMonthPayOutcome()
    object Unpaid : CurrentMonthPayOutcome()
    object Noop : CurrentMonthPayOutcome()
    data class NeedsArrearsChoice(val arrears: List<ArrearsMonth>) : CurrentMonthPayOutcome()
}

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

    // فیکس bug.md/F1+NB-31: منبع واحدِ حقیقتِ اقساط واقعی؛ DAO از همان اینستنس
    // دیتابیس گرفته می‌شود تا سازنده (و همهٔ فراخوانی‌های موجود در اپ/تست) دست‌نخورده بماند.
    private val loanInstallmentDao: LoanInstallmentDao get() = (database as AppDatabase).loanInstallmentDao()
    val allInstallments: Flow<List<LoanInstallment>> get() = loanInstallmentDao.getAllInstallments()

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
                // که بقیهٔ اپ استفاده می‌کنند. ثابت‌های دستیِ قبلی یک
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
                val loan1Id = loanDao.insertLoan(loan1)
                // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط seed (ترتیبی از ماه شروع، تاریخ نامعلوم)
                seedSequentialInstallmentRecords(loan1Id, loan1.startYear, loan1.startMonth, loan1.paidMonths, loan1.monthlyAmount)

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
                seedSequentialInstallmentRecords(loan2Id, loan2.startYear, loan2.startMonth, loan2.paidMonths, loan2.monthlyAmount)
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
                val loan3Id = loanDao.insertLoan(loan3)
                seedSequentialInstallmentRecords(loan3Id, loan3.startYear, loan3.startMonth, loan3.paidMonths, loan3.monthlyAmount)
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
            // فیکس bug.md/F1+NB-31: رکوردهای اقساط واقعی هم یتیم نمانند
            loanInstallmentDao.clearAllInstallments()
        }
        orphanIconPaths.forEach { LoanIconHelper.deleteCustomIconFile(it) }
        appPreferences.initialDataSeeded = true
    }

    /**
     * فیکس bug.md/NB-41: «بازنشانی کامل برنامه» — برخلاف clearAllLoansAndReset که
     * کاربران را عمداً نگه می‌دارد، این عملیات واقعاً همه‌چیز را پاک می‌کند:
     * وام‌ها، سهم‌های شرکا، رکوردهای تاریخچهٔ پرداخت، اقساط واقعی و همهٔ
     * کاربران (حتی کاربر مبنا)؛ سپس در همان تراکنش اتمیک یک کاربر مبنا
     * پیش‌فرض («من») می‌سازد تا اینوارینت NB-4 (دقیقاً یک کاربر مبنا) حتی
     * بلافاصله بعد از بازنشانی هم برقرار بماند. پرچم initialDataSeeded
     * دست‌نخورده می‌ماند تا وام‌های دمو برنگردند (قاعدهٔ P3-10) و آیکون‌های
     * سفارشی یتیم پس از commit موفق طبق الگوی NB-19 از دیسک پاک می‌شوند
     * (اینوارینت #۹ — هر مسیر «حذف همه/بازنشانی» همهٔ اجزای هم‌زیرساختی را پاک می‌کند).
     */
    suspend fun clearAllDataAndCreateDefaultBaseUser() = withContext(Dispatchers.IO) {
        val orphanIconPaths = loanDao.getLoansWithCustomIcon().mapNotNull { it.customIconPath }
        database.withTransaction {
            paymentRecordDao.clearAllRecords()
            loanDao.clearAllLoans()
            debtorShareDao.clearAllShares()
            loanInstallmentDao.clearAllInstallments()
            userProfileDao.clearAllUsers()
            userProfileDao.insertUser(
                UserProfile(
                    id = 0L,
                    name = "من",
                    isPrimary = true,
                    avatarColorHex = 0xFF10B981L,
                    notes = "کاربر مبنا",
                    // فیکس bug.md/F6: کاربر مبنای تازه هم شناسهٔ عمومی یکتا می‌گیرد
                    publicId = uniqueUserPublicId()
                )
            )
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
        records: List<PaymentRecord>,
        // فیکس bug.md/F1+NB-31 (بخش بکاپ): رکوردهای واقعی اقساط از بکاپ‌های جدید
        installments: List<LoanInstallment> = emptyList(),
        // فیکس bug.md/NB-18: مسیر محلیِ فایل‌های آیکونی که از دادهٔ embedشدهٔ بکاپ
        // قبلاً روی دیسکِ همین دستگاه نوشته شده‌اند (loanId → absolute path)
        iconPaths: Map<Long, String> = emptyMap()
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
            // فیکس bug.md/F1+NB-31: جدول اقساط واقعی هم در جایگزینی کامل پاک می‌شود
            loanInstallmentDao.clearAllInstallments()

            // فیکس bug.md/P3-1: نام‌های ورودی از بکاپ/داده‌های قدیمی هم هنگام بازیابی در
            // شکل استاندارد ذخیره می‌شوند تا داده‌های قدیمی با قاعدهٔ واحد تطبیق همگرا شوند.
            // فیکس bug.md/F6: شناسهٔ عمومیِ معتبرِ بکاپ دست‌نخورده حفظ می‌شود (بازیابیِ
            // پایدار/idempotent)؛ رکوردهای قدیمیِ بدون شناسه یا با شناسهٔ خاموشکل، شناسهٔ
            // تازهٔ یکتا می‌گیرند. سهم‌هایی که ownerPublicId ندارند (بکاپ قدیمی) با
            // resolveOwnerPublicId به کاربرِ هم‌نامِ همین مجموعه پیوند می‌خورند.
            val preparedUsers = users.map {
                it.copy(
                    name = PersianNameNormalizer.canonicalName(it.name),
                    publicId = if (PublicId.isValidUserId(it.publicId)) it.publicId else uniqueUserPublicId()
                )
            }
            preparedUsers.forEach { userProfileDao.insertUser(it) }

            loans.forEach {
                loanDao.insertLoan(
                    it.copy(
                        publicId = if (PublicId.isValidLoanId(it.publicId)) it.publicId else uniqueLoanPublicId(),
                        // فیکس bug.md/NB-18: اگر آیکون embedشدهٔ بکاپ روی دیسک نوشته شده
                        // باشد مسیر تازهٔ محلی می‌نشیند؛ بدون دادهٔ آیکون، مسیرِ ورودی فقط
                        // وقتی نگه داشته می‌شود که فایلش واقعاً روی همین دستگاه باشد
                        // (بکاپ قدیمی روی همان دستگاه) — وگرنه صریحاً به fallback
                        // برداری می‌رود و آیکون بی‌صورت نمی‌ماند.
                        customIconPath = iconPaths[it.id]
                            ?: it.customIconPath?.takeIf { p -> p.isNotBlank() && File(p).exists() }
                    )
                )
            }
            shares.forEach {
                val canonicalName = PersianNameNormalizer.canonicalName(it.personName)
                debtorShareDao.insertShare(
                    it.copy(
                        personName = canonicalName,
                        publicId = if (PublicId.isValidShareId(it.publicId)) it.publicId else uniqueSharePublicId(),
                        ownerPublicId = it.ownerPublicId.takeIf(String::isNotBlank)
                            ?: resolveOwnerPublicId(canonicalName, preparedUsers)
                    )
                )
            }
            records.forEach { paymentRecordDao.insertRecord(it) }

            // فیکس bug.md/F1+NB-31 (بخش restore): اگر بکاپ رکوردهای واقعی اقساط را
            // همراه داشته باشد (بکاپ جدید)، همان ماه‌های واقعیِ پرداخت‌شده مستقیماً
            // درج می‌شوند — حتی اگر نامرتّب/جُزئی باشند (مثلاً «فقط این ماه» وسط
            // عقب‌ماندگی). بکاپ قدیمی که آرایهٔ installments ندارد (خالی می‌رسد) به
            // بازسازی ترتیبی از paidMonths برمی‌گردد تا شمارندهٔ وام و رکوردهای
            // اقساط هرگز ناسازگار نمانند.
            val validRestoredLoanIds = loans.map { it.id }.toSet()
            val sanitizedRestoreInstallments = installments.filter {
                it.loanId in validRestoredLoanIds
            }
            if (sanitizedRestoreInstallments.isNotEmpty()) {
                sanitizedRestoreInstallments.forEach { loanInstallmentDao.insertInstallment(it) }
            } else {
                loans.forEach { l ->
                    seedSequentialInstallmentRecords(
                        loanId = l.id,
                        startYear = l.startYear,
                        startMonth = l.startMonth,
                        paidMonths = l.paidMonths.coerceIn(0, l.totalMonths),
                        monthlyAmount = l.monthlyAmount
                    )
                }
            }
        }
        staleIconPaths.forEach { LoanIconHelper.deleteCustomIconFile(it) }
    }

    // ---------------------------------------------------------------- فیکس bug.md/NB-34+NB-29 (ادغام ایمپورت)

    /**
     * فیکس bug.md/NB-34: کلید تطبیق کاربر در ادغام — اول publicId معتبر (F6)؛
     * بکاپ قدیمی بدون شناسه → نام کانونی (PersianNameNormalizer: ي/ك عربی، فاصله‌ها).
     * همان منطق در پیش‌نمایش (MainViewModel) و خودِ merge استفاده می‌شود تا هرگز واگرا نشود.
     */
    fun matchExistingUser(backupUser: UserProfile, existingUsers: List<UserProfile>): UserProfile? {
        if (PublicId.isValidUserId(backupUser.publicId)) {
            existingUsers.firstOrNull { it.publicId == backupUser.publicId }?.let { return it }
        }
        return existingUsers.firstOrNull { PersianNameNormalizer.namesMatch(it.name, backupUser.name) }
    }

    /**
     * فیکس bug.md/NB-34: کلید تطبیق وام — اول publicId؛ fallback بکاپ قدیمی:
     * عنوان + مبلغ ماهانه + سال/ماه شروع (تصمیم بازبینی ۴.۵).
     */
    fun matchExistingLoan(backupLoan: Loan, existingLoans: List<Loan>): Loan? {
        if (PublicId.isValidLoanId(backupLoan.publicId)) {
            existingLoans.firstOrNull { it.publicId == backupLoan.publicId }?.let { return it }
        }
        return existingLoans.firstOrNull {
            it.title.trim() == backupLoan.title.trim() &&
                it.monthlyAmount == backupLoan.monthlyAmount &&
                it.startYear == backupLoan.startYear &&
                it.startMonth == backupLoan.startMonth
        }
    }

    /**
     * فیکس bug.md/NB-34: کلید تطبیق سهم — اول publicId؛ fallback: همان وامِ محلی
     * ([localLoanId] نگاشت‌شده) + نام کانونی شریک.
     */
    fun matchExistingShare(
        backupShare: DebtorShare,
        existingShares: List<DebtorShare>,
        localLoanId: Long
    ): DebtorShare? {
        if (PublicId.isValidShareId(backupShare.publicId)) {
            existingShares.firstOrNull { it.publicId == backupShare.publicId }?.let { return it }
        }
        return existingShares.firstOrNull {
            it.loanId == localLoanId &&
                PersianNameNormalizer.namesMatch(it.personName, backupShare.personName)
        }
    }

    /**
     * فیکس bug.md/NB-34: خلاصهٔ نتیجهٔ ادغام برای نمایش به کاربر.
     */
    data class MergeSummary(
        val newUsers: Int, val updatedUsers: Int, val skippedUsers: Int,
        val newLoans: Int, val updatedLoans: Int, val skippedLoans: Int,
        val newShares: Int, val updatedShares: Int, val skippedShares: Int
    ) {
        val newTotal: Int get() = newUsers + newLoans + newShares
        val updatedTotal: Int get() = updatedUsers + updatedLoans + updatedShares
        val skippedTotal: Int get() = skippedUsers + skippedLoans + skippedShares
    }

    /**
     * فیکس bug.md/NB-34 (+NB-29): حالت «ادغام» ایمپورت — برخلاف جایگزینی کامل، دادهٔ
     * فعلی پاک نمی‌شود؛ فقط موارد جدید درج می‌شوند و موارد تکراری (با کلید publicId،
     * یا fallback نام کانونی/عنوان+مبلغ+ماه شروع برای بکاپ‌های قدیمی) به انتخاب کاربر
     * یا به‌روزرسانی می‌شوند یا دست‌نخورده رد می‌شوند ([updateDuplicates]).
     *
     * idempotent است (اینوارینت #۱۰): ایمپورت دوبارِ همان فایل هیچ وام/کاربر/شریک
     * تکراری نمی‌سازد، چون درج فقط برای مواردِ بدون تطبیق اتفاق می‌افتد.
     * کل عملیات داخل یک تراکنش اتمیک است؛ پرچم کاربر مبنا و شناسه‌های عمومیِ رکوردهای
     * محلی در به‌روزرسانی حفظ می‌شوند.
     */
    suspend fun mergeFromBackup(
        users: List<UserProfile>,
        loans: List<Loan>,
        shares: List<DebtorShare>,
        records: List<PaymentRecord>,
        installments: List<LoanInstallment> = emptyList(),
        iconPaths: Map<Long, String> = emptyMap(),
        updateDuplicates: Boolean
    ): MergeSummary = withContext(Dispatchers.IO) {
        database.withTransaction {
            var existingUsers = userProfileDao.getAllUsersOnce()
            val existingLoans = loanDao.getAllLoansOnce()
            val existingShares = debtorShareDao.getAllSharesOnce()
            val hasLocalPrimary = existingUsers.any { it.isPrimary }

            var newUsers = 0; var updatedUsers = 0; var skippedUsers = 0
            var newLoans = 0; var updatedLoans = 0; var skippedLoans = 0
            var newShares = 0; var updatedShares = 0; var skippedShares = 0

            // ۱) کاربران — پرچم مبنا و publicId رکورد محلیِ موجود همیشه حفظ می‌شود
            for (u in users) {
                val canonicalName = PersianNameNormalizer.canonicalName(u.name)
                val matched = matchExistingUser(u, existingUsers)
                if (matched != null) {
                    if (updateDuplicates) {
                        userProfileDao.updateUser(
                            matched.copy(
                                name = canonicalName,
                                notes = u.notes,
                                avatarColorHex = u.avatarColorHex
                            )
                        )
                        updatedUsers++
                    } else {
                        skippedUsers++
                    }
                } else {
                    val newUserPublicId = if (PublicId.isValidUserId(u.publicId)) u.publicId else uniqueUserPublicId()
                    // اگر دیتابیس خالی است پرچم مبنا حفظ می‌شود؛ وگرنه کاربر تازه مبنا نمی‌شود
                    val newUserIsPrimary = if (hasLocalPrimary) false else u.isPrimary
                    val insertedUserId = userProfileDao.insertUser(
                        u.copy(
                            id = 0L,
                            name = canonicalName,
                            publicId = newUserPublicId,
                            isPrimary = newUserIsPrimary
                        )
                    )
                    // فیکس تست NB-34: کاربر تازه‌درج‌شده به snapshot موجود هم اضافه می‌شود تا
                    // رکوردهای بعدیِ همین حلقه (مثلاً نام عربی «علي») او را تکراری ببینند
                    // و دوباره درج نشود (snapshot کهنه = دو کاربر از یک نفر).
                    existingUsers = existingUsers + u.copy(
                        id = insertedUserId,
                        name = canonicalName,
                        publicId = newUserPublicId,
                        isPrimary = newUserIsPrimary
                    )
                    newUsers++
                }
            }
            // اینوارینت NB-4: بعد از merge دقیقاً یک کاربر مبنا وجود دارد
            val mergedUsers = userProfileDao.getAllUsersOnce()
            if (mergedUsers.isNotEmpty() && mergedUsers.none { it.isPrimary }) {
                userProfileDao.setPrimaryUser(mergedUsers.first().id)
            }

            // ۲) وام‌ها — نگاشت id بکاپ → id محلی برای سهم/رکورد/قسط‌ها
            val backupLoanIdToLocal = mutableMapOf<Long, Long>()
            val updatedLoanBackupIds = mutableSetOf<Long>()
            val newLoanBackupIds = mutableSetOf<Long>()
            for (l in loans) {
                val matched = matchExistingLoan(l, existingLoans)
                if (matched != null) {
                    backupLoanIdToLocal[l.id] = matched.id
                    if (updateDuplicates) {
                        loanDao.updateLoan(
                            matched.copy(
                                title = l.title,
                                dueDay = l.dueDay,
                                monthlyAmount = l.monthlyAmount,
                                totalMonths = l.totalMonths,
                                paidMonths = l.paidMonths.coerceIn(0, l.totalMonths),
                                startYear = l.startYear,
                                startMonth = l.startMonth,
                                endYear = l.endYear,
                                endMonth = l.endMonth,
                                category = l.category,
                                colorHex = l.colorHex,
                                notes = l.notes,
                                myShareAmount = l.myShareAmount,
                                hasPartners = l.hasPartners,
                                isArchived = l.isArchived,
                                isCurrentMonthPaid = l.isCurrentMonthPaid,
                                iconName = l.iconName,
                                iconShape = l.iconShape,
                                // فیکس bug.md/NB-18: اگر بکاپ آیکون embed داشت جایگزین می‌شود؛
                                // وگرنه آیکون محلیِ فعلی حفظ می‌شود (ادغام آیکون را نمی‌بلعد)
                                customIconPath = iconPaths[l.id] ?: matched.customIconPath
                            )
                        )
                        updatedLoanBackupIds.add(l.id)
                        updatedLoans++
                    } else {
                        skippedLoans++
                    }
                } else {
                    val newId = loanDao.insertLoan(
                        l.copy(
                            id = 0L,
                            publicId = if (PublicId.isValidLoanId(l.publicId)) l.publicId else uniqueLoanPublicId(),
                            customIconPath = iconPaths[l.id]
                        )
                    )
                    backupLoanIdToLocal[l.id] = newId
                    newLoanBackupIds.add(l.id)
                    newLoans++
                }
            }

            // ۳) اقساط و رکوردهای هر وام — وامِ به‌روزرسانی‌شده: نسخهٔ بکاپ جایگزین می‌شود؛
            // وامِ جدید: رکوردهای بکاپ (یا بازسازی ترتیبی از paidMonths برای بکاپ قدیمی)؛
            // وامِ ردشده: کاملاً دست‌نخورده. بعد از آن شمارنده‌ها از رکوردهای واقعی sync می‌شوند.
            val currentShamsi = ShamsiDateHelper.getEffectiveShamsiDate(appPreferences)
            for (l in loans) {
                val localId = backupLoanIdToLocal[l.id] ?: continue
                val isUpdated = l.id in updatedLoanBackupIds
                val isNew = l.id in newLoanBackupIds
                if (!isUpdated && !isNew) continue // وامِ ردشده: اقساط/رکوردهایش دست نمی‌خورد
                val loanInstallments = installments.filter { it.loanId == l.id }
                if (isUpdated) {
                    loanInstallmentDao.deleteInstallmentsForLoan(localId)
                    paymentRecordDao.deleteRecordsForLoan(localId)
                }
                if (loanInstallments.isNotEmpty()) {
                    loanInstallments.forEach {
                        loanInstallmentDao.insertInstallment(it.copy(id = 0L, loanId = localId))
                    }
                } else {
                    // بکاپ قدیمی بدون رکورد قسط → بازسازی ترتیبی از paidMonths بکاپ
                    seedSequentialInstallmentRecords(
                        loanId = localId,
                        startYear = l.startYear,
                        startMonth = l.startMonth,
                        paidMonths = l.paidMonths.coerceIn(0, l.totalMonths),
                        monthlyAmount = l.monthlyAmount
                    )
                }
                records.filter { it.loanId == l.id }.forEach {
                    paymentRecordDao.insertRecord(it.copy(id = 0L, loanId = localId))
                }
                loanDao.getLoanByIdOnce(localId)?.let { syncLoanCountersFromRecords(it, currentShamsi) }
            }

            // ۴) سهم‌ها — ownerPublicId بکاپِ قدیمی با resolveOwnerPublicId پیوند می‌خورد
            val mergedUsersForShares = userProfileDao.getAllUsersOnce()
            for (s in shares) {
                val localLoanId = backupLoanIdToLocal[s.loanId] ?: run { skippedShares++; continue }
                val canonicalName = PersianNameNormalizer.canonicalName(s.personName)
                val matched = matchExistingShare(s, existingShares, localLoanId)
                if (matched != null) {
                    if (updateDuplicates) {
                        debtorShareDao.updateShare(
                            matched.copy(
                                loanId = localLoanId,
                                loanTitle = s.loanTitle,
                                personName = canonicalName,
                                shareAmount = s.shareAmount,
                                dueDay = s.dueDay,
                                isPaidForCurrentMonth = s.isPaidForCurrentMonth,
                                lastPaidMonthName = s.lastPaidMonthName,
                                shareDescription = s.shareDescription,
                                ownerPublicId = s.ownerPublicId.takeIf { it.isNotBlank() }
                                    ?: resolveOwnerPublicId(canonicalName, mergedUsersForShares)
                            )
                        )
                        updatedShares++
                    } else {
                        skippedShares++
                    }
                } else {
                    debtorShareDao.insertShare(
                        s.copy(
                            id = 0L,
                            loanId = localLoanId,
                            personName = canonicalName,
                            publicId = if (PublicId.isValidShareId(s.publicId)) s.publicId else uniqueSharePublicId(),
                            ownerPublicId = s.ownerPublicId.takeIf { it.isNotBlank() }
                                ?: resolveOwnerPublicId(canonicalName, mergedUsersForShares)
                        )
                    )
                    newShares++
                }
            }

            MergeSummary(
                newUsers = newUsers, updatedUsers = updatedUsers, skippedUsers = skippedUsers,
                newLoans = newLoans, updatedLoans = updatedLoans, skippedLoans = skippedLoans,
                newShares = newShares, updatedShares = updatedShares, skippedShares = skippedShares
            )
        }
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
        // فیکس bug.md/NB-36: دفاع در عمق — یکتایی نام کاربر در خود Repository تضمین می‌شود؛
        // UI (AddEditUserDialog و دیالوگ وام) همان چک را زنده انجام می‌دهد. هنگام ویرایش،
        // خودِ کاربر (id یکسان) از بررسی کنار گذاشته می‌شود.
        val usersSnapshot = userProfileDao.getAllUsersOnce()
        val duplicateName = usersSnapshot.any {
            it.id != canonicalUser.id && PersianNameNormalizer.namesMatch(it.name, canonicalUser.name)
        }
        if (duplicateName) {
            throw DuplicateNameException(
                "کاربری با نام «${canonicalUser.name}» از قبل وجود دارد؛ لطفاً نام دیگری انتخاب کنید."
            )
        }
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
        shares: List<DebtorShare>,
        // فیکس bug.md/NB-37: مجموعهٔ (سال، ماه) هدفِ اقساط از دیالوگ ویرایش — null یعنی
        // فراخوانی قدیمی (بدون دیالوگِ رکورد-محور): رفتار قبلی حفظ می‌شود.
        targetPaidYearMonths: Set<Pair<Int, Int>>? = null,
        currentDate: ShamsiDate? = null
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
            // فیکس bug.md/NB-36: دفاع در عمق — یکتایی عنوان وام با کلید استاندارد؛
            // هنگام ویرایش، خودِ وام (id یکسان) از بررسی کنار گذاشته می‌شود.
            val loansSnapshot = loanDao.getAllLoansOnce()
            val duplicateTitle = loansSnapshot.any {
                it.id != loanWithId.id &&
                    PersianNameNormalizer.matchKey(it.title) == PersianNameNormalizer.matchKey(loanWithId.title)
            }
            if (duplicateTitle) {
                throw DuplicateNameException(
                    "وامی با نام «${loanWithId.title}» از قبل ثبت شده است؛ لطفاً نام دیگری انتخاب کنید."
                )
            }
            // فیکس bug.md/NB-37: تاریخِ هم‌ارزسازی رکوردها — فراخوانی‌های قدیمیِ بدون
            // تاریخ از ساعت سیستم استفاده می‌کنند (مثل پرداخت‌های عادی).
            val effectiveDate = currentDate ?: ShamsiDateHelper.getSystemShamsiDate()
            if (loanWithId.id == 0L) {
                // وام جدید: سهم‌ها همان‌طور که از دیالوگ آماده‌اند شناسه و پیوند مالک می‌گیرند
                val newLoanId = loanDao.insertLoan(loanWithId)
                shares.forEach { share ->
                    // فیکس bug.md/NB-35 (اینوارینت #۱۱): دفاع در عمق — سهمِ شریکِ جدید
                    // باید مالکِ تعریف‌شده داشته باشد؛ ownerPublicId خالی فقط وقتی
                    // پذیرفته می‌شود که کاربرِ هم‌نام در فهرست کاربران موجود باشد
                    // (resolve fallback) تا سهمِ بی‌مالک هرگز ساخته نشود.
                    val resolvedOwner = share.ownerPublicId.ifBlank {
                        resolveOwnerPublicId(share.personName, usersForOwners)
                    }
                    if (resolvedOwner.isBlank()) {
                        throw OwnerlessShareException(
                            "شریک «${share.personName}» در فهرست کاربران تعریف نشده است؛ ابتدا از دکمهٔ «+ کاربر جدید» ثبتش کنید."
                        )
                    }
                    debtorShareDao.insertShare(
                        share.copy(
                            // فیکس bug.md/P3-1: نام شریک در شکل استاندارد ذخیره می‌شود
                            personName = PersianNameNormalizer.canonicalName(share.personName),
                            // فیکس bug.md/F6: شناسهٔ سهم + پیوند به publicId کاربرِ صاحبش
                            publicId = share.publicId.ifBlank { uniqueSharePublicId() },
                            ownerPublicId = resolvedOwner,
                            loanId = newLoanId,
                            loanTitle = loanWithId.title,
                            dueDay = loanWithId.dueDay
                        )
                    )
                }
                // فیکس bug.md/F1+NB-31: وامِ جدید با paidMonths > 0 (ثبت وامِ نیمه‌پرداخت‌شده)
                // هم باید رکورد اقساطِ هم‌ارز داشته باشد تا شمارنده و رکوردها سازگار بمانند.
                if (targetPaidYearMonths != null) {
                    // فیکس bug.md/NB-37: وام جدید هم همان مجموعهٔ (سال، ماه) واقعیِ
                    // تیک‌خوردهٔ دیالوگ را رکورد می‌گیرد (نه seed ترتیبی) — منبع واحد.
                    reconcileInstallments(loanWithId.copy(id = newLoanId), targetPaidYearMonths, effectiveDate)
                } else {
                    // فیکس bug.md/F1+NB-31: وامِ جدید با paidMonths > 0 (ثبت وامِ نیمه‌پرداخت‌شده)
                    // هم باید رکورد اقساطِ هم‌ارز داشته باشد تا شمارنده و رکوردها سازگار بمانند.
                    seedSequentialInstallmentRecords(
                        loanId = newLoanId,
                        startYear = loanWithId.startYear,
                        startMonth = loanWithId.startMonth,
                        paidMonths = safePaidMonths,
                        monthlyAmount = loanWithId.monthlyAmount
                    )
                }
                newLoanId
            } else {
                loanDao.updateLoan(loanWithId)
                // رکوردهای تاریخچه با شماره‌ی بالاتر از کل اقساط (باقی‌مانده از داده‌ی قدیمی
                // یا کاهشِ مدت وام) معنایی ندارند و حذف می‌شوند تا تاریخچه با وام سازگار بماند.
                paymentRecordDao.deleteRecordsAboveInstallment(loanWithId.id, loanWithId.totalMonths)
                // فیکس bug.md/F1+NB-31: هم‌ارزِ همان قاعده برای رکوردهای اقساط واقعی —
                // کاهش مدت وام در ویرایش، رکوردهای بی‌مرجع را پاک می‌کند.
                loanInstallmentDao.deleteInstallmentsAboveNumber(loanWithId.id, loanWithId.totalMonths)
                // فیکس bug.md/NB-37: رکوردهای اقساط با مجموعهٔ (سال، ماه) هدفِ دیالوگ هم‌ارز
                // می‌شوند و شمارنده‌ها از روی رکوردها sync — ویرایش و صفحهٔ اصلی دیگر
                // هرگز واگرا نمی‌شوند (ادامهٔ مستقیم درس NB-31).
                if (targetPaidYearMonths != null) {
                    reconcileInstallments(loanWithId, targetPaidYearMonths, effectiveDate)
                }
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
                        // فیکس bug.md/NB-35 (اینوارینت #۱۱): سهمِ جدیدِ بدون تطبیق هم فقط
                        // با مالکِ تعریف‌شده قابل ثبت است (سهم‌های matched قدیمی/legacy
                        // عمداً permissive می‌مانند تا ویرایش وام‌های قدیمی نشکند).
                        val resolvedOwner = newShare.ownerPublicId.ifBlank {
                            resolveOwnerPublicId(newShare.personName, usersForOwners)
                        }
                        if (resolvedOwner.isBlank()) {
                            throw OwnerlessShareException(
                                "شریک «${newShare.personName}» در فهرست کاربران تعریف نشده است؛ ابتدا از دکمهٔ «+ کاربر جدید» ثبتش کنید."
                            )
                        }
                        newShare.copy(
                            publicId = uniqueSharePublicId(),
                            ownerPublicId = resolvedOwner
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
            // فیکس bug.md/F1+NB-31: رکوردهای اقساط واقعی وام حذف‌شده هم پاک می‌شوند
            loanInstallmentDao.deleteInstallmentsForLoan(loanId)
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

    // ---------------------------------------------------------------- فیکس bug.md/F1+NB-31 (قسط واقعی)

    /**
     * بازسازی رکوردهای اقساط یک وام به شکل ترتیبی از ماه شروع — هم‌ارزِ دادهٔ قدیمیِ
     * شمارندهٔ paidMonths (مهاجرت/seed/restore بکاپ‌های قدیمی). تاریخ واقعی پرداخت
     * نامعلوم است (timestamp=0، تاریخ خالی) و از این پس پرداخت‌های جدید ماهِ جاریِ
     * تقویم را با تاریخ واقعی ثبت می‌کنند.
     */
    private suspend fun seedSequentialInstallmentRecords(
        loanId: Long,
        startYear: Int,
        startMonth: Int,
        paidMonths: Int,
        monthlyAmount: Long
    ) {
        for (k in 1..paidMonths.coerceAtLeast(0)) {
            val m = ShamsiDateHelper.installmentMonth(startYear, startMonth, k)
            loanInstallmentDao.insertInstallment(
                LoanInstallment(
                    loanId = loanId,
                    installmentNumber = k,
                    year = m.year,
                    month = m.month,
                    amount = monthlyAmount
                )
            )
        }
    }

    /** تعداد قسط‌هایی که سررسیدشان رسیده یا گذشته است (ماه جاری هم اگر روزِ سررسید رسیده باشد) */
    private fun payableInstallmentNumbers(loan: Loan, currentDate: ShamsiDate): Int =
        ShamsiDateHelper.calculateElapsedInstallments(
            loan.startYear, loan.startMonth, loan.dueDay,
            currentDate.year, currentDate.month, currentDate.day,
            loan.totalMonths
        )

    /**
     * ماه‌های سررسیدگذشتهٔ بدون رکورد — به‌جز خودِ ماه جاری (ماه جاری «عقب‌مانده»
     * نیست؛ پرداختش انتخابِ کاربر است). این همان عقب‌ماندگی‌هایی است که NB-31 و NB-16
     * باید به کاربر نشان دهند.
     */
    private fun findArrearsMonths(
        loan: Loan,
        currentDate: ShamsiDate,
        existing: List<LoanInstallment>
    ): List<ArrearsMonth> {
        val paidNumbers = existing.map { it.installmentNumber }.toSet()
        val curNumber = ShamsiDateHelper.installmentNumberForMonth(
            loan.startYear, loan.startMonth, currentDate.year, currentDate.month
        )
        val payable = payableInstallmentNumbers(loan, currentDate)
        return (1..payable).filter { it !in paidNumbers && it != curNumber }.map { k ->
            val m = ShamsiDateHelper.installmentMonth(loan.startYear, loan.startMonth, k)
            ArrearsMonth(installmentNumber = k, year = m.year, month = m.month)
        }
    }

    /** درج رکورد قسط واقعی + آینهٔ آن در تاریخچهٔ payment_records (منبع واحد مبلغ — P1-3) */
    private suspend fun insertInstallmentRecord(
        loan: Loan,
        number: Int,
        year: Int,
        month: Int,
        currentDate: ShamsiDate
    ) {
        loanInstallmentDao.insertInstallment(
            LoanInstallment(
                loanId = loan.id,
                installmentNumber = number,
                year = year,
                month = month,
                amount = loan.monthlyAmount,
                paidAtTimestamp = System.currentTimeMillis(),
                paidDateShamsi = currentDate.formatted()
            )
        )
        upsertInstallmentRecord(loan, number, currentDate.formatted())
    }

    /** پس‌گرفتن یک قسط واقعی + حذف آینهٔ آن از تاریخچه */
    private suspend fun removeInstallmentRecord(loan: Loan, number: Int, year: Int, month: Int) {
        loanInstallmentDao.deleteByLoanAndMonth(loan.id, year, month)
        paymentRecordDao.deleteRecordsForInstallment(loan.id, number)
    }

    /**
     * فیکس bug.md/NB-37: هم‌ارزسازی رکوردهای واقعی اقساط با مجموعهٔ (سال، ماه) هدف —
     * درج ماه‌های جدید (شمارهٔ قسط از نگاشتِ ماه شروع وام؛ paidAt = اکنون)، حذف
     * ماه‌های اضافه (با آینهٔ تاریخچه‌شان — الگوی removeInstallmentRecord) و سپس
     * syncLoanCountersFromRecords تا شمارنده و پرچم ماه جاری تصویرِ همگامِ رکوردها
     * بمانند. ماه‌های خارج از بازهٔ ۱..totalMonths (تغییر تاریخ شروع/کاهش مدت)
     * از هدف کنار گذاشته و حذف می‌شوند — رفتارِ قبلیِ کاهش مدت حفظ شده است.
     * PaymentRecord ها فقط طبق همان قاعدهٔ آینه حرکت می‌کنند؛ رکوردهای موجود
     * دوباره نوشته نمی‌شوند (تاریخچهٔ پرداختِ ثبت‌شده دست‌نخورده می‌ماند).
     */
    private suspend fun reconcileInstallments(
        loan: Loan,
        targetYearMonths: Set<Pair<Int, Int>>,
        currentDate: ShamsiDate
    ) {
        val target = targetYearMonths.mapNotNull { (y, m) ->
            val n = ShamsiDateHelper.installmentNumberForMonth(loan.startYear, loan.startMonth, y, m)
            if (n != null && n in 1..loan.totalMonths) y to m else null
        }.toSet()
        val existing = loanInstallmentDao.getInstallmentsForLoanOnce(loan.id)
        // درج ماه‌های هدفِ بدون رکورد (با تاریخ واقعیِ اکنون — مثل پرداخت عادی)
        target.filter { (y, m) -> existing.none { it.year == y && it.month == m } }.forEach { (y, m) ->
            val n = ShamsiDateHelper.installmentNumberForMonth(loan.startYear, loan.startMonth, y, m)
                ?: return@forEach
            insertInstallmentRecord(loan, n, y, m, currentDate)
        }
        // حذف رکوردهایی که در مجموعهٔ هدف نیستند (با آینهٔ تاریخچه)
        existing.filter { (it.year to it.month) !in target }.forEach {
            removeInstallmentRecord(loan, it.installmentNumber, it.year, it.month)
        }
        syncLoanCountersFromRecords(loan, currentDate)
    }

    /**
     * فیکس bug.md/F1+NB-31: paidMonths و isCurrentMonthPaid دیگر «وضعیت مستقل»
     * نیستند — تصویرِ همگامِ رکوردهای واقعی اقساط‌اند؛ هر تغییر رکوردها باید با
     * همین تابع جمع‌بندی شود تا شمارنده و رکوردها هرگز ناسازگار نمانند.
     */
    private suspend fun syncLoanCountersFromRecords(loan: Loan, currentDate: ShamsiDate) {
        val records = loanInstallmentDao.getInstallmentsForLoanOnce(loan.id)
        val paid = records.size
        val completed = paid >= loan.totalMonths
        val currentPaid = completed ||
            records.any { it.year == currentDate.year && it.month == currentDate.month }
        loanDao.updateLoan(
            loan.copy(paidMonths = paid, isCurrentMonthPaid = currentPaid)
        )
    }

    /**
     * فیکس bug.md/F1+NB-31: ضربه به «پرداخت این ماه» — به ماهِ جاریِ واقعیِ تقویم وصل است:
     * - ماه جاری پرداخت شده → پس گرفته می‌شود (رفتار toggle قبلی حفظ شد؛ P1-3 برقرار:
     *   رکورد تاریخچه حذف می‌شود، دوبار ضربهٔ سریع با نسخهٔ کهنهٔ UI رکورد تکراری نمی‌سازد).
     * - عقب‌ماندگی نیست → قسط ماه جاری مستقیم ثبت می‌شود (بند ۵ تصمیم کاربر).
     * - عقب‌ماندگی هست → NeedsArrearsChoice تا UI دیالوگ «فقط این ماه / همهٔ عقب‌مانده‌ها»
     *   را نشان دهد (بند ۵ تصمیم کاربر — هر ماه سؤال نپرسد).
     */
    suspend fun payCurrentMonthSmart(loan: Loan, currentDate: ShamsiDate): CurrentMonthPayOutcome =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val current = loanDao.getLoanByIdOnce(loan.id)
                    ?: return@withTransaction CurrentMonthPayOutcome.Noop
                // وام تسویه‌شده تیک ماه جاری را تغییر نمی‌دهد و رکورد شبحی نمی‌سازد
                if (current.isCompleted && current.isCurrentMonthPaid) {
                    return@withTransaction CurrentMonthPayOutcome.Noop
                }
                val existing = loanInstallmentDao.getInstallmentsForLoanOnce(current.id)
                if (existing.any { it.year == currentDate.year && it.month == currentDate.month }) {
                    // برداشتن تیک ماه جاری — قسط واقعی همان ماه پس گرفته می‌شود
                    val number = ShamsiDateHelper.installmentNumberForMonth(
                        current.startYear, current.startMonth, currentDate.year, currentDate.month
                    ) ?: return@withTransaction CurrentMonthPayOutcome.Noop
                    removeInstallmentRecord(current, number, currentDate.year, currentDate.month)
                    syncLoanCountersFromRecords(current, currentDate)
                    CurrentMonthPayOutcome.Unpaid
                } else {
                    val number = ShamsiDateHelper.installmentNumberForMonth(
                        current.startYear, current.startMonth, currentDate.year, currentDate.month
                    )
                    if (number == null || number > current.totalMonths) {
                        // ماه جاری خارج از بازهٔ وام است (بعد از قسط آخر)
                        return@withTransaction CurrentMonthPayOutcome.Noop
                    }
                    val arrears = findArrearsMonths(current, currentDate, existing)
                    if (arrears.isEmpty()) {
                        insertInstallmentRecord(current, number, currentDate.year, currentDate.month, currentDate)
                        syncLoanCountersFromRecords(current, currentDate)
                        CurrentMonthPayOutcome.PaidDirect
                    } else {
                        CurrentMonthPayOutcome.NeedsArrearsChoice(arrears)
                    }
                }
            }
        }

    /** گزینهٔ «فقط این ماه» از دیالوگ عقب‌ماندگی — ثبت مستقیم ماه جاری بدون پرداخت عقب‌مانده‌ها */
    suspend fun payInstallmentMonth(loan: Loan, year: Int, month: Int, currentDate: ShamsiDate) =
        withContext(Dispatchers.IO) {
            database.withTransaction<Unit> {
                val current = loanDao.getLoanByIdOnce(loan.id) ?: return@withTransaction
                val number = ShamsiDateHelper.installmentNumberForMonth(
                    current.startYear, current.startMonth, year, month
                ) ?: return@withTransaction
                if (number > current.totalMonths) return@withTransaction
                val already = loanInstallmentDao.getInstallmentsForLoanOnce(current.id)
                    .any { it.year == year && it.month == month }
                if (!already) {
                    insertInstallmentRecord(current, number, year, month, currentDate)
                    syncLoanCountersFromRecords(current, currentDate)
                }
            }
        }

    /** گزینهٔ «همهٔ عقب‌مانده‌ها تا الان» از دیالوگ — همهٔ ماه‌های عقب‌افتاده + ماه جاری */
    suspend fun payArrearsAndCurrentMonth(loan: Loan, currentDate: ShamsiDate) =
        withContext(Dispatchers.IO) {
            database.withTransaction<Unit> {
                val current = loanDao.getLoanByIdOnce(loan.id) ?: return@withTransaction
                val existing = loanInstallmentDao.getInstallmentsForLoanOnce(current.id)
                findArrearsMonths(current, currentDate, existing).forEach { ar ->
                    insertInstallmentRecord(current, ar.installmentNumber, ar.year, ar.month, currentDate)
                }
                val curNumber = ShamsiDateHelper.installmentNumberForMonth(
                    current.startYear, current.startMonth, currentDate.year, currentDate.month
                )
                val currentMissing = existing.none {
                    it.year == currentDate.year && it.month == currentDate.month
                }
                if (curNumber != null && curNumber <= current.totalMonths && currentMissing) {
                    insertInstallmentRecord(current, curNumber, currentDate.year, currentDate.month, currentDate)
                }
                syncLoanCountersFromRecords(current, currentDate)
            }
        }

    /**
     * افزایش/کاهش دستی (دکمه‌های +/−) — طبق بند ۶ متد فیکس NB-31 همچنان ترتیبی کار
     * می‌کنند اما روی رکوردهای واقعی: «+» اولین ماهِ بدون رکورد را می‌پردازد و «−»
     * آخرین رکورد را پس می‌گیرد. فیکس‌های P1-3/NB-3 (حذف آینهٔ تاریخچه، بدون تکراری،
     * پرچم ماه جاری همگام) به‌طور خودکار از syncLoanCountersFromRecords برقرارند.
     */
    suspend fun recordInstallmentPayment(
        loan: Loan,
        isIncrement: Boolean,
        currentDate: ShamsiDate
    ) = withContext(Dispatchers.IO) {
        database.withTransaction<Unit> {
            val current = loanDao.getLoanByIdOnce(loan.id) ?: return@withTransaction
            val existing = loanInstallmentDao.getInstallmentsForLoanOnce(current.id)
                .sortedBy { it.installmentNumber }
            if (isIncrement) {
                val paidNumbers = existing.map { it.installmentNumber }.toSet()
                val next = (1..current.totalMonths).firstOrNull { it !in paidNumbers }
                if (next != null) {
                    val m = ShamsiDateHelper.installmentMonth(current.startYear, current.startMonth, next)
                    insertInstallmentRecord(current, next, m.year, m.month, currentDate)
                }
            } else {
                existing.lastOrNull()?.let { last ->
                    removeInstallmentRecord(current, last.installmentNumber, last.year, last.month)
                }
            }
            syncLoanCountersFromRecords(current, currentDate)
        }
    }

    suspend fun updateDebtorPaidStatus(shareId: Long, isPaid: Boolean, monthName: String) = withContext(Dispatchers.IO) {
        debtorShareDao.updatePaidStatus(shareId, isPaid, monthName)
    }
}

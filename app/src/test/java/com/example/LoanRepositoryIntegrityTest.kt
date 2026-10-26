package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LoanRepository
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile
import com.example.util.AppPreferences
import com.example.util.PublicId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تست‌های رگرسیون bug.md:
 *  - P1-3: تاریخچه‌ی پرداخت (حذف رکورد هنگام برداشتن تیک، بدون تکراری، مبلغ یکسان)
 *  - P1-4: اتمیک بودن restore (rollback کامل در صورت خطا)
 *  - P3-10: seed داده‌ی راهنما فقط یک‌بار در هر نصب و هرگز بعد از حذف توسط کاربر
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanRepositoryIntegrityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: LoanRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LoanRepository(
            loanDao = db.loanDao(),
            debtorShareDao = db.debtorShareDao(),
            paymentRecordDao = db.paymentRecordDao(),
            userProfileDao = db.userProfileDao(),
            database = db,
            appPreferences = AppPreferences(context)
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun insertSharedLoan(paidMonths: Int = 3): Loan {
        // وام شراکتی: قسط ۴ میلیون، سهم من ۲ میلیون
        val id = repository.saveLoanWithShares(
            Loan(
                title = "وام شراکتی تست",
                dueDay = 10,
                monthlyAmount = 4_000_000L,
                totalMonths = 12,
                paidMonths = paidMonths,
                myShareAmount = 2_000_000L,
                hasPartners = true
            ),
            listOf(
                DebtorShare(
                    loanId = 0L,
                    loanTitle = "",
                    personName = "علی",
                    shareAmount = 2_000_000L
                )
            )
        )
        return repository.allLoans.first().first { it.id == id }
    }

    private suspend fun currentLoan(id: Long): Loan = repository.allLoans.first().first { it.id == id }

    // ---------------------------------------------------------------- P1-3

    @Test
    fun p1_3_toggleOffRemovesRecord_andToggleOnAgainDoesNotDuplicate() = runBlocking {
        val loan = insertSharedLoan(paidMonths = 3)

        repository.toggleCurrentMonthPaid(currentLoan(loan.id), "1405/08/03")
        var records = repository.allRecords.first()
        assertEquals(1, records.size)
        assertEquals(4, records.single().installmentNumber)
        assertEquals(4, currentLoan(loan.id).paidMonths)

        // برداشتن تیک: رکورد باید حذف شود و شمارنده برگردد
        repository.toggleCurrentMonthPaid(currentLoan(loan.id), "1405/08/03")
        records = repository.allRecords.first()
        assertTrue("رکورد پس از برداشتن تیک باید حذف شود", records.isEmpty())
        assertEquals(3, currentLoan(loan.id).paidMonths)
        assertFalse(currentLoan(loan.id).isCurrentMonthPaid)

        // تیک دوباره: دقیقاً یک رکورد، نه دو تا
        repository.toggleCurrentMonthPaid(currentLoan(loan.id), "1405/08/03")
        records = repository.allRecords.first()
        assertEquals(1, records.size)
        assertEquals(4, records.single().installmentNumber)
    }

    @Test
    fun p1_3_staleLoanObjectDoesNotCreateDuplicateRecords() = runBlocking {
        val loan = insertSharedLoan(paidMonths = 3)
        val stale = currentLoan(loan.id) // نسخه‌ی کهنه‌ی UI

        // دوبار ضربه‌ی سریع با همان نسخه‌ی کهنه
        repository.toggleCurrentMonthPaid(stale, "1405/08/03")
        repository.toggleCurrentMonthPaid(stale, "1405/08/03")

        // دو toggle = برگشت به حالت اول؛ هیچ رکوردی نباید باقی بماند
        assertTrue(repository.allRecords.first().isEmpty())
        assertEquals(3, currentLoan(loan.id).paidMonths)
    }

    @Test
    fun p1_3_amountIsConsistentAcrossToggleAndIncrementPaths() = runBlocking {
        val loan = insertSharedLoan(paidMonths = 3)

        repository.toggleCurrentMonthPaid(currentLoan(loan.id), "1405/08/03") // قسط ۴
        repository.recordInstallmentPayment(currentLoan(loan.id), isIncrement = true, curShamsiFormatted = "1405/08/03") // قسط ۵

        val records = repository.allRecords.first().sortedBy { it.installmentNumber }
        assertEquals(listOf(4, 5), records.map { it.installmentNumber })
        // هر دو مسیر کل قسط ماهانه را ثبت می‌کنند، نه ترکیبی از «سهم» و «قسط کامل»
        assertTrue(records.all { it.amountPaid == 4_000_000L })
    }

    @Test
    fun p1_3_decrementRemovesRecordOfThatInstallment() = runBlocking {
        val loan = insertSharedLoan(paidMonths = 3)
        repository.recordInstallmentPayment(currentLoan(loan.id), isIncrement = true, curShamsiFormatted = "1405/08/03") // ۴
        repository.recordInstallmentPayment(currentLoan(loan.id), isIncrement = true, curShamsiFormatted = "1405/08/03") // ۵
        assertEquals(2, repository.allRecords.first().size)

        repository.recordInstallmentPayment(currentLoan(loan.id), isIncrement = false, curShamsiFormatted = "1405/08/03")

        val records = repository.allRecords.first()
        assertEquals(listOf(4), records.map { it.installmentNumber })
        assertEquals(4, currentLoan(loan.id).paidMonths)
    }

    @Test
    fun p1_3_completedLoanTickDoesNotCreateGhostRecord() = runBlocking {
        val loan = insertSharedLoan(paidMonths = 12) // کاملاً تسویه‌شده (clamp)
        repository.toggleCurrentMonthPaid(currentLoan(loan.id), "1405/08/03")
        assertTrue(repository.allRecords.first().isEmpty())
        assertEquals(12, currentLoan(loan.id).paidMonths)
    }

    // ---------------------------------------------------------------- P1-4

    private fun sampleSnapshotLoan() = Loan(
        id = 100L,
        title = "وام بازیابی‌شده",
        dueDay = 5,
        monthlyAmount = 1_000_000L,
        totalMonths = 6,
        paidMonths = 1,
        myShareAmount = 1_000_000L
    )

    @Test
    fun p1_4_restoreReplacesAllDataOnSuccess() = runBlocking {
        insertSharedLoan()

        repository.restoreDatabaseSnapshot(
            users = listOf(UserProfile(id = 1L, name = "من", isPrimary = true)),
            loans = listOf(sampleSnapshotLoan()),
            shares = emptyList(),
            records = listOf(
                PaymentRecord(
                    loanId = 100L,
                    loanTitle = "وام بازیابی‌شده",
                    installmentNumber = 1,
                    amountPaid = 1_000_000L,
                    paymentDateShamsi = "1405/07/05"
                )
            )
        )

        val loans = repository.allLoans.first()
        assertEquals(listOf("وام بازیابی‌شده"), loans.map { it.title })
        assertEquals(1, repository.allRecords.first().size)
        assertEquals(1, repository.allUsers.first().size)
    }

    @Test
    fun p1_4_failedRestoreRollsBackAndKeepsExistingData() = runBlocking {
        val existing = insertSharedLoan()
        repository.toggleCurrentMonthPaid(currentLoan(existing.id), "1405/08/03")
        val loansBefore = repository.allLoans.first()
        val sharesBefore = repository.allShares.first()
        val recordsBefore = repository.allRecords.first()

        // فهرستی که هنگام درج سهم‌ها استثنا می‌دهد = شبیه‌سازی شکست/کشته شدن وسط restore
        // (تا آن لحظه کاربران، وام‌ها و جدول‌های پاک‌شده باید توسط rollback برگردند)
        val explodingShares = object : AbstractList<DebtorShare>() {
            override val size: Int = 1
            override fun get(index: Int): DebtorShare = throw IllegalStateException("simulated crash mid-restore")
        }

        try {
            repository.restoreDatabaseSnapshot(
                users = listOf(UserProfile(id = 1L, name = "من", isPrimary = true)),
                loans = listOf(sampleSnapshotLoan()),
                shares = explodingShares,
                records = emptyList()
            )
            fail("restore باید خطا را به فراخواننده منتقل کند")
        } catch (e: IllegalStateException) {
            assertEquals("simulated crash mid-restore", e.message)
        }

        // هیچ تغییر نیمه‌کاره‌ای نباید باقی بماند: داده‌ی قبلی عیناً سر جایش است
        assertEquals(loansBefore, repository.allLoans.first())
        assertEquals(sharesBefore, repository.allShares.first())
        assertEquals(recordsBefore, repository.allRecords.first())
        assertTrue(repository.allLoans.first().none { it.id == 100L })
    }

    // ---------------------------------------------------------------- P3-10

    @Test
    fun p3_10_demoDataIsSeededOnlyOnceAndNeverRecreatedAfterDeletion() = runBlocking {
        // اولین اجرا (نصب تمیز): داده‌ی راهنما ساخته می‌شود
        repository.checkAndSeedInitialData()
        val seededLoans = repository.allLoans.first()
        assertEquals(3, seededLoans.size)
        assertEquals(2, repository.allUsers.first().size)

        // کاربر وام‌های دمو را پاک می‌کند (تکی یا «پاکسازی کامل وام‌ها»)
        seededLoans.forEach { repository.deleteLoan(it.id) }
        assertTrue(repository.allLoans.first().isEmpty())

        // اجرای بعدی اپ: seed دوباره نباید اتفاق بیفتد (باگ گزارش‌شده)
        repository.checkAndSeedInitialData()
        assertTrue(
            "پس از حذف توسط کاربر، داده‌ی دمو نباید دوباره ساخته شود",
            repository.allLoans.first().isEmpty()
        )
    }

    @Test
    fun p3_10_upgraderWithExistingDataGetsNoDemoDataAndFlagIsClosed() = runBlocking {
        // کاربر قدیمی که از قبل داده دارد (پرچم هنوز set نشده): وام دمو نباید اضافه شود
        insertSharedLoan()
        repository.checkAndSeedInitialData()
        assertEquals(1, repository.allLoans.first().size)

        // و پس از حذف داده‌هایش هم seed دوباره نباید بیاید
        repository.allLoans.first().forEach { repository.deleteLoan(it.id) }
        repository.checkAndSeedInitialData()
        assertTrue(repository.allLoans.first().isEmpty())
    }

    // ---------------------------------------------------------------- NB-14

    @Test
    fun nb_14_clearAllLoansLeavesNoOrphanPaymentRecords() = runBlocking {
        // وام با تاریخچهٔ پرداخت واقعی
        val loan = insertSharedLoan(paidMonths = 3)
        repository.toggleCurrentMonthPaid(currentLoan(loan.id), "1405/08/03")
        assertTrue(repository.allRecords.first().isNotEmpty())

        repository.clearAllLoansAndReset()

        assertTrue(repository.allLoans.first().isEmpty())
        assertTrue(repository.allShares.first().isEmpty())
        assertTrue(
            "«پاکسازی تمام وام‌ها» نباید رکورد تاریخچهٔ یتیم به جا بگذارد",
            repository.allRecords.first().isEmpty()
        )
    }

    // ---------------------------------------------------------------- F6 (شناسهٔ یکتای عمومی)

    @Test
    fun f6_backfillAssignsUniquePublicIdsAndLinksOwnersIdempotently() = runBlocking {
        // دادهٔ قدیمیِ بدون شناسه — مثل رکوردهای مهاجرت‌خورده از نسخهٔ ۴ دیتابیس
        val loanId = db.loanDao().insertLoan(
            Loan(title = "وام قدیمی", dueDay = 5, monthlyAmount = 1_000_000L, totalMonths = 12)
        )
        db.debtorShareDao().insertShare(
            DebtorShare(loanId = loanId, loanTitle = "وام قدیمی", personName = "علی", shareAmount = 500_000L)
        )
        db.userProfileDao().insertUser(UserProfile(name = "علی"))

        repository.backfillMissingPublicIds()

        val users = db.userProfileDao().getAllUsersOnce()
        val loans = repository.allLoans.first()
        val shares = repository.allShares.first()
        // همهٔ رکوردها شناسهٔ معتبر دارند
        assertTrue(users.all { PublicId.isValidUserId(it.publicId) })
        assertTrue(loans.all { PublicId.isValidLoanId(it.publicId) })
        assertTrue(shares.all { PublicId.isValidShareId(it.publicId) })
        // شناسه‌ها یکتا هستند
        assertEquals(users.size, users.map { it.publicId }.toSet().size)
        assertEquals(loans.size, loans.map { it.publicId }.toSet().size)
        assertEquals(shares.size, shares.map { it.publicId }.toSet().size)
        // پیوند سهم↔کاربر: سهمِ «علی» مالکش publicId کاربرِ «علی» است
        val aliUser = users.first { it.name == "علی" }
        val aliShare = shares.first { it.personName == "علی" }
        assertEquals(aliUser.publicId, aliShare.ownerPublicId)

        // idempotent — اجرای دوباره هیچ شناسه‌ای را عوض نمی‌کند
        repository.backfillMissingPublicIds()
        val users2 = db.userProfileDao().getAllUsersOnce()
        val loans2 = repository.allLoans.first()
        assertEquals(users.map { it.publicId }.toSet(), users2.map { it.publicId }.toSet())
        assertEquals(loans.map { it.publicId }.toSet(), loans2.map { it.publicId }.toSet())
    }

    @Test
    fun f6_newSavesGetPublicIdsAutomaticallyAndEditPreservesThem() = runBlocking {
        // ذخیرهٔ جدید از مسیر اصلی اپ: وام و سهم خودبه‌خود شناسه می‌گیرند
        val loan = insertSharedLoan(paidMonths = 2)
        val savedLoan = repository.allLoans.first().first { it.id == loan.id }
        assertTrue(PublicId.isValidLoanId(savedLoan.publicId))
        val savedShare = repository.allShares.first().first()
        assertTrue(PublicId.isValidShareId(savedShare.publicId))

        // ویرایش وام (حذف/درج دوبارهٔ سهم‌ها) نباید شناسه‌ها را عوض کند
        repository.saveLoanWithShares(
            savedLoan.copy(title = "وام ویرایش‌شده"),
            listOf(savedShare.copy(shareAmount = 600_000L))
        )
        val editedLoan = repository.allLoans.first().first { it.id == loan.id }
        val editedShare = repository.allShares.first().first()
        assertEquals("هویت وام نباید با edit عوض شود", savedLoan.publicId, editedLoan.publicId)
        assertEquals("هویت سهم نباید با edit عوض شود", savedShare.publicId, editedShare.publicId)
    }
}

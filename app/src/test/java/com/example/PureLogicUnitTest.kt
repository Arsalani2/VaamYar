package com.example

import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.LoanInstallment
import com.example.model.LoanShareCalculator
import com.example.model.UserProfile
import com.example.util.BackupHelper
import com.example.util.BackupPreferences
import com.example.util.InputLimits
import com.example.util.NumberFormatter
import com.example.util.PublicId
import com.example.util.ShamsiDate
import com.example.util.ShamsiDateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تست‌های واحد JUnit برای منطق‌های خالص و مستقل (bug.md/P4-2 و NB-5 و P3-2 و NB-4 و NB-33).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PureLogicUnitTest {

    // ---------------------------------------------------------------- NumberFormatter (NB-5, P3-4)

    @Test
    fun parseAmountStrict_validFormats() {
        assertEquals(1500L, NumberFormatter.parseAmountStrict("1500"))
        assertEquals(1500L, NumberFormatter.parseAmountStrict("۱۵۰۰"))
        assertEquals(1500L, NumberFormatter.parseAmountStrict("1,500"))
        assertEquals(1500L, NumberFormatter.parseAmountStrict("۱،۵۰۰"))
        assertEquals(1500L, NumberFormatter.parseAmountStrict("۱٬۵۰۰"))
        assertEquals(1500L, NumberFormatter.parseAmountStrict("1500 تومان"))
        assertEquals(1500L, NumberFormatter.parseAmountStrict("1500 ت"))
        assertEquals(0L, NumberFormatter.parseAmountStrict(""))
        assertEquals(0L, NumberFormatter.parseAmountStrict("   "))
        assertEquals(0L, NumberFormatter.parseAmountStrict("تومان"))
    }

    @Test
    fun parseAmountStrict_rejectsInvalidDecimalSeparators() {
        // جداکننده‌های ممیز/اعشار نباید بی‌صدا به ارقام چسبانده شوند
        assertNull(NumberFormatter.parseAmountStrict("1.5"))
        assertNull(NumberFormatter.parseAmountStrict("۱.۵"))
        assertNull(NumberFormatter.parseAmountStrict("2/500"))
        assertNull(NumberFormatter.parseAmountStrict("۲/۵۰۰"))
        assertNull(NumberFormatter.parseAmountStrict("۲٫۵۰۰"))
        assertNull(NumberFormatter.parseAmountStrict("abc"))
    }

    @Test
    fun parseIntStrict_handlesBoundariesAndOverflow() {
        assertEquals(12, NumberFormatter.parseIntStrict("12"))
        assertEquals(120, NumberFormatter.parseIntStrict("۱۲۰"))
        assertEquals(Int.MAX_VALUE, NumberFormatter.parseIntStrict(Int.MAX_VALUE.toString()))
        // سرریز فراتر از Int.MAX_VALUE باید null برگرداند نه عدد منفی
        assertNull(NumberFormatter.parseIntStrict("9999999999"))
        assertNull(NumberFormatter.parseIntStrict("-5"))
    }

    // ---------------------------------------------------------------- LoanShareCalculator (P3-2)

    @Test
    fun computeMyShare_partnerlessLoanTakesFullAmount() {
        val loan = Loan(
            id = 1L,
            title = "وام انفرادی",
            dueDay = 5,
            monthlyAmount = 2_000_000L,
            totalMonths = 12,
            paidMonths = 0,
            hasPartners = false
        )
        val myShare = LoanShareCalculator.computeMyShare(loan, emptyList())
        assertEquals(2_000_000L, myShare)
    }

    @Test
    fun computeMyShare_sharedLoanDeductsPartnersSum() {
        val loan = Loan(
            id = 2L,
            title = "وام شراکتی",
            dueDay = 10,
            monthlyAmount = 5_000_000L,
            totalMonths = 12,
            paidMonths = 0,
            hasPartners = true
        )
        val shares = listOf(
            DebtorShare(loanId = 2L, loanTitle = "وام شراکتی", personName = "علی", shareAmount = 2_000_000L),
            DebtorShare(loanId = 2L, loanTitle = "وام شراکتی", personName = "رضا", shareAmount = 1_500_000L)
        )
        val myShare = LoanShareCalculator.computeMyShare(loan, shares)
        assertEquals(1_500_000L, myShare)
    }

    @Test
    fun computeMyShare_sharedLoanFullCoverByPartnersReturnsZero() {
        val loan = Loan(
            id = 3L,
            title = "وام تماماً واگذار شده",
            dueDay = 15,
            monthlyAmount = 3_000_000L,
            totalMonths = 10,
            paidMonths = 0,
            hasPartners = true
        )
        val shares = listOf(
            DebtorShare(loanId = 3L, loanTitle = "وام تماماً واگذار شده", personName = "مهدی", shareAmount = 3_000_000L)
        )
        val myShare = LoanShareCalculator.computeMyShare(loan, shares)
        assertEquals(0L, myShare)
    }

    // ---------------------------------------------------------------- ShamsiDateHelper (NB-12, NB-25)

    @Test
    fun calculateEndDate_matchesSeedLoanSchedule() {
        // فیکس bug.md/NB-25: پایان وام‌های seed باید دقیقاً خروجی calculateEndDate باشد
        // (قسط اول = ماه شروع؛ قسط آخر = ماه شروع + مدت − ۱).
        // loan1: شروع 1403/4، ۲۴ ماه → قسط آخر 1405/03 (نه 1405/04)
        assertEquals(ShamsiDate(1405, 3, 1), ShamsiDateHelper.calculateEndDate(1403, 4, 24))
        // loan2: شروع 1402/11، ۳۶ ماه → قسط آخر 1405/10 (نه 1405/11)
        assertEquals(ShamsiDate(1405, 10, 1), ShamsiDateHelper.calculateEndDate(1402, 11, 36))
        // loan3: شروع 1403/8، ۱۲ ماه → قسط آخر 1404/07 (نه 1404/08)
        assertEquals(ShamsiDate(1404, 7, 1), ShamsiDateHelper.calculateEndDate(1403, 8, 12))
        // مرزها: وام یک‌قسطه و وامِ دقیقاً ۱۲ماهه
        assertEquals(ShamsiDate(1405, 1, 1), ShamsiDateHelper.calculateEndDate(1405, 1, 1))
        assertEquals(ShamsiDate(1405, 12, 1), ShamsiDateHelper.calculateEndDate(1405, 1, 12))
        // عبور از سال: شروع آذر ۱۲۰ ماه → قسط آخر اسفند
        assertEquals(ShamsiDate(1415, 11, 1), ShamsiDateHelper.calculateEndDate(1405, 12, 120))
    }

    @Test
    fun daysUntilDue_calculatesAccurately() {
        // همان روز
        val today = ShamsiDate(1405, 8, 10)
        assertEquals(0, ShamsiDateHelper.daysUntilDue(10, today))

        // ۳ روز بعد در همان ماه
        assertEquals(3, ShamsiDateHelper.daysUntilDue(13, today))

        // سررسید سپری‌شده در همین ماه (محاسبه تا سررسید ماه بعد: ۲۰ روز مانده در آبان + ۵ روز در آذر)
        assertEquals(25, ShamsiDateHelper.daysUntilDue(5, today))
    }

    @Test
    fun nb16_daysUntilDueThisMonth_negativeMeansOverdue() {
        // فیکس bug.md/NB-16: موعدِ همین ماه سپری شده → مقدار منفی = X روز عقب‌افتادگی
        val today = ShamsiDate(1405, 8, 10)

        // سررسید روز ۵، امروز ۱۰ آبان → ۵- روز از موعد گذشته
        assertEquals(-5, ShamsiDateHelper.daysUntilDueThisMonth(5, today))

        // همان روز → صفر
        assertEquals(0, ShamsiDateHelper.daysUntilDueThisMonth(10, today))

        // هنوز نرسیده → مثبت
        assertEquals(3, ShamsiDateHelper.daysUntilDueThisMonth(13, today))

        // clamp روز سررسید به طول ماه: آبان ۳۰روزه است؛ سررسید ۳۱ → ۳۰
        assertEquals(20, ShamsiDateHelper.daysUntilDueThisMonth(31, today))

        // اسفند ۱۴۰۵ (سال عادی، ۲۹ روز): سررسید ۳۰/۳۱ → ۲۹
        val esfand = ShamsiDate(1405, 12, 20)
        assertEquals(9, ShamsiDateHelper.daysUntilDueThisMonth(31, esfand))
    }

    @Test
    fun getDaysInShamsiMonth_matchesStandardCalendar() {
        // شش ماه اول ۳۱ روزه
        for (m in 1..6) {
            assertEquals(31, ShamsiDateHelper.getDaysInShamsiMonth(1405, m))
        }
        // پنج ماه دوم ۳۰ روزه
        for (m in 7..11) {
            assertEquals(30, ShamsiDateHelper.getDaysInShamsiMonth(1405, m))
        }
    }

    // ---------------------------------------------------------------- InputLimits (NB-33)

    @Test
    fun validateDurationMonths_validAndInvalidValues() {
        // مقادیر معتبر — نباید خطا بدهند (شامل ارقام فارسی)
        assertNull(InputLimits.validateDurationMonths("12"))
        assertNull(InputLimits.validateDurationMonths("1"))
        assertNull(InputLimits.validateDurationMonths("120"))
        assertNull(InputLimits.validateDurationMonths("۲۴"))

        // خارج از بازه — خطای صریح به‌جای clamp بی‌صدا (اینوارینت #۶: تایپ ۲۰۰ ≠ ذخیرهٔ ۱۲۰)
        assertNotNull(InputLimits.validateDurationMonths("121"))
        assertNotNull(InputLimits.validateDurationMonths("200"))
        assertNotNull(InputLimits.validateDurationMonths("0"))
        assertNotNull(InputLimits.validateDurationMonths("00"))

        // خالی/غیرعددی — باید خطای صریح بدهد نه پذیرشِ بی‌صدا با مقدار پیش‌فرض
        assertNotNull(InputLimits.validateDurationMonths(""))
        assertNotNull(InputLimits.validateDurationMonths("abc"))
    }

    @Test
    fun validateTitle_lengthAndBlank() {
        // معتبر
        assertNull(InputLimits.validateTitle("وام مسکن"))
        assertNull(InputLimits.validateTitle("  بانک رسالت  "))

        // خالی/فاصله — خطا
        assertNotNull(InputLimits.validateTitle(""))
        assertNotNull(InputLimits.validateTitle("   "))

        // بلندتر از سقف — خطای صریح (نه برش بی‌صدا)
        val longTitle = "عنوان وام بسیار طولانی".repeat(10)
        assertTrue(longTitle.trim().length > InputLimits.MAX_TITLE_LENGTH)
        assertNotNull(InputLimits.validateTitle(longTitle))
    }

    // ---------------------------------------------------------------- PublicId (F6)

    @Test
    fun publicId_formatPrefixLengthAndUniqueness() {
        val loans = (1..500).map { PublicId.newLoanId() }
        val users = (1..500).map { PublicId.newUserId() }
        val shares = (1..500).map { PublicId.newShareId() }

        // قالب: وام L- + ۷ نویسه؛ کاربر U- + ۶؛ سهم S- + ۶
        loans.forEach { assertTrue(PublicId.isValidLoanId(it)) }
        users.forEach { assertTrue(PublicId.isValidUserId(it)) }
        shares.forEach { assertTrue(PublicId.isValidShareId(it)) }
        assertTrue(loans.all { it.startsWith("L-") && it.length == 9 })
        assertTrue(users.all { it.startsWith("U-") && it.length == 8 })
        assertTrue(shares.all { it.startsWith("S-") && it.length == 8 })

        // یکتایی در حجم زیاد (فضای ۶۲^۶/۶۲^۷)
        assertEquals(500, loans.toSet().size)
        assertEquals(500, users.toSet().size)
        assertEquals(500, shares.toSet().size)

        // شناسهٔ خاموشکل/جعلی معتبر نیست (در restore شناسهٔ تازه می‌گیرد)
        assertFalse(PublicId.isValidLoanId(""))
        assertFalse(PublicId.isValidLoanId("L-abc"))
        assertFalse(PublicId.isValidLoanId("L-abcdefg"))
        assertFalse(PublicId.isValidLoanId("X-abcdefg"))
        assertFalse(PublicId.isValidUserId("U-abcdefg"))
        assertFalse(PublicId.isValidShareId("S-abcde"))
    }

    // ---------------------------------------------------------------- BackupHelper Validation (NB-4)

    @Test
    fun backupValidation_clampsValuesAndFiltersOrphans() {
        val json = BackupHelper.createBackupJson(
            users = listOf(
                UserProfile(id = 1L, name = "علی", isPrimary = false, publicId = "U-aaa111") // بدون کاربر مبنا
            ),
            loans = listOf(
                Loan(
                    id = 10L,
                    title = "وام تستی",
                    dueDay = 1,
                    monthlyAmount = 1_000_000L,
                    totalMonths = 200, // باید به ۱۲۰ clamp شود
                    paidMonths = 250,  // باید به ۱۲۰ clamp شود
                    myShareAmount = 1_000_000L,
                    publicId = "L-aaa1111"
                )
            ),
            shares = listOf(
                DebtorShare(id = 1L, loanId = 10L, loanTitle = "وام تستی", personName = "علی", shareAmount = 500_000L, publicId = "S-aaa111", ownerPublicId = "U-aaa111"),
                DebtorShare(id = 2L, loanId = 999L, loanTitle = "وام ناموجود", personName = "سهم یتیم", shareAmount = 500_000L) // وام ۹۹۹ وجود ندارد
            ),
            records = emptyList(),
            preferences = BackupPreferences(),
            appVersion = "1.0.4"
        )

        val result = BackupHelper.parseAndValidateBackup(json)
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)

        // ۱) بررسی ارتقای کاربر به isPrimary
        assertTrue(data!!.users.any { it.isPrimary })

        // ۲) بررسی clamp مدت و اقساط
        val loan = data.loans.first()
        assertEquals(120, loan.totalMonths)
        assertEquals(120, loan.paidMonths)

        // ۳) بررسی حذف سهم یتیم
        assertEquals(1, data.shares.size)
        assertEquals(10L, data.shares.first().loanId)

        // ۴) فیکس bug.md/F6: شناسه‌های عمومی معتبر باید بدون تغییر حفظ شوند
        assertEquals("U-aaa111", data.users.first().publicId)
        assertEquals("L-aaa1111", data.loans.first().publicId)
        assertEquals("S-aaa111", data.shares.first().publicId)
        assertEquals("U-aaa111", data.shares.first().ownerPublicId)
    }

    @Test
    fun f1_nb31_backupCarriesRealInstallmentsAndSanitizesThem() {
        val json = BackupHelper.createBackupJson(
            users = listOf(UserProfile(id = 1L, name = "علی", isPrimary = true, publicId = "U-aaa111")),
            loans = listOf(
                Loan(
                    id = 10L, title = "وام تستی", dueDay = 10,
                    monthlyAmount = 1_000_000L, totalMonths = 12, paidMonths = 2,
                    startYear = 1405, startMonth = 4,
                    myShareAmount = 1_000_000L, publicId = "L-aaa1111"
                )
            ),
            shares = emptyList(),
            records = emptyList(),
            installments = listOf(
                // معتبر: قسط ۱ و ۳ وام ۱۰ (قسط ۳ پرداخت‌شده وسط عقب‌ماندگی — سناریوی «فقط این ماه»)
                LoanInstallment(loanId = 10L, installmentNumber = 1, year = 1405, month = 4, amount = 1_000_000L, paidDateShamsi = "1405/04/05"),
                LoanInstallment(loanId = 10L, installmentNumber = 3, year = 1405, month = 6, amount = 1_000_000L, paidDateShamsi = "1405/06/05"),
                // یتیم: وام ۹۹ وجود ندارد
                LoanInstallment(loanId = 999L, installmentNumber = 1, year = 1405, month = 4, amount = 1_000_000L),
                // خارج از بازه: قسط ۹۹ وامِ ۱۲ماهه
                LoanInstallment(loanId = 10L, installmentNumber = 99, year = 1406, month = 4, amount = 1_000_000L)
            ),
            preferences = BackupPreferences(),
            appVersion = "1.0.4"
        )

        val result = BackupHelper.parseAndValidateBackup(json)
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!

        // فقط قسط‌های متعلق به وام‌های همین بکاپ و در بازهٔ ۱..totalMonths می‌مانند
        assertEquals(listOf(1 to 4, 3 to 6), data.installments.map { it.installmentNumber to it.month })
        assertEquals(1_000_000L, data.installments.first().amount)
        assertEquals("1405/04/05", data.installments.first().paidDateShamsi)
    }

    @Test
    fun f1_nb31_legacyBackupWithoutInstallmentsArrayParsesToEmptyList() {
        // بکاپ قدیمی (قبل از F1+NB-31) آرایهٔ installments را ندارد → خالی برمی‌گردد
        // و restore به بازسازی ترتیبی از paidMonths برمی‌گردد
        val usersArray = org.json.JSONArray()
            .put(
                org.json.JSONObject()
                    .put("id", 1L)
                    .put("name", "علی")
                    .put("isPrimary", true)
                    .put("publicId", "U-aaa111")
            )
        val loansArray = org.json.JSONArray()
            .put(
                org.json.JSONObject()
                    .put("id", 10L)
                    .put("title", "وام قدیمی")
                    .put("dueDay", 10)
                    .put("monthlyAmount", 1_000_000L)
                    .put("totalMonths", 12)
                    .put("paidMonths", 3)
                    .put("startYear", 1405)
                    .put("startMonth", 4)
                    .put("publicId", "L-aaa1111")
            )
        // بکاپ قدیمی: فقط users/loans/shares/records/preferences — بدون installments
        val payload = org.json.JSONObject()
            .put("users", usersArray)
            .put("loans", loansArray)
            .put("shares", org.json.JSONArray())
            .put("records", org.json.JSONArray())
        val root = org.json.JSONObject()
            .put("app", "VaamYar")
            .put("appIdentifier", "com.aistudio.vamyar")
            .put("schemaVersion", 1)
            .put("payload", payload)

        val result = BackupHelper.parseAndValidateBackup(root.toString())
        assertTrue(result.isSuccess)
        val data = result.getOrNull()!!
        assertTrue(data.installments.isEmpty())
        assertEquals(3, data.loans.first().paidMonths)
    }

    // ---------------------------------------------------------------- F1+NB-31 (نگاشت قسط ↔ ماه شمسی)

    @Test
    fun f1_installmentMonthMappingAndRoundtrip() {
        // قسط ۱ = ماه شروع؛ قسط ۹ِ وامِ تیر ۱۴۰۳ = اسفند ۱۴۰۳؛ قسط ۱۰ = فروردین ۱۴۰۴
        assertEquals(ShamsiDate(1403, 4, 1), ShamsiDateHelper.installmentMonth(1403, 4, 1))
        assertEquals(ShamsiDate(1403, 12, 1), ShamsiDateHelper.installmentMonth(1403, 4, 9))
        assertEquals(ShamsiDate(1404, 1, 1), ShamsiDateHelper.installmentMonth(1403, 4, 10))
        // عبور از مرز سال در دی/بهمن/اسفند
        assertEquals(ShamsiDate(1404, 1, 1), ShamsiDateHelper.installmentMonth(1403, 12, 2))
        // ورودی نامعتبر (۰) به قسط ۱ تبدیل می‌شود، نه crash
        assertEquals(ShamsiDate(1405, 4, 1), ShamsiDateHelper.installmentMonth(1405, 4, 0))

        // معکوس: شمارهٔ قسط از سال/ماه شمسی
        assertEquals(29, ShamsiDateHelper.installmentNumberForMonth(1403, 4, 1405, 8))
        assertEquals(1, ShamsiDateHelper.installmentNumberForMonth(1403, 4, 1403, 4))
        // قبل از شروع وام → null
        assertNull(ShamsiDateHelper.installmentNumberForMonth(1403, 4, 1403, 3))

        // رفت‌وبرگشت: هر شمارهٔ قسط به ماه و برعکس
        for (k in 1..36) {
            val m = ShamsiDateHelper.installmentMonth(1403, 11, k)
            assertEquals(k, ShamsiDateHelper.installmentNumberForMonth(1403, 11, m.year, m.month))
        }

        // ماه‌های «سررسیدرسیده» با روزِ سررسید: سناریوی NB-31 (شروع تیر ۱۴۰۵، روز سررسید ۱۰)
        // در ۱۵ آبان: اقساط ۱..۵ (تیر..آبان) سررسیدشان گذشته/رسیده است
        assertEquals(
            5,
            ShamsiDateHelper.calculateElapsedInstallments(1405, 4, 10, 1405, 8, 15, 12)
        )
        // در ۳ آبان (قبل از روز سررسید): فقط تیر..مهر (۴ قسط)
        assertEquals(
            4,
            ShamsiDateHelper.calculateElapsedInstallments(1405, 4, 10, 1405, 8, 3, 12)
        )
    }
}

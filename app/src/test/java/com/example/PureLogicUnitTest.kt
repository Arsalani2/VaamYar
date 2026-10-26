package com.example

import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.LoanShareCalculator
import com.example.model.UserProfile
import com.example.util.BackupHelper
import com.example.util.BackupPreferences
import com.example.util.NumberFormatter
import com.example.util.ShamsiDate
import com.example.util.ShamsiDateHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * تست‌های واحد JUnit برای منطق‌های خالص و مستقل (bug.md/P4-2 و NB-5 و P3-2 و NB-4).
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

    // ---------------------------------------------------------------- ShamsiDateHelper (NB-12)

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

    // ---------------------------------------------------------------- BackupHelper Validation (NB-4)

    @Test
    fun backupValidation_clampsValuesAndFiltersOrphans() {
        val json = BackupHelper.createBackupJson(
            users = listOf(
                UserProfile(id = 1L, name = "علی", isPrimary = false) // بدون کاربر مبنا
            ),
            loans = listOf(
                Loan(
                    id = 10L,
                    title = "وام تستی",
                    dueDay = 1,
                    monthlyAmount = 1_000_000L,
                    totalMonths = 200, // باید به ۱۲۰ clamp شود
                    paidMonths = 250,  // باید به ۱۲۰ clamp شود
                    myShareAmount = 1_000_000L
                )
            ),
            shares = listOf(
                DebtorShare(id = 1L, loanId = 10L, loanTitle = "وام تستی", personName = "علی", shareAmount = 500_000L),
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
    }
}

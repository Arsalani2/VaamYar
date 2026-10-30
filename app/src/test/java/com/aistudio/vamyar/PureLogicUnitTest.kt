package com.aistudio.vamyar

import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
import com.aistudio.vamyar.model.LoanShareCalculator
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.util.BackupHelper
import com.aistudio.vamyar.util.BackupIcon
import com.aistudio.vamyar.util.BackupPreferences
import com.aistudio.vamyar.util.InputLimits
import com.aistudio.vamyar.util.LoanIconHelper
import com.aistudio.vamyar.util.LiquidityLoanInput
import com.aistudio.vamyar.util.LiquidityPaidRecord
import com.aistudio.vamyar.util.NumberFormatter
import com.aistudio.vamyar.util.PersianNameNormalizer
import com.aistudio.vamyar.util.PublicId
import com.aistudio.vamyar.util.ShamsiDate
import com.aistudio.vamyar.util.ShamsiDateHelper
import com.aistudio.vamyar.util.computeLiquidityForecast
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

    /** فیکس bug.md/NB-42: قالب ساعت (ساعت اعلان روزانهٔ تنظیمات) از تنظیم ارقام کاربر پیروی می‌کند. */
    @Test
    fun nb42_formatClock_respectsDigitsSetting() {
        assertEquals("۱۰:۰۵", NumberFormatter.formatClock(10, 5, usePersian = true))
        assertEquals("10:05", NumberFormatter.formatClock(10, 5, usePersian = false))
        assertEquals("۰۰:۰۰", NumberFormatter.formatClock(0, 0, usePersian = true))
        assertEquals("23:59", NumberFormatter.formatClock(23, 59, usePersian = false))
    }

    /** فیکس bug.md/NB-42: مبلغ قسط در اعلان/یادآوری هم با هر دو تنظیم ارقام درست قالب می‌شود. */
    @Test
    fun nb42_formatPrice_respectsDigitsSetting() {
        assertEquals("۲,۵۰۰ تومان", NumberFormatter.formatPrice(2_500L, usePersian = true, compactThousands = false))
        assertEquals("2,500 تومان", NumberFormatter.formatPrice(2_500L, usePersian = false, compactThousands = false))
        assertEquals("۲,۰۰۰ هزار ت", NumberFormatter.formatPrice(2_000_000L, usePersian = true, compactThousands = true))
        assertEquals("2,000 هزار ت", NumberFormatter.formatPrice(2_000_000L, usePersian = false, compactThousands = true))
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
    fun nb17_effectiveDueDay_clampsDueDayToMonthLength() {
        // فیکس bug.md/NB-17: سررسید ۳۱ در ماه‌های ۳۰روزه → ۳۰
        assertEquals(30, ShamsiDateHelper.effectiveDueDay(1405, 7, 31))  // مهر
        assertEquals(30, ShamsiDateHelper.effectiveDueDay(1405, 11, 31)) // بهمن

        // اسفندِ سال عادی (۱۴۰۵، ۲۹ روز): سررسید ۳۰/۳۱ → ۲۹
        assertEquals(29, ShamsiDateHelper.effectiveDueDay(1405, 12, 31))
        assertEquals(29, ShamsiDateHelper.effectiveDueDay(1405, 12, 30))

        // ماه ۳۱روزه: سررسید ۳۱ دست‌نخورده
        assertEquals(31, ShamsiDateHelper.effectiveDueDay(1405, 1, 31)) // فروردین
        assertEquals(31, ShamsiDateHelper.effectiveDueDay(1405, 6, 31)) // شهریور

        // سررسیدهای عادی تغییری نمی‌کنند
        assertEquals(15, ShamsiDateHelper.effectiveDueDay(1405, 8, 15))
        assertEquals(1, ShamsiDateHelper.effectiveDueDay(1405, 8, 1))
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

    // ---------------------------------------------------------------- NB-36 (یکتایی عنوان وام)

    @Test
    fun nb36_validateUniqueTitle_matchesCanonicalAndExcludesSelf() {
        val existing = listOf("بانک رسالت", "وام مسکن", "خرید خودرو")

        // تکراری — حتی با فاصله‌های زائد (کلید استاندارد نام، الگوی NB-20)
        assertNotNull(InputLimits.validateUniqueTitle("بانک رسالت", existing))
        assertNotNull(InputLimits.validateUniqueTitle(" بانک  رسالت ", existing))
        assertNotNull(InputLimits.validateUniqueTitle("وام مسکن", existing))

        // عنوان آزاد — معتبر؛ خالی مالتِ validateTitle است و اینجا خطا نمی‌دهد
        assertNull(InputLimits.validateUniqueTitle("وام طلایی", existing))
        assertNull(InputLimits.validateUniqueTitle("", existing))

        // هنگام ویرایش، عنوانِ خودِ وام تکراری نیست (selfKey کنار گذاشته می‌شود)
        val selfKey = PersianNameNormalizer.matchKey("بانک رسالت")
        assertNull(InputLimits.validateUniqueTitle("بانک رسالت", existing, selfKey = selfKey))
        // اما هم‌نامی با وامِ دیگر همچنان تکراری است
        assertNotNull(InputLimits.validateUniqueTitle("وام مسکن", existing, selfKey = selfKey))
    }

    // ---------------------------------------------------------------- NB-35 (شریک = کاربر تعریف‌شده)

    @Test
    fun nb35_isDefinedPartner_matchesCanonical() {
        val defined = listOf("علی", "سارا رضایی", "محمود")
        // عضو تعریف‌شده — حتی با فاصله‌های زائد و هم‌نویسهٔ عربی (ي/ك)
        assertTrue(InputLimits.isDefinedPartner("علی", defined))
        assertTrue(InputLimits.isDefinedPartner(" علي ", defined))
        assertTrue(InputLimits.isDefinedPartner("سارا  رضایی", defined))
        // نام الکی/غریبه → رد (شریک فقط از کاربران تعریف‌شده)
        assertFalse(InputLimits.isDefinedPartner("غریبه", defined))
        assertFalse(InputLimits.isDefinedPartner("", defined))
        // فهرست خالی هم هیچ شریکی را قبول نمی‌کند
        assertFalse(InputLimits.isDefinedPartner("علی", emptyList()))
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
        assertFalse(PublicId.isValidLoanId("L-abcde1g"))
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

    // ---------------------------------------------------------------- NB-18 (آیکون سفارشی در بکاپ)

    @Test
    fun nb18_backupEmbedsIconAndSanitizesPath() {
        val loanWithIcon = Loan(
            id = 10L, title = "وام با آیکون", dueDay = 10,
            monthlyAmount = 1_000_000L, totalMonths = 12, paidMonths = 1,
            startYear = 1405, startMonth = 4,
            myShareAmount = 1_000_000L, publicId = "L-aaa1111",
            // مسیر absolute دستگاه — نباید عیناً در JSON بیاید (نشت مسیر فایلسیستم)
            customIconPath = "/data/user/0/com.aistudio.vamyar/files/loan_icons/loan_icon_123.png"
        )
        val loanMissingIcon = Loan(
            id = 11L, title = "وام بدون فایل آیکون", dueDay = 5,
            monthlyAmount = 500_000L, totalMonths = 6, paidMonths = 1,
            startYear = 1405, startMonth = 4,
            myShareAmount = 500_000L, publicId = "L-bbb2222",
            customIconPath = "/data/user/0/com.aistudio.vamyar/files/loan_icons/loan_icon_missing.png"
        )

        // فقط وام ۱۰ فایلش «روی دیسک هست» و embed می‌شود؛ وام ۱۱ چون در icons نیست، path حذف می‌شود
        val json = BackupHelper.createBackupJson(
            users = listOf(UserProfile(id = 1L, name = "علی", isPrimary = true, publicId = "U-aaa111")),
            loans = listOf(loanWithIcon, loanMissingIcon),
            shares = emptyList(),
            records = emptyList(),
            preferences = BackupPreferences(),
            appVersion = "1.0.4",
            icons = listOf(
                BackupIcon(loanId = 10L, fileName = "loan_icon_123.png", dataBase64 = "AQIDBA==")
            )
        )

        // مسیر absolute نباید در خروجی باشد
        assertFalse(json.contains("/data/user/0/"))

        val data = BackupHelper.parseAndValidateBackup(json).getOrNull()!!
        // وام ۱۰: path فقط «نام فایل» + دادهٔ آیکون embed شده
        assertEquals("loan_icon_123.png", data.loans.first { it.id == 10L }.customIconPath)
        assertEquals(1, data.icons.size)
        assertEquals(10L, data.icons.first().loanId)
        assertEquals("AQIDBA==", data.icons.first().dataBase64)
        // وام ۱۱: فایل روی دیسک نبود → فیلد path حذف شد (restore به fallback برداری می‌رود)
        assertNull(data.loans.first { it.id == 11L }.customIconPath)
    }

    @Test
    fun nb18_saveCustomIconBytesRoundTripsFileContent() {
        // چرخهٔ restore: بایت‌های base64 بکاپ → فایل واقعی روی دیسکِ همین دستگاه
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val iconBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)

        val savedPath = LoanIconHelper.saveCustomIconBytes(context, iconBytes)
        assertNotNull(savedPath)

        val savedFile = java.io.File(savedPath!!)
        assertTrue(savedFile.exists())
        assertTrue(savedFile.readBytes().contentEquals(iconBytes))
        assertTrue(savedFile.parentFile?.name == "loan_icons")
        // پاک‌سازی فایل تست
        LoanIconHelper.deleteCustomIconFile(savedPath)
        assertFalse(savedFile.exists())
    }

    // ---------------------------------------------------------------- NB-38 (نام‌گذاری برخوردناپذیر آیکون)

    @Test
    fun nb38_fiftyIconsBackToBackGetDistinctFiles() {
        // سناریوی کاربر: حلقهٔ ایمپورت بکاپ چند آیکون را پشت‌سرهم در همان میلی‌ثانیه
        // می‌نویسد — پیش از فیکس، دو آیکونِ همان میلی‌ثانیه نام فایل یکسان می‌گرفتند
        // و فایل دومی روی اولی overwrite می‌شد (همه یک عکس / عکس‌های قاطی).
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val savedPaths = (1..50).map { index ->
            val iconBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, index.toByte())
            val path = LoanIconHelper.saveCustomIconBytes(context, iconBytes)
            assertNotNull(path)
            path!!
        }
        // ۵۰ فایل متمایز
        assertEquals(50, savedPaths.toSet().size)
        // …با محتوای درست (هر آیکون بایت اختصاصی خودش)
        savedPaths.forEachIndexed { index, path ->
            val file = java.io.File(path)
            assertTrue(file.exists())
            assertTrue(
                "محتوای فایل ${index + 1}ام باید بایت اختصاصی خودش باشد",
                file.readBytes().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, (index + 1).toByte()))
            )
        }
        // پاک‌سازی فایل‌های تست
        savedPaths.forEach { LoanIconHelper.deleteCustomIconFile(it) }
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

    // ---------------------------------------------------------------- F7 (پیش‌بینی نقدینگی ۳/۶ ماه آینده)

    /**
     * فیکس bug.md/F7: پنجره از ماه جاری شروع می‌شود؛ قسطِ پرداخت‌شدهٔ ماه جاری حساب
     * نمی‌شود، شروعِ آیندهٔ وام و تسویهٔ وسط پنجره درست حساب می‌شود و عبور از مرز
     * اسفند/فروردین سال را درست رد می‌کند.
     */
    @Test
    fun f7_liquidityForecastSumsOnlyUnpaidInstallmentsWithinWindow() {
        // امروز: آبان ۱۴۰۵ — پنجره: آبان..اسفند ۱۴۰۵ + فروردین ۱۴۰۶
        val loanA = LiquidityLoanInput(loanId = 21L, startYear = 1405, startMonth = 4, totalMonths = 12, myMonthlyShare = 4_000_000L)
        val loanB = LiquidityLoanInput(loanId = 22L, startYear = 1405, startMonth = 9, totalMonths = 6, myMonthlyShare = 2_000_000L) // شروع آذر
        val loanD = LiquidityLoanInput(loanId = 24L, startYear = 1405, startMonth = 8, totalMonths = 1, myMonthlyShare = 1_000_000L) // فقط آبان، بعدش تسویه
        val forecast = computeLiquidityForecast(
            currentYear = 1405,
            currentMonth = 8,
            loans = listOf(loanA, loanB, loanD),
            // آبانِ وام A پرداخت‌شده → در نقدینگیِ آینده حساب نمی‌شود
            paidMonthsByLoan = mapOf(21L to setOf(1405 to 4, 1405 to 8)),
            myShareByLoan = mapOf(21L to 4_000_000L, 22L to 2_000_000L, 24L to 1_000_000L),
            paidRecords = listOf(
                LiquidityPaidRecord(21L, 1405, 4),
                LiquidityPaidRecord(21L, 1405, 8)
            )
        )

        // ۶ ردیف به ترتیب زمانی: آبان..اسفند ۱۴۰۵ + فروردین ۱۴۰۶
        assertEquals(6, forecast.months.size)
        assertEquals(1405, forecast.months[0].year)
        assertEquals(8, forecast.months[0].month)
        assertEquals(1406, forecast.months[5].year) // مرز اسفند/فروردین رد شده
        assertEquals(1, forecast.months[5].month)

        // آبان: وام A پرداخت‌شده، وام B هنوز شروع نشده → فقط وام D
        assertEquals(1_000_000L, forecast.months[0].amount)
        assertEquals(1, forecast.months[0].loansCount)
        // آذر تا فروردین: هر ماه A (۴م) + B (۲م)؛ دیگری صفر
        for (i in 1..5) {
            assertEquals(6_000_000L, forecast.months[i].amount)
            assertEquals(2, forecast.months[i].loansCount)
        }

        // مجموع ۳ ماه (آبان+آذر+دی) و ۶ ماه
        assertEquals(13_000_000L, forecast.next3MonthsTotal)
        assertEquals(31_000_000L, forecast.next6MonthsTotal)

        // میانگین تاریخچه از ۲ ماه دارای رکورد (تیر و آبان وام A): هر دو ۴م → میانگین ۴م
        assertEquals(4_000_000L, forecast.historyMonthlyAverage)
        assertEquals(2, forecast.historyMonthsCount)
    }

    /** فیکس bug.md/F7: میانگین تاریخچه مجموعِ چند وام در یک ماه را یک نمونه می‌شمارد و بدون تاریخچه null است. */
    @Test
    fun f7_liquidityForecastHistoryAverageFromRealRecords() {
        // تاریخچه: تیر (A=۴م)، مرداد (A=۴م + C=۲م)، شهریور (A=۴م) → ۳ ماه متمایز
        val forecast = computeLiquidityForecast(
            currentYear = 1405,
            currentMonth = 8,
            loans = listOf(
                LiquidityLoanInput(loanId = 21L, startYear = 1405, startMonth = 4, totalMonths = 12, myMonthlyShare = 4_000_000L)
            ),
            paidMonthsByLoan = emptyMap(),
            myShareByLoan = mapOf(21L to 4_000_000L, 23L to 2_000_000L),
            paidRecords = listOf(
                LiquidityPaidRecord(21L, 1405, 4),
                LiquidityPaidRecord(21L, 1405, 5),
                LiquidityPaidRecord(23L, 1405, 5), // وامِ تسویه‌شده هم در تاریخچه می‌ماند
                LiquidityPaidRecord(21L, 1405, 6)
            )
        )
        // (۴ + ۶ + ۴) ÷ ۳ = ۴٬۶۶۶٬۶۶۶ (تقسیم صحیح)
        assertEquals(4_666_666L, forecast.historyMonthlyAverage)
        assertEquals(3, forecast.historyMonthsCount)

        // بدون هیچ رکوردی: میانگین null و پنجره‌ها صفر
        val empty = computeLiquidityForecast(
            currentYear = 1405, currentMonth = 8,
            loans = emptyList(), paidMonthsByLoan = emptyMap(),
            myShareByLoan = emptyMap(), paidRecords = emptyList()
        )
        assertNull(empty.historyMonthlyAverage)
        assertEquals(0, empty.historyMonthsCount)
        assertEquals(0L, empty.next3MonthsTotal)
        assertEquals(0L, empty.next6MonthsTotal)
        assertEquals(6, empty.months.size)
    }

    // ---------------------------------------------------------------- SB-01 (دفاع ایمپورت بکاپ دست‌ساز)

    /** ریشهٔ بکاپ دست‌سازِ مینیمال (بدون چک‌سام — مجاز به‌عنوان legacy) با payload قابل تزریق. */
    private fun sb01_handcraftedRoot(payloadBuild: (org.json.JSONObject) -> Unit): String {
        val payload = org.json.JSONObject()
        payloadBuild(payload)
        return org.json.JSONObject()
            .put("app", "VaamYar")
            .put("appIdentifier", "com.aistudio.vamyar")
            .put("schemaVersion", 1)
            .put("payload", payload)
            .toString()
    }

    private fun sb01_loanObj(id: Long) = org.json.JSONObject()
        .put("id", id)
        .put("title", "وام $id")
        .put("dueDay", 10)
        .put("monthlyAmount", 1_000_000L)
        .put("totalMonths", 12)
        .put("paidMonths", 0)
        .put("startYear", 1405)
        .put("startMonth", 1)

    /** فیکس bug.md/SB-01: فایل بزرگ‌تر از سقف مطلق (۱۰M کاراکتر) قبل از هر پارسی رد می‌شود. */
    @Test
    fun sb01_backupLargerThanJsonCapIsRejected() {
        val huge = "a".repeat(BackupHelper.MAX_BACKUP_JSON_CHARS + 10)
        val result = BackupHelper.parseAndValidateBackup(huge)
        assertTrue(result.isFailure)
        assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, result.exceptionOrNull()?.message)
    }

    /** فیکس bug.md/SB-01: سقف تعداد رکوردها در همهٔ آرایه‌ها — رد با پیام واحد. */
    @Test
    fun sb01_backupCountCapsRejectHandcraftedFiles() {
        // ۵۰۱ وام (سقف ۵۰۰)
        run {
            val json = sb01_handcraftedRoot { p ->
                val arr = org.json.JSONArray()
                for (i in 1..BackupHelper.MAX_BACKUP_LOANS + 1) arr.put(sb01_loanObj(i.toLong()))
                p.put("users", org.json.JSONArray()).put("loans", arr)
            }
            val r = BackupHelper.parseAndValidateBackup(json)
            assertTrue(r.isFailure)
            assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, r.exceptionOrNull()?.message)
        }
        // ۲۰۱ کاربر (سقف ۲۰۰)
        run {
            val json = sb01_handcraftedRoot { p ->
                val arr = org.json.JSONArray()
                for (i in 1..BackupHelper.MAX_BACKUP_USERS + 1) arr.put(org.json.JSONObject().put("id", i.toLong()).put("name", "کاربر$i").put("isPrimary", i == 1))
                p.put("users", arr)
            }
            val r = BackupHelper.parseAndValidateBackup(json)
            assertTrue(r.isFailure)
            assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, r.exceptionOrNull()?.message)
        }
        // ۱۰۰۱ سهم (سقف ۱۰۰۰)
        run {
            val json = sb01_handcraftedRoot { p ->
                val shares = org.json.JSONArray()
                for (i in 1..BackupHelper.MAX_BACKUP_SHARES + 1) shares.put(org.json.JSONObject().put("loanId", 10L).put("personName", "شریک$i").put("shareAmount", 100L))
                p.put("users", org.json.JSONArray()).put("loans", org.json.JSONArray().put(sb01_loanObj(10L))).put("shares", shares)
            }
            val r = BackupHelper.parseAndValidateBackup(json)
            assertTrue(r.isFailure)
            assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, r.exceptionOrNull()?.message)
        }
        // ۶۰٬۰۰۱ قسط (سقف ۶۰٬۰۰۰)
        run {
            val json = sb01_handcraftedRoot { p ->
                val arr = org.json.JSONArray()
                for (i in 1..BackupHelper.MAX_BACKUP_INSTALLMENTS + 1) arr.put(org.json.JSONObject().put("loanId", 10L).put("installmentNumber", 1).put("year", 1405).put("month", 1).put("amount", 0L))
                p.put("users", org.json.JSONArray()).put("loans", org.json.JSONArray().put(sb01_loanObj(10L))).put("installments", arr)
            }
            val r = BackupHelper.parseAndValidateBackup(json)
            assertTrue(r.isFailure)
            assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, r.exceptionOrNull()?.message)
        }
        // ۶۰٬۰۰۱ رکورد پرداخت (سقف ۶۰٬۰۰۰)
        run {
            val json = sb01_handcraftedRoot { p ->
                val arr = org.json.JSONArray()
                for (i in 1..BackupHelper.MAX_BACKUP_RECORDS + 1) arr.put(org.json.JSONObject().put("id", i.toLong()).put("loanId", 10L).put("installmentNumber", 1).put("amountPaid", 0L))
                p.put("users", org.json.JSONArray()).put("loans", org.json.JSONArray().put(sb01_loanObj(10L))).put("records", arr)
            }
            val r = BackupHelper.parseAndValidateBackup(json)
            assertTrue(r.isFailure)
            assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, r.exceptionOrNull()?.message)
        }
    }

    /** فیکس bug.md/SB-01: آیکون base64 عظیم یا بیش از سقف تعداد → رد کل فایل (ضد OOM هنگام decode). */
    @Test
    fun sb01_oversizedOrTooManyIconsAreRejected() {
        // یک آیکون با base64 بزرگ‌تر از سقف
        run {
            val json = sb01_handcraftedRoot { p ->
                val icons = org.json.JSONArray()
                    .put(org.json.JSONObject().put("loanId", 10L).put("fileName", "ic.png").put("data", "A".repeat(BackupHelper.MAX_ICON_BASE64_CHARS + 1)))
                p.put("users", org.json.JSONArray()).put("loans", org.json.JSONArray().put(sb01_loanObj(10L))).put("icons", icons)
            }
            val r = BackupHelper.parseAndValidateBackup(json)
            assertTrue(r.isFailure)
            assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, r.exceptionOrNull()?.message)
        }
        // ۵۰۱ آیکون (سقف ۵۰۰)
        run {
            val json = sb01_handcraftedRoot { p ->
                val icons = org.json.JSONArray()
                for (i in 1..BackupHelper.MAX_BACKUP_ICONS + 1) icons.put(org.json.JSONObject().put("loanId", 10L).put("fileName", "ic$i.png").put("data", "AAAA"))
                p.put("users", org.json.JSONArray()).put("loans", org.json.JSONArray().put(sb01_loanObj(10L))).put("icons", icons)
            }
            val r = BackupHelper.parseAndValidateBackup(json)
            assertTrue(r.isFailure)
            assertEquals(BackupHelper.ERR_BACKUP_INVALID_OR_TOO_LARGE, r.exceptionOrNull()?.message)
        }
    }

    /** فیکس bug.md/SB-01: مقادیر خصمانهٔ بکاپ دست‌ساز کلمپ/کوتاه می‌شوند، نه کرش یا دادهٔ منفی. */
    @Test
    fun sb01_hostileValuesAreClamped() {
        val longText = "x".repeat(10_000)
        val json = sb01_handcraftedRoot { p ->
            p.put(
                "users",
                org.json.JSONArray()
                    .put(org.json.JSONObject().put("id", 1L).put("name", longText).put("isPrimary", true).put("notes", longText))
            ).put(
                "loans",
                org.json.JSONArray()
                    .put(
                        org.json.JSONObject()
                            .put("id", 10L).put("title", longText)
                            .put("dueDay", 99)
                            .put("monthlyAmount", -5_000L)
                            .put("totalMonths", 12).put("paidMonths", 0)
                            .put("startYear", 999).put("startMonth", 99)
                            .put("endYear", 9999).put("endMonth", 0)
                            .put("myShareAmount", -1L)
                            .put("notes", longText)
                    )
            ).put(
                "shares",
                org.json.JSONArray()
                    .put(org.json.JSONObject().put("loanId", 10L).put("personName", longText).put("loanTitle", longText).put("shareAmount", -1L).put("dueDay", 99).put("shareDescription", longText))
            ).put(
                "records",
                org.json.JSONArray()
                    .put(org.json.JSONObject().put("id", 1L).put("loanId", 10L).put("installmentNumber", 0).put("amountPaid", -5L).put("loanTitle", longText))
            ).put(
                "installments",
                org.json.JSONArray()
                    .put(org.json.JSONObject().put("loanId", 10L).put("installmentNumber", 1).put("year", 9999).put("month", 99).put("amount", -1L))
            )
        }

        val data = BackupHelper.parseAndValidateBackup(json).getOrNull()!!
        // کاربر
        assertEquals(BackupHelper.MAX_TEXT_FIELD_CHARS, data.users.first().name.length)
        assertEquals(BackupHelper.MAX_NOTES_FIELD_CHARS, data.users.first().notes.length)
        // وام
        val loan = data.loans.first()
        assertEquals(BackupHelper.MAX_TEXT_FIELD_CHARS, loan.title.length)
        assertEquals(BackupHelper.MAX_NOTES_FIELD_CHARS, loan.notes.length)
        assertEquals(31, loan.dueDay)
        assertEquals(0L, loan.monthlyAmount)
        assertEquals(0L, loan.myShareAmount)
        assertEquals(BackupHelper.MIN_SHAMSI_YEAR, loan.startYear)
        assertEquals(12, loan.startMonth)
        assertEquals(BackupHelper.MAX_SHAMSI_YEAR, loan.endYear)
        assertEquals(1, loan.endMonth)
        // سهم
        val share = data.shares.first()
        assertEquals(BackupHelper.MAX_TEXT_FIELD_CHARS, share.personName.length)
        assertEquals(0L, share.shareAmount)
        assertEquals(31, share.dueDay)
        assertEquals(BackupHelper.MAX_NOTES_FIELD_CHARS, share.shareDescription.length)
        // رکورد
        val record = data.records.first()
        assertEquals(1, record.installmentNumber)
        assertEquals(0L, record.amountPaid)
        assertEquals(BackupHelper.MAX_TEXT_FIELD_CHARS, record.loanTitle.length)
        // قسط
        val installment = data.installments.first()
        assertEquals(BackupHelper.MAX_SHAMSI_YEAR, installment.year)
        assertEquals(12, installment.month)
        assertEquals(0L, installment.amount)
    }

    /** فیکس bug.md/SB-01: publicId تکراری — اولین رکورد نگه داشته می‌شود، تکراری خالی می‌شود. */
    @Test
    fun sb01_duplicatePublicIdsKeepFirstAndBlankTheSecond() {
        val json = sb01_handcraftedRoot { p ->
            p.put(
                "users",
                org.json.JSONArray()
                    .put(org.json.JSONObject().put("id", 1L).put("name", "اول").put("isPrimary", true).put("publicId", "U-dup001"))
                    .put(org.json.JSONObject().put("id", 2L).put("name", "دوم").put("publicId", "U-dup001"))
            ).put(
                "loans",
                org.json.JSONArray()
                    .put(sb01_loanObj(10L).put("publicId", "L-dup001"))
                    .put(sb01_loanObj(11L).put("publicId", "L-dup001"))
            ).put(
                "shares",
                org.json.JSONArray()
                    .put(org.json.JSONObject().put("loanId", 10L).put("personName", "الف").put("publicId", "S-dup001"))
                    .put(org.json.JSONObject().put("loanId", 10L).put("personName", "ب").put("publicId", "S-dup001"))
            )
        }

        val data = BackupHelper.parseAndValidateBackup(json).getOrNull()!!
        assertEquals("U-dup001", data.users[0].publicId)
        assertEquals("", data.users[1].publicId)
        assertEquals("L-dup001", data.loans[0].publicId)
        assertEquals("", data.loans[1].publicId)
        assertEquals("S-dup001", data.shares[0].publicId)
        assertEquals("", data.shares[1].publicId)
    }

    /** فیکس bug.md/SB-01: بکاپ legacy مینیمال (بدون publicId/installments/icons/چک‌سام/حتی users) همچنان پارس می‌شود. */
    @Test
    fun sb01_legacyMinimalBackupStillParses() {
        val json = sb01_handcraftedRoot { p ->
            p.put("users", org.json.JSONArray()).put("loans", org.json.JSONArray().put(sb01_loanObj(10L)))
        }
        val data = BackupHelper.parseAndValidateBackup(json).getOrNull()!!
        // بدون users → کاربر مبنا ساخته می‌شود (مسیر legacy NB-4)
        assertTrue(data.users.isNotEmpty() && data.users.first().isPrimary)
        assertEquals(1, data.loans.size)
        assertTrue(data.installments.isEmpty())
        assertTrue(data.icons.isEmpty())
    }
}

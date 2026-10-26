package com.example.util

/**
 * فیکس bug.md/F7 (تأیید کاربر — بازبینی ۴.۵): پیش‌بینی نقدینگی ۳ و ۶ ماه آینده.
 *
 * مجموع اقساطِ «سهم خودِ کاربر» در ماه‌های پیشِ رو (به تفکیک ماه + مجموع کل ۳ و ۶
 * ماهه) + مقایسه با میانگین پرداخت‌های خودِ او از تاریخچهٔ واقعی (رکوردهای
 * LoanInstallment) — **بدون فیلد درآمد** (تصمیم کاربر: فعلاً نه).
 *
 * کاملاً خالص و بدون وابستگی به Room/UI تا مستقیماً unit-test شود (PureLogicUnitTest).
 *
 * قواعد:
 * - پنجره از «ماه جاری» شروع می‌شود (ماه جاری + ۵ ماه بعد). قسطِ ماهِ جاری فقط اگر
 *   هنوز پرداخت نشده باشد حساب می‌شود — بقیهٔ ماه‌ها همه مشمول‌اند.
 * - وامی در یک ماه مشمول است که آن ماه داخل بازهٔ اقساطش باشد (installmentNumberForMonth
 *   ∈ ۱..totalMonths) — شروعِ آیندهٔ وام و تسویهٔ وسط پنجره درست حساب می‌شود.
 * - ماه‌های پرداخت‌شده (رکورد واقعی موجود) در پنجره صفر حساب می‌شوند (نقدینگیِ آینده).
 * - عقب‌ماندگی‌های قبل از ماه جاری جزو پنجره نیستند (جایشان چیپ/دیالوگ عقب‌افتادگی است).
 * - میانگین تاریخچه: مجموع سهمِ ماهانهٔ کاربر در ماه‌های دارای رکوردِ پرداخت ÷ تعداد
 *   ماه‌های متمایز. چون سهمِ تاریخی شرکا ثبت نمی‌شود، سهمِ فعلیِ همان وام به‌عنوان
 *   تقریبِ استاندارد به کار می‌رود.
 */

data class LiquidityLoanInput(
    val loanId: Long,
    val startYear: Int,
    val startMonth: Int,
    val totalMonths: Int,
    /** سهم ماهانهٔ خود کاربر برای این وام (LoanShareCalculator) */
    val myMonthlyShare: Long
)

data class LiquidityMonthRow(
    val year: Int,
    val month: Int,
    /** مجموع سهم کاربر در این ماه (۰ = چیزی موعود نیست) */
    val amount: Long,
    /** تعداد وام‌های دارای قسطِ پرداخت‌نشده در این ماه */
    val loansCount: Int
)

data class LiquidityForecast(
    /** ماه‌های پنجره به ترتیب زمانی (ماه جاری اول) — ۶ ردیف */
    val months: List<LiquidityMonthRow>,
    val next3MonthsTotal: Long,
    val next6MonthsTotal: Long,
    /** میانگین پرداخت ماهانهٔ کاربر از تاریخچهٔ واقعی — null = تاریخچه‌ای ثبت نشده */
    val historyMonthlyAverage: Long?,
    val historyMonthsCount: Int
)

/** یک قسطِ واقعاً پرداخت‌شده از تاریخچه (برای میانگین) */
data class LiquidityPaidRecord(
    val loanId: Long,
    val year: Int,
    val month: Int
)

fun computeLiquidityForecast(
    currentYear: Int,
    currentMonth: Int,
    /** وام‌های فعالِ مشمول پنجره (تسویه‌شده/آرشیوشده لازم نیست) */
    loans: List<LiquidityLoanInput>,
    /** loanId → مجموع (سال، ماه) اقساطِ پرداخت‌شدهٔ واقعیِ همان وام */
    paidMonthsByLoan: Map<Long, Set<Pair<Int, Int>>>,
    /** loanId → سهم ماهانهٔ فعلیِ کاربر — برای همهٔ وام‌های دارای رکورد (حتی تسویه‌شده) */
    myShareByLoan: Map<Long, Long>,
    /** همهٔ رکوردهای واقعی پرداخت (پایهٔ میانگین تاریخچه) */
    paidRecords: List<LiquidityPaidRecord>,
    monthsAhead: Int = 6
): LiquidityForecast {
    val horizon = monthsAhead.coerceAtLeast(6)
    val curIndex = currentYear * 12 + (currentMonth - 1)

    val months = (0 until horizon).map { offset ->
        val idx = curIndex + offset
        val y = idx / 12
        val m = idx % 12 + 1
        var amount = 0L
        var count = 0
        for (loan in loans) {
            // این ماه داخل بازهٔ اقساط وام هست؟ (شروع آینده/تسویهٔ وسط پنجره)
            val installment = ShamsiDateHelper.installmentNumberForMonth(
                loan.startYear, loan.startMonth, y, m
            )
            if (installment == null || installment > loan.totalMonths) continue
            // قسطِ این ماه قبلاً پرداخت شده → در نقدینگیِ آینده حساب نمی‌شود
            if (paidMonthsByLoan[loan.loanId]?.contains(y to m) == true) continue
            amount += loan.myMonthlyShare
            count++
        }
        LiquidityMonthRow(year = y, month = m, amount = amount, loansCount = count)
    }

    val next3MonthsTotal = months.take(3).sumOf { it.amount }
    val next6MonthsTotal = months.take(6).sumOf { it.amount }

    // میانگین ماهانهٔ پرداخت‌های خود کاربر از تاریخچهٔ واقعی — هر متمایزِ (سال، ماه) یک نمونه
    val monthTotals = paidRecords
        .groupBy { it.year to it.month }
        .mapValues { (_, recs) -> recs.sumOf { myShareByLoan[it.loanId] ?: 0L } }
    val historyMonthsCount = monthTotals.size
    val historyMonthlyAverage = if (historyMonthsCount > 0) {
        monthTotals.values.sum() / historyMonthsCount
    } else null

    return LiquidityForecast(
        months = months,
        next3MonthsTotal = next3MonthsTotal,
        next6MonthsTotal = next6MonthsTotal,
        historyMonthlyAverage = historyMonthlyAverage,
        historyMonthsCount = historyMonthsCount
    )
}

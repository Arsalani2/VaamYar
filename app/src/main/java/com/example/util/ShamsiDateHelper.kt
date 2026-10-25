package com.example.util

import java.util.Calendar

data class ShamsiDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int
) {
    val monthName: String
        get() = ShamsiDateHelper.getMonthName(month)

    fun formatted(usePersianDigits: Boolean = true): String {
        val dStr = NumberFormatter.formatNumber(day, usePersianDigits)
        val yStr = NumberFormatter.formatNumber(year, usePersianDigits)
        return "$dStr $monthName $yStr"
    }

    fun toStandardFormat(usePersianDigits: Boolean = false): String {
        val y = if (usePersianDigits) NumberFormatter.toPersianDigits(year) else "%04d".format(year)
        val m = if (usePersianDigits) NumberFormatter.toPersianDigits(month) else "%02d".format(month)
        val d = if (usePersianDigits) NumberFormatter.toPersianDigits(day) else "%02d".format(day)
        return "$y/$m/$d"
    }
}

object ShamsiDateHelper {

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    fun getMonthName(month: Int): String {
        val index = (month - 1).coerceIn(0, 11)
        return MONTH_NAMES[index]
    }

    /**
     * System Shamsi date computed from Gregorian device clock
     */
    fun getCurrentShamsiDate(): ShamsiDate = getSystemShamsiDate()

    fun getSystemShamsiDate(): ShamsiDate {
        val cal = Calendar.getInstance()
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Canonical Birashk / Pournader algorithm for Gregorian to Jalali conversion
     */
    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): ShamsiDate {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return ShamsiDate(jy, jm, jd)
    }

    /**
     * Calculates the exact final payoff month and year (قسط آخر).
     */
    fun calculateEndDate(startYear: Int, startMonth: Int, totalMonths: Int): ShamsiDate {
        if (totalMonths <= 0) return ShamsiDate(startYear, startMonth, 1)
        val totalMonthsFromZero = (startMonth - 1) + (totalMonths - 1)
        val endYear = startYear + (totalMonthsFromZero / 12)
        val endMonth = (totalMonthsFromZero % 12) + 1
        return ShamsiDate(endYear, endMonth, 1)
    }

    /**
     * Calculates intelligent auto-detected number of paid installments
     * from start date until current date.
     * Example: Start: 1405/06/07, Current: 1405/08/10 -> 2 months passed (Shahrivar + Mehr),
     * and day 10 >= day 7, so Aban is also due -> 3 installments.
     */
    fun calculateElapsedInstallments(
        startYear: Int,
        startMonth: Int,
        startDay: Int,
        curYear: Int,
        curMonth: Int,
        curDay: Int,
        totalMonths: Int
    ): Int {
        val startTotalMonths = startYear * 12 + (startMonth - 1)
        val curTotalMonths = curYear * 12 + (curMonth - 1)

        if (curTotalMonths < startTotalMonths) return 0
        if (curTotalMonths == startTotalMonths && curDay < startDay) return 0

        var elapsed = (curTotalMonths - startTotalMonths)
        if (curDay >= startDay) {
            elapsed += 1
        }
        return elapsed.coerceIn(0, totalMonths)
    }

    /**
     * Detailed human-readable explanation of loan schedule in Shamsi
     */
    fun formatScheduleExplanation(
        startYear: Int,
        startMonth: Int,
        totalMonths: Int,
        paidMonths: Int,
        usePersianDigits: Boolean = true
    ): String {
        val end = calculateEndDate(startYear, startMonth, totalMonths)
        val remaining = (totalMonths - paidMonths).coerceAtLeast(0)
        val sMonthName = getMonthName(startMonth)
        val eMonthName = getMonthName(end.month)
        val sYearStr = NumberFormatter.formatNumber(startYear, usePersianDigits)
        val eYearStr = NumberFormatter.formatNumber(end.year, usePersianDigits)
        val totalStr = NumberFormatter.formatNumber(totalMonths, usePersianDigits)
        val paidStr = NumberFormatter.formatNumber(paidMonths, usePersianDigits)
        val remStr = NumberFormatter.formatNumber(remaining, usePersianDigits)

        return if (remaining == 0) {
            "این وام از $sMonthName $sYearStr تا $eMonthName $eYearStr ($totalStr قسط) بوده و تسویه کامل شده است ✓"
        } else {
            "دوره بازپرداخت: از $sMonthName $sYearStr تا $eMonthName $eYearStr ($totalStr قسط)\nوضعیت: $paidStr قسط پرداخت‌شده • $remStr قسط مانده تا تسویه نهایی"
        }
    }

    /**
     * Calculates days until due day in current Shamsi month or next month.
     */
    fun daysUntilDue(dueDay: Int, currentDay: Int): Int {
        return if (dueDay >= currentDay) {
            dueDay - currentDay
        } else {
            (30 - currentDay) + dueDay
        }
    }

    /**
     * فیکس bug.md/P1-6: کلید ماه شمسی مثل "1405-08" برای تشخیص تغییر ماه
     * (پایهٔ رِسِت خودکار ماهانهٔ پرچم‌های «پرداخت ماه جاری»).
     */
    fun monthKey(date: ShamsiDate): String = "%04d-%02d".format(date.year, date.month)

    /**
     * فیکس bug.md/P1-6 (و P2-4): تاریخ مؤثر اپ — تاریخ سفارشی تنظیمات اگر فعال باشد،
     * وگرنده ساعت سیستم. منطقِ «ماه جاری» باید از همین تاریخ پیروی کند.
     */
    fun getEffectiveShamsiDate(prefs: AppPreferences): ShamsiDate =
        if (prefs.isCustomDateEnabled) ShamsiDate(prefs.customYear, prefs.customMonth, prefs.customDay)
        else getSystemShamsiDate()
}

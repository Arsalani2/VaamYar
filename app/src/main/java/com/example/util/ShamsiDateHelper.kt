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
     * فیکس bug.md/P2-6: «چند روز تا سررسید» با تقویم شمسیِ واقعی محاسبه می‌شود — قبلاً همه‌ی
     * ماه‌ها ۳۰ روز فرض می‌شدند و در ماه‌های ۳۱روزه (فروردین–شهریور) و اسفند ۱ تا ۲ روز
     * خطا می‌داد. اگر سررسیدِ ماه جاری گذشته باشد، فاصله تا سررسیدِ ماه بعد با طول واقعی
     * هر دو ماه حساب می‌شود. سررسیدِ بزرگ‌تر از طول ماه (مثل ۳۱ در مهر) روی آخرین روزِ
     * همان ماه منطبق می‌شود.
     */
    fun daysUntilDue(dueDay: Int, currentDate: ShamsiDate): Int {
        val daysThisMonth = getDaysInShamsiMonth(currentDate.year, currentDate.month)
        val effectiveDueDay = dueDay.coerceIn(1, daysThisMonth)
        return if (effectiveDueDay >= currentDate.day) {
            effectiveDueDay - currentDate.day
        } else {
            val daysNextMonth = if (currentDate.month == 12) {
                getDaysInShamsiMonth(currentDate.year + 1, 1)
            } else {
                getDaysInShamsiMonth(currentDate.year, currentDate.month + 1)
            }
            (daysThisMonth - currentDate.day) + dueDay.coerceIn(1, daysNextMonth)
        }
    }

    /**
     * فیکس bug.md/NB-16: فاصله تا سررسیدِ «همین ماه» — منفی یعنی X روز از موعدِ این
     * ماه گذشته است (عقب‌افتادگیِ سررسید). برخلاف daysUntilDue که برای سررسیدِ سپری‌شده
     * به ماهِ بعد می‌پرد و همیشه ≥ ۰ برمی‌گرداند، این تابع وضعیت واقعیِ همین ماه را
     * برمی‌گرداند تا UI «X روز عقب» را نشان بدهد.
     */
    fun daysUntilDueThisMonth(dueDay: Int, currentDate: ShamsiDate): Int {
        val effectiveDueDay = dueDay.coerceIn(1, getDaysInShamsiMonth(currentDate.year, currentDate.month))
        return effectiveDueDay - currentDate.day
    }

    /**
     * فیکس bug.md/NB-17: روز سررسیدِ مؤثر در یک ماه مشخص — dueDay=31 در ماه‌های
     * ۳۰روزه (مهر..بهمن) به ۳۰ و در اسفندِ ۲۹روزه به ۲۹ محدود می‌شود تا اعلانِ
     * «امروز/فردا»ِ وامِ با سررسید ۳۱ هرگز گم نشود (قبلاً چون 31 != 30، اصلاً
     * شرط برابر نمی‌شد و یادآوری هیچ‌وقت پخش نمی‌شد).
     */
    fun effectiveDueDay(year: Int, month: Int, dueDay: Int): Int =
        dueDay.coerceIn(1, getDaysInShamsiMonth(year, month))

    /**
     * فیکس bug.md/F1+NB-31: سال/ماه شمسیِ واقعیِ قسط [installmentNumber]-اُم (از ۱)
     * نسبت به ماه شروع وام — قسط ۱ = ماه شروع؛ قسط ۱۳ِ وامِ مهرماهِ ۱۴۰۳ = مهر ۱۴۰۴.
     * عبور از سال و مرز اسفند/فروردین درست حساب می‌شود. day=1 است (ماه، نه روز).
     */
    fun installmentMonth(startYear: Int, startMonth: Int, installmentNumber: Int): ShamsiDate {
        val n = (installmentNumber.coerceAtLeast(1) - 1)
        val total = startYear * 12 + (startMonth - 1) + n
        return ShamsiDate(total / 12, total % 12 + 1, 1)
    }

    /**
     * فیکس bug.md/F1+NB-31: معکوسِ installmentMonth — شمارهٔ قسط (از ۱) متناظر با
     * سال/ماه شمسیِ داده‌شده؛ null = قبل از ماه شروع وام.
     * (بزرگ‌تر از totalMonths بودن را فراخوانی‌کننده چک می‌کند.)
     */
    fun installmentNumberForMonth(startYear: Int, startMonth: Int, year: Int, month: Int): Int? {
        val diff = (year * 12 + month - 1) - (startYear * 12 + startMonth - 1)
        return if (diff < 0) null else diff + 1
    }

    /**
     * فیکس bug.md/P2-2: طول واقعی ماه‌های تقویم شمسی — فروردین تا شهریور ۳۱ روز،
     * مهر تا بهمن ۳۰ روز، اسفند ۲۹ روز و در سال کبیسه ۳۰ روز.
     */
    fun getDaysInShamsiMonth(year: Int, month: Int): Int = when {
        month in 1..6 -> 31
        month in 7..11 -> 30
        month == 12 -> if (isShamsiLeapYear(year)) 30 else 29
        else -> 30 // ورودی نامعتبر؛ مقدار امن
    }

    /**
     * فیکس bug.md/P2-2: سال کبیسه‌ی تقویم جلالی با قاعده‌ی چرخه‌ی ۳۳ساله
     * (معتبر برای بازه‌ی سال‌های کاربردی اپ؛ مثل ۱۴۰۳ و ۱۴۰۸ که اسفندشان ۳۰ روز است).
     */
    fun isShamsiLeapYear(year: Int): Boolean =
        (((25 * year + 11) % 33) + 33) % 33 < 8

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

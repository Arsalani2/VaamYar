package com.aistudio.vamyar.util

/**
 * فیکس bug.md/NB-33: آستانه‌های اعتبارسنجی ورودی‌های عددی/متنی در یک‌جا متمرکز شده‌اند
 * تا کنترل ورودی‌ها صریح و با بازخورد زنده باشد — نه سختگیرانه، ولی هیچ مقداری هم
 * بی‌صدا clamp یا تغییر نمی‌کند (اینوارینت #۶). قرارداد: خروجی null یعنی ورودی معتبر؛
 * غیر null یعنی پیام خطای فارسیِ نمایش‌دادنی زیر همان فیلد.
 */
object InputLimits {
    const val MIN_DURATION_MONTHS = 1
    const val MAX_DURATION_MONTHS = 120
    const val MAX_TITLE_LENGTH = 60
    const val MAX_AMOUNT_DIGITS = 15

    /** ارقام فارسی/عربی را به ASCII نگاشت و هر نویسهٔ غیرعددی را می‌اندازد. */
    fun normalizeDigits(raw: String): String = raw.filter { it.isDigit() }.map { c ->
        when (c) {
            in '\u06F0'..'\u06F9' -> (c - 0x06F0 + 0x0030)
            in '\u0660'..'\u0669' -> (c - 0x0660 + 0x0030)
            else -> c
        }
    }.joinToString("")

    /** اعتبارسنجی «تعداد کل اقساط (ماه)». null = معتبر؛ غیر null = پیام خطا. */
    fun validateDurationMonths(rawText: String): String? {
        val digits = normalizeDigits(rawText)
        if (digits.isEmpty()) return "تعداد اقساط را وارد کنید."
        val months = digits.toIntOrNull() ?: return "تعداد اقساط باید عدد باشد."
        return when {
            months < MIN_DURATION_MONTHS -> "تعداد اقساط نمی‌تواند کمتر از $MIN_DURATION_MONTHS باشد."
            months > MAX_DURATION_MONTHS -> "⚠️ حداکثر $MAX_DURATION_MONTHS ماه مجاز است؛ مقدار را اصلاح کنید."
            else -> null
        }
    }

    /** اعتبارسنجی عنوان وام. null = معتبر؛ غیر null = پیام خطا. */
    fun validateTitle(raw: String): String? = when {
        raw.isBlank() -> "عنوان وام نمی‌تواند خالی باشد."
        raw.trim().length > MAX_TITLE_LENGTH ->
            "عنوان حداکثر می‌تواند $MAX_TITLE_LENGTH نویسه باشد."
        else -> null
    }

    /**
     * فیکس bug.md/NB-36 (اینوارینت #۱۲): یکتایی عنوان وام — تطبیق با کلید استاندارد
     * (PersianNameNormalizer.matchKey) تا هم‌نویسه‌های عربی ي/ك، نیم‌فاصله و فاصله‌های
     * زائد یکسان شمرده شوند. هنگام ویرایش وام، کلیدِ عنوانِ خودِ وام با [selfKey] از
     * بررسی کنار گذاشته می‌شود. خالی‌بودن/طول مالتِ validateTitle است و اینجا خطا
     * نمی‌دهد. null = معتبر؛ غیر null = پیام خطا زیر همان فیلد.
     */
    fun validateUniqueTitle(raw: String, existingTitles: List<String>, selfKey: String? = null): String? {
        if (raw.isBlank()) return null
        val key = PersianNameNormalizer.matchKey(raw)
        val duplicate = existingTitles.any { existing ->
            val existingKey = PersianNameNormalizer.matchKey(existing)
            existingKey == key && existingKey != selfKey
        }
        return if (duplicate) "این نام وام قبلاً ثبت شده است؛ لطفاً نام دیگری انتخاب کنید." else null
    }

    /**
     * فیکس bug.md/NB-35 (اینوارینت #۱۱): شریک فقط از میان کاربران تعریف‌شده قابل
     * انتخاب است — نام آزاد/غریبه رد می‌شود. تطبیق با کلید استاندارد تا هم‌نویسهٔ
     * عربی (ي/ك) و فاصله‌های زائد هم‌نام شمرده شوند.
     */
    fun isDefinedPartner(partnerName: String, definedUserNames: List<String>): Boolean =
        definedUserNames.any {
            PersianNameNormalizer.matchKey(it.trim()) == PersianNameNormalizer.matchKey(partnerName)
        }
}

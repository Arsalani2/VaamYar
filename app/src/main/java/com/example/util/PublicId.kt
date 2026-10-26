package com.example.util

import kotlin.random.Random

/**
 * فیکس bug.md/F6 (بازبینی ۴.۵ — تأیید کاربر؛ جایگزین تصمیم A4 نام‌پایه):
 * شناسهٔ یکتای عمومی رکوردها. هویت هر رکورد در منطق کد با این شناسه مدیریت می‌شود،
 * نه با نام/عنوان؛ نام فقط نمایشی است (اینوارینت #۱۰). قالب‌ها:
 *  - وام:   `L-xxxxxxx` (۷ نویسهٔ الفبایی-عددی)
 *  - کاربر: `U-xxxxxx` (۶ نویسه)
 *  - سهم:   `S-xxxxxx` (۶ نویسه)
 * نویسه‌های مبهم (O/۰، I/l/1 و…) از الفبا حذف شده‌اند تا خواناییِ انسانی حفظ شود.
 * مقدار خالی ("") یعنی «هنوز تخصیص نیافته» — رکوردهای قدیمی در اولین اجرای اپ
 * با backfillMissingPublicIds شناسه می‌گیرند و بکاپ‌های قدیمی هنگام restore.
 */
object PublicId {

    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

    const val LOAN_PREFIX = "L"
    const val USER_PREFIX = "U"
    const val SHARE_PREFIX = "S"

    const val LOAN_ID_LENGTH = 7
    const val USER_ID_LENGTH = 6
    const val SHARE_ID_LENGTH = 6

    fun newLoanId(): String = generate(LOAN_PREFIX, LOAN_ID_LENGTH)
    fun newUserId(): String = generate(USER_PREFIX, USER_ID_LENGTH)
    fun newShareId(): String = generate(SHARE_PREFIX, SHARE_ID_LENGTH)

    private fun generate(prefix: String, length: Int): String {
        val sb = StringBuilder(prefix.length + 1 + length)
        sb.append(prefix).append('-')
        repeat(length) { sb.append(CHARSET[Random.nextInt(CHARSET.length)]) }
        return sb.toString()
    }

    /**
     * اعتبارسنجی شکل شناسه؛ برای پاک‌سازی ورودیِ بکاپ — شناسهٔ خاموشکل/جعلی را
     * معتبر نمی‌دانیم تا هنگام بازیابی شناسهٔ تازه و سالم تولید شود.
     */
    fun isValid(publicId: String, prefix: String, idLength: Int): Boolean {
        if (publicId.length != prefix.length + 1 + idLength) return false
        if (!publicId.startsWith("$prefix-")) return false
        return publicId.drop(prefix.length + 1).all { it in CHARSET }
    }

    fun isValidLoanId(publicId: String): Boolean = isValid(publicId, LOAN_PREFIX, LOAN_ID_LENGTH)
    fun isValidUserId(publicId: String): Boolean = isValid(publicId, USER_PREFIX, USER_ID_LENGTH)
    fun isValidShareId(publicId: String): Boolean = isValid(publicId, SHARE_PREFIX, SHARE_ID_LENGTH)
}

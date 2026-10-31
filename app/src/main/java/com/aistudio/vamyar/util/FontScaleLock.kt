package com.aistudio.vamyar.util

/**
 * قفل مقیاس فونت (فیکس bug.md/NB-60):
 * فونت سیستمِ خیلی بزرگ یا خیلی کوچک نباید چیدمان برنامه را به‌هم بریزد؛
 * مقیاس فونتِ محاسباتی در MainActivity داخل این بازه قفل می‌شود.
 *
 * نکته (دستور کاربر): در آینده دکمهٔ تنظیمات «اندازهٔ فونت» اضافه می‌شود
 * تا خود کاربر بتواند فونت را بزرگ/کوچک کند؛ همان تنظیم باید از همین بازه به‌عنوان
 * دامنهٔ مجاز استفاده کند تا قفل پابرجا بماند.
 */
object FontScaleLock {
    const val MIN_FONT_SCALE = 0.9f
    const val MAX_FONT_SCALE = 1.3f

    fun clamp(fontScale: Float): Float = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
}

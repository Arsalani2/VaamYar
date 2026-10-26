package com.aistudio.vamyar.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberFormatter {

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(number: Any?): String {
        if (number == null) return ""
        val str = number.toString()
        val sb = StringBuilder()
        for (ch in str) {
            if (ch in '0'..'9') {
                sb.append(persianDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumber(
        number: Any?,
        usePersian: Boolean = true
    ): String {
        if (number == null) return ""
        val str = number.toString()
        return if (usePersian) toPersianDigits(str) else str
    }

    fun formatWithCommas(amount: Long, usePersian: Boolean = false): String {
        if (amount == 0L) return "0"
        val symbols = DecimalFormatSymbols(Locale.US)
        val formatted = DecimalFormat("#,###", symbols).format(amount)
        return if (usePersian) toPersianDigits(formatted) else formatted
    }

    /**
     * Formats price respecting:
     * - usePersian: Persian or English digits
     * - compactThousands: removes 3 zeros (e.g. 890,000 becomes 890 هزار تومان or 890)
     */
    fun formatPrice(
        amount: Long,
        withUnit: Boolean = true,
        usePersian: Boolean = true,
        compactThousands: Boolean = false
    ): String {
        val symbols = DecimalFormatSymbols(Locale.US)

        return if (compactThousands) {
            val thousandsValue = amount / 1000.0
            val formatted = if (thousandsValue % 1.0 == 0.0) {
                DecimalFormat("#,###", symbols).format(thousandsValue.toLong())
            } else {
                DecimalFormat("#,###.#", symbols).format(thousandsValue)
            }
            val digitFormatted = if (usePersian) toPersianDigits(formatted) else formatted
            if (withUnit) "$digitFormatted هزار ت" else digitFormatted
        } else {
            val formatter = DecimalFormat("#,###", symbols)
            val formatted = formatter.format(amount)
            val digitFormatted = if (usePersian) toPersianDigits(formatted) else formatted
            if (withUnit) "$digitFormatted تومان" else digitFormatted
        }
    }

    /**
     * فیکس bug.md/NB-5 (و P3-4):
     * اعتبارسنجی صریح برای ورودی‌های مالی:
     * - ارقام فارسی و عربی را استاندارد می‌کند.
     * - جداکننده‌های هزارگانِ مجاز (, و ، و ٬ و فاصله) و واحدهای «تومان»/«هزار»/«ت» را برمی‌دارد.
     * - اگر نویسهٔ غیرمجازی مانند ممیز/اعشار (. یا / یا ٫) وجود داشته باشد، null برمی‌گرداند تا از خطای مالی 10x/100x جلوگیری شود.
     */
    fun parseAmountStrict(input: String?): Long? {
        if (input.isNullOrBlank()) return 0L
        val trimmed = input.trim()
            .replace("تومان", "")
            .replace("هزار", "")
            .replace("ت", "")
            .trim()
        if (trimmed.isEmpty()) return 0L

        val sb = StringBuilder()
        for (ch in trimmed) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                ',', '،', '٬', ' ' -> { /* جداکننده مجاز هزارگان */ }
                else -> return null // کاراکتر غیرمجاز (مثل . یا / یا ٫)
            }
        }
        val digits = sb.toString()
        if (digits.isEmpty()) return 0L
        return digits.toLongOrNull()
    }

    /**
     * فیکس bug.md/P3-4: جلوگیری از سرریز به اعداد منفی برای مقادیر بزرگتر از Int.MAX_VALUE
     */
    fun parseIntStrict(input: String?): Int? {
        val amount = parseAmountStrict(input) ?: return null
        if (amount > Int.MAX_VALUE || amount < 0L) return null
        return amount.toInt()
    }

    fun parseAmount(input: String): Long {
        if (input.isBlank()) return 0L
        val strict = parseAmountStrict(input)
        if (strict != null) return strict

        val clean = input
            .replace("،", "")
            .replace("٬", "")
            .replace(",", "")
            .replace(" ", "")
            .replace("تومان", "")
            .replace("هزار", "")
            .replace("ت", "")
            .map { ch ->
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                }
            }
            .joinToString("")
            .filter { it.isDigit() }

        return clean.toLongOrNull() ?: 0L
    }

    fun parseInt(input: String): Int {
        val strict = parseIntStrict(input)
        if (strict != null) return strict
        return parseAmount(input).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Converts a numeric amount to Persian words (e.g. 800000 -> هشتصد هزار تومان)
     */
    fun toPersianWords(amount: Long): String {
        if (amount == 0L) return "صفر تومان"
        if (amount < 0L) return "منفی " + toPersianWords(-amount)

        val yekan = arrayOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
        val dahha = arrayOf("", "ده", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
        val dahTaNoozdah = arrayOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
        val sadha = arrayOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
        val steps = arrayOf("", "هزار", "میلیون", "میلیارد", "تریلیون")

        fun sectionToWords(num: Int): String {
            val s = num / 100
            val d = (num % 100) / 10
            val y = num % 10
            val parts = mutableListOf<String>()

            if (s > 0) parts.add(sadha[s])
            if (d == 1) {
                parts.add(dahTaNoozdah[y])
            } else {
                if (d > 1) parts.add(dahha[d])
                if (y > 0) parts.add(yekan[y])
            }
            return parts.joinToString(" و ")
        }

        var temp = amount
        var stepIndex = 0
        val groupWords = mutableListOf<String>()

        while (temp > 0) {
            val chunk = (temp % 1000).toInt()
            if (chunk > 0) {
                val words = sectionToWords(chunk)
                val suffix = steps[stepIndex]
                val combined = if (suffix.isNotEmpty()) "$words $suffix" else words
                groupWords.add(0, combined)
            }
            temp /= 1000
            stepIndex++
        }

        return groupWords.joinToString(" و ") + " تومان"
    }
}

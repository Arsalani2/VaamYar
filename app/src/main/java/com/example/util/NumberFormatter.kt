package com.example.util

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

    fun parseAmount(input: String): Long {
        if (input.isBlank()) return 0L
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
        return parseAmount(input).toInt()
    }
}

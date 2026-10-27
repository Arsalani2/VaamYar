package com.aistudio.vamyar.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aistudio.vamyar.util.ShamsiDateHelper

@Entity(tableName = "loans", indices = [Index("publicId")])
data class Loan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // فیکس bug.md/F6: شناسهٔ یکتای عمومی — هویت وام در منطق کد با این مدیریت می‌شود،
    // نه با عنوان (نام فقط نمایشی است). خالی = هنوز تخصیص نیافته (backfill/درج تخصیص می‌دهد).
    val publicId: String = "",
    val title: String,
    val dueDay: Int,
    val monthlyAmount: Long,
    val totalMonths: Int,
    // مقدار پیش‌فرض صفر برای سازگاری با ساخت‌های قدیمی (بکاپ/تست‌هایی که این فیلد را نمی‌دهند)
    val paidMonths: Int = 0,
    val startYear: Int = 1403,
    val startMonth: Int = 7, // مهر
    val endYear: Int = 1404,
    val endMonth: Int = 7,
    val category: String = "بانکی",
    val colorHex: Long = 0xFF1E88E5,
    val notes: String = "",
    val myShareAmount: Long = 0L, // سهم کاربر اصلی (شخصی)
    val hasPartners: Boolean = false, // آیا این وام دارای شریک است
    val isArchived: Boolean = false,
    val isCurrentMonthPaid: Boolean = false, // آیا قسط ماه جاری پرداخت شده است
    val iconName: String = "account_balance", // نام آیکون پیش‌فرض
    val iconShape: String = "ROUNDED_SQUARE", // ROUNDED_SQUARE یا CIRCLE
    val customIconPath: String? = null // آدرس تصویر اختصاصی آپلود شده در حافظه برنامه
) {
    val remainingMonths: Int
        get() = (totalMonths - paidMonths).coerceAtLeast(0)

    val remainingTotalDebt: Long
        get() = remainingMonths.toLong() * monthlyAmount

    val effectiveMyShare: Long
        get() = if (hasPartners || myShareAmount < monthlyAmount) myShareAmount else monthlyAmount

    val remainingMyDebt: Long
        get() = remainingMonths.toLong() * effectiveMyShare

    val totalLoanAmount: Long
        get() = totalMonths.toLong() * monthlyAmount

    val totalPaidAmount: Long
        get() = paidMonths.toLong() * monthlyAmount

    val progress: Float
        get() = if (totalMonths > 0) (paidMonths.toFloat() / totalMonths).coerceIn(0f, 1f) else 0f

    val isCompleted: Boolean
        get() = paidMonths >= totalMonths

    val othersShareAmount: Long
        get() = (monthlyAmount - effectiveMyShare).coerceAtLeast(0L)

    val startMonthName: String
        get() = ShamsiDateHelper.getMonthName(startMonth)

    val endMonthName: String
        get() = ShamsiDateHelper.getMonthName(endMonth)

    val payoffCountdownText: String
        get() = if (isCompleted) "تسویه شده" else "$remainingMonths ماه مانده تا تسویه ($endMonthName $endYear)"
}

/**
 * فیکس bug.md/P3-2: منبع واحد و خالص برای محاسبهٔ «سهم من».
 * قاعدهٔ واحد:
 * - وام بدون شریک یا بدون سهمِ ثبت‌شده: ۱۰۰٪ مبلغ قسط ماهانه (monthlyAmount)
 * - وام دارای شریک: monthlyAmount منهای مجموع سهم شرکا (با clamp در ≥ ۰)
 */
object LoanShareCalculator {
    fun computeMyShare(loan: Loan, shares: List<DebtorShare>): Long {
        val loanShares = shares.filter { it.loanId == loan.id }
        return if (loanShares.isNotEmpty()) {
            val partnersSum = loanShares.sumOf { it.shareAmount }
            (loan.monthlyAmount - partnersSum).coerceAtLeast(0L)
        } else if (loan.hasPartners) {
            loan.myShareAmount.coerceAtLeast(0L)
        } else {
            loan.monthlyAmount
        }
    }
}

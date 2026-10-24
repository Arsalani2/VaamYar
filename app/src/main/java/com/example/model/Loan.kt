package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.util.ShamsiDateHelper

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val dueDay: Int,
    val monthlyAmount: Long,
    val totalMonths: Int,
    val paidMonths: Int,
    val startYear: Int = 1403,
    val startMonth: Int = 7, // مهر
    val endYear: Int = 1404,
    val endMonth: Int = 7,
    val category: String = "بانکی",
    val colorHex: Long = 0xFF1E88E5,
    val notes: String = "",
    val myShareAmount: Long = 0L, // سهم کاربر اصلی (شخصی)
    val isArchived: Boolean = false,
    val isCurrentMonthPaid: Boolean = false // آیا قسط ماه جاری پرداخت شده است
) {
    val remainingMonths: Int
        get() = (totalMonths - paidMonths).coerceAtLeast(0)

    val remainingTotalDebt: Long
        get() = remainingMonths.toLong() * monthlyAmount

    val remainingMyDebt: Long
        get() = remainingMonths.toLong() * (if (myShareAmount > 0) myShareAmount else monthlyAmount)

    val totalLoanAmount: Long
        get() = totalMonths.toLong() * monthlyAmount

    val totalPaidAmount: Long
        get() = paidMonths.toLong() * monthlyAmount

    val progress: Float
        get() = if (totalMonths > 0) (paidMonths.toFloat() / totalMonths).coerceIn(0f, 1f) else 0f

    val isCompleted: Boolean
        get() = paidMonths >= totalMonths

    val effectiveMyShare: Long
        get() = if (myShareAmount > 0) myShareAmount else monthlyAmount

    val othersShareAmount: Long
        get() = (monthlyAmount - effectiveMyShare).coerceAtLeast(0L)

    val startMonthName: String
        get() = ShamsiDateHelper.getMonthName(startMonth)

    val endMonthName: String
        get() = ShamsiDateHelper.getMonthName(endMonth)

    val payoffCountdownText: String
        get() = if (isCompleted) "تسویه شده ✓" else "$remainingMonths ماه مانده تا تسویه ($endMonthName $endYear)"
}

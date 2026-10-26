package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * فیکس bug.md/F1 + NB-31: رکورد واقعیِ هر قسط پرداخت‌شده — به‌جای شمارندهٔ ترتیبی
 * paidMonths که هیچ اطلاعی از ماهِ واقعیِ تقویم نداشت (گزارش کاربر: وامِ شروع‌شده در
 * تیر، در آبان «پرداخت این ماه» فقط نودِ تیر را تیک می‌زد).
 *
 * هر رکورد = یک قسطِ مشخص با سال/ماه شمسیِ واقعی و تاریخِ واقعی پرداخت؛ «ماه‌های
 * سپری‌شدهٔ بدون رکورد» همان عقب‌ماندگی‌ها هستند (NB-16). ستون‌های paidMonths و
 * isCurrentMonthPaid روی Loan به‌عنوان تصویرِ همگام‌شده با همین رکوردها نگه
 * داده می‌شوند (تطبیق count رکوردها) تا مصرف‌کنندگان قدیمی (بکاپ/خلاصه‌ها) سالم بمانند.
 *
 * ایندکس یکتای (loanId, year, month): هر وام در هر ماه حداکثر یک قسط — پرداختِ
 * دوباره idempotent است و دوبار ضربهٔ سریع رکورد تکراری نمی‌سازد.
 */
@Entity(
    tableName = "loan_installments",
    indices = [Index(value = ["loanId", "year", "month"], unique = true)]
)
data class LoanInstallment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanId: Long,
    /** شمارهٔ قسط از ۱ (نسبت به ماه شروع وام) */
    val installmentNumber: Int,
    /** سال شمسیِ واقعی این قسط */
    val year: Int,
    /** ماه شمسیِ واقعی این قسط (۱..۱۲) */
    val month: Int,
    val amount: Long,
    /** timestamp واقعی لحظهٔ پرداخت؛ ۰ = رکورد مهاجرت‌شده/seed (تاریخ واقعی نامعلوم) */
    val paidAtTimestamp: Long = 0L,
    /** تاریخ شمسیِ قالب‌شدهٔ لحظهٔ پرداخت؛ خالی = مهاجرت/seed */
    val paidDateShamsi: String = ""
)

package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderType(val titleFa: String, val defaultDescFa: String) {
    MONTHLY_SUMMARY(
        "پیامک خلاصه ابتدای ماه",
        "ارسال پیامک مجموع اقساط و وضعیت ماه به کاربر مبنا یا شماره خاص"
    ),
    PRE_DUE_ALERT(
        "هشدار ۲ روز قبل سررسید",
        "ارسال پیامک یادآوری قبل از موعد پرداخت اقساط"
    ),
    DUE_DAY_ACTION(
        "روز سررسید: تماس و پیامک",
        "تماس خودکار با شماره هدف در روز پرداخت و سپس ارسال پیامک تأیید"
    ),
    DEBTOR_NOTICE(
        "یادآوری وصول به شرکا",
        "پیامک هوشمند به شرکا (زهرا، محمدرضا و...) جهت واریز سهم وام"
    )
}

@Entity(tableName = "reminder_rules")
data class ReminderRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: ReminderType,
    val isEnabled: Boolean = true,
    val targetUserName: String = "",
    val targetPhone: String = "",
    val dayOfMonth: Int = 1, // روز ماه (۱ تا ۳۱)
    val hour: Int = 9,
    val minute: Int = 0,
    val isSmartMessage: Boolean = true, // استفاده از متن هوشمند با ارقام و نام دقیق
    val customMessageTemplate: String = "",
    val autoCallEnabled: Boolean = false, // زنگ زدن خودکار قبل از پیامک
    val lastTriggeredShamsi: String = "",
    val isSystemDefault: Boolean = true
) {
    val timeFormatted: String
        get() = String.format("%02d:%02d", hour, minute)
}

package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RemoteActionType(val titleFa: String) {
    SYSTEM_TOGGLE("فعال / غیرفعال‌سازی سیستم"),
    MARK_INSTALLMENT_PAID("ثبت پرداخت قسط وام"),
    QUERY_STATUS("استعلام مانده و وضعیت اقساط"),
    CUSTOM_RESPONSE("پاسخ سفارشی")
}

enum class PowerConsumptionMode(
    val titleFa: String,
    val subtitleFa: String,
    val descriptionFa: String
) {
    ECO(
        "حالت بهینه (کم‌مصرف)",
        "مصرف باتری بسیار ناچیز",
        "دریافت دستورات فقط در زمان باز بودن برنامه یا اتصال به شارژر برای جلوگیری از تخلیه باتری"
    ),
    REALTIME(
        "حالت ۲۴ ساعته (آماده‌باش کامل)",
        "پاسخگویی آنی",
        "دریافت و پردازش بلادرنگ تمام دستورات پیامکی در هر زمان از شبانه‌روز"
    ),
    SCHEDULED(
        "حالت زمان‌بندی‌شده (ساعات کاری)",
        "صرفه‌جویی متوازن",
        "فعالیت سیستم تنها از ساعت ۰۸:۰۰ صبح تا ۲۲:۰۰ شب برای حفظ منابع و شارژ دستگاه"
    )
}

@Entity(tableName = "remote_commands")
data class RemoteCommand(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val triggerPattern: String, // الگوی دستور مثلاً "بانک ملی - پرداخت شد"
    val actionType: RemoteActionType,
    val replyTemplate: String,
    val isEnabled: Boolean = true,
    val isSystemDefault: Boolean = true
)

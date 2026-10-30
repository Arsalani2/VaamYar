package com.aistudio.vamyar.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aistudio.vamyar.MainActivity
import com.aistudio.vamyar.util.AppPreferences
import com.aistudio.vamyar.util.NumberFormatter
import java.util.Calendar

object NotificationHelper {

    const val CHANNEL_ID = "vamyar_installments_channel"
    const val CHANNEL_NAME = "اعلان سررسید اقساط"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "یادآوری تاریخ سررسید و پرداخت اقساط وام‌ها"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        id: Int = NOTIFICATION_ID
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, builder.build())
    }

    /**
     * فیکس bug.md/P2-7: زمان‌بندی چکِ روزانهٔ اعلان‌ها.
     *
     * قبلاً setInexactRepeating(INTERVAL_DAY) استفاده می‌شد که زیر Doze ساعت‌ها عقب
     * می‌افتد؛ پرمیشن SCHEDULE_EXACT_ALARM در Manifest بود ولی canScheduleExactAlarms()
     * هیچ‌جا چک نمی‌شد و ساعت چک هم ۱۰:۰۰ هاردکد بود (قانون ReminderRule.hour مرده بود
     * و از آن جدول هیچ ردیفی هم ساخته نمی‌شود).
     *
     * اکنون: آلارم «یک‌بارهٔ دقیق» (setExactAndAllowWhileIdle) برای نوبتِ بعدیِ ساعتِ
     * یادآوریِ ذخیره‌شده در AppPreferences (پیش‌فرض ۱۰:۰۰)؛ InstallmentAlarmReceiver بعد
     * از هر چک نوبتِ فردا را دوباره زمان‌بندی می‌کند و BootCompletedReceiver (فیکس P2-1)
     * بعد از ری‌بوت/تغییر ساعتِ سیستم زنجیره را از نو برپا می‌کند. بدون اجازهٔ exact،
     * به setAndAllowWhileIdle فول‌بک می‌کنیم (بهترین تلاشِ در دسترس).
     */
    fun scheduleDailyCheck(context: Context) {
        val prefs = AppPreferences(context)
        scheduleDailyCheck(context, prefs.reminderHour, prefs.reminderMinute)
    }

    fun scheduleDailyCheck(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, InstallmentAlarmReceiver::class.java).apply {
            action = InstallmentAlarmReceiver.ACTION_CHECK_INSTALLMENTS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerAtMillis = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                // آلارم دقیق: زیر Doze هم سرِ ساعتِ مقرر اجرا می‌شود
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // فول‌بک: اجازهٔ exact نیست یا اندروید قدیمی‌تر است — بهترین تلاشِ در دسترس
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // اگر اجازه در لحظهٔ ثبت از دست رفت، دوباره با بهترین تلاش تلاش می‌کنیم
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------ فیکس bug.md/SB-08: راهنمای اعلان/باتری OEM

    /**
     * فیکس bug.md/SB-08: متن Toast وقتی اجازهٔ «ساعت و هشدار دقیق» رد شده —
     * کاربر باید بداند یادآوری‌ها دیرتر ولی همچنان می‌رسند (فول‌بک واقعی است).
     */
    const val OEM_EXACT_ALARM_FALLBACK_TEXT =
        "اجازهٔ «ساعت و هشدار دقیق» رد شده است؛ یادآوری‌ها دیرتر ولی همچنان می‌رسند."

    /** فیکس bug.md/SB-08: آیا برنامه اجازهٔ exact alarm دارد؟ (اندروید < S همیشه true) */
    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    /**
     * فیکس bug.md/SB-08: مسیرهای تنظیمات باتری/اعلانِ برندهای رایج (whitelist).
     * سازندهٔ دستگاه (Build.MANUFACTURER، lowercased مقایسه می‌شود) به مسیرهای
     * مخصوص همان برند map می‌شود؛ برند ناشناس فقط گام‌های عمومی را می‌گیرد.
     * همیشه گام‌های عمومی (اعلان‌ها + بهینه‌سازی باتری) هم پیوست می‌شوند.
     * خالص و قابل‌تست JVM — پارامتر سازنده صریح است، نه Build مستقیم.
     */
    fun oemBatteryPaths(manufacturer: String): List<String> {
        val m = manufacturer.lowercase()
        val brandSpecific = when {
            "xiaomi" in m || "redmi" in m || "poco" in m -> listOf(
                "تنظیمات ← اپ‌ها ← مدیریت اپ‌ها ← وام‌یار ← صرفه‌جویی باتری ← «بدون محدودیت»",
                "تنظیمات ← باتری ← بهینه‌ساز باتری ← وام‌یار را از پایان خودکار مستثنا کنید"
            )
            "huawei" in m || "honor" in m -> listOf(
                "تنظیمات ← باتری ← راه‌اندازی اپ ← وام‌یار ← کلیدهای «مدیریت خودکار» را خاموش کنید",
                "تنظیمات ← اپ‌ها ← وام‌یار ← مصرف باتری ← راه‌اندازی ← «بدون محدودیت»"
            )
            "samsung" in m -> listOf(
                "تنظیمات ← باتری ← محدودیت‌های پس‌زمینه ← وام‌یار ← «بدون محدودیت»",
                "تنظیمات ← نگهداری دستگاه ← باتری ← اپ‌های خواب‌رفته ← وام‌یار را از فهرست خارج کنید"
            )
            else -> emptyList()
        }
        val generic = listOf(
            "تنظیمات ← اپ‌ها ← وام‌یار ← اعلان‌ها ← مطمئن شوید اعلان‌ها روشن‌اند",
            "تنظیمات ← باتری ← بهینه‌سازی باتری ← وام‌یار ← «بهینه‌سازی نشود»"
        )
        return brandSpecific + generic
    }
}

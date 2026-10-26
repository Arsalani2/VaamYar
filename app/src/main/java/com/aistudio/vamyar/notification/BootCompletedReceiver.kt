package com.aistudio.vamyar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * فیکس bug.md/P2-1: آلارم‌های AlarmManager با ری‌بوت گوشی پاک می‌شوند؛ پرمیشن
 * RECEIVE_BOOT_COMPLETED در Manifest بود ولی هیچ Receiver برایش تعریف نشده بود و
 * بعد از ری‌بوت، چکِ روزانهٔ اعلان‌ها برای همیشه از کار می‌افتاد.
 *
 * این Receiver بعد از اتمام بوت (BOOT_COMPLETED) و نیز تغییر ساعت/منطقهٔ زمانیِ
 * سیستم (TIME_SET / TIMEZONE_CHANGED — که می‌تواند آلارمِ یک‌بارهٔ زمان‌بندی‌شده را
 * نامعتبر کند) چکِ روزانه را با ساعتِ ذخیره‌شده در تنظیمات دوباره زمان‌بندی
 * می‌کند تا زنجیرهٔ یادآوری هرگز گم نشود.
 *
 * توجه: هر سه اکشن protected broadcast هستند و فقط سیستم می‌تواند بفرستد.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val shouldReschedule = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        if (shouldReschedule) {
            NotificationHelper.scheduleDailyCheck(context)
        }
    }
}

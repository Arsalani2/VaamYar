package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.util.AppPreferences
import com.example.util.NumberFormatter
import com.example.util.MonthlyRolloverHelper
import com.example.util.ShamsiDateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class InstallmentAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_INSTALLMENTS = "com.example.vamyar.CHECK_INSTALLMENTS"
        const val ACTION_TEST_NOTIFICATION = "com.example.vamyar.TEST_NOTIFICATION"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TEST_NOTIFICATION) {
            NotificationHelper.showNotification(
                context = context,
                title = "تست اعلان وام‌یار 🔔",
                message = "اعلان‌های سررسید اقساط با موفقیت فعال هستند و به موقع شما را مطلع خواهند کرد."
            )
            return
        }

        // Daily alarm check
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // فیکس bug.md/P1-6: چک روزانهٔ تغییر ماه — حتی اگر اپ مدتی باز نشده باشد،
                // پرچم‌های «پرداخت ماه جاری» با عوض شدن ماه شمسی رِسِت می‌شوند.
                MonthlyRolloverHelper.rolloverIfNeeded(context)

                val db = AppDatabase.getDatabase(context)
                val loans = db.loanDao().getAllActiveLoans().firstOrNull() ?: emptyList()
                // فیکس bug.md/P2-4: Receiver همیشه از ساعت سیستم می‌خواند و تاریخ سفارشیِ تنظیمات را
                // نادیده می‌گرفت؛ اکنون از همان «تاریخ مؤثر» اپ (getEffectiveShamsiDate از فیکس P1-6)
                // استفاده می‌کند تا اعلان‌های «امروز/فردا» دقیقاً هماهنگ با منطق داخلی اپ باشد
                // (رِسِت ماهانهٔ P1-6 از قبل مؤثر بود؛ این اعلان‌ها هم مؤثر شدند).
                val currentShamsi = ShamsiDateHelper.getEffectiveShamsiDate(AppPreferences(context))
                val today = currentShamsi.day

                // فیکس bug.md/P2-2: «فردا» با تقویم شمسیِ واقعی محاسبه می‌شود، نه today+1 ساده.
                // قبلاً در آخرین روز هر ماه (مثلاً ۳۰ آبان) یادآوریِ قسط اول ماه بعد (۱ آذر)
                // هرگز پخش نمی‌شد. حالا در آخرین روز ماه، فردا = ۱ ماه بعد است (سال هم در
                // اسفند درست می‌چرخد) و عنوان اعلان تاریخ دقیق فردا را نشان می‌دهد.
                val daysInCurrentMonth = ShamsiDateHelper.getDaysInShamsiMonth(currentShamsi.year, currentShamsi.month)
                val tomorrowIsNextMonth = today >= daysInCurrentMonth
                val tomorrowDay = if (tomorrowIsNextMonth) 1 else today + 1
                val tomorrowMonthName = if (tomorrowIsNextMonth) {
                    ShamsiDateHelper.getMonthName(if (currentShamsi.month == 12) 1 else currentShamsi.month + 1)
                } else {
                    currentShamsi.monthName
                }

                // فیکس bug.md/P2-3: وامِ تسویه‌نشده‌ای که قسط همین ماه را پرداخت کرده
                // (isCurrentMonthPaid) دیگر اعلانِ بی‌فایده‌ی «امروز موعد پرداخت» نمی‌گیرد.
                // یادآوریِ «فردا» هم فقط وقتی حذف می‌شود که فردا در همین ماه باشد و قسط همین
                // ماه پرداخت شده باشد؛ برای سررسیدِ اول ماه بعد (که پرچم هنوز وجود ندارد)
                // یادآوری مثل همیشه پخش می‌شود.
                val activeLoans = loans.filter { !it.isCompleted }
                val dueToday = activeLoans.filter { !it.isCurrentMonthPaid && it.dueDay == today }
                val dueTomorrow = activeLoans.filter {
                    it.dueDay == tomorrowDay && (tomorrowIsNextMonth || !it.isCurrentMonthPaid)
                }

                if (dueToday.isNotEmpty()) {
                    val titles = dueToday.joinToString("، ") { it.title }
                    val totalAmount = dueToday.sumOf { it.monthlyAmount }
                    NotificationHelper.showNotification(
                        context = context,
                        title = "سررسید قسط امروز (${NumberFormatter.toPersianDigits(today)} ${currentShamsi.monthName})",
                        message = "امروز موعد پرداخت $titles به مبلغ ${NumberFormatter.formatPrice(totalAmount)} است.",
                        id = 1002
                    )
                } else if (dueTomorrow.isNotEmpty()) {
                    val titles = dueTomorrow.joinToString("، ") { it.title }
                    NotificationHelper.showNotification(
                        context = context,
                        title = "یادآوری: سررسید فردا (${NumberFormatter.toPersianDigits(tomorrowDay)} $tomorrowMonthName) ⏰",
                        message = "فردا موعد پرداخت $titles فرا می‌رسد.",
                        id = 1003
                    )
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}

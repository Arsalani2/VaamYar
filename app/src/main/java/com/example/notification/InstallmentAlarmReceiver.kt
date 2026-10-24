package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.util.NumberFormatter
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
                val db = AppDatabase.getDatabase(context)
                val loans = db.loanDao().getAllActiveLoans().firstOrNull() ?: emptyList()
                val currentShamsi = ShamsiDateHelper.getCurrentShamsiDate()
                val today = currentShamsi.day

                // Find loans due today or tomorrow
                val dueToday = loans.filter { !it.isCompleted && it.dueDay == today }
                val dueTomorrow = loans.filter { !it.isCompleted && it.dueDay == (today + 1) }

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
                        title = "یادآوری: سررسید فردا ⏰",
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

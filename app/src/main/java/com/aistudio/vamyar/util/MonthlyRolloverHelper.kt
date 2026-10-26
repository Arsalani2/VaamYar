package com.aistudio.vamyar.util

import android.content.Context
import androidx.room.withTransaction
import com.aistudio.vamyar.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * فیکس bug.md/P1-6: رِسِت خودکار ماهانه.
 *
 * پرچم‌های «پرداخت ماه جاری» (isCurrentMonthPaid وام‌ها و isPaidForCurrentMonth سهم‌ها)
 * قبلاً فقط با دکمهٔ دستی صفر می‌شدند؛ در نتیجه با عوض شدن ماه شمسی، ماه جدید هم
 * تیک‌خورده به‌نظر می‌رسید. این هِلپر ماهِ مؤثر اپ (تاریخ سفارشی یا سیستم) را به یک
 * «کلید ماه» مثل 1405-08 تبدیل می‌کند و اگر با آخرین رِسِتِ ثبت‌شده فرق داشته باشد،
 * هر دو جدول را درون یک تراکنش اتمیک صفر می‌کند و کلید جدید را فقط پس از موفقیت ذخیره می‌کند.
 *
 * نکتهٔ طراحی: نگهداریِ کلید در SharedPreferences (و نه ستونی در اسکیمای Room) عمدی است
 * تا نیازی به Migration نباشد — با توجه به حساسیت دادهٔ مالی و ریسک‌های P1-1.
 *
 * فراخوانی: شروع اپ (VamyarApp.onCreate) + چک روزانهٔ اعلان‌ها (InstallmentAlarmReceiver).
 */
object MonthlyRolloverHelper {

    /**
     * اگر ماه مؤثر از آخرین رِسِت گذشته باشد، پرچم‌های «پرداخت ماه جاری» را رِسِت می‌کند.
     *
     * @return true اگر رِسِت انجام شد، false اگر نیازی نبود (همان ماه یا اولین اجرا).
     */
    suspend fun rolloverIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = AppPreferences(context)
        val currentKey = ShamsiDateHelper.monthKey(
            ShamsiDateHelper.getEffectiveShamsiDate(prefs)
        )
        val lastKey = prefs.lastRolloverMonthKey

        // اولین اجرا بعد از این فیکس: فقط کلید ماه ثبت می‌شود تا وضعیت فعلی کاربر
        // دست‌نخورده باقی بماند؛ رِسِتِ واقعی از عوض شدن ماهِ بعد انجام می‌شود.
        if (lastKey.isEmpty()) {
            prefs.lastRolloverMonthKey = currentKey
            return@withContext false
        }

        if (currentKey == lastKey) {
            return@withContext false
        }

        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            db.loanDao().resetCurrentMonthPaidFlags()
            db.debtorShareDao().resetMonthlyPaidStatus()
        }

        // کلید ماه فقط پس از موفقیت تراکنش ذخیره می‌شود؛ اگر وسط کار خطا رخ دهد،
        // اجرای بعدی اپ/آلارم دوباره تلاش می‌کند (rollback یعنی هیچ چیز نیمه‌کاره نمانده).
        prefs.lastRolloverMonthKey = currentKey
        true
    }
}

package com.aistudio.vamyar.util

import android.content.Context
import android.content.SharedPreferences
import com.aistudio.vamyar.ui.components.LoanViewMode
import com.aistudio.vamyar.ui.theme.AppThemeMode
import com.aistudio.vamyar.ui.theme.ThemePreset

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vamyar_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VIEW_MODE = "key_loan_view_mode"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_THEME_PRESET = "key_theme_preset"
        private const val KEY_PERSIAN_DIGITS = "key_persian_digits"
        private const val KEY_COMPACT_THOUSANDS = "key_compact_thousands"
        private const val KEY_HAS_SEEN_GUIDE = "key_has_seen_guide"
        private const val KEY_CUSTOM_DATE_ENABLED = "key_custom_date_enabled"
        private const val KEY_CUSTOM_YEAR = "key_custom_year"
        private const val KEY_CUSTOM_MONTH = "key_custom_month"
        private const val KEY_CUSTOM_DAY = "key_custom_day"
        private const val KEY_LAST_ROLLOVER_MONTH = "key_last_rollover_month" // فیکس bug.md/P1-6
        private const val KEY_REMINDER_HOUR = "key_reminder_hour" // فیکس bug.md/P2-7
        private const val KEY_REMINDER_MINUTE = "key_reminder_minute" // فیکس bug.md/P2-7
        private const val KEY_INITIAL_DATA_SEEDED = "key_initial_data_seeded" // فیکس bug.md/P3-10
        private const val KEY_LAST_AUTO_BACKUP_AT = "key_last_auto_backup_at" // فیکس bug.md/SB-07
    }

    var loanViewMode: LoanViewMode
        get() {
            val name = prefs.getString(KEY_VIEW_MODE, LoanViewMode.RECTANGLE.name)
            return try {
                LoanViewMode.valueOf(name ?: LoanViewMode.RECTANGLE.name)
            } catch (e: Exception) {
                LoanViewMode.RECTANGLE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_VIEW_MODE, value.name).apply()
        }

    var themeMode: AppThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            return try {
                AppThemeMode.valueOf(name ?: AppThemeMode.SYSTEM.name)
            } catch (e: Exception) {
                AppThemeMode.SYSTEM
            }
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    var themePreset: ThemePreset
        get() {
            val name = prefs.getString(KEY_THEME_PRESET, ThemePreset.EMERALD_GOLD.name)
            return try {
                ThemePreset.valueOf(name ?: ThemePreset.EMERALD_GOLD.name)
            } catch (e: Exception) {
                ThemePreset.EMERALD_GOLD
            }
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_PRESET, value.name).apply()
        }

    var usePersianDigits: Boolean
        get() = prefs.getBoolean(KEY_PERSIAN_DIGITS, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSIAN_DIGITS, value).apply()

    var compactThousands: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_THOUSANDS, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_THOUSANDS, value).apply()

    var hasSeenAppGuide: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_GUIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_GUIDE, value).apply()

    var isCustomDateEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_DATE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CUSTOM_DATE_ENABLED, value).apply()

    var customYear: Int
        get() = prefs.getInt(KEY_CUSTOM_YEAR, 1403)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_YEAR, value).apply()

    var customMonth: Int
        get() = prefs.getInt(KEY_CUSTOM_MONTH, 7)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_MONTH, value).apply()

    var customDay: Int
        get() = prefs.getInt(KEY_CUSTOM_DAY, 1)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_DAY, value).apply()

    // فیکس bug.md/P1-6: کلید ماهِ آخرین رِسِت خودکار (مثل "1405-08")؛
    // خالی یعنی هنوز هیچ رِسِتی ثبت نشده (اولین اجرای اپ).
    var lastRolloverMonthKey: String
        get() = prefs.getString(KEY_LAST_ROLLOVER_MONTH, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LAST_ROLLOVER_MONTH, value).apply()
        }

    // فیکس bug.md/P2-7: ساعتِ چکِ روزانهٔ اعلان‌ها دیگر هاردکد نیست و اینجا persist
    // می‌شود (پیش‌فرض ۱۰:۰۰، مثل رفتار قبلی). برای تغییر کافی است این دو کلید نوشته
    // و NotificationHelper.scheduleDailyCheck(context) دوباره فراخوانی شود —
    // Receiver و BootCompletedReceiver هر دو از همین منبع می‌خوانند.
    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, 10)
        set(value) {
            prefs.edit().putInt(KEY_REMINDER_HOUR, value.coerceIn(0, 23)).apply()
        }

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, 0)
        set(value) {
            prefs.edit().putInt(KEY_REMINDER_MINUTE, value.coerceIn(0, 59)).apply()
        }

    // فیکس bug.md/P3-10 (تصمیم A1): پرچم «داده‌ی راهنما seed شده یا نه» —
    // seed دقیقاً یک‌بار در هر نصب انجام می‌شود و این پرچم پس از اولین اجرا
    // برای همیشه true می‌ماند تا حذف وام‌های دمو توسط کاربر هرگز به seedِ
    // دوباره منجر نشود (باگ گزارش‌شده: وام‌های دمو بعد از هر پاک‌سازی در
    // اجرای بعدی دوباره ساخته می‌شدند).
    var initialDataSeeded: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_DATA_SEEDED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INITIAL_DATA_SEEDED, value).apply()
        }

    // فیکس bug.md/SB-07: زمان آخرین بکاپ خودکار (۰ = هنوز هیچ). مبنای
    // «بکاپ هفتگی در startup» است و فقط پس از نوشتن موفق فایل set می‌شود.
    var lastAutoBackupAt: Long
        get() = prefs.getLong(KEY_LAST_AUTO_BACKUP_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_AUTO_BACKUP_AT, value).apply()
        }

    fun setCustomDate(year: Int, month: Int, day: Int) {
        prefs.edit()
            .putBoolean(KEY_CUSTOM_DATE_ENABLED, true)
            .putInt(KEY_CUSTOM_YEAR, year)
            .putInt(KEY_CUSTOM_MONTH, month)
            .putInt(KEY_CUSTOM_DAY, day)
            .apply()
    }

    fun clearCustomDate() {
        prefs.edit().putBoolean(KEY_CUSTOM_DATE_ENABLED, false).apply()
    }
}

package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.components.LoanViewMode

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vamyar_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VIEW_MODE = "key_loan_view_mode"
        private const val KEY_CUSTOM_DATE_ENABLED = "key_custom_date_enabled"
        private const val KEY_CUSTOM_YEAR = "key_custom_year"
        private const val KEY_CUSTOM_MONTH = "key_custom_month"
        private const val KEY_CUSTOM_DAY = "key_custom_day"
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

package com.aistudio.vamyar.util

import android.content.Context
import android.os.Build
import com.aistudio.vamyar.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Calendar

/**
 * فیکس bug.md/SB-04: کرش‌لاگ محلیِ آفلاین (تصمیم کاربر: بدون Crashlytics — هیچ
 * داده‌ای به هیچ سروری ارسال نمی‌شود). هندلرِ استثناهای دست‌نیافتنی در
 * VamyarApp.onCreate نصب می‌شود؛ هر کرش در
 * filesDir/crash_logs/crash-<timestamp>.txt با نسخهٔ اپ/سازنده و مدل دستگاه/
 * Android API/stacktrace نوشته می‌شود، حداکثر [MAX_CRASH_LOG_FILES] فایل نگه
 * داشته می‌شود و در پایان به handler قبلی سیستم delegate می‌شود تا رفتار
 * استاندارد اندروید (دیالوگ کرش/پایان پروسه) دست‌نخورده بماند.
 *
 * کاربر از تنظیمات (بخش «عیب‌یابی») می‌تواند آخرین کرش را ببیند، گزارش را با
 * FileProvider به اشتراک بگذارد یا لاگ‌ها را پاک کند.
 */
object CrashLogHelper {

    const val CRASH_LOG_DIR_NAME = "crash_logs"
    const val CRASH_LOG_FILE_PREFIX = "crash-"
    const val CRASH_LOG_FILE_EXTENSION = ".txt"
    const val MAX_CRASH_LOG_FILES = 5
    const val NO_CRASH_RECORDED_TEXT = "هیچ خطایی ثبت نشده است."

    // سقف طول stacktrace در فایل — stacktrace پاتولوژیک نباید فایل غول‌پیکر بسازد
    private const val MAX_STACK_TRACE_CHARS = 20_000

    fun crashLogDir(context: Context): File = File(context.filesDir, CRASH_LOG_DIR_NAME)

    /**
     * نصب هندلر پیش‌فرض استثناهای دست‌نیافتنی. نوشتن لاگ هرگز نباید رفتار
     * استاندارد سیستم را جایگزین کند؛ پس از نوشتن (حتی در خطای نوشتن) همیشه
     * به handler قبلی delegate می‌شود.
     */
    fun installDefaultHandler(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(appContext, thread, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * نوشتن لاگِ یک کرش در دایرکتوری خصوصی اپ. در هر خطایی null برمی‌گرداند —
     * مسیر کرش هرگز خودش exception پرتاب نمی‌کند.
     */
    fun writeCrashLog(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        timestampMillis: Long = System.currentTimeMillis()
    ): File? = writeCrashLogToDir(crashLogDir(context), buildCrashLogContent(thread, throwable, timestampMillis), timestampMillis)

    /**
     * هستهٔ قابل‌تستِ JVM (بدون Context): محتوای داده‌شده را با نام
     * crash-<timestamp>.txt در دایرکتوری می‌نویسد و سپس کپ ۵ فایل را اعمال می‌کند.
     */
    fun writeCrashLogToDir(dir: File, content: String, timestampMillis: Long): File? {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val file = uniqueCrashLogFile(dir, timestampMillis)
            file.writeText(content, Charsets.UTF_8)
            enforceMaxFiles(dir)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * محتوای کامل فایل لاگ: زمان شمسی + ساعت، نسخه/versionCode، سازنده/مدل دستگاه،
     * Android API، نام رشتهٔ اجرا و stacktrace.
     */
    fun buildCrashLogContent(thread: Thread, throwable: Throwable, timestampMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val shamsi = ShamsiDateHelper.gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val clock = NumberFormatter.formatClock(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            usePersian = false
        )
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
            .toString().trim().take(MAX_STACK_TRACE_CHARS)
        return buildString {
            appendLine("گزارش خطای وام‌یار (crash log)")
            appendLine("--------------------------------")
            appendLine("زمان: ${shamsi.formatted()} — ساعت $clock")
            appendLine("نسخهٔ اپ: ${BuildConfig.VERSION_NAME} (کد ${BuildConfig.VERSION_CODE})")
            appendLine("دستگاه: ${Build.MANUFACTURER} ${Build.MODEL} — Android API ${Build.VERSION.SDK_INT}")
            appendLine("رشتهٔ اجرا: ${thread.name}")
            appendLine("--------------------------------")
            appendLine("Stacktrace:")
            appendLine(stackTrace)
        }
    }

    /** همهٔ فایل‌های لاگ، از جدیدترین به قدیمی‌ترین (نام فایل = timestamp). */
    fun listCrashLogs(context: Context): List<File> =
        crashLogDir(context)
            .listFiles { f ->
                f.isFile && f.name.startsWith(CRASH_LOG_FILE_PREFIX) && f.name.endsWith(CRASH_LOG_FILE_EXTENSION)
            }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun latestCrashLog(context: Context): File? = listCrashLogs(context).firstOrNull()

    /** پاک‌کردن همهٔ لاگ‌ها؛ false یعنی خطای I/O. */
    fun clearCrashLogs(context: Context): Boolean = try {
        crashLogDir(context).listFiles()?.forEach { it.delete() }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /**
     * متن خلاصهٔ «آخرین کرش» برای صفحهٔ تنظیمات — با تاریخ شمسی و ساعتِ تنظیمات
     * ارقام کاربر؛ اگر لاگی نبود [NO_CRASH_RECORDED_TEXT].
     */
    fun latestCrashSummary(context: Context, usePersianDigits: Boolean): String {
        val latest = latestCrashLog(context) ?: return NO_CRASH_RECORDED_TEXT
        val millis = crashTimestampFromFileName(latest.name)
            ?: return "آخرین کرش: ${latest.name}"
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val shamsi = ShamsiDateHelper.gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val clock = NumberFormatter.formatClock(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            usePersian = usePersianDigits
        )
        return "آخرین کرش: ${shamsi.formatted(usePersianDigits)}، ساعت $clock (${latest.name})"
    }

    /** استخراج timestamp از نام فایل لاگ (crash-<millis>.txt یا crash-<millis>-1.txt). */
    fun crashTimestampFromFileName(name: String): Long? = name
        .removePrefix(CRASH_LOG_FILE_PREFIX)
        .removeSuffix(CRASH_LOG_FILE_EXTENSION)
        .substringBefore('-')
        .toLongOrNull()

    /**
     * کپ [maxFiles] فایل — قدیمی‌ترین‌ها (کوچک‌ترین timestamp در نام) حذف می‌شوند.
     */
    internal fun enforceMaxFiles(dir: File, maxFiles: Int = MAX_CRASH_LOG_FILES) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(CRASH_LOG_FILE_PREFIX) } ?: return
        if (files.size > maxFiles) {
            files.sortedBy { it.name }.take(files.size - maxFiles).forEach { it.delete() }
        }
    }

    /** نام فایل یکتا — اگر همان میلی‌ثانیه دوباره کرش رخ داد، پسوند شاخص. */
    private fun uniqueCrashLogFile(dir: File, timestampMillis: Long): File {
        var candidate = File(dir, "$CRASH_LOG_FILE_PREFIX$timestampMillis$CRASH_LOG_FILE_EXTENSION")
        var index = 1
        while (candidate.exists() && index < 100) {
            candidate = File(dir, "$CRASH_LOG_FILE_PREFIX$timestampMillis-$index$CRASH_LOG_FILE_EXTENSION")
            index++
        }
        return candidate
    }
}

package com.aistudio.vamyar.util

import android.content.Context
import com.aistudio.vamyar.BuildConfig
import com.aistudio.vamyar.data.LoanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * فیکس bug.md/SB-07: بکاپ خودکار هفتگی محلی (تصمیم کاربر — کاملاً آفلاین، بدون
 * ابر/سرویس خارجی). در startup اپ، اگر از آخرین بکاپ خودکار بیش از ۷ روز گذشته
 * باشد (یا هنوز هیچ نبوده)، یک فایل بکاپ JSON کامل در filesDir/auto_backups
 * نوشته می‌شود؛ حداکثر [MAX_AUTO_BACKUP_FILES] نسخهٔ آخر می‌ماند (قدیمی‌ترین‌ها
 * حذف می‌شوند) و همهٔ کارها روی Dispatchers.IO انجام می‌شود.
 *
 * هستهٔ نوشتن/چرخش ([writeAutoBackupToDir]) بدون Context قابل‌تست JVM است
 * (همان الگوی CrashLogHelper). محتوای فایل همان فرمت بکاپ دستی
 * (BackupHelper.createBackupJson) است — از جمله آیکون‌های embedشده (NB-18) و
 * اقساط واقعی (F1+NB-31) — تا در صورت نیاز قابل restore باشد.
 *
 * نکتهٔ صادقانه (در صفحهٔ تنظیمات هم آمده): این فایل‌ها فقط روی همین دستگاه
 * و داخل حافظهٔ برنامه‌اند و با حذف نصب برنامه پاک می‌شوند؛ جایگزینِ خروجی
 * دوره‌ای دستی نیستند، لایهٔ اطمینان اضافه‌اند.
 */
object AutoBackupHelper {

    const val AUTO_BACKUP_DIR_NAME = "auto_backups"
    const val AUTO_BACKUP_FILE_PREFIX = "auto-"
    const val AUTO_BACKUP_FILE_EXTENSION = ".json"
    const val MAX_AUTO_BACKUP_FILES = 3
    const val AUTO_BACKUP_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000L
    const val NO_AUTO_BACKUP_TEXT = "هنوز بکاپ خودکاری ساخته نشده است."

    fun autoBackupDir(context: Context): File =
        File(context.filesDir, AUTO_BACKUP_DIR_NAME)

    /**
     * آیا وقتِ بکاپ خودکار است؟ ۰ یعنی «هرگز نشده» → اولین اجرای اپ هم بکاپ
     * پایه می‌سازد تا همیشه حداقل یک نسخه موجود باشد.
     */
    fun shouldAutoBackup(lastAutoBackupAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        lastAutoBackupAt <= 0L || now - lastAutoBackupAt >= AUTO_BACKUP_INTERVAL_MS

    /**
     * بکاپ خودکار در صورت نیاز — از VamyarApp.onCreate (پس از rollover/seed)
     * صدا می‌شود. داده‌ها مستقیم از DAOهای Room (first()) خوانده می‌شوند تا به
     * جمع‌آوریِ UI وابسته نباشد. فقط پس از نوشتن موفق فایل، زمان در تنظیمات
     * ثبت می‌شود (اگر وسط کار شکست بخورد، اجرای بعدی دوباره تلاش می‌کند).
     * هر خطایی را می‌بلعد و false برمی‌گرداند — بکاپ خودکار هرگز نباید
     * راه‌اندازی اپ را بشکند.
     */
    suspend fun performAutoBackupIfNeeded(context: Context, repository: LoanRepository): Boolean =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val appPreferences = AppPreferences(appContext)
            val now = System.currentTimeMillis()
            if (!shouldAutoBackup(appPreferences.lastAutoBackupAt, now)) return@withContext false
            try {
                val users = repository.allUsers.first()
                val loans = repository.allLoans.first()
                val shares = repository.allShares.first()
                val records = repository.allRecords.first()
                val installments = repository.allInstallments.first()

                // فیکس bug.md/NB-18: آیکون‌های سفارشیِ موجود روی دیسک، مثل بکاپ
                // دستی به‌صورت base64 embed می‌شوند (نه مسیر absolute).
                val icons = loans.mapNotNull { loan ->
                    val path = loan.customIconPath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val iconFile = File(path)
                    if (!iconFile.exists()) return@mapNotNull null
                    BackupIcon(
                        loanId = loan.id,
                        fileName = iconFile.name,
                        dataBase64 = android.util.Base64.encodeToString(
                            iconFile.readBytes(),
                            android.util.Base64.NO_WRAP
                        )
                    )
                }

                val preferences = BackupPreferences(
                    themeMode = appPreferences.themeMode.name,
                    themePreset = appPreferences.themePreset.name,
                    usePersianDigits = appPreferences.usePersianDigits,
                    compactThousands = appPreferences.compactThousands,
                    customYear = appPreferences.customYear,
                    customMonth = appPreferences.customMonth,
                    customDay = appPreferences.customDay,
                    isCustomDateEnabled = appPreferences.isCustomDateEnabled
                )

                val json = BackupHelper.createBackupJson(
                    users = users,
                    loans = loans,
                    shares = shares,
                    records = records,
                    installments = installments,
                    icons = icons,
                    preferences = preferences,
                    // فیکس bug.md/P2-13: نسخه از BuildConfig تزریق می‌شود، نه هاردکد.
                    appVersion = BuildConfig.VERSION_NAME,
                    shamsiDateFormatted = ShamsiDateHelper.getEffectiveShamsiDate(appPreferences)
                        .toStandardFormat(usePersianDigits = false)
                )

                val file = writeAutoBackupToDir(autoBackupDir(appContext), json, now)
                if (file != null) {
                    // ثبت زمان فقط پس از موفقیت — شکست یعنی تلاش دوباره در اجرای بعدی.
                    appPreferences.lastAutoBackupAt = now
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    /**
     * هستهٔ قابل‌تست JVM (بدون Context): نوشتن auto-<timestamp>.json در
     * دایرکتوری داده‌شده + اعمال کپ [maxFiles] (قدیمی‌ترین‌ها حذف می‌شوند).
     */
    fun writeAutoBackupToDir(
        dir: File,
        json: String,
        timestampMillis: Long,
        maxFiles: Int = MAX_AUTO_BACKUP_FILES
    ): File? {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val file = uniqueAutoBackupFile(dir, timestampMillis)
            file.writeText(json, Charsets.UTF_8)
            enforceMaxFiles(dir, maxFiles)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** همهٔ فایل‌های بکاپ خودکار، از جدیدترین به قدیمی‌ترین. */
    fun listAutoBackups(dir: File): List<File> =
        dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(AUTO_BACKUP_FILE_PREFIX) &&
                file.name.endsWith(AUTO_BACKUP_FILE_EXTENSION)
        }?.sortedByDescending { it.name } ?: emptyList()

    /** خلاصهٔ «آخرین بکاپ خودکار» برای صفحهٔ تنظیمات — تاریخ شمسی + ساعت. */
    fun latestAutoBackupSummary(context: Context, usePersianDigits: Boolean): String {
        val latest = listAutoBackups(autoBackupDir(context)).firstOrNull()
            ?: return NO_AUTO_BACKUP_TEXT
        val millis = autoBackupTimestampFromFileName(latest.name)
            ?: return "آخرین بکاپ خودکار: ${latest.name}"
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
        return "آخرین بکاپ خودکار: ${shamsi.formatted(usePersianDigits)}، ساعت $clock"
    }

    /** استخراج timestamp از نام فایل (auto-<millis>.json یا auto-<millis>-N.json). */
    fun autoBackupTimestampFromFileName(name: String): Long? = name
        .removePrefix(AUTO_BACKUP_FILE_PREFIX)
        .removeSuffix(AUTO_BACKUP_FILE_EXTENSION)
        .substringBefore('-')
        .toLongOrNull()

    /** کپ [maxFiles] فایل — قدیمی‌ترین‌ها (timestamp کوچک‌تر در نام) حذف می‌شوند. */
    internal fun enforceMaxFiles(dir: File, maxFiles: Int = MAX_AUTO_BACKUP_FILES) {
        val files = dir.listFiles { file ->
            file.isFile && file.name.startsWith(AUTO_BACKUP_FILE_PREFIX)
        } ?: return
        if (files.size > maxFiles) {
            files.sortedBy { it.name }.take(files.size - maxFiles).forEach { it.delete() }
        }
    }

    /** نام فایل یکتا — اگر همان میلی‌ثانیه دوباره نوشته شد، پسوند شاخص -N. */
    private fun uniqueAutoBackupFile(dir: File, timestampMillis: Long): File {
        var candidate = File(dir, "$AUTO_BACKUP_FILE_PREFIX$timestampMillis$AUTO_BACKUP_FILE_EXTENSION")
        var index = 1
        while (candidate.exists() && index < 100) {
            candidate = File(dir, "$AUTO_BACKUP_FILE_PREFIX$timestampMillis-$index$AUTO_BACKUP_FILE_EXTENSION")
            index++
        }
        return candidate
    }
}

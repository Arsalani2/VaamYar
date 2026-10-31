package com.aistudio.vamyar.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.aistudio.vamyar.BuildConfig
import com.aistudio.vamyar.data.LoanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * فیکس bug.md/SB-07: پشتیبان‌گیری خودکار هفتگی (تصمیم کاربر — کاملاً آفلاین، بدون
 * ابر/سرویس خارجی). در startup اپ، اگر از آخرین پشتیبان خودکار بیش از ۷ روز گذشته
 * باشد (یا هنوز هیچ نبوده)، یک فایل JSON کامل نوشته می‌شود؛ حداکثر
 * [MAX_AUTO_BACKUP_FILES] نسخهٔ آخر می‌ماند (قدیمی‌ترین‌ها حذف می‌شوند) و همهٔ
 * کارها روی Dispatchers.IO انجام می‌شود.
 *
 * فیکس bug.md/NB-63: محل ذخیره از حافظهٔ خصوصی برنامه (filesDir/auto_backups که با
 * حذف نصب پاک می‌شد) به پوشهٔ عمومی Documents/VaamYar گوشی منتقل شد تا پشتیبان‌ها
 * با حذف برنامه باقی بمانند — در Android 10+ (API 29+) از طریق MediaStore و بدون
 * هیچ مجوزی؛ در اندرویدهای قدیمی‌تر (API < 29) fallback به همان حافظهٔ داخلی برنامه
 * است (نوشتن حافظهٔ عمومی در آن نسخه‌ها مجوز runtime می‌خواهد). پشتیبان‌های قدیمیِ
 * حافظهٔ داخلی یک‌بار به پوشهٔ عمومی مهاجرت داده می‌شوند.
 *
 * فیکس bug.md/NB-64: در تمام رشته‌های کاربر، واژهٔ فارسی «پشتیبان» جایگزین
 * واژهٔ «بکاپ» شد.
 *
 * هستهٔ نوشتن/چرخش ([writeAutoBackupToDir]) بدون Context قابل‌تست JVM است
 * (همان الگوی CrashLogHelper). محتوای فایل همان فرمت پشتیبان دستی
 * (BackupHelper.createBackupJson) است — از جمله آیکون‌های embedشده (NB-18) و
 * اقساط واقعی (F1+NB-31) — تا در صورت نیاز قابل restore باشد.
 */
object AutoBackupHelper {

    /** حافظهٔ داخلی برنامه — fallback اندرویدهای قدیمی و مبدأ مهاجرت پشتیبان‌های قدیمی. */
    const val AUTO_BACKUP_DIR_NAME = "auto_backups"
    const val AUTO_BACKUP_FILE_PREFIX = "auto-"
    const val AUTO_BACKUP_FILE_EXTENSION = ".json"
    const val MAX_AUTO_BACKUP_FILES = 3
    const val AUTO_BACKUP_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000L
    const val NO_AUTO_BACKUP_TEXT = "هنوز پشتیبان خودکاری ساخته نشده است."

    // فیکس bug.md/NB-63: پوشهٔ عمومی پشتیبان‌های خودکار در Documents گوشی
    const val AUTO_BACKUP_PUBLIC_DIR_NAME = "VaamYar"
    const val AUTO_BACKUP_RELATIVE_PATH = "Documents/VaamYar"

    /** آیا این نسخهٔ اندروید پشتیبان را در پوشهٔ عمومی (MediaStore) می‌نویسد؟ */
    fun usesPublicStorage(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= 29

    fun autoBackupDir(context: Context): File =
        File(context.filesDir, AUTO_BACKUP_DIR_NAME)

    /**
     * آیا وقتِ پشتیبان خودکار است؟ ۰ یعنی «هرگز نشده» → اولین اجرای اپ هم
     * پشتیبان پایه می‌سازد تا همیشه حداقل یک نسخه موجود باشد.
     */
    fun shouldAutoBackup(lastAutoBackupAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        lastAutoBackupAt <= 0L || now - lastAutoBackupAt >= AUTO_BACKUP_INTERVAL_MS

    /**
     * پشتیبان خودکار در صورت نیاز — از VamyarApp.onCreate (پس از rollover/seed)
     * صدا می‌شود. داده‌ها مستقیم از DAOهای Room (first()) خوانده می‌شوند تا به
     * جمع‌آوریِ UI وابسته نباشد. فقط پس از نوشتن موفق فایل، زمان در تنظیمات
     * ثبت می‌شود (اگر وسط کار شکست بخورد، اجرای بعدی دوباره تلاش می‌کند).
     * هر خطایی را می‌بلعد و false برمی‌گرداند — پشتیبان خودکار هرگز نباید
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

                // فیکس bug.md/NB-18: آیکون‌های سفارشیِ موجود روی دیسک، مثل پشتیبان
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

                // فیکس bug.md/NB-63: Android 10+ → پوشهٔ عمومی Documents/VaamYar
                // (ماندگار پس از حذف نصب)؛ اندروید قدیمی → fallback حافظهٔ داخلی.
                val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    migrateLegacyInternalBackups(appContext)
                    writeAutoBackupPublicFile(appContext, json, now)
                } else {
                    writeAutoBackupToDir(autoBackupDir(appContext), json, now) != null
                }
                if (written) {
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
     * فیکس bug.md/NB-63: مهاجرت یک‌بارهٔ پشتیبان‌های قدیمیِ حافظهٔ داخلی به پوشهٔ
     * عمومی — بهترین تلاش؛ فایل داخلی فقط وقتی پاک می‌شود که نسخهٔ عمومی‌اش
     * واقعاً نوشته شده باشد (هیچ داده‌ای عقب انداخته نمی‌شود).
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun migrateLegacyInternalBackups(context: Context) {
        val legacyFiles = listAutoBackups(autoBackupDir(context))
        if (legacyFiles.isEmpty()) return
        legacyFiles.forEach { file ->
            val migrated = runCatching {
                val json = file.readText(Charsets.UTF_8)
                val timestamp = autoBackupTimestampFromFileName(file.name) ?: file.lastModified()
                writeAutoBackupPublicFile(context, json, timestamp)
            }.getOrDefault(false)
            if (migrated) {
                runCatching { file.delete() }
            }
        }
    }

    /**
     * نوشتن پشتیبان در پوشهٔ عمومی Documents/VaamYar از طریق MediaStore — فقط
     * Android 10+ و بدون هیچ مجوزی (فایل‌های خودِ برنامه). الگوی IS_PENDING تا
     * فایل نیمه‌نوشته هرگز در فایل‌منیجر دیده نشود.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun writeAutoBackupPublicFile(context: Context, json: String, timestampMillis: Long): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val name = "$AUTO_BACKUP_FILE_PREFIX$timestampMillis$AUTO_BACKUP_FILE_EXTENSION"
        val pendingValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, AUTO_BACKUP_RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pendingValues) ?: return false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }
        val doneValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { resolver.update(uri, doneValues, null, null) }
        enforceMaxFilesMediaStore(resolver)
        return true
    }

    /**
     * همهٔ نام فایل‌های پشتیبان خودکار در پوشهٔ عمومی، از جدیدترین به قدیمی‌ترین
     * (نام فایل = auto-<millis>.json؛ نوکِ timestamp خودش ترتیب زمانی می‌دهد).
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun listPublicAutoBackupNames(resolver: ContentResolver): List<String> {
        val collection = MediaStore.Files.getContentUri("external")
        val names = mutableListOf<String>()
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("$AUTO_BACKUP_RELATIVE_PATH/", "$AUTO_BACKUP_FILE_PREFIX%$AUTO_BACKUP_FILE_EXTENSION"),
                "${MediaStore.MediaColumns.DISPLAY_NAME} DESC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) names.add(cursor.getString(nameIdx))
            }
        }
        return names
    }

    /** کپ [maxFiles] نسخهٔ عمومی — قدیمی‌ترین‌ها (نام = timestamp) حذف می‌شوند. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun enforceMaxFilesMediaStore(resolver: ContentResolver, maxFiles: Int = MAX_AUTO_BACKUP_FILES) {
        val names = listPublicAutoBackupNames(resolver)
        if (names.size <= maxFiles) return
        val collection = MediaStore.Files.getContentUri("external")
        names.drop(maxFiles).forEach { name ->
            runCatching {
                resolver.delete(
                    collection,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf("$AUTO_BACKUP_RELATIVE_PATH/", name)
                )
            }
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

    /** همهٔ فایل‌های پشتیبان خودکارِ مسیر فایلی، از جدیدترین به قدیمی‌ترین. */
    fun listAutoBackups(dir: File): List<File> =
        dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(AUTO_BACKUP_FILE_PREFIX) &&
                file.name.endsWith(AUTO_BACKUP_FILE_EXTENSION)
        }?.sortedByDescending { it.name } ?: emptyList()

    /** خلاصهٔ «آخرین پشتیبان خودکار» برای صفحهٔ تنظیمات — تاریخ شمسی + ساعت. */
    fun latestAutoBackupSummary(context: Context, usePersianDigits: Boolean): String {
        val latestName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listPublicAutoBackupNames(context.contentResolver).firstOrNull()
        } else {
            listAutoBackups(autoBackupDir(context)).firstOrNull()?.name
        } ?: return NO_AUTO_BACKUP_TEXT
        val millis = autoBackupTimestampFromFileName(latestName)
            ?: return "آخرین پشتیبان خودکار: $latestName"
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
        return "آخرین پشتیبان خودکار: ${shamsi.formatted(usePersianDigits)}، ساعت $clock"
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

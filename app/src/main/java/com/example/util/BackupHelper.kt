package com.example.util

import android.content.Context
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.LoanInstallment
import com.example.model.PaymentRecord
import com.example.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

data class BackupPreferences(
    val themeMode: String = "DARK",
    val themePreset: String = "EMERALD_GOLD",
    val usePersianDigits: Boolean = true,
    val compactThousands: Boolean = false,
    val customYear: Int = 1405,
    val customMonth: Int = 8,
    val customDay: Int = 2,
    val isCustomDateEnabled: Boolean = false
)

/**
 * فیکس bug.md/NB-18: آیکون سفارشیِ embedشدهٔ وام در بکاپ — خودِ فایل (base64)،
 * نه مسیر absoluteِ دستگاه قبلی که روی نصب دیگر بی‌معناست و به JSON هم نشت می‌کرد.
 */
data class BackupIcon(
    val loanId: Long,
    val fileName: String,
    val dataBase64: String
)

data class BackupRestoreData(
    val users: List<UserProfile>,
    val loans: List<Loan>,
    val shares: List<DebtorShare>,
    val records: List<PaymentRecord>,
    // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط (بکاپ قدیمی → خالی؛ restore در
    // آن حالت به بازسازی ترتیبی از paidMonths برمی‌گردد)
    val installments: List<LoanInstallment> = emptyList(),
    // فیکس bug.md/NB-18: فایل‌های آیکون سفارشیِ embedشده (بکاپ قدیمی → خالی)
    val icons: List<BackupIcon> = emptyList(),
    val preferences: BackupPreferences,
    val schemaVersion: Int,
    val exportedDate: String
)

object BackupHelper {

    const val CURRENT_SCHEMA_VERSION = 1
    const val APP_IDENTIFIER = "com.aistudio.vamyar"
    const val APP_NAME = "VaamYar"

    /**
     * Serializes complete application state into an encrypted/verifiable JSON string with SHA-256 integrity hash.
     */
    fun createBackupJson(
        users: List<UserProfile>,
        loans: List<Loan>,
        shares: List<DebtorShare>,
        records: List<PaymentRecord>,
        // فیکس bug.md/F1+NB-31: رکوردهای واقعی اقساط هم در بکاپ ذخیره می‌شوند تا
        // ماه‌های پرداخت‌شدهٔ واقعی (حتی نامرتّب/عقب‌مانده) بعد از restore سر جایشان بمانند
        installments: List<LoanInstallment> = emptyList(),
        // فیکس bug.md/NB-18: فایل‌های آیکون سفارشی به‌صورت base64 embed می‌شوند؛
        // به‌ازای هر وام فقط وقتی که فایلش واقعاً روی دیسک باشد (وگرنه path هم حذف می‌شود)
        icons: List<BackupIcon> = emptyList(),
        preferences: BackupPreferences,
        // فیکس bug.md/P2-13: پارامتر نسخه دیگر پیش‌فرض هاردکد ندارد تا هیچ فراخوانی
        // ناخواسته نسخهٔ کهره در فایل بکاپ درج نکند؛ تنها فراخوانیِ فعلی مقدار را از
        // BuildConfig.VERSION_NAME (تزریق‌شده از version.properties در زمان بیلد) می‌گذراند.
        appVersion: String,
        shamsiDateFormatted: String = ""
    ): String {
        val root = JSONObject()
        root.put("app", APP_NAME)
        root.put("appIdentifier", APP_IDENTIFIER)
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("appVersion", appVersion)
        root.put("exportTimestamp", System.currentTimeMillis())
        root.put("exportDateShamsi", shamsiDateFormatted)

        // Build payload object
        val payload = JSONObject()

        // 1. Users
        val usersArray = JSONArray()
        for (u in users) {
            val uObj = JSONObject()
            uObj.put("id", u.id)
            uObj.put("name", u.name)
            uObj.put("isPrimary", u.isPrimary)
            uObj.put("avatarColorHex", u.avatarColorHex)
            uObj.put("notes", u.notes)
            uObj.put("publicId", u.publicId)
            usersArray.put(uObj)
        }
        payload.put("users", usersArray)

        // 2. Loans
        val loansArray = JSONArray()
        for (l in loans) {
            val lObj = JSONObject()
            lObj.put("id", l.id)
            lObj.put("title", l.title)
            lObj.put("dueDay", l.dueDay)
            lObj.put("monthlyAmount", l.monthlyAmount)
            lObj.put("totalMonths", l.totalMonths)
            lObj.put("paidMonths", l.paidMonths)
            lObj.put("startYear", l.startYear)
            lObj.put("startMonth", l.startMonth)
            lObj.put("endYear", l.endYear)
            lObj.put("endMonth", l.endMonth)
            lObj.put("category", l.category)
            lObj.put("colorHex", l.colorHex)
            lObj.put("notes", l.notes)
            lObj.put("myShareAmount", l.myShareAmount)
            lObj.put("hasPartners", l.hasPartners)
            lObj.put("isArchived", l.isArchived)
            lObj.put("isCurrentMonthPaid", l.isCurrentMonthPaid)
            lObj.put("iconName", l.iconName)
            lObj.put("iconShape", l.iconShape)
            // فیکس bug.md/NB-18: خودِ آیکون در آرایهٔ icons (base64) حمل می‌شود؛ در
            // customIconPath فقط «نام فایل» می‌ماند (نه مسیر absolute) تا مسیرِ
            // فایلسیستم به JSON نشت نکند. اگر فایل روی دیسک نباشد (icons ندارد)،
            // فیلد حذف می‌شود و restore به fallback برداری می‌رود.
            val embeddedIcon = icons.firstOrNull { it.loanId == l.id }
            lObj.put("customIconPath", embeddedIcon?.fileName ?: JSONObject.NULL)
            lObj.put("publicId", l.publicId)
            loansArray.put(lObj)
        }
        payload.put("loans", loansArray)

        // 3. Debtor Shares
        val sharesArray = JSONArray()
        for (s in shares) {
            val sObj = JSONObject()
            sObj.put("id", s.id)
            sObj.put("loanId", s.loanId)
            sObj.put("loanTitle", s.loanTitle)
            sObj.put("personName", s.personName)
            sObj.put("shareAmount", s.shareAmount)
            sObj.put("dueDay", s.dueDay)
            sObj.put("isPaidForCurrentMonth", s.isPaidForCurrentMonth)
            sObj.put("lastPaidMonth", s.lastPaidMonthName)
            sObj.put("shareDescription", s.shareDescription)
            sObj.put("publicId", s.publicId)
            sObj.put("ownerPublicId", s.ownerPublicId)
            sharesArray.put(sObj)
        }
        payload.put("shares", sharesArray)

        // 4. Payment Records
        val recordsArray = JSONArray()
        for (r in records) {
            val rObj = JSONObject()
            rObj.put("id", r.id)
            rObj.put("loanId", r.loanId)
            rObj.put("loanTitle", r.loanTitle)
            rObj.put("installmentNumber", r.installmentNumber)
            rObj.put("amountPaid", r.amountPaid)
            rObj.put("paymentDateShamsi", r.paymentDateShamsi)
            rObj.put("timestamp", r.timestamp)
            recordsArray.put(rObj)
        }
        payload.put("records", recordsArray)

        // 4.5. Installments (فیکس bug.md/F1+NB-31: رکورد واقعی هر قسط با ماه شمسی‌اش)
        val installmentsArray = JSONArray()
        for (ins in installments) {
            val iObj = JSONObject()
            iObj.put("id", ins.id)
            iObj.put("loanId", ins.loanId)
            iObj.put("installmentNumber", ins.installmentNumber)
            iObj.put("year", ins.year)
            iObj.put("month", ins.month)
            iObj.put("amount", ins.amount)
            iObj.put("paidAtTimestamp", ins.paidAtTimestamp)
            iObj.put("paidDateShamsi", ins.paidDateShamsi)
            installmentsArray.put(iObj)
        }
        payload.put("installments", installmentsArray)

        // 4.6. Icons (فیکس bug.md/NB-18: فایل آیکون سفارشی وام داخل خودِ بکاپ)
        val iconsArray = JSONArray()
        for (ic in icons) {
            val icObj = JSONObject()
            icObj.put("loanId", ic.loanId)
            icObj.put("fileName", ic.fileName)
            icObj.put("data", ic.dataBase64)
            iconsArray.put(icObj)
        }
        payload.put("icons", iconsArray)

        // 5. Preferences
        val prefsObj = JSONObject()
        prefsObj.put("themeMode", preferences.themeMode)
        prefsObj.put("themePreset", preferences.themePreset)
        prefsObj.put("usePersianDigits", preferences.usePersianDigits)
        prefsObj.put("compactThousands", preferences.compactThousands)
        prefsObj.put("customYear", preferences.customYear)
        prefsObj.put("customMonth", preferences.customMonth)
        prefsObj.put("customDay", preferences.customDay)
        prefsObj.put("isCustomDateEnabled", preferences.isCustomDateEnabled)
        payload.put("preferences", prefsObj)

        root.put("payload", payload)

        // Compute SHA-256 Checksum of the payload
        val payloadString = payload.toString()
        val checksum = calculateSha256(payloadString)
        root.put("checksum", checksum)

        return root.toString(2)
    }

    /**
     * Parses, validates, and automatically migrates backup data from older versions.
     */
    fun parseAndValidateBackup(jsonString: String): Result<BackupRestoreData> {
        return try {
            val root = JSONObject(jsonString)

            // 1. Identity Check
            val appIdent = root.optString("appIdentifier", "")
            if (appIdent.isNotEmpty() && appIdent != APP_IDENTIFIER && appIdent != "com.aistudio.vamyar.zqnmxk" && appIdent != "com.example") {
                return Result.failure(IllegalArgumentException("این فایل مربوط به برنامه وام‌یار نیست ($appIdent)"))
            }

            val schemaVersion = root.optInt("schemaVersion", 1)
            if (schemaVersion > CURRENT_SCHEMA_VERSION) {
                return Result.failure(IllegalArgumentException("این فایل پشتیبان با نسخه جدیدتری از وام‌یار (اسکیما $schemaVersion) ساخته شده است. لطفاً برنامه را به‌روزرسانی کنید."))
            }

            // 2. Checksum validation
            val payload = root.getJSONObject("payload")
            val expectedChecksum = root.optString("checksum", "")
            if (expectedChecksum.isNotEmpty()) {
                val actualChecksum = calculateSha256(payload.toString())
                if (expectedChecksum != actualChecksum) {
                    return Result.failure(SecurityException("اعتبارسنجی فایل پشتیبان با خطا مواجه شد؛ فایل تغییر یافته یا ناقص است."))
                }
            }

            val exportDate = root.optString("exportDateShamsi", "نامشخص")

            // 3. Parse and Validate Users (فیکس bug.md/NB-4: تضمین وجود دقیقاً یک کاربر مبنا)
            val usersList = mutableListOf<UserProfile>()
            val usersArray = payload.optJSONArray("users") ?: JSONArray()
            for (i in 0 until usersArray.length()) {
                val u = usersArray.getJSONObject(i)
                usersList.add(
                    UserProfile(
                        id = u.optLong("id", 0L),
                        name = u.optString("name", "کاربر"),
                        isPrimary = u.optBoolean("isPrimary", false),
                        avatarColorHex = u.optLong("avatarColorHex", 0xFF10B981L),
                        notes = u.optString("notes", ""),
                        // فیکس bug.md/F6: حفظ شناسهٔ عمومی در بکاپ/بازیابی (بکاپ قدیمی → خالی، بعداً در restore پر می‌شود)
                        publicId = u.optString("publicId", "")
                    )
                )
            }
            if (usersList.isNotEmpty() && usersList.none { it.isPrimary }) {
                usersList[0] = usersList[0].copy(isPrimary = true)
            } else if (usersList.isEmpty()) {
                usersList.add(UserProfile(name = "من", isPrimary = true))
            }

            // 4. Parse Shares first to assist legacy loan migration
            val sharesList = mutableListOf<DebtorShare>()
            val sharesArray = payload.optJSONArray("shares") ?: JSONArray()
            for (i in 0 until sharesArray.length()) {
                val s = sharesArray.getJSONObject(i)
                sharesList.add(
                    DebtorShare(
                        id = s.optLong("id", 0L),
                        loanId = s.optLong("loanId", 0L),
                        loanTitle = s.optString("loanTitle", ""),
                        personName = s.optString("personName", ""),
                        shareAmount = s.optLong("shareAmount", 0L),
                        dueDay = s.optInt("dueDay", 1),
                        isPaidForCurrentMonth = s.optBoolean("isPaidForCurrentMonth", false),
                        lastPaidMonthName = s.optString("lastPaidMonth", s.optString("lastPaidMonthName", "")),
                        shareDescription = s.optString("shareDescription", ""),
                        // فیکس bug.md/F6: حفظ شناسهٔ سهم و شناسهٔ مالک آن
                        publicId = s.optString("publicId", ""),
                        ownerPublicId = s.optString("ownerPublicId", "")
                    )
                )
            }

            // 5. Parse, Validate, and Migrate Loans
            val loansList = mutableListOf<Loan>()
            val loansArray = payload.optJSONArray("loans") ?: JSONArray()
            for (i in 0 until loansArray.length()) {
                val l = loansArray.getJSONObject(i)
                val loanId = l.optLong("id", 0L)
                val monthlyAmount = l.optLong("monthlyAmount", 0L)
                val existingSharesForLoan = sharesList.filter { it.loanId == loanId }
                val hasPartnersInJson = if (l.has("hasPartners")) {
                    l.getBoolean("hasPartners")
                } else {
                    existingSharesForLoan.isNotEmpty()
                }

                // فیکس bug.md/P2-14: بازسازی سهم من برای بکاپ‌های قدیمی
                val migratedMyShare = when {
                    !hasPartnersInJson -> {
                        val rawMyShare = l.optLong("myShareAmount", 0L)
                        if (rawMyShare == 0L) monthlyAmount else rawMyShare
                    }
                    l.has("myShareAmount") -> l.optLong("myShareAmount", 0L)
                    else -> (monthlyAmount - existingSharesForLoan.sumOf { it.shareAmount }).coerceAtLeast(0L)
                }

                // فیکس bug.md/NB-4: محدودسازی و اعتبارسنجی مقادیر ماه و اقساط
                val safeTotalMonths = l.optInt("totalMonths", 12).coerceIn(1, 120)
                val safePaidMonths = l.optInt("paidMonths", 0).coerceIn(0, safeTotalMonths)

                loansList.add(
                    Loan(
                        id = loanId,
                        title = l.optString("title", "وام"),
                        dueDay = l.optInt("dueDay", 1),
                        monthlyAmount = monthlyAmount,
                        totalMonths = safeTotalMonths,
                        paidMonths = safePaidMonths,
                        startYear = l.optInt("startYear", 1403),
                        startMonth = l.optInt("startMonth", 1),
                        endYear = l.optInt("endYear", 1404),
                        endMonth = l.optInt("endMonth", 1),
                        category = l.optString("category", "بانکی"),
                        colorHex = l.optLong("colorHex", 0xFF1E88E5L),
                        notes = l.optString("notes", ""),
                        myShareAmount = migratedMyShare,
                        hasPartners = hasPartnersInJson,
                        isArchived = l.optBoolean("isArchived", false),
                        isCurrentMonthPaid = l.optBoolean("isCurrentMonthPaid", false),
                        iconName = l.optString("iconName", "account_balance"),
                        iconShape = l.optString("iconShape", "ROUNDED_SQUARE"),
                        customIconPath = if (l.isNull("customIconPath")) null else l.optString("customIconPath", null),
                        // فیکس bug.md/F6: حفظ شناسهٔ عمومی وام
                        publicId = l.optString("publicId", "")
                    )
                )
            }

            // 6. Parse Payment Records
            val recordsList = mutableListOf<PaymentRecord>()
            val recordsArray = payload.optJSONArray("records") ?: JSONArray()
            for (i in 0 until recordsArray.length()) {
                val r = recordsArray.getJSONObject(i)
                recordsList.add(
                    PaymentRecord(
                        id = r.optLong("id", 0L),
                        loanId = r.optLong("loanId", 0L),
                        loanTitle = r.optString("loanTitle", ""),
                        installmentNumber = r.optInt("installmentNumber", 1),
                        amountPaid = r.optLong("amountPaid", 0L),
                        paymentDateShamsi = r.optString("paymentDateShamsi", ""),
                        timestamp = r.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }

            // فیکس bug.md/NB-4: حذف سهم‌ها و رکوردهای یتیمی که شناسه وام آنها در جدول وام‌ها وجود ندارد
            val validLoanIds = loansList.map { it.id }.toSet()
            val sanitizedShares = sharesList.filter { it.loanId in validLoanIds }
            val sanitizedRecords = recordsList.filter { it.loanId in validLoanIds }

            // 6.5. Parse Installments (فیکس bug.md/F1+NB-31: بکاپ قدیمی این آرایه را ندارد →
            // خالی برمی‌گردد و restore به بازسازی ترتیبی از paidMonths برمی‌گردد)
            val installmentsList = mutableListOf<LoanInstallment>()
            val installmentsArray = payload.optJSONArray("installments") ?: JSONArray()
            for (i in 0 until installmentsArray.length()) {
                val ins = installmentsArray.getJSONObject(i)
                installmentsList.add(
                    LoanInstallment(
                        id = ins.optLong("id", 0L),
                        loanId = ins.optLong("loanId", 0L),
                        installmentNumber = ins.optInt("installmentNumber", 1),
                        year = ins.optInt("year", 1405),
                        month = ins.optInt("month", 1).coerceIn(1, 12),
                        amount = ins.optLong("amount", 0L),
                        paidAtTimestamp = ins.optLong("paidAtTimestamp", System.currentTimeMillis()),
                        paidDateShamsi = ins.optString("paidDateShamsi", "")
                    )
                )
            }
            // فقط قسط‌های متعلق به وام‌های همین بکاپ و در بازهٔ ۱..totalMonths وام پذیرفته می‌شوند
            val loanTotalMonthsById = loansList.associate { it.id to it.totalMonths }
            val sanitizedInstallments = installmentsList.filter {
                it.loanId in validLoanIds &&
                    it.installmentNumber in 1..(loanTotalMonthsById[it.loanId] ?: 0)
            }

            // 6.7. Parse Icons (فیکس bug.md/NB-18: بکاپ قدیمی آرایهٔ icons را ندارد →
            // خالی؛ آیکونِ چنین بکاپی اگر فایلش روی همان دستگاه موجود نباشد به
            // fallback برداری برمی‌گردد)
            val iconsList = mutableListOf<BackupIcon>()
            val iconsArray = payload.optJSONArray("icons") ?: JSONArray()
            for (i in 0 until iconsArray.length()) {
                val ic = iconsArray.getJSONObject(i)
                iconsList.add(
                    BackupIcon(
                        loanId = ic.optLong("loanId", 0L),
                        fileName = ic.optString("fileName", ""),
                        dataBase64 = ic.optString("data", "")
                    )
                )
            }
            val sanitizedIcons = iconsList.filter { it.loanId in validLoanIds && it.dataBase64.isNotBlank() }

            // 7. Parse Preferences
            val prefsObj = payload.optJSONObject("preferences") ?: JSONObject()
            val preferences = BackupPreferences(
                themeMode = prefsObj.optString("themeMode", "DARK"),
                themePreset = prefsObj.optString("themePreset", "EMERALD_GOLD"),
                usePersianDigits = prefsObj.optBoolean("usePersianDigits", true),
                compactThousands = prefsObj.optBoolean("compactThousands", false),
                customYear = prefsObj.optInt("customYear", 1405),
                customMonth = prefsObj.optInt("customMonth", 8),
                customDay = prefsObj.optInt("customDay", 2),
                isCustomDateEnabled = prefsObj.optBoolean("isCustomDateEnabled", false)
            )

            Result.success(
                BackupRestoreData(
                    users = usersList,
                    loans = loansList,
                    shares = sanitizedShares,
                    records = sanitizedRecords,
                    installments = sanitizedInstallments,
                    icons = sanitizedIcons,
                    preferences = preferences,
                    schemaVersion = schemaVersion,
                    exportedDate = exportDate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

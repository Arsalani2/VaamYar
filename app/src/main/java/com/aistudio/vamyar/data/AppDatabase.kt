package com.aistudio.vamyar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.LoanInstallment
import com.aistudio.vamyar.model.PaymentRecord
import com.aistudio.vamyar.model.UserProfile

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aistudio.vamyar.util.ShamsiDateHelper

@Database(
    entities = [Loan::class, DebtorShare::class, PaymentRecord::class, UserProfile::class, LoanInstallment::class],
    version = 6, // فیکس bug.md/F1+NB-31: جدول loan_installments (قسط واقعی با ماه شمسی)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun loanDao(): LoanDao
    abstract fun debtorShareDao(): DebtorShareDao
    abstract fun paymentRecordDao(): PaymentRecordDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun loanInstallmentDao(): LoanInstallmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE loans ADD COLUMN hasPartners INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE loans ADD COLUMN iconName TEXT NOT NULL DEFAULT 'account_balance'")
                database.execSQL("ALTER TABLE loans ADD COLUMN iconShape TEXT NOT NULL DEFAULT 'ROUNDED_SQUARE'")
                database.execSQL("ALTER TABLE loans ADD COLUMN customIconPath TEXT DEFAULT NULL")
            }
        }

        // فیکس bug.md/P3-5: ستون phoneNumber با حذف قابلیت «شماره تماس شریک» از
        // user_profiles حذف می‌شود. چون SQLite نسخه‌های قدیمی اندروید (۸–۱۱) از
        // DROP COLUMN پشتیبانی نمی‌کند، جدول با اسکیمای جدید بازسازی و داده‌های
        // موجود عیناً منتقل می‌شوند (الگوی استاندارد Migration در Room).
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profiles_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`isPrimary` INTEGER NOT NULL, " +
                        "`avatarColorHex` INTEGER NOT NULL, " +
                        "`notes` TEXT NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO `user_profiles_new` (`id`, `name`, `isPrimary`, `avatarColorHex`, `notes`) " +
                        "SELECT `id`, `name`, `isPrimary`, `avatarColorHex`, `notes` FROM `user_profiles`"
                )
                database.execSQL("DROP TABLE `user_profiles`")
                database.execSQL("ALTER TABLE `user_profiles_new` RENAME TO `user_profiles`")
            }
        }

        // فیکس bug.md/F6: ستون‌های شناسهٔ یکتای عمومی به سه جدول اضافه می‌شوند.
        // مقدار پیش‌فرض خالی یعنی «تخصیص‌نیافته»؛ شناسه‌های واقعی در اولین اجرای اپ
        // توسط LoanRepository.backfillMissingPublicIds تولید و ذخیره می‌شوند (چون در
        // SQL مهاجرت نمی‌توان مولدِ تصادفیِ تست‌پذیر نوشت و منطق باید یک‌جا باشد).
        // ایندکس‌ها برای جست‌وجوی سریع با publicId (کلید ادغام آیندهٔ NB-34).
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE loans ADD COLUMN publicId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE debtor_shares ADD COLUMN publicId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE debtor_shares ADD COLUMN ownerPublicId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN publicId TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_publicId` ON `loans` (`publicId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_debtor_shares_publicId` ON `debtor_shares` (`publicId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_debtor_shares_ownerPublicId` ON `debtor_shares` (`ownerPublicId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_user_profiles_publicId` ON `user_profiles` (`publicId`)")
            }
        }

        // فیکس bug.md/F1+NB-31: جدول جدید loan_installments — رکورد به‌ازای هر قسطِ
        // پرداخت‌شده با سال/ماه شمسیِ واقعی. داده‌های موجودِ هر وام (paidMonths قسطِ
        // ترتیبی از ماه شروع) به رکوردهای ماهِ واقعی تبدیل می‌شوند تا هیچ پرداختی از
        // دست نرود؛ رفتارِ قدیمیِ «ترتیبی» فقط به‌عنوان حالتِ آغازین حفظ می‌شود و
        // از این پس «پرداخت این ماه» همیشه ماهِ جاریِ تقویم را ثبت می‌کند.
        // مقدار paidAtTimestamp=0 و paidDateShamsi='' یعنی «تاریخ واقعی پرداخت نامعلوم»
        // (مهاجرت از شمارندهٔ قدیمی) — تاریخ واقعی فقط برای پرداخت‌های جدید ثبت می‌شود.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_installments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "`loanId` INTEGER NOT NULL, " +
                        "`installmentNumber` INTEGER NOT NULL, " +
                        "`year` INTEGER NOT NULL, " +
                        "`month` INTEGER NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`paidAtTimestamp` INTEGER NOT NULL, " +
                        "`paidDateShamsi` TEXT NOT NULL)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_loan_installments_loanId_year_month` " +
                        "ON `loan_installments` (`loanId`, `year`, `month`)"
                )
                // backfill: برای هر وام، قسط‌های ۱..paidMonths با ماه شمسیِ متناظر درج می‌شوند
                val cursor = database.query(
                    "SELECT id, startYear, startMonth, paidMonths, monthlyAmount FROM loans WHERE paidMonths > 0"
                )
                cursor.use {
                    while (it.moveToNext()) {
                        val loanId = it.getLong(0)
                        val startYear = it.getInt(1)
                        val startMonth = it.getInt(2)
                        val paidMonths = it.getInt(3)
                        val monthlyAmount = it.getLong(4)
                        for (k in 1..paidMonths) {
                            val m = ShamsiDateHelper.installmentMonth(startYear, startMonth, k)
                            val stmt = database.compileStatement(
                                "INSERT OR IGNORE INTO `loan_installments` " +
                                    "(`loanId`, `installmentNumber`, `year`, `month`, `amount`, `paidAtTimestamp`, `paidDateShamsi`) " +
                                    "VALUES (?, ?, ?, ?, ?, 0, '')"
                            )
                            stmt.bindLong(1, loanId)
                            stmt.bindLong(2, k.toLong())
                            stmt.bindLong(3, m.year.toLong())
                            stmt.bindLong(4, m.month.toLong())
                            stmt.bindLong(5, monthlyAmount)
                            stmt.executeInsert()
                            stmt.close()
                        }
                    }
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vamyar_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // فیکس bug.md/P1-1: fallbackToDestructiveMigration(dropAllTables = true) حذف شد.
                    // هر تغییر اسکیمای آینده بدون Migration واقعی، دیگر داده‌های مالی کاربر را
                    // بی‌سروصدا پاک نمی‌کند؛ بلکه Room خطای IllegalStateException می‌دهد تا در زمان
                    // توسعه الزاماً Migration نوشته شود.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Loan::class, DebtorShare::class, PaymentRecord::class, UserProfile::class],
    version = 5, // فیکس bug.md/F6: ستون‌های publicId (+ownerPublicId سهم‌ها) در سه جدول
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun loanDao(): LoanDao
    abstract fun debtorShareDao(): DebtorShareDao
    abstract fun paymentRecordDao(): PaymentRecordDao
    abstract fun userProfileDao(): UserProfileDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vamyar_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

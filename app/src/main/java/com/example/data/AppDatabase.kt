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
    version = 3,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vamyar_database"
                )
                    .addMigrations(MIGRATION_2_3)
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

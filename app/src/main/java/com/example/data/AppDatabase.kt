package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.DebtorShare
import com.example.model.Loan
import com.example.model.PaymentRecord
import com.example.model.UserProfile

@Database(
    entities = [Loan::class, DebtorShare::class, PaymentRecord::class, UserProfile::class],
    version = 2,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vamyar_database"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

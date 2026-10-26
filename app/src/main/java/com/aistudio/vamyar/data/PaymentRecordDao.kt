package com.aistudio.vamyar.data

import androidx.room.*
import com.aistudio.vamyar.model.PaymentRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentRecordDao {
    @Query("SELECT * FROM payment_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE loanId = :loanId ORDER BY installmentNumber DESC")
    fun getRecordsForLoan(loanId: Long): Flow<List<PaymentRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PaymentRecord): Long

    @Delete
    suspend fun deleteRecord(record: PaymentRecord)

    // فیکس bug.md/P1-3: حذف رکورد(های) یک قسط مشخص هنگام برداشتن تیک پرداخت یا کاهش قسط.
    // همه‌ی رکوردهای هم‌شماره پاک می‌شوند تا تکراری‌های قدیمیِ ناشی از باگ هم تمیز شوند.
    @Query("DELETE FROM payment_records WHERE loanId = :loanId AND installmentNumber = :installmentNumber")
    suspend fun deleteRecordsForInstallment(loanId: Long, installmentNumber: Int)

    // فیکس bug.md/P1-9: حذف رکوردهای تاریخچه‌ای که شماره‌شان از کل اقساط وام بیشتر است
    // (مثلاً رکورد قسط ۱۳ در وام ۱۲قسطی) تا تاریخچه همیشه با مدت وام سازگار بماند.
    @Query("DELETE FROM payment_records WHERE loanId = :loanId AND installmentNumber > :maxInstallment")
    suspend fun deleteRecordsAboveInstallment(loanId: Long, maxInstallment: Int)

    @Query("DELETE FROM payment_records WHERE loanId = :loanId")
    suspend fun deleteRecordsForLoan(loanId: Long)

    @Query("DELETE FROM payment_records")
    suspend fun clearAllRecords()
}

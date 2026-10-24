package com.example.data

import androidx.room.*
import com.example.model.PaymentRecord
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
}

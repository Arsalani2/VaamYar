package com.example.data

import androidx.room.*
import com.example.model.Loan
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans WHERE isArchived = 0 ORDER BY dueDay ASC")
    fun getAllActiveLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY dueDay ASC")
    fun getAllLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans WHERE id = :id")
    fun getLoanById(id: Long): Flow<Loan?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    @Delete
    suspend fun deleteLoan(loan: Loan)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoanById(id: Long)

    @Query("DELETE FROM loans")
    suspend fun clearAllLoans()

    @Query("SELECT COUNT(*) FROM loans")
    suspend fun getCount(): Int
}

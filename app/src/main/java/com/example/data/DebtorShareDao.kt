package com.example.data

import androidx.room.*
import com.example.model.DebtorShare
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtorShareDao {
    @Query("SELECT * FROM debtor_shares ORDER BY dueDay ASC")
    fun getAllShares(): Flow<List<DebtorShare>>

    @Query("SELECT * FROM debtor_shares WHERE loanId = :loanId")
    fun getSharesForLoan(loanId: Long): Flow<List<DebtorShare>>

    @Query("SELECT * FROM debtor_shares WHERE personName = :personName")
    suspend fun getSharesForPerson(personName: String): List<DebtorShare>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShare(share: DebtorShare): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shares: List<DebtorShare>)

    @Update
    suspend fun updateShare(share: DebtorShare)

    @Delete
    suspend fun deleteShare(share: DebtorShare)

    @Query("DELETE FROM debtor_shares WHERE loanId = :loanId")
    suspend fun deleteSharesForLoan(loanId: Long)

    @Query("UPDATE debtor_shares SET isPaidForCurrentMonth = :isPaid, lastPaidMonthName = :monthName WHERE id = :id")
    suspend fun updatePaidStatus(id: Long, isPaid: Boolean, monthName: String)

    @Query("UPDATE debtor_shares SET isPaidForCurrentMonth = 0")
    suspend fun resetMonthlyPaidStatus()

    @Query("UPDATE debtor_shares SET personName = :newName WHERE personName = :oldName")
    suspend fun renamePersonInShares(oldName: String, newName: String)

    @Query("DELETE FROM debtor_shares")
    suspend fun clearAllShares()
}

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

    // فیکس bug.md/P1-2: خواندن یک‌باره (غیر-Flow) سهم‌های یک وام برای حفظ
    // وضعیت پرداخت شرکا هنگام ویرایش وام در LoanRepository.saveLoanWithShares
    @Query("SELECT * FROM debtor_shares WHERE loanId = :loanId")
    suspend fun getSharesForLoanOnce(loanId: Long): List<DebtorShare>

    // فیکس bug.md/P3-1: خواندن یک‌باره (غیر-Flow) از همهٔ سهم‌ها برای تطبیق نامِ
    // استانداردشده در LoanRepository (هم‌گام‌سازی rename و چک حذف کاربر)؛ نام‌ها
    // را نمی‌توان با تطبیق بایت‌به‌بایتِ SQL جست‌وجو کرد (ي/ك عربی، فاصله/نیم‌فاصله)
    @Query("SELECT * FROM debtor_shares")
    suspend fun getAllSharesOnce(): List<DebtorShare>

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

    // فیکس bug.md/P1-6: رِسِت پرچم ماه جاری + پاک کردن lastPaidMonthName برای
    // حذف وضعیت متناقض (پرچم صفر ولی نام ماه پرداخت باقی‌مانده)
    @Query("UPDATE debtor_shares SET isPaidForCurrentMonth = 0, lastPaidMonthName = ''")
    suspend fun resetMonthlyPaidStatus()

    @Query("UPDATE debtor_shares SET personName = :newName WHERE personName = :oldName")
    suspend fun renamePersonInShares(oldName: String, newName: String)

    @Query("DELETE FROM debtor_shares")
    suspend fun clearAllShares()
}

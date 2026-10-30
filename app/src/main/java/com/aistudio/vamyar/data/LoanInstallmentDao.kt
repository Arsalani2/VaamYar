package com.aistudio.vamyar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aistudio.vamyar.model.LoanInstallment
import kotlinx.coroutines.flow.Flow

/**
 * فیکس bug.md/F1 + NB-31: DAO جدول loan_installments — منبع واحدِ حقیقتِ
 * «کدام قسطِ کدام ماهِ شمسی پرداخت شده».
 */
@Dao
interface LoanInstallmentDao {

    @Query("SELECT * FROM loan_installments ORDER BY loanId ASC, installmentNumber ASC")
    fun getAllInstallments(): Flow<List<LoanInstallment>>

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId ORDER BY installmentNumber ASC")
    suspend fun getInstallmentsForLoanOnce(loanId: Long): List<LoanInstallment>

    /** IGNORE روی برخوردِ ایندکس یکتای (loanId, year, month): پرداختِ idempotent */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstallment(installment: LoanInstallment): Long

    @Query("DELETE FROM loan_installments WHERE loanId = :loanId AND year = :year AND month = :month")
    suspend fun deleteByLoanAndMonth(loanId: Long, year: Int, month: Int)

    @Query("DELETE FROM loan_installments WHERE loanId = :loanId")
    suspend fun deleteInstallmentsForLoan(loanId: Long)

    /** فیکس bug.md/F1: کاهش مدت وام در ویرایش — رکوردهای بی‌مرجع حذف شوند (هم‌ارز P1-9 برای payment_records) */
    @Query("DELETE FROM loan_installments WHERE loanId = :loanId AND installmentNumber > :maxInstallment")
    suspend fun deleteInstallmentsAboveNumber(loanId: Long, maxInstallment: Int)

    @Query("DELETE FROM loan_installments")
    suspend fun clearAllInstallments()
}

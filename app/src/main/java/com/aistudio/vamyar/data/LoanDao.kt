package com.aistudio.vamyar.data

import androidx.room.*
import com.aistudio.vamyar.model.Loan
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans WHERE isArchived = 0 ORDER BY dueDay ASC")
    fun getAllActiveLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY dueDay ASC")
    fun getAllLoans(): Flow<List<Loan>>

    // فیکس bug.md/NB-34: خواندن یک‌بارهٔ همهٔ وام‌ها برای تطبیق ادغام ایمپورت
    // (پیش‌نمایش و merge داخل تراکنش، بدون Flow)
    @Query("SELECT * FROM loans ORDER BY dueDay ASC")
    suspend fun getAllLoansOnce(): List<Loan>

    @Query("SELECT * FROM loans WHERE id = :id")
    fun getLoanById(id: Long): Flow<Loan?>

    // فیکس bug.md/P1-3: خواندن یک‌باره‌ی وضعیت واقعی وام داخل تراکنش، تا تیک/کاهش با
    // نسخه‌ی کهنه‌ی UI (مثلاً دوبار ضربه‌ی سریع) رکورد تکراری نسازد.
    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoanByIdOnce(id: Long): Loan?

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

    // فیکس bug.md/P1-6: صفر کردن پرچم «پرداخت ماه جاری» همهٔ وام‌ها
    // (رِسِت خودکار ماهانه + دکمهٔ دستی «شروع ماه جدید»)
    @Query("UPDATE loans SET isCurrentMonthPaid = 0 WHERE isCurrentMonthPaid = 1")
    suspend fun resetCurrentMonthPaidFlags()

    // فیکس bug.md/NB-19: وام‌های دارای آیکون سفارشی — مسیر فایل‌ها پیش از تراکنشِ
    // بازنشانی/جایگزینی کامل جمع می‌شود تا پس از commit موفق، فایل‌های یتیم از دیسک پاک شوند.
    @Query("SELECT * FROM loans WHERE customIconPath IS NOT NULL AND customIconPath != ''")
    suspend fun getLoansWithCustomIcon(): List<Loan>

    // فیکس bug.md/F6: جست‌وجوی وام با شناسهٔ عمومی (کلید تطبیق ادغام/بکاپ — NB-34)
    @Query("SELECT * FROM loans WHERE publicId = :publicId LIMIT 1")
    suspend fun getLoanByPublicId(publicId: String): Loan?

    // فیکس bug.md/F6: رکوردهای مهاجرت‌خورده از نسخهٔ ۴ که هنوز شناسهٔ عمومی ندارند
    @Query("SELECT * FROM loans WHERE publicId = ''")
    suspend fun getLoansWithoutPublicId(): List<Loan>
}

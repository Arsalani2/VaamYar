package com.aistudio.vamyar.data

import com.aistudio.vamyar.model.DebtorShare
import com.aistudio.vamyar.model.Loan
import com.aistudio.vamyar.model.UserProfile
import com.aistudio.vamyar.util.PersianNameNormalizer
import com.aistudio.vamyar.util.PublicId

/**
 * فیکس bug.md/NB-34: کلیدهای تطبیق ادغام در سطح پکیج — همان منطقِ متدهای هم‌نامِ
 * LoanRepository (mergeFromBackup) و پیش‌نمایش ایمپورت (MainViewModel) تا هرگز واگرا نشوند.
 * توابع خالص هستند و به هیچ وضعیت اینستنس وابسته نیستند.
 */

/**
 * فیکس bug.md/NB-34: کلید تطبیق کاربر در ادغام — اول publicId معتبر (F6)؛
 * بکاپ قدیمی بدون شناسه → نام کانونی (PersianNameNormalizer: ي/ك عربی، فاصله‌ها).
 */
fun matchExistingUser(backupUser: UserProfile, existingUsers: List<UserProfile>): UserProfile? {
    if (PublicId.isValidUserId(backupUser.publicId)) {
        existingUsers.firstOrNull { it.publicId == backupUser.publicId }?.let { return it }
    }
    return existingUsers.firstOrNull { PersianNameNormalizer.namesMatch(it.name, backupUser.name) }
}

/**
 * فیکس bug.md/NB-34: کلید تطبیق وام — اول publicId؛ fallback بکاپ قدیمی:
 * عنوان + مبلغ ماهانه + سال/ماه شروع (تصمیم بازبینی ۴.۵).
 */
fun matchExistingLoan(backupLoan: Loan, existingLoans: List<Loan>): Loan? {
    if (PublicId.isValidLoanId(backupLoan.publicId)) {
        existingLoans.firstOrNull { it.publicId == backupLoan.publicId }?.let { return it }
    }
    return existingLoans.firstOrNull {
        it.title.trim() == backupLoan.title.trim() &&
            it.monthlyAmount == backupLoan.monthlyAmount &&
            it.startYear == backupLoan.startYear &&
            it.startMonth == backupLoan.startMonth
    }
}

/**
 * فیکس bug.md/NB-34: کلید تطبیق سهم — اول publicId؛ fallback: همان وامِ محلی
 * ([localLoanId] نگاشت‌شده) + نام کانونی شریک.
 */
fun matchExistingShare(
    backupShare: DebtorShare,
    existingShares: List<DebtorShare>,
    localLoanId: Long
): DebtorShare? {
    if (PublicId.isValidShareId(backupShare.publicId)) {
        existingShares.firstOrNull { it.publicId == backupShare.publicId }?.let { return it }
    }
    return existingShares.firstOrNull {
        it.loanId == localLoanId &&
            PersianNameNormalizer.namesMatch(it.personName, backupShare.personName)
    }
}

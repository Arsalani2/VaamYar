package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debtor_shares",
    indices = [Index("publicId"), Index("ownerPublicId")]
)
data class DebtorShare(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // فیکس bug.md/F6: شناسهٔ یکتای عمومی سهم (S-xxxxxx) + شناسهٔ مالکِ واقعی سهم
    // (publicId کاربر مربوطه) — پیوندِ سهم↔کاربر دیگر فقط با نامِ قابل‌شکستنی نیست؛
    // نام فقط نمایشی است. خالی = تخصیص‌نیافته/backfill می‌شود.
    val publicId: String = "",
    val ownerPublicId: String = "",
    val loanId: Long,
    val loanTitle: String,
    val personName: String,
    val shareAmount: Long,
    val shareDescription: String = "",
    val dueDay: Int = 1,
    val isPaidForCurrentMonth: Boolean = false,
    val lastPaidMonthName: String = ""
)

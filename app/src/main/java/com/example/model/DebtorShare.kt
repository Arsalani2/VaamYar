package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debtor_shares")
data class DebtorShare(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanId: Long,
    val loanTitle: String,
    val personName: String,
    val shareAmount: Long,
    val shareDescription: String = "",
    val dueDay: Int = 1,
    val isPaidForCurrentMonth: Boolean = false,
    val lastPaidMonthName: String = ""
)

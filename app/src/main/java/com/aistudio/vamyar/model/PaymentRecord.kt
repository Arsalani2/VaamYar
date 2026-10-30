package com.aistudio.vamyar.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_records")
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanId: Long,
    val loanTitle: String,
    val installmentNumber: Int,
    val amountPaid: Long,
    val paymentDateShamsi: String,
    val timestamp: Long = System.currentTimeMillis()
)

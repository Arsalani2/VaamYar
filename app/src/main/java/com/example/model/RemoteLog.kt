package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_logs")
data class RemoteLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val senderPhone: String,
    val commandText: String,
    val replyText: String,
    val isSuccess: Boolean = true,
    val shamsiDate: String = ""
)

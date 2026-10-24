package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_users")
data class AppUser(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val isPrimary: Boolean = false, // کاربر مبنا
    val colorHex: Long = 0xFF10B981L,
    val role: String = "شریک" // "کاربر مبنا" یا "شریک"
)

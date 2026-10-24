package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isPrimary: Boolean = false, // کاربر مبنا
    val avatarColorHex: Long = 0xFF1976D2L,
    val phoneNumber: String = "",
    val notes: String = ""
)

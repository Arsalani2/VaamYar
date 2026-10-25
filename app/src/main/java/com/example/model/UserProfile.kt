package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isPrimary: Boolean = false, // کاربر مبنا
    // فیکس bug.md/P3-5 (تصمیم کاربر): قابلیت «شماره تماس شریک» از کل پروژه حذف شد؛
    // ستون phoneNumber با MIGRATION_3_4 از دیتابیس حذف می‌شود.
    val avatarColorHex: Long = 0xFF1976D2L,
    val notes: String = ""
)

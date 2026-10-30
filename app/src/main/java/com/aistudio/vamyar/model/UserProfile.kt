package com.aistudio.vamyar.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles", indices = [Index("publicId")])
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // فیکس bug.md/F6: شناسهٔ یکتای عمومی کاربر — جایگزین هویتِ نام‌پایه (تصمیم A4 منسوخ)؛
    // سهم‌های شرکا با ownerPublicId به صاحب واقعی‌شان وصل می‌شوند. خالی = تخصیص‌نیافته.
    val publicId: String = "",
    val name: String,
    val isPrimary: Boolean = false, // کاربر مبنا
    // فیکس bug.md/P3-5 (تصمیم کاربر): قابلیت «شماره تماس شریک» از کل پروژه حذف شد؛
    // ستون phoneNumber با MIGRATION_3_4 از دیتابیس حذف می‌شود.
    val avatarColorHex: Long = 0xFF1976D2L,
    val notes: String = ""
)

package com.aistudio.vamyar.data

import androidx.room.*
import com.aistudio.vamyar.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles ORDER BY isPrimary DESC, id ASC")
    fun getAllUsers(): Flow<List<UserProfile>>

    // فیکس bug.md/P1-7: LIMIT 1 بدون ORDER BY انتخابِ غیرقطعی می‌داد؛
    // اکنون همیشه اولین (قدیمی‌ترین) کاربرِ دارای پرچم مبنا به‌صورت قطعی برگردانده می‌شود.
    @Query("SELECT * FROM user_profiles WHERE isPrimary = 1 ORDER BY id ASC LIMIT 1")
    fun getPrimaryUser(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE name = :name LIMIT 1")
    suspend fun getUserByName(name: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserProfile): Long

    @Update
    suspend fun updateUser(user: UserProfile)

    @Query("DELETE FROM user_profiles WHERE id = :id")
    suspend fun deleteUserById(id: Long)

    @Query("UPDATE user_profiles SET isPrimary = 0")
    suspend fun clearPrimaryFlags()

    @Query("UPDATE user_profiles SET isPrimary = 1 WHERE id = :id")
    suspend fun markUserAsPrimary(id: Long)

    // فیکس bug.md/NB-49: گارد در برابر شناسه نامعتبر؛ هرگز اجازه نمی‌دهد id <= 0
    // پرچم مبنای کاربران دیگر را پاک کرده و سیستم را بدون کاربر مبنا رها کند.
    @Transaction
    suspend fun setPrimaryUser(id: Long) {
        if (id <= 0L) return
        clearPrimaryFlags()
        markUserAsPrimary(id)
    }

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun getCount(): Int

    @Query("DELETE FROM user_profiles")
    suspend fun clearAllUsers()

    // فیکس bug.md/F6: جست‌وجوی کاربر با شناسهٔ عمومی + خواندن یک‌بارهٔ همهٔ کاربران
    // (برای backfill و پیوند ownerPublicId سهم‌ها)
    @Query("SELECT * FROM user_profiles WHERE publicId = :publicId LIMIT 1")
    suspend fun getUserByPublicId(publicId: String): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE publicId = ''")
    suspend fun getUsersWithoutPublicId(): List<UserProfile>

    @Query("SELECT * FROM user_profiles ORDER BY isPrimary DESC, id ASC")
    suspend fun getAllUsersOnce(): List<UserProfile>
}

package com.example.data

import androidx.room.*
import com.example.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles ORDER BY isPrimary DESC, id ASC")
    fun getAllUsers(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles WHERE isPrimary = 1 LIMIT 1")
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

    @Transaction
    suspend fun setPrimaryUser(id: Long) {
        clearPrimaryFlags()
        markUserAsPrimary(id)
    }

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun getCount(): Int

    @Query("DELETE FROM user_profiles")
    suspend fun clearAllUsers()
}

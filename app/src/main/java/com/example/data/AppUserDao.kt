package com.example.data

import androidx.room.*
import com.example.model.AppUser
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUserDao {
    @Query("SELECT * FROM app_users ORDER BY isPrimary DESC, id ASC")
    fun getAllUsers(): Flow<List<AppUser>>

    @Query("SELECT * FROM app_users WHERE isPrimary = 1 LIMIT 1")
    fun getPrimaryUser(): Flow<AppUser?>

    @Query("SELECT * FROM app_users WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryUserDirect(): AppUser?

    @Query("SELECT * FROM app_users WHERE id = :id")
    suspend fun getUserById(id: Long): AppUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: AppUser): Long

    @Update
    suspend fun updateUser(user: AppUser)

    @Delete
    suspend fun deleteUser(user: AppUser)

    @Query("UPDATE app_users SET isPrimary = 0")
    suspend fun clearPrimaryStatus()

    @Query("UPDATE app_users SET isPrimary = 1 WHERE id = :userId")
    suspend fun setPrimaryUser(userId: Long)

    @Query("SELECT COUNT(*) FROM app_users")
    suspend fun getCount(): Int
}

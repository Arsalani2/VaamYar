package com.example.data

import androidx.room.*
import com.example.model.RemoteCommand
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteCommandDao {
    @Query("SELECT * FROM remote_commands ORDER BY isSystemDefault DESC, id ASC")
    fun getAllCommands(): Flow<List<RemoteCommand>>

    @Query("SELECT * FROM remote_commands WHERE isEnabled = 1")
    suspend fun getEnabledCommands(): List<RemoteCommand>

    @Query("SELECT * FROM remote_commands WHERE id = :id")
    suspend fun getCommandById(id: Long): RemoteCommand?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: RemoteCommand): Long

    @Update
    suspend fun updateCommand(command: RemoteCommand)

    @Delete
    suspend fun deleteCommand(command: RemoteCommand)

    @Query("UPDATE remote_commands SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleCommandEnabled(id: Long, isEnabled: Boolean)

    @Query("SELECT COUNT(*) FROM remote_commands")
    suspend fun getCount(): Int
}

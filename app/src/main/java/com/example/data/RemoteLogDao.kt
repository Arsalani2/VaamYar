package com.example.data

import androidx.room.*
import com.example.model.RemoteLog
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteLogDao {
    @Query("SELECT * FROM remote_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<RemoteLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RemoteLog): Long

    @Query("DELETE FROM remote_logs")
    suspend fun clearLogs()
}

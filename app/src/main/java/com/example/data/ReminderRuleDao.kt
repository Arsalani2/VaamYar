package com.example.data

import androidx.room.*
import com.example.model.ReminderRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderRuleDao {
    @Query("SELECT * FROM reminder_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<ReminderRule>>

    @Query("SELECT * FROM reminder_rules WHERE isEnabled = 1")
    suspend fun getEnabledRules(): List<ReminderRule>

    @Query("SELECT * FROM reminder_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): ReminderRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ReminderRule): Long

    @Update
    suspend fun updateRule(rule: ReminderRule)

    @Delete
    suspend fun deleteRule(rule: ReminderRule)

    @Query("UPDATE reminder_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleRuleEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE reminder_rules SET lastTriggeredShamsi = :shamsiDate WHERE id = :id")
    suspend fun updateLastTriggered(id: Long, shamsiDate: String)

    @Query("SELECT COUNT(*) FROM reminder_rules")
    suspend fun getCount(): Int
}

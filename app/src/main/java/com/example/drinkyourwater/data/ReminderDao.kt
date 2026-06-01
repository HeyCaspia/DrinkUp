package com.example.drinkyourwater.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM medicines")
    fun getAllMedicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines")
    suspend fun getAllMedicinesList(): List<Medicine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: Medicine)

    @Delete
    suspend fun deleteMedicine(medicine: Medicine)

    @Query("SELECT * FROM water_reminders")
    fun getAllWaterReminders(): Flow<List<WaterReminder>>

    @Query("SELECT * FROM water_reminders")
    suspend fun getAllWaterRemindersList(): List<WaterReminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaterReminder(waterReminder: WaterReminder)

    @Delete
    suspend fun deleteWaterReminder(waterReminder: WaterReminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ReminderHistory)

    @Query("SELECT * FROM reminder_history WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getHistorySince(startTime: Long): Flow<List<ReminderHistory>>

    @Query("SELECT * FROM reminder_history WHERE timestamp >= :startTime")
    suspend fun getHistorySinceList(startTime: Long): List<ReminderHistory>

    @Query("SELECT COUNT(*) FROM reminder_history WHERE type = 'WATER' AND timestamp >= :startTime")
    suspend fun getWaterCountSince(startTime: Long): Int

    @Query("SELECT COUNT(*) FROM reminder_history WHERE type = 'MEDICINE' AND name = :name AND timestamp >= :startTime")
    suspend fun getMedicineCountSince(name: String, startTime: Long): Int

    @Query("SELECT COUNT(*) FROM reminder_history WHERE type = :type AND name = :name AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getLogCountInRange(type: String, name: String, startTime: Long, endTime: Long): Int
}

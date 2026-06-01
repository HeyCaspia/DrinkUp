package com.example.drinkyourwater.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_reminders")
data class WaterReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String, // format: yyyy-MM-dd
    val startTime: String, // format: HH:mm
    val intervalMinutes: Int,
    val timesPerDay: Int,
    val repeatDays: String = "1,2,3,4,5,6,7", // Default every day
    val isPaused: Boolean = false
)

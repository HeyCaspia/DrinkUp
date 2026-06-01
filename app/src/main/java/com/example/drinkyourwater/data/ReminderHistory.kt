package com.example.drinkyourwater.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "reminder_history")
data class ReminderHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String, // "MEDICINE" or "WATER"
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

package com.example.drinkyourwater.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val date: String, // format: yyyy-MM-dd
    val time: String, // format: HH:mm
    val repeatDays: String, // comma separated days: "1,2,3" (1=Sun, 7=Sat)
    val intervalMinutes: Int,
    val timesPerDay: Int,
    val endDate: String? = null,
    val isPaused: Boolean = false
)

package com.example.drinkyourwater.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.drinkyourwater.data.ReminderDatabase
import kotlinx.coroutines.CoroutineScope
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        val message = intent.getStringExtra("message") ?: "Time to drink!"
        val type = intent.getStringExtra("type")
        val name = intent.getStringExtra("name")
        val scheduledTime = intent.getLongExtra("scheduledTime", 0L)
        
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val bedtime = prefs.getString("bedtime", "22:00") ?: "22:00"
        val wakeupTime = prefs.getString("wakeup_time", "07:00") ?: "07:00"

        if (isCurrentTimeInSleepWindow(bedtime, wakeupTime)) {
            return // Don't notify during sleep
        }

        if (type != null && name != null) {
            val database = ReminderDatabase.getDatabase(context)
            val dao = database.reminderDao()
            
            CoroutineScope(Dispatchers.IO).launch {
                // If scheduledTime is provided, check if user already logged this interval
                if (scheduledTime > 0) {
                    val logWindowStart = scheduledTime - (20 * 60 * 1000) // 20 mins window
                    val logCount = dao.getLogCountInRange(type, name, logWindowStart, System.currentTimeMillis())
                    if (logCount > 0) {
                        return@launch // Already logged, skip notification
                    }
                }

                val medicine = if (type == "MEDICINE") dao.getAllMedicinesList().find { it.name == name } else null
                val water = if (type == "WATER") dao.getAllWaterRemindersList().firstOrNull() else null

                val existsAndNotPaused = when (type) {
                    "MEDICINE" -> medicine != null && !medicine.isPaused
                    "WATER" -> water != null && !water.isPaused
                    else -> true
                }
                
                if (existsAndNotPaused) {
                    NotificationHelper(context).showNotification(title, message, type, name)
                }
            }
        } else {
            NotificationHelper(context).showNotification(title, message)
        }
    }

    private fun isCurrentTimeInSleepWindow(bedtime: String, wakeupTime: String): Boolean {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val bedtimeParts = bedtime.split(":")
        val bedMinutes = (bedtimeParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (bedtimeParts.getOrNull(1)?.toIntOrNull() ?: 0)

        val wakeupParts = wakeupTime.split(":")
        val wakeMinutes = (wakeupParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (wakeupParts.getOrNull(1)?.toIntOrNull() ?: 0)

        return if (bedMinutes < wakeMinutes) {
            currentMinutes in bedMinutes until wakeMinutes
        } else {
            currentMinutes >= bedMinutes || currentMinutes < wakeMinutes
        }
    }
}

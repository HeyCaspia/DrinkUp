package com.example.drinkyourwater.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import com.example.drinkyourwater.data.ReminderDatabase
import com.example.drinkyourwater.utils.DateTimeUtils
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
                val medicine = if (type == "MEDICINE") dao.getAllMedicinesList().find { it.name == name } else null
                val water = if (type == "WATER") dao.getAllWaterRemindersList().firstOrNull() else null

                // 1. Check if item exists and is not paused
                val existsAndNotPaused = when (type) {
                    "MEDICINE" -> medicine != null && !medicine.isPaused
                    "WATER" -> water != null && !water.isPaused
                    else -> true
                }
                if (!existsAndNotPaused) return@launch

                // 2. Check if daily goal is already reached (based on last wake-up time)
                val cycleStartTime = DateTimeUtils.getLastWakeUpTime(wakeupTime)
                
                if (type == "MEDICINE" && medicine != null) {
                    val countInCycle = dao.getLogCountInRange("MEDICINE", medicine.name, cycleStartTime, System.currentTimeMillis())
                    if (countInCycle >= medicine.timesPerDay) return@launch
                } else if (type == "WATER" && water != null) {
                    val countInCycle = dao.getLogCountInRange("WATER", "Water", cycleStartTime, System.currentTimeMillis())
                    if (countInCycle >= water.timesPerDay) return@launch
                }

                // 3. Check if user already logged this specific interval (T-20m window)
                if (scheduledTime > 0) {
                    val logWindowStart = scheduledTime - (20 * 60 * 1000)
                    val logCount = dao.getLogCountInRange(type, name, logWindowStart, System.currentTimeMillis())
                    if (logCount > 0) return@launch
                }
                
                NotificationHelper(context).showNotification(title, message, type, name, scheduledTime)

                // Start the nagging cycle if this was a primary reminder (not a warning)
                if (scheduledTime > 0) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val nagIntent = Intent(context, MissedIntervalReceiver::class.java).apply {
                        putExtra("type", type)
                        putExtra("name", name)
                        putExtra("scheduledTime", scheduledTime)
                    }
                    val requestCode = Math.abs(name.hashCode() + (scheduledTime % 100000).toInt() + 3000000)
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        nagIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerAt = System.currentTimeMillis() + (30 * 60 * 1000)
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    }
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

package com.example.drinkyourwater.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import com.example.drinkyourwater.data.ReminderDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class MissedIntervalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val name = intent.getStringExtra("name") ?: return
        val medicineId = intent.getIntExtra("medicineId", -1).let { if (it == -1) null else it }
        val scheduledTime = intent.getLongExtra("scheduledTime", 0L)
        
        val database = ReminderDatabase.getDatabase(context)
        val dao = database.reminderDao()
        
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val bedtime = prefs.getString("bedtime", "22:00") ?: "22:00"
        val wakeupTime = prefs.getString("wakeup_time", "07:00") ?: "07:00"

        if (isCurrentTimeInSleepWindow(bedtime, wakeupTime)) {
            return // Don't notify during sleep
        }

        CoroutineScope(Dispatchers.IO).launch {
            val medicine = if (type == "MEDICINE") {
                if (medicineId != null) dao.getAllMedicinesList().find { it.id == medicineId }
                else dao.getAllMedicinesList().find { it.name == name }
            } else null
            val water = if (type == "WATER") dao.getAllWaterRemindersList().firstOrNull() else null

            val existsAndNotPaused = when (type) {
                "MEDICINE" -> medicine != null && !medicine.isPaused
                "WATER" -> water != null && !water.isPaused
                else -> true
            }

            if (!existsAndNotPaused) return@launch

            val startTime = scheduledTime - (15 * 60 * 1000)
            val endTime = System.currentTimeMillis()
            
            val count = dao.getLogCountInRange(type, medicineId, startTime, endTime)
            
            if (count == 0) {
                NotificationHelper(context).showNotification(
                    "Missed Interval",
                    "You missed your scheduled $name at ${formatTo12Hour(scheduledTime)}. Please take it now if possible!",
                    type,
                    name,
                    scheduledTime,
                    medicineId
                )

                // Reschedule nag in 30 minutes
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val nagIntent = Intent(context, MissedIntervalReceiver::class.java).apply {
                    putExtra("type", type)
                    putExtra("name", name)
                    putExtra("medicineId", medicineId)
                    putExtra("scheduledTime", scheduledTime)
                }
                
                val requestCode = Math.abs((medicineId ?: name.hashCode()) + (scheduledTime % 100000).toInt() + 3000000)
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
    }
    
    private fun formatTo12Hour(timeMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timeMillis))
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

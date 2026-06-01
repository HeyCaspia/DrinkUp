package com.example.drinkyourwater.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.drinkyourwater.data.ReminderDatabase
import java.util.Calendar

class DailySummaryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = ReminderDatabase.getDatabase(applicationContext)
        val dao = database.reminderDao()
        val helper = NotificationHelper(applicationContext)
        
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // 1. Check missed medicines (only those not paused)
        val medicines = dao.getAllMedicinesList().filter { !it.isPaused }
        val missedMedicines = mutableListOf<String>()
        
        for (med in medicines) {
            val countToday = dao.getMedicineCountSince(med.name, todayStart)
            if (countToday < med.timesPerDay) {
                missedMedicines.add(med.name)
            }
        }
        
        if (missedMedicines.isNotEmpty()) {
            helper.showNotification(
                "Missed Medicine!",
                "You still have doses to take: ${missedMedicines.joinToString(", ")}. Don't forget!"
            )
        }
        
        // 2. Check water intake (only if not paused)
        val isWaterPaused = dao.getAllWaterRemindersList().any { it.isPaused }
        if (!isWaterPaused) {
            val waterCount = dao.getWaterCountSince(todayStart)
            if (waterCount < 8) {
                helper.showNotification(
                    "Hydration Alert",
                    "You haven't been drinking enough, bruh. Only $waterCount glasses so far!"
                )
            } else {
                helper.showNotification(
                    "Daily Summary",
                    "You drank $waterCount glasses of water today! Great job staying hydrated."
                )
            }
        }
        
        return Result.success()
    }
}

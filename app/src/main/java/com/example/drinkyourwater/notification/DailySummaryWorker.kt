package com.example.drinkyourwater.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.drinkyourwater.data.ReminderDatabase

class DailySummaryWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = ReminderDatabase.getDatabase(applicationContext)
        val dao = database.reminderDao()
        val helper = NotificationHelper(applicationContext)
        
        // Window: Last 24 hours from right now (which is triggered at bedtime)
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000)
        
        // 1. Check missed medicines (only those not paused)
        val medicines = dao.getAllMedicinesList().filter { !it.isPaused }
        val missedMedicines = mutableListOf<String>()
        
        for (med in medicines) {
            val countInWindow = dao.getLogCountInRange("MEDICINE", med.name, startTime, endTime)
            if (countInWindow < med.timesPerDay) {
                missedMedicines.add(med.name)
            }
        }
        
        if (missedMedicines.isNotEmpty()) {
            helper.showNotification(
                "Missed Medicine!",
                "In the last 24 hours, you missed doses for: ${missedMedicines.joinToString(", ")}. Don't forget!"
            )
        }
        
        // 2. Check water intake (only if not paused)
        val isWaterPaused = dao.getAllWaterRemindersList().any { it.isPaused }
        if (!isWaterPaused) {
            val waterCount = dao.getLogCountInRange("WATER", "Water Reminder", startTime, endTime)
            if (waterCount < 8) {
                helper.showNotification(
                    "Hydration Alert",
                    "You didn't drink enough water in the last 24 hours. Only $waterCount glasses logged!"
                )
            } else {
                helper.showNotification(
                    "Daily Summary",
                    "You drank $waterCount glasses of water in the last 24 hours! Great job staying hydrated."
                )
            }
        }
        
        return Result.success()
    }
}

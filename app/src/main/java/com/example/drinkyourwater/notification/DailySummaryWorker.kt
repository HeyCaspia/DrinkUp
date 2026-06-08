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
        val waterReminders = dao.getAllWaterRemindersList()
        val isWaterPaused = waterReminders.any { it.isPaused }
        val waterGoal = waterReminders.sumOf { it.timesPerDay }.let { if (it > 0) it else 8 }
        
        if (!isWaterPaused) {
            val waterCount = dao.getLogCountInRange("WATER", "Water", startTime, endTime)
            if (waterCount < waterGoal) {
                helper.showNotification(
                    "Hydration Alert",
                    "In the last 24 hours, you only drank $waterCount/$waterGoal glasses. Keep it up tomorrow!"
                )
            } else {
                helper.showNotification(
                    "Daily Summary",
                    "Goal achieved! You drank $waterCount glasses of water in the last 24 hours. Great job!"
                )
            }
        }
        
        return Result.success()
    }
}

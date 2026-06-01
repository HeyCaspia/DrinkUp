package com.example.drinkyourwater.notification

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val title = inputData.getString("title") ?: "Reminder"
        val message = inputData.getString("message") ?: "Time to drink!"
        
        NotificationHelper(applicationContext).showNotification(title, message)
        
        return Result.success()
    }
}

package com.example.drinkyourwater.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.drinkyourwater.data.ReminderDatabase
import com.example.drinkyourwater.data.ReminderHistory
import com.example.drinkyourwater.widget.DrinkWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val name = intent.getStringExtra("name") ?: return
        val notificationId = intent.getIntExtra("notificationId", 0)
        val medicineId = intent.getIntExtra("medicineId", -1).let { if (it == -1) null else it }
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())

        val database = ReminderDatabase.getDatabase(context)
        val dao = database.reminderDao()

        CoroutineScope(Dispatchers.IO).launch {
            dao.insertHistory(ReminderHistory(type = type, name = name, medicineId = medicineId, timestamp = timestamp))
            DrinkWidget().updateAll(context)
            
            // Cancel the notification after logging
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }
}

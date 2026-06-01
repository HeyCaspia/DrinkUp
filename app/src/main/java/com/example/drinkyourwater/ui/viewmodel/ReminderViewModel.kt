package com.example.drinkyourwater.ui.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.drinkyourwater.data.Medicine
import com.example.drinkyourwater.data.ReminderDatabase
import com.example.drinkyourwater.data.ReminderHistory
import com.example.drinkyourwater.data.WaterReminder
import com.example.drinkyourwater.notification.AlarmReceiver
import com.example.drinkyourwater.notification.MissedIntervalReceiver
import com.example.drinkyourwater.notification.DailySummaryWorker
import com.example.drinkyourwater.notification.ReminderWorker
import com.example.drinkyourwater.widget.DrinkWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import com.example.drinkyourwater.notification.NotificationHelper

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderDao = ReminderDatabase.getDatabase(application).reminderDao()
    private val workManager = WorkManager.getInstance(application)
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs: SharedPreferences = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: Flow<Boolean> = _notificationsEnabled

    private val _bedtime = MutableStateFlow(prefs.getString("bedtime", "22:00") ?: "22:00")
    val bedtime: Flow<String> = _bedtime

    private val _wakeupTime = MutableStateFlow(prefs.getString("wakeup_time", "07:00") ?: "07:00")
    val wakeupTime: Flow<String> = _wakeupTime

    private val _pendingLogRequest = MutableStateFlow<Pair<String, String>?>(null) // Type, Name
    val pendingLogRequest: Flow<Pair<String, String>?> = _pendingLogRequest

    init {
        scheduleDailySummary()
    }

    fun requestManualLog(type: String, name: String) {
        _pendingLogRequest.value = type to name
    }

    fun clearLogRequest() {
        _pendingLogRequest.value = null
    }

    fun toggleNotifications(enabled: Boolean) {
        prefs.edit { putBoolean("notifications_enabled", enabled) }
        _notificationsEnabled.value = enabled
        if (!enabled) {
            cancelAllAlarms()
        }
    }

    fun updateBedtime(time: String) {
        prefs.edit { putString("bedtime", time) }
        _bedtime.value = time
        scheduleDailySummary()
    }

    fun updateWakeupTime(time: String) {
        prefs.edit { putString("wakeup_time", time) }
        _wakeupTime.value = time
    }

    private fun cancelAllAlarms() {
        // Implementation for canceling alarms if needed
    }

    private fun scheduleDailySummary() {
        val now = Calendar.getInstance()
        val bedtimeStr = prefs.getString("bedtime", "22:00") ?: "22:00"
        val parts = bedtimeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 22
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val summaryTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (summaryTime.before(now)) {
            summaryTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        val delay = summaryTime.timeInMillis - now.timeInMillis
        
        val dailySummaryRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "daily_water_summary",
            ExistingPeriodicWorkPolicy.REPLACE,
            dailySummaryRequest
        )
    }

    val allMedicines: Flow<List<Medicine>> = reminderDao.getAllMedicines()
    val allWaterReminders: Flow<List<WaterReminder>> = reminderDao.getAllWaterReminders()

    // Fetch history for the last 30 days to support the calendar view
    private val _historyTimeFilter = MutableStateFlow(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<List<ReminderHistory>> = _historyTimeFilter.flatMapLatest { startTime ->
        reminderDao.getHistorySince(startTime)
    }

    fun setHistoryFilter(calendarField: Int) {
        // Keep fetching at least 30 days or more if needed, but for now we'll just keep the 30-day window
    }

    private fun getTimeAgo(calendarField: Int): Long {
        val calendar = Calendar.getInstance()
        when (calendarField) {
            Calendar.DAY_OF_YEAR -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
            }
            Calendar.WEEK_OF_YEAR -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            Calendar.MONTH -> calendar.add(Calendar.MONTH, -1)
        }
        return calendar.timeInMillis
    }

    fun markMedicineAsTaken(medicine: Medicine) {
        viewModelScope.launch {
            reminderDao.insertHistory(
                ReminderHistory(type = "MEDICINE", name = medicine.name)
            )
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun markWaterAsDrank(waterReminder: WaterReminder) {
        viewModelScope.launch {
            reminderDao.insertHistory(
                ReminderHistory(type = "WATER", name = "Water Reminder")
            )
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun addMedicine(name: String, date: String, time: String, intervalMinutes: Int, timesPerDay: Int, repeatDays: String, endDate: String?) {
        viewModelScope.launch {
            val medicine = Medicine(
                name = name,
                date = date,
                time = time,
                repeatDays = repeatDays,
                intervalMinutes = intervalMinutes,
                timesPerDay = timesPerDay,
                endDate = endDate,
                isPaused = false
            )
            reminderDao.insertMedicine(medicine)
            if (_notificationsEnabled.value) {
                scheduleReminderAlarms(name, time, intervalMinutes, if (repeatDays.isNotEmpty()) 10 else timesPerDay, repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun addWaterReminder(date: String, startTime: String, intervalMinutes: Int, timesPerDay: Int, repeatDays: String) {
        viewModelScope.launch {
            val waterReminder = WaterReminder(
                date = date,
                startTime = startTime,
                intervalMinutes = intervalMinutes,
                timesPerDay = timesPerDay,
                repeatDays = repeatDays,
                isPaused = false
            )
            reminderDao.insertWaterReminder(waterReminder)
            if (_notificationsEnabled.value) {
                scheduleReminderAlarms("Water", startTime, intervalMinutes, if (repeatDays.isNotEmpty()) 10 else timesPerDay, repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun togglePauseMedicine(medicine: Medicine) {
        viewModelScope.launch {
            val updated = medicine.copy(isPaused = !medicine.isPaused)
            reminderDao.insertMedicine(updated)
            if (!updated.isPaused && _notificationsEnabled.value) {
                scheduleReminderAlarms(updated.name, updated.time, updated.intervalMinutes, if (updated.repeatDays.isNotEmpty()) 10 else updated.timesPerDay, updated.repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun togglePauseWater(waterReminder: WaterReminder) {
        viewModelScope.launch {
            val updated = waterReminder.copy(isPaused = !waterReminder.isPaused)
            reminderDao.insertWaterReminder(updated)
            if (!updated.isPaused && _notificationsEnabled.value) {
                scheduleReminderAlarms("Water", updated.startTime, updated.intervalMinutes, if (updated.repeatDays.isNotEmpty()) 10 else updated.timesPerDay, updated.repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }

    private fun scheduleReminderAlarms(name: String, startTime: String, intervalMinutes: Int, times: Int, repeatDays: String) {
        val sdf24 = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val now = Calendar.getInstance()
        
        val startCal = Calendar.getInstance().apply {
            val date = try { sdf24.parse(startTime) } catch(e: Exception) { null } ?: return
            val tempCal = Calendar.getInstance().apply { time = date }
            set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
        }

        val enabledDays = repeatDays.split(",").filter { it.isNotEmpty() }.map { it.toInt() }

        for (i in 0 until times) {
            val currentSlot = (startCal.clone() as Calendar)
            
            if (enabledDays.isEmpty() || enabledDays.contains(currentSlot.get(Calendar.DAY_OF_WEEK))) {
                val type = if (name == "Water") "WATER" else "MEDICINE"
                // Generate unique but consistent IDs for this slot
                val baseId = (name.hashCode() + i).let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) } % 1000000
                
                // Alarm 1: 10 minutes before
                val tMinus10 = (currentSlot.clone() as Calendar).apply { add(Calendar.MINUTE, -10) }
                if (tMinus10.after(now)) {
                    scheduleAlarm("Upcoming: $name", "10 minutes left to take your $name!", tMinus10.timeInMillis, type, name, currentSlot.timeInMillis, baseId + 1000000)
                }
                
                // Alarm 2: 1 minute before
                val tMinus1 = (currentSlot.clone() as Calendar).apply { add(Calendar.MINUTE, -1) }
                if (tMinus1.after(now)) {
                    scheduleAlarm("Action Needed: $name", "1 minute left! Please take your $name.", tMinus1.timeInMillis, type, name, currentSlot.timeInMillis, baseId + 2000000)
                }

                // Missed Check: 30 minutes after
                val tPlus30 = (currentSlot.clone() as Calendar).apply { add(Calendar.MINUTE, 30) }
                if (tPlus30.after(now)) {
                    scheduleMissedCheck(type, name, currentSlot.timeInMillis, tPlus30.timeInMillis, baseId + 3000000)
                }
            }

            if (intervalMinutes > 0) {
                startCal.add(Calendar.MINUTE, intervalMinutes)
            } else {
                break
            }
        }
    }

    private fun scheduleAlarm(title: String, message: String, timeInMillis: Long, type: String? = null, name: String? = null, scheduledTime: Long? = null, requestCode: Int = 0) {
        val intent = Intent(getApplication(), AlarmReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("type", type)
            putExtra("name", name)
            putExtra("scheduledTime", scheduledTime)
        }
        
        val finalRequestCode = if (requestCode == 0) timeInMillis.toInt() else requestCode

        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(),
            finalRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        }
    }

    private fun scheduleMissedCheck(type: String, name: String, scheduledTime: Long, checkTime: Long, requestCode: Int = 0) {
        val intent = Intent(getApplication(), MissedIntervalReceiver::class.java).apply {
            putExtra("type", type)
            putExtra("name", name)
            putExtra("scheduledTime", scheduledTime)
        }
        
        val finalRequestCode = if (requestCode == 0) checkTime.toInt() else requestCode

        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(),
            finalRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent)
        }
    }

    fun addManualHistory(type: String, name: String, date: String, time: String) {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val timestamp = try {
                sdf.parse("$date $time")?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
            reminderDao.insertHistory(ReminderHistory(type = type, name = name, timestamp = timestamp))
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch { 
            reminderDao.deleteMedicine(medicine)
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun deleteWaterReminder(waterReminder: WaterReminder) {
        viewModelScope.launch { 
            reminderDao.deleteWaterReminder(waterReminder)
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateMedicine(medicine: Medicine) {
        viewModelScope.launch {
            reminderDao.insertMedicine(medicine)
            if (_notificationsEnabled.value && !medicine.isPaused) {
                scheduleReminderAlarms(medicine.name, medicine.time, medicine.intervalMinutes, if (medicine.repeatDays.isNotEmpty()) 10 else medicine.timesPerDay, medicine.repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateWaterReminder(waterReminder: WaterReminder) {
        viewModelScope.launch {
            reminderDao.insertWaterReminder(waterReminder)
            if (_notificationsEnabled.value && !waterReminder.isPaused) {
                scheduleReminderAlarms("Water", waterReminder.startTime, waterReminder.intervalMinutes, if (waterReminder.repeatDays.isNotEmpty()) 10 else waterReminder.timesPerDay, waterReminder.repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateMedicineTime(medicine: Medicine, newTime: String) {
        viewModelScope.launch {
            val updatedMedicine = medicine.copy(time = newTime)
            reminderDao.insertMedicine(updatedMedicine)
            if (_notificationsEnabled.value && !updatedMedicine.isPaused) {
                scheduleReminderAlarms(medicine.name, newTime, medicine.intervalMinutes, if (medicine.repeatDays.isNotEmpty()) 10 else medicine.timesPerDay, medicine.repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateWaterTime(waterReminder: WaterReminder, newTime: String) {
        viewModelScope.launch {
            val updatedWater = waterReminder.copy(startTime = newTime)
            reminderDao.insertWaterReminder(updatedWater)
            if (_notificationsEnabled.value && !updatedWater.isPaused) {
                scheduleReminderAlarms("Water", newTime, waterReminder.intervalMinutes, if (updatedWater.repeatDays.isNotEmpty()) 10 else updatedWater.timesPerDay, updatedWater.repeatDays)
            }
            DrinkWidget().updateAll(getApplication())
        }
    }
}

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
import java.util.Date
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

    private val _pendingLogRequest = MutableStateFlow<Triple<String, String, Int?>?>(null) // Type, Name, medicineId
    val pendingLogRequest: Flow<Triple<String, String, Int?>?> = _pendingLogRequest

    init {
        scheduleDailySummary()
        refreshAllAlarms()
    }

    fun requestManualLog(type: String, name: String, medicineId: Int? = null) {
        _pendingLogRequest.value = Triple(type, name, medicineId)
    }

    fun clearLogRequest() {
        _pendingLogRequest.value = null
    }

    fun toggleNotifications(enabled: Boolean) {
        prefs.edit { putBoolean("notifications_enabled", enabled) }
        _notificationsEnabled.value = enabled
        if (!enabled) {
            cancelAllAlarms()
        } else {
            refreshAllAlarms()
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
        // Alarms are verified in receivers.
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

    private val _historyTimeFilter = MutableStateFlow(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.timeInMillis)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: Flow<List<ReminderHistory>> = _historyTimeFilter.flatMapLatest { startTime ->
        reminderDao.getHistorySince(startTime)
    }

    fun setHistoryFilter(calendarField: Int) {}

    fun markMedicineAsTaken(medicine: Medicine) {
        viewModelScope.launch {
            reminderDao.insertHistory(ReminderHistory(type = "MEDICINE", name = medicine.name, medicineId = medicine.id))
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun markWaterAsDrank(waterReminder: WaterReminder) {
        viewModelScope.launch {
            reminderDao.insertHistory(ReminderHistory(type = "WATER", name = "Water"))
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun addMedicine(name: String, date: String, time: String, interval: Int, times: Int, repeatDays: String, endDate: String?) {
        viewModelScope.launch {
            val medicine = Medicine(name = name, date = date, time = time, repeatDays = repeatDays, intervalMinutes = interval, timesPerDay = times, endDate = endDate, isPaused = false)
            reminderDao.insertMedicine(medicine)
            // We need the ID generated by DB to schedule accurately.
            // Since refreshAllAlarms is called on init and manual logs, let's just refresh.
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun addWaterReminder(date: String, startTime: String, interval: Int, times: Int, repeatDays: String) {
        viewModelScope.launch {
            val waterReminder = WaterReminder(date = date, startTime = startTime, intervalMinutes = interval, timesPerDay = times, repeatDays = repeatDays, isPaused = false)
            reminderDao.insertWaterReminder(waterReminder)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun togglePauseMedicine(medicine: Medicine) {
        viewModelScope.launch {
            val updated = medicine.copy(isPaused = !medicine.isPaused)
            reminderDao.insertMedicine(updated)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun togglePauseWater(waterReminder: WaterReminder) {
        viewModelScope.launch {
            val updated = waterReminder.copy(isPaused = !waterReminder.isPaused)
            reminderDao.insertWaterReminder(updated)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateMedicine(medicine: Medicine) {
        viewModelScope.launch {
            reminderDao.insertMedicine(medicine)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateWaterReminder(waterReminder: WaterReminder) {
        viewModelScope.launch {
            reminderDao.insertWaterReminder(waterReminder)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateMedicineTime(medicine: Medicine, newTime: String) {
        viewModelScope.launch {
            val updatedMedicine = medicine.copy(time = newTime)
            reminderDao.insertMedicine(updatedMedicine)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun updateWaterTime(waterReminder: WaterReminder, newTime: String) {
        viewModelScope.launch {
            val updatedWater = waterReminder.copy(startTime = newTime)
            reminderDao.insertWaterReminder(updatedWater)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch { 
            reminderDao.deleteMedicine(medicine)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun deleteWaterReminder(waterReminder: WaterReminder) {
        viewModelScope.launch { 
            reminderDao.deleteWaterReminder(waterReminder)
            refreshAllAlarms()
            DrinkWidget().updateAll(getApplication())
        }
    }

    fun addManualHistory(type: String, name: String, date: String, time: String, medicineId: Int? = null) {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val timestamp = try {
                sdf.parse("$date $time")?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
            reminderDao.insertHistory(ReminderHistory(type = type, name = name, timestamp = timestamp, medicineId = medicineId))
            DrinkWidget().updateAll(getApplication())
        }
    }

    private fun refreshAllAlarms() {
        viewModelScope.launch {
            val medicines = reminderDao.getAllMedicinesList()
            val waterReminders = reminderDao.getAllWaterRemindersList()
            if (_notificationsEnabled.value) {
                medicines.filter { !it.isPaused }.forEach { scheduleReminderAlarms(it.name, it.date, it.time, it.intervalMinutes, it.timesPerDay, it.repeatDays, it.endDate, it.id) }
                waterReminders.filter { !it.isPaused }.forEach { scheduleReminderAlarms("Water", it.date, it.startTime, it.intervalMinutes, it.timesPerDay, it.repeatDays, null, null) }
            }
        }
    }

    private fun scheduleReminderAlarms(name: String, startDate: String, startTime: String, interval: Int, times: Int, repeatDays: String, endDate: String?, medicineId: Int?) {
        val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val now = Calendar.getInstance()
        
        val startCal = Calendar.getInstance().apply {
            val d = try { sdfDate.parse(startDate) } catch(e: Exception) { null } ?: Date()
            val t = try { sdfTime.parse(startTime) } catch(e: Exception) { null } ?: Date()
            val dCal = Calendar.getInstance().apply { time = d }
            val tCal = Calendar.getInstance().apply { time = t }
            set(dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH), tCal.get(Calendar.HOUR_OF_DAY), tCal.get(Calendar.MINUTE), 0)
        }

        val endCal = endDate?.let {
            Calendar.getInstance().apply {
                time = try { sdfDate.parse(it) } catch(e: Exception) { null } ?: Date(Long.MAX_VALUE)
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            }
        } ?: Calendar.getInstance().apply { timeInMillis = Long.MAX_VALUE }

        val enabledDays = repeatDays.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
        val limit = (Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 14) })
        
        val dayWalker = (startCal.clone() as Calendar)
        while (dayWalker.before(endCal) && dayWalker.before(limit)) {
            if (enabledDays.isEmpty() || enabledDays.contains(dayWalker.get(Calendar.DAY_OF_WEEK))) {
                for (doseNum in 0 until times) {
                    val doseSlot = (dayWalker.clone() as Calendar)
                    if (interval > 0) doseSlot.add(Calendar.MINUTE, doseNum * interval) else if (doseNum > 0) break
                    
                    if (doseSlot.after(now) && doseSlot.before(endCal)) {
                        val type = if (name == "Water") "WATER" else "MEDICINE"
                        val baseId = Math.abs((medicineId ?: name.hashCode()) + doseSlot.get(Calendar.DAY_OF_YEAR) * 31 + doseNum) % 1000000
                        scheduleAlarm("Upcoming: $name", "10 minutes left!", doseSlot.timeInMillis - 600000, type, name, doseSlot.timeInMillis, baseId + 1000000, medicineId)
                        scheduleAlarm("Action Needed: $name", "1 minute left!", doseSlot.timeInMillis - 60000, type, name, doseSlot.timeInMillis, baseId + 2000000, medicineId)
                        scheduleMissedCheck(type, name, doseSlot.timeInMillis, doseSlot.timeInMillis + 1800000, baseId + 3000000, medicineId)
                    }
                }
            }
            dayWalker.add(Calendar.DAY_OF_YEAR, 1)
            val tCal = Calendar.getInstance().apply { time = try { sdfTime.parse(startTime)!! } catch(e:Exception){Date()} }
            dayWalker.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY)); dayWalker.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
        }
    }

    private fun scheduleAlarm(title: String, message: String, timeInMillis: Long, type: String?, name: String?, scheduledTime: Long?, requestCode: Int, medicineId: Int?) {
        if (timeInMillis < System.currentTimeMillis()) return
        val intent = Intent(getApplication(), AlarmReceiver::class.java).apply {
            putExtra("title", title); putExtra("message", message); putExtra("type", type); putExtra("name", name); putExtra("scheduledTime", scheduledTime); putExtra("medicineId", medicineId)
        }
        val pendingIntent = PendingIntent.getBroadcast(getApplication(), requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
    }

    private fun scheduleMissedCheck(type: String, name: String, scheduledTime: Long, checkTime: Long, requestCode: Int, medicineId: Int?) {
        val intent = Intent(getApplication(), MissedIntervalReceiver::class.java).apply {
            putExtra("type", type); putExtra("name", name); putExtra("scheduledTime", scheduledTime); putExtra("medicineId", medicineId)
        }
        val pendingIntent = PendingIntent.getBroadcast(getApplication(), requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (alarmManager.canScheduleExactAlarms()) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent)
        else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent)
    }
}

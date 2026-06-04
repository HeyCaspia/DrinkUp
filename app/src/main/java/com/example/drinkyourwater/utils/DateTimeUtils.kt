package com.example.drinkyourwater.utils

import java.text.SimpleDateFormat
import java.util.*

object DateTimeUtils {
    fun formatTo12Hour(time24: String): String {
        return try {
            val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf24.parse(time24)
            sdf12.format(date!!)
        } catch (e: Exception) {
            time24
        }
    }

    fun calculateNextTime(startTime24: String, intervalMinutes: Int = 0, timesPerDay: Int = 1, lastLogTime: Long? = null, wakeupTimeStr: String? = null, history: List<com.example.drinkyourwater.data.ReminderHistory>? = null, type: String? = null, name: String? = null): String {
        val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        // Check if daily goal is reached based on sleep cycle
        if (wakeupTimeStr != null && history != null && type != null && name != null) {
            val cycleStartTime = getLastWakeUpTime(wakeupTimeStr)
            val countInCycle = history.count { it.type == type && it.name == name && it.timestamp >= cycleStartTime }
            
            if (countInCycle >= timesPerDay) {
                // Goal reached! Reset to usual start time for tomorrow.
                val startCalTomorrow = Calendar.getInstance().apply {
                    val date = try { sdf24.parse(startTime24)!! } catch(e:Exception){Date()}
                    val tempCal = Calendar.getInstance().apply { time = date }
                    set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                return formatTo12Hour(sdf24.format(startCalTomorrow.time))
            }
        }

        val startCal = Calendar.getInstance().apply {
            val date = try { sdf24.parse(startTime24) } catch(e: Exception) { null } ?: return "Unknown"
            val tempCal = Calendar.getInstance().apply { time = date }
            set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
        }

        val referenceTime = Calendar.getInstance()
        lastLogTime?.let {
            if (it > referenceTime.timeInMillis) {
                referenceTime.timeInMillis = it + 60000
            } else if (it > referenceTime.timeInMillis - 1000 * 60 * 5) {
                referenceTime.timeInMillis = it + 60000
            }
        }

        if (intervalMinutes <= 0) {
            if (startCal.before(referenceTime)) {
                startCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            var count = 0
            while (startCal.before(referenceTime) && count < timesPerDay - 1) {
                startCal.add(Calendar.MINUTE, intervalMinutes)
                count++
            }
            if (startCal.before(referenceTime)) {
                val firstDoseTomorrow = Calendar.getInstance().apply {
                    val date = try { sdf24.parse(startTime24)!! } catch(e:Exception){Date()}
                    val tempCal = Calendar.getInstance().apply { time = date }
                    set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                return formatTo12Hour(sdf24.format(firstDoseTomorrow.time))
            }
        }

        return formatTo12Hour(sdf24.format(startCal.time))
    }

    fun getLastWakeUpTime(wakeupTimeStr: String): Long {
        val now = Calendar.getInstance()
        val parts = wakeupTimeStr.split(":")
        val wakeHour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val wakeMin = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val wakeToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, wakeHour)
            set(Calendar.MINUTE, wakeMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return if (now.after(wakeToday)) {
            wakeToday.timeInMillis
        } else {
            wakeToday.add(Calendar.DAY_OF_YEAR, -1)
            wakeToday.timeInMillis
        }
    }
}

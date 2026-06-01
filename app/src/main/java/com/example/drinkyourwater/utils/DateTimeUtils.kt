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

    fun calculateNextTime(startTime24: String, intervalMinutes: Int = 0, timesPerDay: Int = 1, lastLogTime: Long? = null): String {
        val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
        
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
}

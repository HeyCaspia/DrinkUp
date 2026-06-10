package com.example.drinkyourwater.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import com.example.drinkyourwater.MainActivity
import com.example.drinkyourwater.R
import com.example.drinkyourwater.data.ReminderDatabase
import com.example.drinkyourwater.utils.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DrinkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = ReminderDatabase.getDatabase(context)
        val dao = database.reminderDao()
        
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val wakeupTime = prefs.getString("wakeup_time", "07:00") ?: "07:00"

        val now = Calendar.getInstance()
        val cycleStartTime = DateTimeUtils.getLastWakeUpTime(wakeupTime)

        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val dateString = dateFormat.format(now.time)

        // Fetch direct snapshots
        val medicines = withContext(Dispatchers.IO) { dao.getAllMedicinesList() }
        val waterReminders = withContext(Dispatchers.IO) { dao.getAllWaterRemindersList() }
        val allHistory = withContext(Dispatchers.IO) { dao.getHistorySinceList(0) } 
        
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        
        // Filter medicines for today
        val medsToday = medicines.filter { med ->
            val enabledDays = med.repeatDays.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
            enabledDays.isEmpty() || enabledDays.contains(currentDay)
        }

        // Only show items that still have pending doses/glasses in this cycle
        val pendingMeds = medsToday.filter { med ->
            val takenInCycle = allHistory.count { it.type == "MEDICINE" && it.medicineId == med.id && it.timestamp >= cycleStartTime }
            takenInCycle < med.timesPerDay
        }
        
        val waterDrankInCycle = allHistory.count { it.type == "WATER" && it.name == "Water" && it.timestamp >= cycleStartTime }
        val waterGoal = waterReminders.sumOf { it.timesPerDay }
        val isWaterPending = waterReminders.isNotEmpty() && waterDrankInCycle < waterGoal

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(12.dp)
                    .cornerRadius(16.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_worm_happy),
                        contentDescription = null,
                        modifier = GlanceModifier.size(32.dp)
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "Drink UP!",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold, 
                                fontSize = 16.sp,
                                color = ColorProvider(Color(0xFF2E7D32))
                            )
                        )
                        Text(
                            text = dateString,
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = ColorProvider(Color.Gray)
                            )
                        )
                    }
                }
                
                Spacer(GlanceModifier.height(12.dp))
                
                if (pendingMeds.isEmpty() && !isWaterPending) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All set for today!",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ColorProvider(Color.Gray))
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        items(pendingMeds) { med ->
                            val lastLog = allHistory.filter { it.type == "MEDICINE" && it.medicineId == med.id }.maxByOrNull { it.timestamp }?.timestamp
                            val nextTime = DateTimeUtils.calculateNextTime(med.time, med.intervalMinutes, med.timesPerDay, lastLog, wakeupTime, allHistory, "MEDICINE", med.name, med.id)
                            
                            WidgetRow(
                                label = med.name,
                                nextTime = nextTime,
                                color = Color(0xFF1976D2)
                            )
                        }
                        
                        if (isWaterPending) {
                            item {
                                val lastWaterLog = allHistory.filter { it.type == "WATER" }.maxByOrNull { it.timestamp }?.timestamp
                                val nextWaterTime = DateTimeUtils.calculateNextTime(waterReminders.first().startTime, waterReminders.first().intervalMinutes, waterReminders.first().timesPerDay, lastWaterLog, wakeupTime, allHistory, "WATER", "Water")

                                WidgetRow(
                                    label = "Water",
                                    nextTime = nextWaterTime,
                                    color = Color(0xFF0288D1)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    @androidx.compose.runtime.Composable
    private fun WidgetRow(label: String, nextTime: String, color: Color) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(Color(0xFFF5F5F5))
                .padding(8.dp)
                .cornerRadius(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(color)
                    .cornerRadius(4.dp)
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = label,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Black))
            )
            Text(
                text = nextTime,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ColorProvider(color))
            )
        }
    }
}

class DrinkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DrinkWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Ensure widget updates when date/time changes significantly
        if (intent.action == Intent.ACTION_DATE_CHANGED || 
            intent.action == Intent.ACTION_TIME_CHANGED || 
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            CoroutineScope(Dispatchers.Main).launch {
                DrinkWidget().updateAll(context)
            }
        }
    }
}

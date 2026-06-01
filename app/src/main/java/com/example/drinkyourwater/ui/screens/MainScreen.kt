package com.example.drinkyourwater.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drinkyourwater.R
import com.example.drinkyourwater.data.Medicine
import com.example.drinkyourwater.data.ReminderHistory
import com.example.drinkyourwater.data.WaterReminder
import com.example.drinkyourwater.ui.viewmodel.ReminderViewModel
import com.example.drinkyourwater.utils.DateTimeUtils
import com.example.drinkyourwater.utils.IconUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ReminderViewModel
) {
    val context = LocalContext.current
    val medicines by viewModel.allMedicines.collectAsState(initial = emptyList())
    val waterReminders by viewModel.allWaterReminders.collectAsState(initial = emptyList())
    val history by viewModel.history.collectAsState(initial = emptyList())
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val pendingLogRequest by viewModel.pendingLogRequest.collectAsState(initial = null)
    
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showManualLogDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingLogRequest) {
        if (pendingLogRequest != null) {
            showManualLogDialog = true
        }
    }
    var shiftScheduleItem by remember { mutableStateOf<Pair<String, String>?>(null) } // Type, Name
    var confirmMedicineByMed by remember { mutableStateOf<Medicine?>(null) }
    var confirmWaterByRem by remember { mutableStateOf<WaterReminder?>(null) }
    var confirmDeleteMedicine by remember { mutableStateOf<Medicine?>(null) }
    var confirmDeleteWater by remember { mutableStateOf<WaterReminder?>(null) }
    var editingMedicine by remember { mutableStateOf<Medicine?>(null) }
    var editingWater by remember { mutableStateOf<WaterReminder?>(null) }
    var isThirsty by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Reminders", "History", "Settings")

    if (showAddReminderDialog || editingMedicine != null || editingWater != null) {
        AddReminderDialog(
            onDismiss = { 
                showAddReminderDialog = false
                editingMedicine = null
                editingWater = null
            },
            onAdd = { type, name, date, time, interval, times, repeatDays, endDate ->
                if (type == "Medicine") {
                    val med = editingMedicine?.copy(
                        name = name, date = date, time = time, intervalMinutes = interval,
                        timesPerDay = times, repeatDays = repeatDays, endDate = endDate
                    ) ?: Medicine(
                        name = name, date = date, time = time, intervalMinutes = interval,
                        timesPerDay = times, repeatDays = repeatDays, endDate = endDate
                    )
                    if (editingMedicine != null) viewModel.updateMedicine(med) else viewModel.addMedicine(name, date, time, interval, times, repeatDays, endDate)
                } else {
                    val rem = editingWater?.copy(
                        date = date, startTime = time, intervalMinutes = interval,
                        timesPerDay = times, repeatDays = repeatDays
                    ) ?: WaterReminder(
                        date = date, startTime = time, intervalMinutes = interval,
                        timesPerDay = times, repeatDays = repeatDays
                    )
                    if (editingWater != null) viewModel.updateWaterReminder(rem) else viewModel.addWaterReminder(date, time, interval, times, repeatDays)
                }
                showAddReminderDialog = false
                editingMedicine = null
                editingWater = null
            },
            initialMedicine = editingMedicine,
            initialWater = editingWater
        )
    }

    if (showManualLogDialog) {
        ManualLogDialog(
            onDismiss = { 
                showManualLogDialog = false
                viewModel.clearLogRequest()
            },
            onAdd = { type, name, date, time ->
                viewModel.addManualHistory(type, name, date, time)
                showManualLogDialog = false
                viewModel.clearLogRequest()
            },
            medicines = medicines,
            initialType = pendingLogRequest?.first ?: "WATER",
            initialName = pendingLogRequest?.second ?: "Water"
        )
    }

    if (shiftScheduleItem != null) {
        AlertDialog(
            onDismissRequest = { shiftScheduleItem = null },
            title = { Text("Shift Schedule?") },
            text = { Text("You logged this ${shiftScheduleItem?.first?.lowercase()} now. Would you like to shift your future reminders for ${shiftScheduleItem?.second} to start from now?") },
            confirmButton = {
                Button(onClick = {
                    shiftScheduleItem?.let { (type, name) ->
                        val now = Calendar.getInstance()
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        if (type == "MEDICINE") {
                            medicines.find { it.name == name }?.let {
                                viewModel.updateMedicineTime(it, sdf.format(now.time))
                            }
                        } else {
                            waterReminders.firstOrNull()?.let {
                                viewModel.updateWaterTime(it, sdf.format(now.time))
                            }
                        }
                    }
                    shiftScheduleItem = null
                }) { Text("Shift Future") }
            },
            dismissButton = {
                TextButton(onClick = { shiftScheduleItem = null }) { Text("Keep Original") }
            }
        )
    }

    if (confirmMedicineByMed != null) {
        AlertDialog(
            onDismissRequest = { confirmMedicineByMed = null },
            title = { Text("Log Medicine?") },
            text = { Text("Confirm that you have taken ${confirmMedicineByMed?.name}?") },
            confirmButton = {
                Button(onClick = {
                    confirmMedicineByMed?.let { med ->
                        viewModel.markMedicineAsTaken(med)
                        if (med.timesPerDay > 1) {
                            shiftScheduleItem = "MEDICINE" to med.name
                        }
                    }
                    confirmMedicineByMed = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmMedicineByMed = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmWaterByRem != null) {
        AlertDialog(
            onDismissRequest = { confirmWaterByRem = null },
            title = { Text("Log Water?") },
            text = { Text("Confirm that you have drank water?") },
            confirmButton = {
                Button(onClick = {
                    confirmWaterByRem?.let { rem ->
                        viewModel.markWaterAsDrank(rem)
                        if (rem.timesPerDay > 1) {
                            shiftScheduleItem = "WATER" to "Water"
                        }
                    }
                    confirmWaterByRem = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWaterByRem = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmDeleteMedicine != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteMedicine = null },
            title = { Text("Delete Medicine?") },
            text = { Text("Are you sure you want to remove ${confirmDeleteMedicine?.name}? This will stop all future reminders.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteMedicine?.let { viewModel.deleteMedicine(it) }
                        confirmDeleteMedicine = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteMedicine = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmDeleteWater != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteWater = null },
            title = { Text("Delete Reminder?") },
            text = { Text("Are you sure you want to remove this water schedule?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteWater?.let { viewModel.deleteWaterReminder(it) }
                        confirmDeleteWater = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteWater = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            "Drink UP!",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        ) 
                    },
                    actions = {
                        IconButton(onClick = {
                            isThirsty = !isThirsty
                            IconUtils.setThirstyIcon(context, isThirsty)
                        }) {
                            Icon(
                                painter = painterResource(id = if (isThirsty) R.drawable.ic_worm_thirsty else R.drawable.ic_worm_happy),
                                contentDescription = "Mood",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showManualLogDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manual Log")
                }
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = { showAddReminderDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Drink/Meds", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (medicines.isEmpty() && waterReminders.isEmpty()) {
                    item {
                        EmptyState()
                    }
                }

                if (medicines.isNotEmpty()) {
                    item { SectionHeader("Medicines", Icons.Default.Medication) }
                    items(medicines) { medicine ->
                        MedicineItem(
                            medicine, 
                            history = history,
                            onDelete = { confirmDeleteMedicine = it },
                            onDone = { med -> confirmMedicineByMed = med },
                            onPause = { viewModel.togglePauseMedicine(it) },
                            onEdit = { editingMedicine = it }
                        )
                    }
                }

                if (waterReminders.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { SectionHeader("Water Tracking", Icons.Default.WaterDrop) }
                    items(waterReminders) { waterReminder ->
                        WaterReminderItem(
                            waterReminder, 
                            history = history,
                            onDelete = { confirmDeleteWater = it },
                            onDone = { rem -> confirmWaterByRem = rem },
                            onPause = { viewModel.togglePauseWater(it) },
                            onEdit = { editingWater = it }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("Sleep Schedule", Icons.Default.NightsStay) }
                item {
                    val bedtime = viewModel.bedtime.collectAsState(initial = "22:00").value
                    val wakeupTime = viewModel.wakeupTime.collectAsState(initial = "07:00").value
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedTab = 2 },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bedtime, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bedtime Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${DateTimeUtils.formatTo12Hour(bedtime)} - ${DateTimeUtils.formatTo12Hour(wakeupTime)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Reminders pause while you sleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "HeyCaspia",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        } else if (selectedTab == 1) {
            HistoryScreen(
                history = history,
                modifier = Modifier.padding(innerPadding)
            )
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "HeyCaspia",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
        } else {
            SettingsScreen(
                notificationsEnabled = notificationsEnabled,
                onToggleNotifications = { viewModel.toggleNotifications(it) },
                bedtime = viewModel.bedtime.collectAsState(initial = "22:00").value,
                wakeupTime = viewModel.wakeupTime.collectAsState(initial = "07:00").value,
                onUpdateBedtime = { viewModel.updateBedtime(it) },
                onUpdateWakeupTime = { viewModel.updateWakeupTime(it) },
                modifier = Modifier.padding(innerPadding)
            )
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "HeyCaspia",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text("No reminders yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.outline)
        Text("Tap the button above to add some!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun MedicineItem(medicine: Medicine, history: List<ReminderHistory>, onDelete: (Medicine) -> Unit, onDone: (Medicine) -> Unit, onPause: (Medicine) -> Unit, onEdit: (Medicine) -> Unit) {
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val enabledDays = medicine.repeatDays.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
    val isEnabledToday = enabledDays.isEmpty() || enabledDays.contains(currentDayOfWeek)
    val dosesTakenToday = history.count { it.type == "MEDICINE" && it.name == medicine.name && it.timestamp >= todayStart }
    val isDoneForToday = dosesTakenToday >= medicine.timesPerDay

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isEnabledToday && !medicine.isPaused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(if (isDoneForToday || !isEnabledToday || medicine.isPaused) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Medication, null, tint = if (isDoneForToday || !isEnabledToday || medicine.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = medicine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isDoneForToday || !isEnabledToday || medicine.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                    if (medicine.isPaused) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("PAUSED", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                val lastLog = history.filter { it.type == "MEDICINE" && it.name == medicine.name }.maxByOrNull { it.timestamp }?.timestamp
                val nextTime = DateTimeUtils.calculateNextTime(medicine.time, medicine.intervalMinutes, medicine.timesPerDay, lastLog)
                Text(text = if (medicine.isPaused) "Reminders paused" else if (!isEnabledToday) "Not scheduled for today" else if (isDoneForToday) "All doses taken today!" else "Next: $nextTime", style = MaterialTheme.typography.bodyMedium, color = if (isDoneForToday || !isEnabledToday || medicine.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (isEnabledToday && !isDoneForToday && !medicine.isPaused) {
                    Text(text = "Initial: ${DateTimeUtils.formatTo12Hour(medicine.time)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val daysLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    daysLabels.forEachIndexed { index, label ->
                        val dayNum = index + 1
                        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = if (enabledDays.contains(dayNum)) FontWeight.Bold else FontWeight.Normal, color = if (enabledDays.contains(dayNum)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 4.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onEdit(medicine) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onPause(medicine) }, modifier = Modifier.size(36.dp)) {
                        Icon(if (medicine.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "Toggle Pause", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onDone(medicine) }, enabled = isEnabledToday && !isDoneForToday && !medicine.isPaused, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = if (isDoneForToday) Icons.Default.CheckCircleOutline else Icons.Default.CheckCircle, contentDescription = "Done", tint = if (isDoneForToday || !isEnabledToday || medicine.isPaused) MaterialTheme.colorScheme.outline else Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onDelete(medicine) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WaterReminderItem(waterReminder: WaterReminder, history: List<ReminderHistory>, onDelete: (WaterReminder) -> Unit, onDone: (WaterReminder) -> Unit, onPause: (WaterReminder) -> Unit, onEdit: (WaterReminder) -> Unit) {
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val enabledDays = waterReminder.repeatDays.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
    val isEnabledToday = enabledDays.isEmpty() || enabledDays.contains(currentDayOfWeek)
    val glassesToday = history.count { it.type == "WATER" && it.timestamp >= todayStart }

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isEnabledToday && !waterReminder.isPaused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(if (!isEnabledToday || waterReminder.isPaused) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WaterDrop, null, tint = if (!isEnabledToday || waterReminder.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Water Reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (!isEnabledToday || waterReminder.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                    if (waterReminder.isPaused) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("PAUSED", modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                val lastLog = history.filter { it.type == "WATER" }.maxByOrNull { it.timestamp }?.timestamp
                val nextTime = DateTimeUtils.calculateNextTime(waterReminder.startTime, waterReminder.intervalMinutes, waterReminder.timesPerDay, lastLog)
                Text(text = if (waterReminder.isPaused) "Reminders paused" else if (!isEnabledToday) "Not scheduled for today" else "Next: $nextTime", style = MaterialTheme.typography.bodyMedium, color = if (!isEnabledToday || waterReminder.isPaused) MaterialTheme.colorScheme.outline else Color(0xFF2196F3), fontWeight = FontWeight.SemiBold)
                if (isEnabledToday && !waterReminder.isPaused) {
                    Text(text = "Start: ${DateTimeUtils.formatTo12Hour(waterReminder.startTime)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val daysLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    daysLabels.forEachIndexed { index, label ->
                        val dayNum = index + 1
                        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = if (enabledDays.contains(dayNum)) FontWeight.Bold else FontWeight.Normal, color = if (enabledDays.contains(dayNum)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                val hours = waterReminder.intervalMinutes / 60
                val mins = waterReminder.intervalMinutes % 60
                val intervalText = when {
                    hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                    hours > 0 -> "${hours}h"
                    else -> "${mins}m"
                }
                Text(text = "Drank: $glassesToday/${waterReminder.timesPerDay} today • Every $intervalText", style = MaterialTheme.typography.labelSmall, color = if (!isEnabledToday || waterReminder.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onEdit(waterReminder) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onPause(waterReminder) }, modifier = Modifier.size(36.dp)) {
                        Icon(if (waterReminder.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "Toggle Pause", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { onDone(waterReminder) }, enabled = isEnabledToday && !waterReminder.isPaused, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.LocalDrink, contentDescription = "Drink", tint = if (!isEnabledToday || waterReminder.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onDelete(waterReminder) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(history: List<ReminderHistory>, modifier: Modifier = Modifier) {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val selectedDateStr = sdfDate.format(selectedDate.time)
    val filteredHistory = history.filter { sdfDate.format(Date(it.timestamp)) == selectedDateStr }

    Column(modifier = modifier.fillMaxSize()) {
        CalendarHeader(selectedDate = selectedDate, onDateSelected = { selectedDate = it })
        Spacer(modifier = Modifier.height(8.dp))
        if (filteredHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("No records for this day", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filteredHistory) { item -> HistoryItem(item) }
            }
        }
    }
}

@Composable
fun CalendarHeader(selectedDate: Calendar, onDateSelected: (Calendar) -> Unit) {
    val dates = remember {
        val list = mutableListOf<Calendar>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -15)
        repeat(31) { list.add(cal.clone() as Calendar); cal.add(Calendar.DAY_OF_YEAR, 1) }
        list
    }
    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate.time), modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dates) { date ->
                    val isSelected = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date.time) == SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(selectedDate.time)
                    Column(
                        modifier = Modifier.width(50.dp).clip(RoundedCornerShape(12.dp)).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { onDateSelected(date) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = SimpleDateFormat("E", Locale.getDefault()).format(date.time).take(1), style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
                        Text(text = SimpleDateFormat("d", Locale.getDefault()).format(date.time), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(item: ReminderHistory) {
    val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (item.type == "MEDICINE") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = if (item.type == "MEDICINE") Icons.Default.Medication else Icons.Default.WaterDrop, contentDescription = null, tint = if (item.type == "MEDICINE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
            Text(text = sdfTime.format(Date(item.timestamp)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit, 
    onAdd: (String, String, String, String, Int, Int, String, String?) -> Unit,
    initialMedicine: Medicine? = null,
    initialWater: WaterReminder? = null
) {
    var type by remember { mutableStateOf(if (initialWater != null) "Water" else "Medicine") }
    var name by remember { mutableStateOf(initialMedicine?.name ?: "") }
    var date by remember { mutableStateOf(initialMedicine?.date ?: initialWater?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var time by remember { mutableStateOf(initialMedicine?.time ?: initialWater?.startTime ?: "08:00") }
    var endDate by remember { mutableStateOf(initialMedicine?.endDate ?: "") }
    
    val initialInterval = initialMedicine?.intervalMinutes ?: initialWater?.intervalMinutes ?: 0
    var intervalDays by remember { mutableStateOf((initialInterval / 1440).toString()) }
    var intervalHours by remember { mutableStateOf(((initialInterval % 1440) / 60).toString()) }
    var intervalMinutes by remember { mutableStateOf((initialInterval % 60).toString()) }
    
    var timesPerDay by remember { mutableStateOf((initialMedicine?.timesPerDay ?: initialWater?.timesPerDay ?: 1).toString()) }
    
    val initialRepeatDays = (initialMedicine?.repeatDays ?: initialWater?.repeatDays ?: "1,2,3,4,5,6,7")
        .split(",")
        .filter { it.isNotEmpty() }
        .map { it.toInt() }
        .toSet()
    var selectedDays by remember { mutableStateOf(initialRepeatDays) }
    
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = time.split(":")[0].toIntOrNull() ?: 8,
        initialMinute = time.split(":")[1].toIntOrNull() ?: 0,
        is24Hour = false
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false }, confirmButton = { TextButton(onClick = { time = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute); showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }, text = { TimePicker(state = timePickerState) }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { 
                    val totalMinutes = (intervalDays.toIntOrNull() ?: 0) * 1440 + (intervalHours.toIntOrNull() ?: 0) * 60 + (intervalMinutes.toIntOrNull() ?: 0)
                    onAdd(type, name.ifBlank { if(type == "Water") "Water" else "" }, date, time, totalMinutes, timesPerDay.toIntOrNull() ?: 1, selectedDays.sorted().joinToString(","), endDate.ifBlank { null }) 
                }, enabled = type == "Water" || name.isNotBlank()
            ) { Text("Save Reminder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, title = { Text("Add Drink/Meds") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = type, onValueChange = {}, readOnly = true, label = { Text("What to drink?") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { listOf("Medicine", "Water").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { type = option; expanded = false }) } }
                    }
                }
                if (type == "Medicine") {
                    item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medicine Name") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Medication, null) }) }
                }
                item { OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Start Date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.CalendarToday, null) }) }
                if (type == "Medicine") item { OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("End Date (Optional)") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.EventAvailable, null) }) }
                item { OutlinedTextField(value = DateTimeUtils.formatTo12Hour(time), onValueChange = { }, label = { Text("Start Time") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { IconButton(onClick = { showTimePicker = true }) { Icon(Icons.Default.Schedule, null) } }, readOnly = true, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { interactionSource -> LaunchedEffect(interactionSource) { interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Release) showTimePicker = true } } }) }
                item { Text("Interval (how long each take)", style = MaterialTheme.typography.labelMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = intervalDays, onValueChange = { intervalDays = it }, label = { Text("Day") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(value = intervalHours, onValueChange = { intervalHours = it }, label = { Text("Hour") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(value = intervalMinutes, onValueChange = { intervalMinutes = it }, label = { Text("Min") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } }
                item { OutlinedTextField(value = timesPerDay, onValueChange = { timesPerDay = it }, label = { Text("How many times per day?") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item { Text("Remind on these days:", style = MaterialTheme.typography.labelMedium); DayOfWeekSelector(selectedDays = selectedDays, onDaysChanged = { selectedDays = it }) }
            }
        }
    )
}

@Composable
fun DayOfWeekSelector(selectedDays: Set<Int>, onDaysChanged: (Set<Int>) -> Unit) {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { index, day ->
            val dayNum = index + 1; val isSelected = selectedDays.contains(dayNum)
            FilterChip(selected = isSelected, onClick = { onDaysChanged(if (isSelected) selectedDays - dayNum else selectedDays + dayNum) }, label = { Text(day, fontSize = 12.sp) }, modifier = Modifier.size(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLogDialog(
    onDismiss: () -> Unit, 
    onAdd: (String, String, String, String) -> Unit, 
    medicines: List<Medicine>,
    initialType: String = "WATER",
    initialName: String = "Water"
) {
    var type by remember { mutableStateOf(initialType) }; var name by remember { mutableStateOf(initialName) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }; var time by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    var expanded by remember { mutableStateOf(false) }; var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), initialMinute = Calendar.getInstance().get(Calendar.MINUTE), is24Hour = false)

    if (showTimePicker) {
        AlertDialog(onDismissRequest = { showTimePicker = false }, confirmButton = { TextButton(onClick = { time = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute); showTimePicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }, text = { TimePicker(state = timePickerState) })
    }

    val medicineOptions = medicines.map { it.name }.distinct() + "Others"
    AlertDialog(
        onDismissRequest = onDismiss, confirmButton = { Button(onClick = { onAdd(type, name, date, time) }, enabled = name.isNotBlank()) { Text("Log") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, title = { Text("Manual History Log") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = type == "WATER", onClick = { type = "WATER"; name = "Water" }); Text("Water"); Spacer(Modifier.width(16.dp)); RadioButton(selected = type == "MEDICINE", onClick = { type = "MEDICINE"; name = medicineOptions.firstOrNull() ?: "" }); Text("Medicine") }
                if (type == "MEDICINE") {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = if (name in medicines.map { it.name }) name else if (name == "") "" else if (name in medicineOptions) name else "Others", onValueChange = {}, readOnly = true, label = { Text("Select Medicine") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { medicineOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { if (option != "Others") name = option else name = ""; expanded = false }) } }
                    }
                    if (name !in medicines.map { it.name } && name != "Water") OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Medicine Name") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = DateTimeUtils.formatTo12Hour(time), onValueChange = { }, label = { Text("Time") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { IconButton(onClick = { showTimePicker = true }) { Icon(Icons.Default.Schedule, null) } }, readOnly = true, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { interactionSource -> LaunchedEffect(interactionSource) { interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.PressInteraction.Release) showTimePicker = true } } })
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit,
    bedtime: String,
    wakeupTime: String,
    onUpdateBedtime: (String) -> Unit,
    onUpdateWakeupTime: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBedtimePicker by remember { mutableStateOf(false) }
    var showWakeupPicker by remember { mutableStateOf(false) }

    val bedtimeState = rememberTimePickerState(
        initialHour = bedtime.split(":")[0].toIntOrNull() ?: 22,
        initialMinute = bedtime.split(":")[1].toIntOrNull() ?: 0,
        is24Hour = false
    )
    val wakeupState = rememberTimePickerState(
        initialHour = wakeupTime.split(":")[0].toIntOrNull() ?: 7,
        initialMinute = wakeupTime.split(":")[1].toIntOrNull() ?: 0,
        is24Hour = false
    )

    if (showBedtimePicker) {
        AlertDialog(
            onDismissRequest = { showBedtimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateBedtime(String.format(Locale.getDefault(), "%02d:%02d", bedtimeState.hour, bedtimeState.minute))
                    showBedtimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showBedtimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = bedtimeState) }
        )
    }

    if (showWakeupPicker) {
        AlertDialog(
            onDismissRequest = { showWakeupPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateWakeupTime(String.format(Locale.getDefault(), "%02d:%02d", wakeupState.hour, wakeupState.minute))
                    showWakeupPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showWakeupPicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = wakeupState) }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("App Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Push Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Receive alerts 10m and 1m before schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onToggleNotifications
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Bedtime Setting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().clickable { showBedtimePicker = true }
                ) {
                    Column {
                        Text("Bedtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Notifications will pause during sleep", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = DateTimeUtils.formatTo12Hour(bedtime),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Wake-up Setting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().clickable { showWakeupPicker = true }
                ) {
                    Column {
                        Text("Wake-up Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Reminders will resume after this", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = DateTimeUtils.formatTo12Hour(wakeupTime),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text(
            "Note: Notifications will include a sound alarm and vibration to ensure you don't miss them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

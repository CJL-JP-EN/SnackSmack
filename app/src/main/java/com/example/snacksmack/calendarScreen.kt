package com.example.snacksmack

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.snacksmack.notifications.NotificationHelper
import com.example.snacksmack.notifications.NotificationScheduler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

// ---------- Data model (requestCode stored so we can cancel/reschedule) ----------
data class Event(
    val title: String,
    val description: String?,
    val startTime: String,   // "hh:mm AM/PM"
    val endTime: String,     // "hh:mm AM/PM"
    val requestCode: Int? = null
)

private const val PREFS_NAME = "calendar_events"
private const val EVENTS_KEY = "events_data"

// JSON save/load
private fun saveEvents(context: Context, events: Map<String, List<Event>>) {
    val json = Gson().toJson(events)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(EVENTS_KEY, json)
        .apply()
}
private fun loadEvents(context: Context): Map<String, List<Event>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(EVENTS_KEY, null) ?: return emptyMap()
    val type = object : TypeToken<Map<String, List<Event>>>() {}.type
    return Gson().fromJson(json, type)
}

object DateTimeManager {
    val dateFmt: SimpleDateFormat by lazy { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
    val timeFmt: SimpleDateFormat by lazy { SimpleDateFormat("hh:mm a", Locale.US) }

    fun parse12h(s: String): Triple<Int, Int, Boolean>? {
        return try {
            if (s.isBlank()) return null
            val parts = s.trim().split(" ")
            if (parts.size != 2) return null
            val (hhmm, ampm) = parts
            val hm = hhmm.split(":")
            if (hm.size != 2) return null
            val h = hm[0].toIntOrNull() ?: return null
            val m = hm[1].toIntOrNull() ?: return null
            Triple(h.coerceIn(1, 12), m.coerceIn(0, 59), ampm.equals("AM", true))
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    snackViewModel: SnackViewModel,
    waterViewModel: WaterViewModel,
    showCreateEventDialog: Boolean = false
) {
    val context = LocalContext.current
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var events by remember { mutableStateOf(loadEvents(context)) }
    var showCreateDialog by remember { mutableStateOf(showCreateEventDialog) }
    var editEvent by remember { mutableStateOf<Event?>(null) }

    val snackData by remember(currentMonth) {
        mutableStateOf(snackViewModel.getMonthPerCategory(currentMonth.atDay(1)))
    }
    val waterData by remember(currentMonth, waterViewModel.goalOz) {
        mutableStateOf(
            waterViewModel.getMonthData(currentMonth.atDay(1)).mapValues {
                it.value to waterViewModel.goalOz
            }
        )
    }

    LaunchedEffect(Unit) {
        NotificationHelper.createChannel(context)
        val appPrefs = context.getSharedPreferences("snacksmack_init", Context.MODE_PRIVATE)
        if (!appPrefs.getBoolean("schedules_created", false)) {
            NotificationScheduler.scheduleDailySnackReminders(context)
            NotificationScheduler.scheduleHydrationEvery2Hours(context)
            appPrefs.edit().putBoolean("schedules_created", true).apply()
        }
    }

    LaunchedEffect(events) { saveEvents(context, events) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CalendarHeader(
            yearMonth = currentMonth,
            onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
            onNextMonth = { currentMonth = currentMonth.plusMonths(1) }
        )
        Spacer(Modifier.height(12.dp))
        CalendarGrid(
            yearMonth = currentMonth,
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it },
            snackData = snackData,
            waterData = waterData
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Selected: ${DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showCreateDialog = true }) { Text("Create Event") }
        Spacer(Modifier.height(16.dp))

        val dayEvents = events[DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))] ?: emptyList()

        if (dayEvents.isEmpty()) {
            Text("No events for ${DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))}.")
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                dayEvents.forEach { ev ->
                    EventCard(
                        event = ev,
                        onDelete = {
                            ev.requestCode?.let { NotificationScheduler.cancel(context, it) }
                            val updated = events.toMutableMap()
                            val list = updated[DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))]?.toMutableList() ?: mutableListOf()
                            list.remove(ev)
                            if (list.isEmpty()) {
                                updated.remove(DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString())))
                            } else {
                                updated[DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))] = list
                            }
                            events = updated
                        },
                        onEditClick = { editEvent = ev }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditEventDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { newEvent ->
                val triggerAt = toEpochMillis(DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString())), newEvent.startTime)
                if (triggerAt != null && ensureExactAlarmsAllowed(context)) {
                    val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
                    val rc = NotificationScheduler.scheduleSnackAtDateTime(
                        context = context,
                        year = cal.get(Calendar.YEAR),
                        month0 = cal.get(Calendar.MONTH),
                        day = cal.get(Calendar.DAY_OF_MONTH),
                        hour24 = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        useHarsh = true,
                        persistent = false
                    )
                    val updated = events.toMutableMap()
                    val list = updated.getOrPut(DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))) { mutableListOf() }.toMutableList()
                    list.add(newEvent.copy(requestCode = rc))
                    updated[DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))] = list
                    events = updated
                }
                showCreateDialog = false
            }
        )
    }

    editEvent?.let { old ->
        CreateOrEditEventDialog(
            initialEvent = old,
            onDismiss = { editEvent = null },
            onConfirm = { upd ->
                val triggerAt = toEpochMillis(DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString())), upd.startTime)
                if (triggerAt != null && ensureExactAlarmsAllowed(context)) {
                    old.requestCode?.let { NotificationScheduler.cancel(context, it) }
                    val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
                    val rc = NotificationScheduler.scheduleSnackAtDateTime(
                        context = context,
                        year = cal.get(Calendar.YEAR),
                        month0 = cal.get(Calendar.MONTH),
                        day = cal.get(Calendar.DAY_OF_MONTH),
                        hour24 = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        useHarsh = true,
                        persistent = false
                    )
                    val updated = events.toMutableMap()
                    val list = updated[DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))]?.toMutableList() ?: mutableListOf()
                    val idx = list.indexOf(old)
                    if (idx != -1) {
                        list[idx] = upd.copy(requestCode = rc)
                        updated[DateTimeManager.dateFmt.format(java.sql.Date.valueOf(selectedDate.toString()))] = list
                        events = updated
                    }
                }
                editEvent = null
            }
        )
    }
}

@Composable
fun CalendarHeader(yearMonth: YearMonth, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onPreviousMonth,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4545A2))
        ) { Text("<") }
        Text(
            text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
        Button(
            onClick = onNextMonth,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4545A2))
        ) { Text(">") }
    }
}

@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    snackData: Map<LocalDate, IntArray>,
    waterData: Map<LocalDate, Pair<Int, Int>>
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfMonth = yearMonth.atDay(1).dayOfWeek

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (dayOfWeek in DayOfWeek.entries) {
                Text(
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(7)) {
            val emptyCells = (firstDayOfMonth.value % 7)
            items(emptyCells) {
                Box(modifier = Modifier.size(50.dp))
            }

            items(daysInMonth) { day ->
                val date = yearMonth.atDay(day + 1)

                val snackCounts = snackData[date] ?: IntArray(4)
                val (waterConsumed, waterGoal) = waterData[date] ?: (0 to 64)
                val waterProgress = if (waterGoal > 0) (waterConsumed.toFloat() / waterGoal).coerceIn(0f, 1f) else 0f

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clickable { onDateSelected(date) },
                    contentAlignment = Alignment.Center
                ) {
                    DayView(
                        day = day + 1,
                        isSelected = date == selectedDate,
                        snackCounts = snackCounts,
                        snackColors = listOf(
                            Color(0xFF7E57C2), // purple
                            Color(0xFF4FC3F7), // light blue
                            Color(0xFFFF7043), // orange red
                            Color(0xFF81C784)  // light green
                        ),
                        waterProgress = waterProgress,
                        waterGoalMet = waterProgress >= 1f
                    )
                }
            }
        }
    }
}

// ---------- Helpers ----------

private fun toEpochMillis(dateStr: String, timeStr: String): Long? {
    return try {
        val dateObj = DateTimeManager.dateFmt.parse(dateStr) ?: return null
        val timeObj = DateTimeManager.timeFmt.parse(timeStr) ?: return null

        val d = Calendar.getInstance().apply { time = dateObj }
        val t = Calendar.getInstance().apply { time = timeObj }

        Calendar.getInstance().apply {
            set(Calendar.YEAR, d.get(Calendar.YEAR))
            set(Calendar.MONTH, d.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, d.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, t.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } catch (_: Exception) {
        null
    }
}

private fun ensureExactAlarmsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val am = context.getSystemService(AlarmManager::class.java)
    if (am.canScheduleExactAlarms()) return true
    val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(i)
    return false
}

@Composable
private fun CreateOrEditEventDialog(
    initialEvent: Event? = null,
    onDismiss: () -> Unit,
    onConfirm: (Event) -> Unit
) {
    var title by remember { mutableStateOf(initialEvent?.title ?: "") }
    var description by remember { mutableStateOf(initialEvent?.description ?: "") }
    var startTime by remember { mutableStateOf(initialEvent?.startTime ?: "") }
    var endTime by remember { mutableStateOf(initialEvent?.endTime ?: "") }

    var titleError by remember { mutableStateOf<String?>(null) }
    var startTimeError by remember { mutableStateOf<String?>(null) }
    var endTimeError by remember { mutableStateOf<String?>(null) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEvent == null) "Create Event" else "Edit Event") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = null },
                    label = { Text("Event Title *") },
                    isError = titleError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                titleError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Button(onClick = { showStartPicker = true }) {
                    Text(if (startTime.isEmpty()) "Set Start Time *" else "Start: $startTime")
                }
                startTimeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(8.dp))

                Button(onClick = { showEndPicker = true }) {
                    Text(if (endTime.isEmpty()) "Set End Time *" else "End: $endTime")
                }
                endTimeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                titleError = if (title.isBlank()) "Title is required" else null
                startTimeError = if (startTime.isEmpty()) "Start time is required" else null
                endTimeError = if (endTime.isEmpty()) "End time is required" else null

                var isTimeLogical = true
                if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                    try {
                        val s = DateTimeManager.timeFmt.parse(startTime)
                        val e = DateTimeManager.timeFmt.parse(endTime)
                        if (s != null && e != null && e.before(s)) {
                            endTimeError = "End time must be after start time"
                            isTimeLogical = false
                        }
                    } catch (_: Exception) {
                        endTimeError = "Invalid time format"
                        isTimeLogical = false
                    }
                }

                if (titleError == null && startTimeError == null && endTimeError == null && isTimeLogical) {
                    onConfirm(Event(title, description.ifBlank { null }, startTime, endTime))
                }
            }) { Text(if (initialEvent == null) "Create" else "Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showStartPicker) {
        val parsed = DateTimeManager.parse12h(startTime) ?: Triple(12, 0, true)
        ScrollTimePickerDialog(
            initialHour12 = parsed.first,
            initialMinute = parsed.second,
            initialIsAm = parsed.third,
            onDismiss = { showStartPicker = false },
            onConfirm = { formatted ->
                startTime = formatted
                startTimeError = null
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        val parsed = DateTimeManager.parse12h(endTime) ?: Triple(12, 0, true)
        ScrollTimePickerDialog(
            initialHour12 = parsed.first,
            initialMinute = parsed.second,
            initialIsAm = parsed.third,
            onDismiss = { showEndPicker = false },
            onConfirm = { formatted ->
                endTime = formatted
                endTimeError = null
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun EventCard(
    event: Event,
    onDelete: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(event.title, fontWeight = FontWeight.Bold)
            Text("Start: ${event.startTime}")
            Text("End: ${event.endTime}")
            event.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onEditClick, modifier = Modifier.weight(1f)) { Text("Edit") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            }
        }
    }
}

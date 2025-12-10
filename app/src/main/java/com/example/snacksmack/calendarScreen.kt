package com.example.snacksmack

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val snackData by remember(currentMonth, snackViewModel.countsToday) {
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
        TextButton(
            onClick = onPreviousMonth,
        ) { Text("<", fontSize = 30.sp) }
        Text(
            text = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
        TextButton(
            onClick = onNextMonth,
        ) { Text(">", fontSize = 30.sp) }
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
    val daysOfWeek = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (dayOfWeek in daysOfWeek) {
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
            val emptyCells = daysOfWeek.indexOf(firstDayOfMonth)
            if (emptyCells > 0) {
                items(emptyCells) {
                    Box(modifier = Modifier.size(50.dp))
                }
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

// ---------- Dialogs & Event Handlers ----------

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
    var timeError by remember { mutableStateOf<String?>(null) }

    var showTimePicker by remember { mutableStateOf(false) }

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
                Spacer(Modifier.height(16.dp))

                // Single button for time selection
                Button(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    val timeText = if (startTime.isEmpty() || endTime.isEmpty()) {
                        "Set Start & End Time *"
                    } else {
                        "$startTime - $endTime"
                    }
                    Text(timeText)
                }
                timeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                titleError = if (title.isBlank()) "Title is required" else null
                timeError = if (startTime.isEmpty() || endTime.isEmpty()) "Start and end times are required" else null

                var isTimeLogical = true
                if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                    try {
                        val s = DateTimeManager.timeFmt.parse(startTime)
                        val e = DateTimeManager.timeFmt.parse(endTime)
                        if (s != null && e != null && e.before(s)) {
                            timeError = "End time must be after start time"
                            isTimeLogical = false
                        }
                    } catch (_: Exception) {
                        timeError = "Invalid time format"
                        isTimeLogical = false
                    }
                }

                if (titleError == null && timeError == null && isTimeLogical) {
                    onConfirm(Event(title, description.ifBlank { null }, startTime, endTime))
                }
            }) { Text(if (initialEvent == null) "Create" else "Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showTimePicker) {
        Dual24hTimePickerDialog(
            initialStartTime = startTime,
            initialEndTime = endTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { newStart, newEnd ->
                startTime = newStart
                endTime = newEnd
                timeError = null
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun Dual24hTimePickerDialog(
    initialStartTime: String,
    initialEndTime: String,
    onDismiss: () -> Unit,
    onConfirm: (startTime: String, endTime: String) -> Unit
) {
    fun parseTo24h(timeStr: String): Pair<Int, Int> {
        if (timeStr.isBlank()) return 12 to 0 // Default to 12:00 PM
        return try {
            val cal = Calendar.getInstance()
            DateTimeManager.timeFmt.parse(timeStr)?.let { cal.time = it }
            cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
        } catch (_: Exception) {
            12 to 0
        }
    }

    val (initialStartHour, initialStartMinute) = parseTo24h(initialStartTime)
    val (initialEndHour, initialEndMinute) = if (initialEndTime.isBlank()) {
        (initialStartHour + 1) % 24 to initialStartMinute
    } else {
        parseTo24h(initialEndTime)
    }

    var startHour by remember { mutableStateOf(initialStartHour) }
    var startMinute by remember { mutableStateOf(initialStartMinute) }
    var endHour by remember { mutableStateOf(initialEndHour) }
    var endMinute by remember { mutableStateOf(initialEndMinute) }

    val hours = (0..23).map { it.toString().padStart(2, '0') }
    val minutes = (0..59).map { it.toString().padStart(2, '0') }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time Range") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Start", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        PickerColumn(
                            title = "",
                            items = hours,
                            selectedIndex = startHour,
                            modifier = Modifier.weight(1f),
                            onSelect = { startHour = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        PickerColumn(
                            title = "",
                            items = minutes,
                            selectedIndex = startMinute,
                            modifier = Modifier.weight(1f),
                            onSelect = { startMinute = it }
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("End", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        PickerColumn(
                            title = "",
                            items = hours,
                            selectedIndex = endHour,
                            modifier = Modifier.weight(1f),
                            onSelect = { endHour = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        PickerColumn(
                            title = "",
                            items = minutes,
                            selectedIndex = endMinute,
                            modifier = Modifier.weight(1f),
                            onSelect = { endMinute = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                fun formatTo12h(hour24: Int, minute: Int): String {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour24)
                        set(Calendar.MINUTE, minute)
                    }
                    return DateTimeManager.timeFmt.format(cal.time)
                }

                val newStartTime = formatTo12h(startHour, startMinute)
                val newEndTime = formatTo12h(endHour, endMinute)
                onConfirm(newStartTime, newEndTime)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
fun EventCard(event: Event, onDelete: () -> Unit, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (event.description?.isNotBlank() == true) {
                    Text(event.description, fontSize = 14.sp, color = Color.Gray)
                }
                Text("Time: ${event.startTime} - ${event.endTime}", fontSize = 14.sp)
            }
            Row {
                TextButton(onClick = onEditClick) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
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

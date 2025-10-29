package com.example.snacksmack

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar

// Data model
data class Event(
    val title: String,
    val description: String?,
    val startTime: String,   // "hh:mm AM/PM"
    val endTime: String      // "hh:mm AM/PM"
)

private const val PREFS_NAME = "calendar_events"
private const val EVENTS_KEY = "events_data"

// JSON save/load
private fun saveEvents(context: Context, events: Map<String, List<Event>>) {
    val json = Gson().toJson(events)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString(EVENTS_KEY, json)
    }
}
private fun loadEvents(context: Context): Map<String, List<Event>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(EVENTS_KEY, null) ?: return emptyMap()
    val type = object : TypeToken<Map<String, List<Event>>>() {}.type
    return Gson().fromJson(json, type)
}

// Formatters
private val dateFmt by lazy { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
private val timeFmt by lazy { SimpleDateFormat("hh:mm a", Locale.US) }

// Main Calendar Screen
@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = remember { dateFmt.format(Calendar.getInstance().time) }

    var selectedDate by remember { mutableStateOf(today) }
    var events by remember { mutableStateOf(loadEvents(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<Event?>(null) }

    // Save events whenever they change
    LaunchedEffect(events) { saveEvents(context, events) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            CalendarViewSection(onDateSelected = { selectedDate = it })
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Selected: $selectedDate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showCreateDialog = true }) { Text("Create Event") }
            Spacer(Modifier.height(16.dp))
        }

        val currentEvents = events[selectedDate] ?: emptyList()

        if (currentEvents.isEmpty()) {
            item { Text("No events for $selectedDate.") }
        } else {
            items(currentEvents) { event ->
                EventCard(
                    event = event,
                    onDelete = {
                        val updated = events.toMutableMap()
                        val dayEvents = updated[selectedDate]?.toMutableList()
                        if (dayEvents != null) {
                            dayEvents.remove(event)
                            if (dayEvents.isEmpty()) updated.remove(selectedDate)
                            else updated[selectedDate] = dayEvents
                            events = updated
                        }
                    },
                    onEditClick = { editEvent = event }
                )
            }
        }
    }

    // Create
    if (showCreateDialog) {
        CreateOrEditEventDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { newEvent ->
                val updated = events.toMutableMap()
                val list = updated.getOrPut(selectedDate) { mutableListOf() }.toMutableList()
                list.add(newEvent)
                updated[selectedDate] = list
                events = updated
                showCreateDialog = false
            }
        )
    }

    // Edit
    editEvent?.let { old ->
        CreateOrEditEventDialog(
            initialEvent = old,
            onDismiss = { editEvent = null },
            onConfirm = { upd ->
                val updated = events.toMutableMap()
                val list = updated[selectedDate]?.toMutableList()
                if (list != null) {
                    val idx = list.indexOf(old)
                    if (idx != -1) {
                        list[idx] = upd
                        updated[selectedDate] = list
                        events = updated
                    }
                }
                editEvent = null
            }
        )
    }
}

// Calendar view
@Composable
private fun CalendarViewSection(onDateSelected: (String) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            android.widget.CalendarView(context).apply {
                setOnDateChangeListener { _, year, month, day ->
                    val cal = Calendar.getInstance().apply { set(year, month, day) }
                    onDateSelected(dateFmt.format(cal.time))
                }
            }
        }
    )
}

// Dialog for creating or editing an event — uses ScrollTimePickerDialog (no wheel)
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
                val isTitleValid = title.isNotBlank().also { if (!it) titleError = "Title is required" }
                val isStartTimeValid = startTime.isNotEmpty().also { if (!it) startTimeError = "Start time is required" }
                val isEndTimeValid = endTime.isNotEmpty().also { if (!it) endTimeError = "End time is required" }

                var logical = true
                if (isStartTimeValid && isEndTimeValid) {
                    try {
                        val s = timeFmt.parse(startTime)
                        val e = timeFmt.parse(endTime)
                        if (s != null && e != null && e.before(s)) {
                            endTimeError = "End time must be after start time"
                            logical = false
                        }
                    } catch (_: Exception) {
                        endTimeError = "Invalid time format"; logical = false
                    }
                }

                if (isTitleValid && isStartTimeValid && isEndTimeValid && logical) {
                    onConfirm(Event(title, description.ifBlank { null }, startTime, endTime))
                }
            }) { Text(if (initialEvent == null) "Create" else "Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )

    // Scroll pickers (replaces TimePickerDialog)
    if (showStartPicker) {
        val (h, m, am) = parse12h(startTime) ?: Triple(12, 0, true)
        ScrollTimePickerDialog(
            initialHour12 = h,
            initialMinute = m,
            initialIsAm = am,
            onDismiss = { showStartPicker = false },
            onConfirm = { formatted ->
                startTime = formatted
                startTimeError = null
                endTimeError = null
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        val (h, m, am) = parse12h(endTime) ?: Triple(12, 0, true)
        ScrollTimePickerDialog(
            initialHour12 = h,
            initialMinute = m,
            initialIsAm = am,
            onDismiss = { showEndPicker = false },
            onConfirm = { formatted ->
                endTime = formatted
                endTimeError = null
                startTimeError = null
                showEndPicker = false
            }
        )
    }
}

// Single event card
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
            event.description?.let { Text(it) }

            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = onEditClick, modifier = Modifier.weight(1f)) { Text("Edit") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
        }
    }
}

/** Parse "hh:mm AM/PM" into (hour12, minute, isAM) */
private fun parse12h(s: String): Triple<Int, Int, Boolean>? = try {
    if (s.isBlank()) null else {
        val parts = s.trim().split(" ")
        if (parts.size != 2) null else {
            val (hhmm, ampm) = parts
            val hm = hhmm.split(":")
            if (hm.size != 2) null else {
                val h = hm[0].toIntOrNull()
                val m = hm[1].toIntOrNull()
                if (h == null || m == null) null
                else Triple(h.coerceIn(1, 12), m.coerceIn(0, 59), ampm.equals("AM", true))
            }
        }
    }
} catch (_: Exception) { null }

/** Exact-alarm permission helper (unchanged; used if you later wire scheduling here) */
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

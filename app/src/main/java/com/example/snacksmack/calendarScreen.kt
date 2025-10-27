package com.example.snacksmack

import android.app.TimePickerDialog
import android.content.Context
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
import java.util.*

// Data model
data class Event(
    val title: String,
    val description: String?,
    val startTime: String,
    val endTime: String
)

// Shared Preferences setup
private const val PREFS_NAME = "calendar_events"
private const val EVENTS_KEY = "events_data"

// Save all events to SharedPreferences as JSON
private fun saveEvents(context: Context, events: Map<String, List<Event>>) {
    val json = Gson().toJson(events)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString(EVENTS_KEY, json)
    }
}

// Load events back from SharedPreferences
private fun loadEvents(context: Context): Map<String, List<Event>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(EVENTS_KEY, null) ?: return emptyMap()
    val type = object : TypeToken<Map<String, List<Event>>>() {}.type
    return Gson().fromJson(json, type)
}

// Main Calendar Screen
@Composable
fun calendarScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val today = remember {
        val cal = Calendar.getInstance()
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(cal.time)
    }

    var selectedDate by remember { mutableStateOf(today) }
    var events by remember { mutableStateOf(loadEvents(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<Event?>(null) }

    // Save events whenever they change
    LaunchedEffect(events) {
        saveEvents(context, events)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CalendarViewSection(onDateSelected = { selectedDate = it })

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Selected: $selectedDate",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))
        Button(onClick = { showCreateDialog = true }) {
            Text(
                text = "Create Event"
            )
        }

        Spacer(Modifier.height(16.dp))

        EventListSection(
            selectedDate = selectedDate,
            events = events[selectedDate] ?: emptyList(),
            onDelete = { eventToDelete ->
                val updatedEvents = events.toMutableMap()
                val dayEvents = updatedEvents[selectedDate]?.toMutableList()
                if (dayEvents != null) {
                    dayEvents.remove(eventToDelete)
                    if (dayEvents.isEmpty()) {
                        updatedEvents.remove(selectedDate)
                    } else {
                        updatedEvents[selectedDate] = dayEvents
                    }
                    events = updatedEvents
                }
            },
            onEditClick = { eventToEdit -> editEvent = eventToEdit }
        )
    }

    if (showCreateDialog) {
        CreateOrEditEventDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { newEvent ->
                val updatedEvents = events.toMutableMap()
                val dayEvents = updatedEvents.getOrPut(selectedDate) { mutableListOf() }.toMutableList()
                dayEvents.add(newEvent)
                updatedEvents[selectedDate] = dayEvents
                events = updatedEvents
                showCreateDialog = false
            }
        )
    }

    editEvent?.let { eventToEdit ->
        CreateOrEditEventDialog(
            initialEvent = eventToEdit,
            onDismiss = { editEvent = null },
            onConfirm = { updatedEvent ->
                val updatedEvents = events.toMutableMap()
                val dayEvents = updatedEvents[selectedDate]?.toMutableList()
                if (dayEvents != null) {
                    val index = dayEvents.indexOf(eventToEdit)
                    if (index != -1) {
                        dayEvents[index] = updatedEvent
                        updatedEvents[selectedDate] = dayEvents
                        events = updatedEvents
                    }
                }
                editEvent = null
            }
        )
    }
}

// Calendar view section
@Composable
private fun CalendarViewSection(onDateSelected: (String) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            android.widget.CalendarView(context).apply {
                setOnDateChangeListener { _, year, month, day ->
                    val cal = Calendar.getInstance()
                    cal.set(year, month, day)
                    val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                    onDateSelected(fmt.format(cal.time))
                }
            }
        }
    )
}

// Dialog for creating or editing an event
@Composable
private fun CreateOrEditEventDialog(
    initialEvent: Event? = null,
    onDismiss: () -> Unit,
    onConfirm: (Event) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialEvent?.title ?: "") }
    var description by remember { mutableStateOf(initialEvent?.description ?: "") }
    var startTime by remember { mutableStateOf(initialEvent?.startTime ?: "") }
    var endTime by remember { mutableStateOf(initialEvent?.endTime ?: "") }

    var titleError by remember { mutableStateOf<String?>(null) }
    var startTimeError by remember { mutableStateOf<String?>(null) }
    var endTimeError by remember { mutableStateOf<String?>(null) }

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

                Button(onClick = {
                    pickTime(context) { picked ->
                        startTime = picked
                        startTimeError = null
                        // Clear end time error as well, so validation is re-triggered
                        endTimeError = null
                    }
                }) {
                    Text(if (startTime.isEmpty()) "Set Start Time *" else "Start: $startTime")
                }
                startTimeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(8.dp))

                Button(onClick = {
                    pickTime(context) { picked ->
                        endTime = picked
                        endTimeError = null
                    }
                }) {
                    Text(if (endTime.isEmpty()) "Set End Time *" else "End: $endTime")
                }
                endTimeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val isTitleValid = if (title.isBlank()) {
                    titleError = "Title is required"
                    false
                } else true

                val isStartTimeValid = if (startTime.isEmpty()) {
                    startTimeError = "Start time is required"
                    false
                } else true

                val isEndTimeValid = if (endTime.isEmpty()) {
                    endTimeError = "End time is required"
                    false
                } else true

                var areTimesLogicallyValid = true
                if (isStartTimeValid && isEndTimeValid) {
                    val (startHour, startMinute) = startTime.split(":").map { it.toInt() }
                    val (endHour, endMinute) = endTime.split(":").map { it.toInt() }

                    if (endHour < startHour || (endHour == startHour && endMinute < startMinute)) {
                        endTimeError = "End time cannot be before start time"
                        areTimesLogicallyValid = false
                    }
                }

                if (isTitleValid && isStartTimeValid && isEndTimeValid && areTimesLogicallyValid) {
                    onConfirm(Event(title, description.ifBlank { null }, startTime, endTime))
                }
            }) {
                Text(if (initialEvent == null) "Create" else "Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Event list section
@Composable
private fun EventListSection(
    selectedDate: String,
    events: List<Event>,
    onDelete: (Event) -> Unit,
    onEditClick: (Event) -> Unit
) {
    if (events.isEmpty()) {
        Text("No events for $selectedDate.")
        return
    }

    LazyColumn {
        items(events) { event ->
            EventCard(
                event = event,
                onDelete = { onDelete(event) },
                onEditClick = { onEditClick(event) }
            )
        }
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

// Helper for picking times
private fun pickTime(context: Context, onTimeSelected: (String) -> Unit) {
    val cal = Calendar.getInstance()
    TimePickerDialog(
        context,
        { _, hour, minute ->
            val time = String.format(Locale.US, "%02d:%02d", hour, minute)
            onTimeSelected(time)
        },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        true
    ).show()
}

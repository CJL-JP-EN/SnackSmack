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
fun CalendarScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val today = remember {
        val cal = Calendar.getInstance()
        // Use a specific locale for consistent date formatting
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(cal.time)
    }

    var selectedDate by remember { mutableStateOf(today) }
    var events by remember { mutableStateOf(loadEvents(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editEvent by remember { mutableStateOf<Event?>(null) }

    // Save events whenever they change
    LaunchedEffect(events) {
        saveEvents(context, events)
    }

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
            Button(onClick = { showCreateDialog = true }) {
                Text(
                    text = "Create Event"
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        val currentEvents = events[selectedDate] ?: emptyList()

        if (currentEvents.isEmpty()) {
            item {
                Text("No events for $selectedDate.")
            }
        } else {
            items(currentEvents) { event ->
                EventCard(
                    event = event,
                    onDelete = {
                        val updatedEvents = events.toMutableMap()
                        val dayEvents = updatedEvents[selectedDate]?.toMutableList()
                        if (dayEvents != null) {
                            dayEvents.remove(event)
                            if (dayEvents.isEmpty()) {
                                updatedEvents.remove(selectedDate)
                            } else {
                                updatedEvents[selectedDate] = dayEvents
                            }
                            events = updatedEvents
                        }
                    },
                    onEditClick = { editEvent = event }
                )
            }
        }
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
                    val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
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
                val isTitleValid = title.isNotBlank().also { if (!it) titleError = "Title is required" }
                val isStartTimeValid = startTime.isNotEmpty().also { if (!it) startTimeError = "Start time is required" }
                val isEndTimeValid = endTime.isNotEmpty().also { if (!it) endTimeError = "End time is required" }

                var areTimesLogicallyValid = true
                if (isStartTimeValid && isEndTimeValid) {
                    try {
                        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
                        val startTimeDate = timeFormat.parse(startTime)
                        val endTimeDate = timeFormat.parse(endTime)
                        if (startTimeDate != null && endTimeDate != null && endTimeDate.before(startTimeDate)) {
                            endTimeError = "End time must be after start time"
                            areTimesLogicallyValid = false
                        }
                    } catch (e: Exception) {
                        endTimeError = "Invalid time format"
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
        // Use a more modern theme that still provides a spinner-style picker
        android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar,
        { _, hourOfDay, minute ->
            val selectedTime = Calendar.getInstance()
            selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
            selectedTime.set(Calendar.MINUTE, minute)
            val timeFormatter = SimpleDateFormat("hh:mm a", Locale.US)
            onTimeSelected(timeFormatter.format(selectedTime.time))
        },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        false // Use 12-hour format with AM/PM selector
    ).show()
}

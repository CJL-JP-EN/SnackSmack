package com.example.snacksmack

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import androidx.compose.ui.viewinterop.AndroidView
import java.util.*

// Calendar Screen

@Composable
fun calendarScreen(modifier: Modifier = Modifier) {
    var selectedDate by remember { mutableStateOf("None") }

    Column(
        modifier = modifier
            .padding(16.dp)
            // Removed .fillMaxSize() to make the Column wrap its content
            .fillMaxWidth(), // Keep Column wide to center its children
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth(),
            // Removed .weight(1f) so CalendarView doesn't expand excessively
            factory = { context ->
                android.widget.CalendarView(context).apply {
                    setOnDateChangeListener { _, year, month, day ->
                        val cal = Calendar.getInstance()
                        cal.set(year, month, day)
                        val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
                        selectedDate = fmt.format(cal.time)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Selected: $selectedDate",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

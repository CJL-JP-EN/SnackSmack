package com.example.snacksmack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ScrollTimePickerDialog(
    initialHour12: Int = 12,          // 1..12
    initialMinute: Int = 0,           // 0..59
    initialIsAm: Boolean = true,      // AM = true
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit        // returns "hh:mm AM/PM"
) {
    var hour by remember { mutableStateOf(initialHour12.coerceIn(1, 12)) }
    var minute by remember { mutableStateOf(initialMinute.coerceIn(0, 59)) }
    var isAm by remember { mutableStateOf(initialIsAm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time", fontWeight = FontWeight.Bold) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PickerColumn("Hour", (1..12).map { it.toString().padStart(2, '0') }, hour - 1, Modifier.weight(1f)) { hour = it + 1 }
                PickerColumn("Min", (0..59).map { it.toString().padStart(2, '0') }, minute, Modifier.weight(1f)) { minute = it }
                PickerColumn("AM/PM", listOf("AM", "PM"), if (isAm) 0 else 1, Modifier.weight(1f)) { isAm = (it == 0) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hh = hour.toString().padStart(2, '0')
                val mm = minute.toString().padStart(2, '0')
                val ampm = if (isAm) "AM" else "PM"
                onConfirm("$hh:$mm $ampm".uppercase(Locale.US))
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun PickerColumn(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier, // Accept a modifier parameter
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier.height(180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(items) { index, label ->
                val selected = index == selectedIndex
                TextButton(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Black,
                        style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

package com.example.snacksmack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// Home Screen

@Composable

fun HomeScreen() {
    val context = LocalContext.current
    var useHarsh by remember { mutableStateOf(true) } // toggle harsh/supportive if you want

    // Create high-priority channel once
    LaunchedEffect(Unit) { NotificationHelper.createHighPriorityChannel(context) }

    // Permission launcher for Android 13+
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                NotificationHelper.showRandomSnackAlert(context, useHarsh)
            }
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Home Page", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    NotificationHelper.showRandomSnackAlert(context, useHarsh)
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                NotificationHelper.showRandomSnackAlert(context, useHarsh)
            }
        }) {
            Text("Random Snack Alert (banner)")
        }

        Spacer(Modifier.height(12.dp))

        // Optional toggle to switch tone without code changes
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (useHarsh) "Mode: Harsh" else "Mode: Supportive")
            Spacer(Modifier.width(12.dp))
            Button(onClick = { useHarsh = !useHarsh }) {
                Text("Toggle Tone")
            }
        }
    }
}

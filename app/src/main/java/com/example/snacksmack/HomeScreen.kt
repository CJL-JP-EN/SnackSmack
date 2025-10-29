package com.example.snacksmack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.snacksmack.notifications.NotificationHelper   // <-- NEW helper import
import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var useHarsh by remember { mutableStateOf(true) }

    // Create notification channel once (new API)
    LaunchedEffect(Unit) { NotificationHelper.createChannel(context) }

    // Current user (same as your code)
    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid

    // Android 13+ permission request
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // New API call (persistent=false gives a normal banner)
                NotificationHelper.showRandomSnackAlert(context, useHarsh)
            }
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(5.dp))

        Image(
            painter = painterResource(id = R.drawable.snacksmack_logo_icon),
            contentDescription = "App Logo",
            modifier = Modifier.height(155.dp)
        )

        Text(
            text = "Welcome Back",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 16.dp)
        )
        Spacer(Modifier.height(10.dp))

        if (uid != null) {
            Text(
                text = uid,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(start = 16.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // TEST button (remove later if you want the app fully auto-scheduled)
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (useHarsh) "Mode: Harsh" else "Mode: Supportive")
            Spacer(Modifier.width(12.dp))
            Button(onClick = { useHarsh = !useHarsh }) {
                Text("Toggle Tone")
            }
        }
    }
}

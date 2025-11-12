package com.example.snacksmack

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snacksmack.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun HomeScreen(
    onOpenCalendar: () -> Unit,
    waterViewModel: WaterViewModel
) {
    val context = LocalContext.current
    var useHarsh by remember { mutableStateOf(true) }
    var bmiValue by remember { mutableStateOf<Float?>(null) }
    var username by remember { mutableStateOf("") }

    // Create notification channel
    LaunchedEffect(Unit) { NotificationHelper.createChannel(context) }

    // Current user
    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid

    // Fetch user data
    if (uid != null) {
        LaunchedEffect(uid) {
            val db = FirebaseFirestore.getInstance()
            val userDocRef = db.collection("users").document(uid)

            // Account info (username)
            userDocRef.collection("userAccountInfo").document("account").get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        username = document.getString("username") ?: ""
                    }
                }

            // Personal info (BMI)
            userDocRef.collection("userPersonalInfo").document("personal").get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        bmiValue = document.getDouble("bmi")?.toFloat()
                    }
                }
        }
    }

    // Android 13+ POST_NOTIFICATIONS (kept since you had it)
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) NotificationHelper.showRandomSnackAlert(context, useHarsh)
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
            modifier = Modifier.align(Alignment.Start).padding(start = 16.dp)
        )
        Spacer(Modifier.height(10.dp))

        if (username.isNotBlank()) {
            Text(
                text = username,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(Modifier.height(10.dp))
        bmiLine(bmiValue = bmiValue)

        Spacer(Modifier.height(24.dp))

        // Stat cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.6f))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WaterCupWidget(vm = waterViewModel)
                }
            }
            CalendarCard(modifier = Modifier.weight(1f).aspectRatio(1f), onClick = onOpenCalendar)
        }
    }
}

package com.example.snacksmack

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
import com.example.snacksmack.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun HomeScreen(
    onOpenSnackWheel: () -> Unit
) {
    val context = LocalContext.current
    var bmiValue by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) { com.example.snacksmack.notifications.NotificationHelper.createChannel(context) }

    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    if (uid != null) {
        LaunchedEffect(uid) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener { doc -> bmiValue = doc.getDouble("bmi")?.toFloat() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
        uid?.let {
            Text(
                text = it,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Spacer(Modifier.height(16.dp))
        bmiLine(bmiValue = bmiValue)

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenSnackWheel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Snack Wheel 🍩")
        }
    }
}


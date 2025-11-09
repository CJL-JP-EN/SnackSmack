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
fun HomeScreen(){
    val context = LocalContext.current
    var useHarsh by remember { mutableStateOf(true) }
    var bmiValue by remember { mutableStateOf<Float?>(null) }
    var username by remember { mutableStateOf("") } // State for username

    // Create notification channel one (new API)
    LaunchedEffect(Unit) { NotificationHelper.createChannel(context) }

    // Current user
    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid

    // Fetch user data from Firebase
    if (uid != null) {
        LaunchedEffect(uid) {
            val db = FirebaseFirestore.getInstance()
            val userDocRef = db.collection("users").document(uid)

            // Fetch Account Info (username)
            userDocRef.collection("userAccountInfo").document("account").get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        username = document.getString("username") ?: ""
                    }
                }

            // Fetch Personal Info (BMI)
            userDocRef.collection("userPersonalInfo").document("personal").get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        bmiValue = document.getDouble("bmi")?.toFloat()
                    }
                }
        }
    }


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

        if (username.isNotBlank()) {
            Text(
                text = username, // Display username instead of UID
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(start = 16.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        bmiLine(bmiValue = bmiValue)
    }
}

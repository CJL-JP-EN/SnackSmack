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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snacksmack.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Composable
fun HomeScreen(
    onOpenCalendar: () -> Unit,
    waterViewModel: WaterViewModel
) {
    val context = LocalContext.current
    var bmiValue by remember { mutableStateOf<Float?>(null) }
    var username by remember { mutableStateOf("") }
    var showWeighInDialog by remember { mutableStateOf(false) }
    var showBmiInfoDialog by remember { mutableStateOf(false) }
    // Use a nullable Boolean: null = loading, true = weighed in, false = needs to weigh in
    var hasWeighedInThisWeek by remember { mutableStateOf<Boolean?>(null) }

    // Create notification channel
    LaunchedEffect(Unit) { NotificationHelper.createChannel(context) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val uid = currentUser?.uid

    if (uid != null) {
        // This effect will re-run when the date changes, ensuring the button resets on Monday.
        LaunchedEffect(uid, LocalDate.now()) {
            val db = FirebaseFirestore.getInstance()
            try {
                // Fetch all user data in parallel for efficiency
                val personalDoc = db.collection("users").document(uid)
                    .collection("userPersonalInfo").document("personal").get()
                val accountDoc = db.collection("users").document(uid)
                    .collection("userAccountInfo").document("account").get()

                val personalData = personalDoc.await()
                val accountData = accountDoc.await()

                if (personalData != null && personalData.exists()) {
                    bmiValue = personalData.getDouble("bmi")?.toFloat()
                    val lastWeighIn = personalData.getLong("lastWeighIn") ?: 0

                    hasWeighedInThisWeek = if (lastWeighIn > 0) {
                        val lastWeighInDate = Instant.ofEpochMilli(lastWeighIn)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val today = LocalDate.now(ZoneId.systemDefault())
                        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        !lastWeighInDate.isBefore(monday)
                    } else {
                        false
                    }
                } else {
                    hasWeighedInThisWeek = false
                }

                if (accountData != null && accountData.exists()) {
                    username = accountData.getString("username") ?: ""
                }

            } catch (e: Exception) {
                println("Error fetching user data: ${e.message}")
                hasWeighedInThisWeek = false // Default to actionable state on error
            }
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
        bmiLine(
            bmiValue = bmiValue,
            onInfoClick = { showBmiInfoDialog = true }
        )

        Spacer(Modifier.height(16.dp))

        val isLoading = hasWeighedInThisWeek == null
        val isComplete = hasWeighedInThisWeek == true

        val weighInButtonColors = when {
            isLoading || isComplete -> ButtonDefaults.buttonColors(
                containerColor = Color.LightGray.copy(alpha = 0.4f),
                contentColor = Color.DarkGray,
                disabledContainerColor = Color.LightGray.copy(alpha = 0.4f),
                disabledContentColor = Color.DarkGray
            )
            else -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }

        Button(
            onClick = { showWeighInDialog = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = weighInButtonColors,
            enabled = !isLoading && !isComplete
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (isComplete) "Weigh-In Complete!" else "Weekly Weigh-In",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f).aspectRatio(1f),
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
    if (showBmiInfoDialog) {
        BmiInfoOverlay(
            bmiValue = bmiValue,
            onDismiss = { showBmiInfoDialog = false }
        )
    }

    if (showWeighInDialog) {
        WeeklyWeighInSheet(
            onDismiss = { showWeighInDialog = false },
            onWeighInSuccess = {
                showWeighInDialog = false
                hasWeighedInThisWeek = true
            }
        )
    }
}
@Composable
private fun BmiInfoOverlay(
    bmiValue: Float?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFFFDF5FF), // soft pastel from snack dialog
        title = {
            Text(
                text = "BMI Info",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            val bmiText = if (bmiValue != null) {
                "BMI also known as Body Mass Index identifies whether your weight is in a healthy range for your height. It’s not perfect as it may not take into account muscle mass but is a good starting point in understanding you body just a bit more.\n" + "\n" +
                        "Under 18.5: Underweight\n" +
                        "18.5 - 24.9: Healthy\n" +
                        "25.0 - 29.9: Overweight\n" +
                        "30.0 and above: Obese"
            } else {
                "BMI not calculated yet. Go to your profile to calculate it."
            }
            Text(
                text = bmiText,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

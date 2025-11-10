package com.example.snacksmack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun UserInfoScreen(onSaveSuccess: () -> Unit) {
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }
    var weightLbs by remember { mutableStateOf("") }
    var bmi by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val lbsToKg = 0.453592
    val ftToMeters = 0.3048
    val inToMeters = 0.0254

    // Automatically calculate BMI as the user types
    LaunchedEffect(feet, inches, weightLbs) {
        val feetVal = feet.toDoubleOrNull() ?: 0.0
        val inchesVal = inches.toDoubleOrNull() ?: 0.0
        val weightValLbs = weightLbs.toDoubleOrNull() ?: 0.0

        if (weightValLbs > 0 && (feetVal > 0 || inchesVal > 0)) {
            val heightInMeters = (feetVal * ftToMeters) + (inchesVal * inToMeters)
            val weightInKg = weightValLbs * lbsToKg
            if (heightInMeters > 0) {
                bmi = (weightInKg / (heightInMeters * heightInMeters)).let { Math.round(it * 10.0) / 10.0 }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(32.dp)
                .verticalScroll(rememberScrollState()), // Make it scrollable
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Enter Your Information", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            // Height Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = feet,
                    onValueChange = { feet = it },
                    label = { Text("Height (ft)") },
                    leadingIcon = { Icon(Icons.Default.Straighten, "Height") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = inches,
                    onValueChange = { inches = it },
                    label = { Text("in") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Weight Input
            OutlinedTextField(
                value = weightLbs,
                onValueChange = { weightLbs = it },
                label = { Text("Weight (lbs)") },
                leadingIcon = { Icon(Icons.Default.MonitorWeight, "Weight") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text("Harshness Level")
            Slider(
                value = 0.5f, // A fixed value
                onValueChange = { coroutineScope.launch { snackbarHostState.showSnackbar("Nope, you can't change this") } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val feetVal = feet.toDoubleOrNull()
                    val inchesVal = inches.toDoubleOrNull()
                    val weightValLbs = weightLbs.toDoubleOrNull()

                    if (feetVal == null || inchesVal == null || weightValLbs == null) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter valid numbers for all fields.") }
                        return@Button
                    }

                    isLoading = true
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val db = FirebaseFirestore.getInstance()
                        val personalInfoRef = db.collection("users").document(user.uid)
                            .collection("userPersonalInfo").document("personal")

                        // Format height as a string like "6'2\""
                        val heightString = "${feetVal.toInt()}'${inchesVal.toInt()}\""

                        // Save the formatted height string and other data
                        val updates = mapOf(
                            "height" to heightString,
                            "weight_lbs" to weightValLbs,
                            "bmi" to bmi
                        )

                        personalInfoRef.update(updates)
                            .addOnSuccessListener {
                                isLoading = false
                                onSaveSuccess() // Navigate on success
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                coroutineScope.launch { snackbarHostState.showSnackbar("Error saving data: ${e.message}") }
                            }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Confirm and Save")
                }
            }
        }
    }
}

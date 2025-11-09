package com.example.snacksmack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
fun UserInfoScreen() {
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var bmi by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Automatically calculate BMI
    LaunchedEffect(height, weight) {
        val h = height.toDoubleOrNull() ?: 0.0
        val w = weight.toDoubleOrNull() ?: 0.0
        if (h > 0 && w > 0) {
            // Standard BMI formula: BMI = kg / m^2. Assuming weight is in kg and height is in meters.
            bmi = (w / (h * h)).let { Math.round(it * 10.0) / 10.0 } 
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Enter Your Information", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                label = { Text("Height (in meters)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Weight (in kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Hardness Level")
            Slider(
                value = 0.5f, // A fixed value
                onValueChange = { 
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Nope, you can't change this")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val h = height.toDoubleOrNull()
                    val w = weight.toDoubleOrNull()

                    if (h == null || w == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Please enter valid height and weight.")
                        }
                        return@Button
                    }

                    isLoading = true
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val db = FirebaseFirestore.getInstance()
                        val personalInfoRef = db.collection("users").document(user.uid)
                            .collection("userPersonalInfo").document("personal")

                        val updates = mapOf(
                            "height" to h,
                            "weight" to w,
                            "bmi" to bmi
                        )

                        personalInfoRef.update(updates)
                            .addOnSuccessListener {
                                isLoading = false
                                // You can add navigation to the home screen here if desired
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Error saving data: ${e.message}")
                                }
                            }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Save")
                }
            }
        }
    }
}

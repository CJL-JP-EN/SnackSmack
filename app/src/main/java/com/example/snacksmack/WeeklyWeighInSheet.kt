package com.example.snacksmack

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.round

@Composable
fun WeeklyWeighInSheet(
    onDismiss: () -> Unit,
    onWeighInSuccess: () -> Unit
) {
    var weightLbs by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Update Your Weight") },
        text = {
            Column {
                OutlinedTextField(
                    value = weightLbs,
                    onValueChange = { weightLbs = it },
                    label = { Text("Weight (lbs)") },
                    leadingIcon = { Icon(Icons.Default.MonitorWeight, "Weight") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val weightVal = weightLbs.toDoubleOrNull()
                    if (weightVal == null) {
                        errorMessage = "Please enter a valid weight."
                        return@Button
                    }

                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid == null) {
                        errorMessage = "You must be logged in to save your weight."
                        return@Button
                    }

                    errorMessage = null // Clear previous errors
                    isLoading = true

                    coroutineScope.launch {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            val personalInfoRef = db.collection("users").document(uid)
                                .collection("userPersonalInfo").document("personal")

                            val personalInfoDoc = personalInfoRef.get().await()
                            val heightInInches = personalInfoDoc.getDouble("height")

                            if (heightInInches != null) {
                                val heightInMeters = heightInInches * 0.0254
                                val weightInKg = weightVal * 0.453592
                                val newBmi = if (heightInMeters > 0) {
                                    (weightInKg / (heightInMeters * heightInMeters)).let {
                                        (it * 10.0).round(1) / 10.0
                                    }
                                } else 0.0

                                val weighInEntry = hashMapOf(
                                    "timestamp" to System.currentTimeMillis(),
                                    "weight_lbs" to weightVal,
                                    "bmi" to newBmi
                                )
                                db.collection("users").document(uid).collection("weighIns")
                                    .add(weighInEntry).await()

                                personalInfoRef.update(
                                    mapOf(
                                        "weight_lbs" to weightVal,
                                        "bmi" to newBmi,
                                        "lastWeighIn" to System.currentTimeMillis()
                                    )
                                ).await()

                                onWeighInSuccess() // Notify success!
                            } else {
                                errorMessage = "Height not found. BMI cannot be calculated."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

// Custom round function to handle Doubles with specific decimal places
fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

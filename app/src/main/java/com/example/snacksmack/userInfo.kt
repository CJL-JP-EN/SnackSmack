package com.example.snacksmack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(onSaveSuccess: () -> Unit, onNavigateBack: () -> Unit) {
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }
    var weightLbs by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var age by remember { mutableIntStateOf(0) }
    var bmi by remember { mutableDoubleStateOf(0.0) }
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
                bmi = round(weightInKg / (heightInMeters * heightInMeters) * 10.0) / 10.0
            }
        }
    }

    // Calculate age as user types DOB
    LaunchedEffect(day, month, year) {
        val dayVal = day.toIntOrNull()
        val monthVal = month.toIntOrNull()
        val yearVal = year.toIntOrNull()

        if (dayVal != null && monthVal != null && yearVal != null) {
            val dob = Calendar.getInstance()
            dob.set(yearVal, monthVal - 1, dayVal)
            val today = Calendar.getInstance()
            var calculatedAge = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                calculatedAge--
            }
            age = calculatedAge
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Information") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
            Spacer(modifier = Modifier.height(16.dp))

            // Date of Birth Input Row
            Text("Date of Birth", style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it },
                    label = { Text("MM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it },
                    label = { Text("DD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("YYYY") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1.5f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Sex Selection
            var expanded by remember { mutableStateOf(false) }
            val sexOptions = listOf("Male", "Female")
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = sex,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sex") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    sexOptions.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                sex = selectionOption
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            Text("Harshness Level")
            Slider(
                value = 0.5f,
                onValueChange = { coroutineScope.launch { snackbarHostState.showSnackbar("Nope, you can't change this") } },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Black,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val feetVal = feet.toDoubleOrNull()
                    val inchesVal = inches.toDoubleOrNull()
                    val weightValLbs = weightLbs.toDoubleOrNull()
                    val dayVal = day.toIntOrNull()
                    val monthVal = month.toIntOrNull()
                    val yearVal = year.toIntOrNull()

                    if (feetVal == null || inchesVal == null || weightValLbs == null || dayVal == null || monthVal == null || yearVal == null || sex.isBlank()) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter valid numbers for all fields.") }
                        return@Button
                    }

                    isLoading = true
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val db = FirebaseFirestore.getInstance()
                        val personalInfoRef = db.collection("users").document(user.uid)
                            .collection("userPersonalInfo").document("personal")

                        val totalHeightInInches = (feetVal * 12) + inchesVal
                        val dobCalendar = Calendar.getInstance().apply {
                            set(yearVal, monthVal - 1, dayVal)
                        }

                        // Save the formatted height string and other data
                        val updates = mapOf(
                            "height" to totalHeightInInches,
                            "weight_lbs" to weightValLbs,
                            "bmi" to bmi,
                            "dob" to dobCalendar.time,
                            "age" to age,
                            "sex" to sex
                        )

                        personalInfoRef.set(updates)
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

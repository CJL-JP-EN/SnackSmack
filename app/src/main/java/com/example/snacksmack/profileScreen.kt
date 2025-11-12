package com.example.snacksmack

import android.util.Log
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class ProfileScreenState(
    val feet: String = "",
    val inches: String = "",
    val weight: String = "",
    val profileText: String = "Please sign up or log in.",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false
)

private class ProfileScreenEvents(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val state: MutableState<ProfileScreenState>,
    private val navController: NavController
) {
    private val lbsToKg = 0.453592
    private val metersToFeet = 3.28084
    private val metersToInches = 39.3701

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            state.value = state.value.copy(isLoggedIn = user != null)
            if (user == null) {
                clearState()
            }
        }
    }

    fun onFeetChange(newValue: String) {
        state.value = state.value.copy(feet = newValue)
    }

    fun onInchesChange(newValue: String) {
        state.value = state.value.copy(inches = newValue)
    }

    fun onWeightChange(newValue: String) {
        state.value = state.value.copy(weight = newValue)
    }

    fun onLogout() {
        auth.signOut()
        showSnackbar("You have been logged out.")
        navController.navigate("login") {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
        }
    }

    fun onSaveProfile() = coroutineScope.launch {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            showSnackbar("You must be logged in to save data.")
            return@launch
        }

        val feetVal = state.value.feet.toDoubleOrNull() ?: 0.0
        val inchesVal = state.value.inches.toDoubleOrNull() ?: 0.0
        val weightLbs = state.value.weight.toDoubleOrNull()

        if (weightLbs == null) {
            showSnackbar("Please enter a valid weight.")
            return@launch
        }

        setLoading(true)
        try {
            val heightM = (feetVal / metersToFeet) + (inchesVal / metersToInches)
            val weightKg = weightLbs * lbsToKg
            val bmi = calculateAndFormatBMI(weightKg, heightM)
            val heightString = "${feetVal.toInt()}'${inchesVal.toInt()}\""

            val userProfile = mapOf(
                "height" to heightString,
                "weight_lbs" to weightLbs,
                "bmi" to bmi
            )

            db.collection("users").document(uid).collection("userPersonalInfo").document("personal").set(userProfile).await()
            showSnackbar("Profile saved!")

            displayProfile(feetVal, inchesVal, weightLbs, bmi)
        } catch (e: Exception) {
            showSnackbar("Failed to save profile: ${e.message}")
        } finally {
            setLoading(false)
        }
    }

    fun loadProfileDataForCurrentUser() {
        auth.currentUser?.uid?.let { loadProfileData(it) }
    }

    private fun setLoading(isLoading: Boolean) {
        state.value = state.value.copy(isLoading = isLoading)
    }

    private fun showSnackbar(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    private fun loadProfileData(uid: String) {
        setLoading(true)
        db.collection("users").document(uid).collection("userPersonalInfo").document("personal").get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val heightString = doc.getString("height")
                    val loadedWeightLbs = doc.getDouble("weight_lbs")
                    val bmi = doc.getDouble("bmi")

                    if (heightString != null && loadedWeightLbs != null && bmi != null) {
                        val parts = heightString.replace("\"", "").split("'")
                        if (parts.size == 2) {
                            val feet = parts[0]
                            val inches = parts[1]

                            state.value = state.value.copy(
                                feet = feet,
                                inches = inches,
                                weight = loadedWeightLbs.toString()
                            )
                            displayProfile(feet.toDouble(), inches.toDouble(), loadedWeightLbs, bmi)
                        } else {
                            updateProfileText("Welcome! Please save your height and weight.")
                        }
                    } else {
                        updateProfileText("Welcome! Please save your height and weight.")
                    }
                } else {
                    updateProfileText("Welcome! Please save your height and weight.")
                }
            }
            .addOnFailureListener { e -> showSnackbar("Failed to load profile: ${e.message}") }
            .addOnCompleteListener { setLoading(false) }
    }

    private fun updateProfileText(text: String) {
        state.value = state.value.copy(profileText = text)
    }

    private fun clearState() {
        state.value = ProfileScreenState()
    }

    private fun displayProfile(feet: Double, inches: Double, weightLbs: Double, bmiVal: Double) {
        val text = """
            📏 Height: ${feet.toInt()}' ${Math.round(inches * 10.0) / 10.0}"
            ⚖️ Weight: ${Math.round(weightLbs * 10.0) / 10.0} lbs
            💪 BMI: $bmiVal
        """.trimIndent()
        updateProfileText(text)
    }

    private fun calculateAndFormatBMI(weightKg: Double, heightM: Double): Double {
        if (heightM == 0.0) return 0.0
        return (weightKg / (heightM * heightM)).let { Math.round(it * 10.0) / 10.0 }
    }
}

@Composable
fun ProfileScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state = remember { mutableStateOf(ProfileScreenState()) }

    val events = remember(coroutineScope, snackbarHostState, navController) {
        ProfileScreenEvents(
            auth = FirebaseAuth.getInstance(),
            db = FirebaseFirestore.getInstance(),
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            state = state,
            navController = navController
        )
    }

    LaunchedEffect(state.value.isLoggedIn) {
        if (state.value.isLoggedIn) {
            events.loadProfileDataForCurrentUser()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        ProfileScreenContent(
            modifier = Modifier.padding(paddingValues),
            state = state.value,
            events = events
        )
    }
}

@Composable
private fun ProfileScreenContent(
    modifier: Modifier = Modifier,
    state: ProfileScreenState,
    events: ProfileScreenEvents
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())) {
            ProfileDataCard(profileText = state.profileText)
            Spacer(Modifier.height(24.dp))
            HealthDataSection(
                feet = state.feet,
                inches = state.inches,
                weight = state.weight,
                isLoading = state.isLoading,
                onFeetChange = events::onFeetChange,
                onInchesChange = events::onInchesChange,
                onWeightChange = events::onWeightChange,
                onSaveClick = events::onSaveProfile
            )
        }

        if (state.isLoggedIn) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = events::onLogout,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Logout")
            }
        }
    }
}

@Composable
private fun ProfileDataCard(profileText: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = profileText,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun HealthDataSection(
    feet: String,
    inches: String,
    weight: String,
    isLoading: Boolean,
    onFeetChange: (String) -> Unit,
    onInchesChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSaveClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = feet,
            onValueChange = onFeetChange,
            label = { Text("Height (ft)") },
            leadingIcon = { Icon(Icons.Default.Straighten, "Height") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isLoading
        )
        OutlinedTextField(
            value = inches,
            onValueChange = onInchesChange,
            label = { Text("in") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isLoading
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = weight,
        onValueChange = onWeightChange,
        label = { Text("Weight (lbs)") },
        leadingIcon = { Icon(Icons.Default.MonitorWeight, "Weight") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !isLoading
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onSaveClick, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("Save Profile Data")
        }
    }
}
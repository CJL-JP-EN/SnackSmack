package com.example.snacksmack

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

//================================================================================
// 1. STATE HOLDER: A data class to represent the entire screen state.
//================================================================================
private data class ProfileScreenState(
    val email: String = "",
    val password: String = "",
    val height: String = "",
    val weight: String = "",
    val profileText: String = "Please sign up or log in.",
    val isLoading: Boolean = false
)

//================================================================================
// 2. LOGIC HANDLER / EVENT PROCESSOR
// Handles all business logic, separated from the UI.
//================================================================================
private class ProfileScreenEvents(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val state: MutableState<ProfileScreenState>
) {
    // --- Public Event Handlers ---
    fun onEmailChange(newValue: String) {
        state.value = state.value.copy(email = newValue)
    }

    fun onPasswordChange(newValue: String) {
        state.value = state.value.copy(password = newValue)
    }

    fun onHeightChange(newValue: String) {
        state.value = state.value.copy(height = newValue)
    }

    fun onWeightChange(newValue: String) {
        state.value = state.value.copy(weight = newValue)
    }

    fun onSignUp() = coroutineScope.launch {
        if (!validateAuthInputs()) return@launch
        setLoading(true)
        try {
            val result = auth.createUserWithEmailAndPassword(state.value.email, state.value.password).await()
            showSnackbar("Account created successfully!")
            result.user?.uid?.let { loadProfileData(it) }
        } catch (e: Exception) {
            showSnackbar("Signup failed: ${e.message}")
        } finally {
            setLoading(false)
        }
    }

    fun onLogin() = coroutineScope.launch {
        if (!validateAuthInputs()) return@launch
        setLoading(true)
        try {
            val result = auth.signInWithEmailAndPassword(state.value.email, state.value.password).await()
            showSnackbar("Logged in successfully!")
            result.user?.uid?.let { loadProfileData(it) }
        } catch (e: Exception) {
            showSnackbar("Login failed: ${e.message}")
        } finally {
            setLoading(false)
        }
    }

    fun onSaveProfile() = coroutineScope.launch {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            showSnackbar("You must be logged in to save data.")
            return@launch
        }

        val heightVal = state.value.height.toDoubleOrNull()
        val weightVal = state.value.weight.toDoubleOrNull()

        if (heightVal == null || weightVal == null) {
            showSnackbar("Please enter valid height and weight.")
            return@launch
        }

        setLoading(true)
        try {
            val bmi = calculateAndFormatBMI(weightVal, heightVal)
            val userProfile = mapOf("height" to heightVal, "weight" to weightVal, "bmi" to bmi)
            db.collection("users").document(uid).set(userProfile).await()
            showSnackbar("Profile saved!")
            displayProfile(heightVal, weightVal, bmi)
        } catch (e: Exception) {
            showSnackbar("Failed to save profile: ${e.message}")
        } finally {
            setLoading(false)
        }
    }

    fun loadProfileDataForCurrentUser() {
        auth.currentUser?.uid?.let { loadProfileData(it) }
    }

    // --- Private Helper Functions ---
    private fun setLoading(isLoading: Boolean) {
        state.value = state.value.copy(isLoading = isLoading)
    }

    private fun showSnackbar(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    private fun loadProfileData(uid: String) {
        setLoading(true)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val loadedHeight = doc.getDouble("height")
                    val loadedWeight = doc.getDouble("weight")
                    if (loadedHeight != null && loadedWeight != null) {
                        val bmi = calculateAndFormatBMI(loadedWeight, loadedHeight)
                        displayProfile(loadedHeight, loadedWeight, bmi)
                    } else {
                        updateProfileText("Welcome! Please save your height and weight.")
                    }
                }
            }
            .addOnFailureListener { e -> showSnackbar("Failed to load profile: ${e.message}") }
            .addOnCompleteListener { setLoading(false) }
    }

    private fun updateProfileText(text: String) {
        state.value = state.value.copy(profileText = text)
    }

    private fun displayProfile(heightVal: Double, weightVal: Double, bmiVal: Double) {
        val text = """
            📏 Height: $heightVal m
            ⚖️ Weight: $weightVal kg
            💪 BMI: $bmiVal
        """.trimIndent()
        updateProfileText(text)
    }
    private fun calculateAndFormatBMI(weightKg: Double, heightM: Double): Double {
        if (heightM == 0.0) return 0.0
        return (weightKg / (heightM * heightM)).let { Math.round(it * 10.0) / 10.0 }
    }

    private fun validateAuthInputs(): Boolean {
        if (state.value.email.isBlank() || state.value.password.isBlank()) {
            showSnackbar("Email and password cannot be empty.")
            return false
        }
        return true
    }
}

//================================================================================
// 3. MAIN COMPOSABLE: The orchestrator.
// It holds the state and connects the UI with the logic.
//================================================================================
@Composable
fun ProfileScreen() {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state = remember { mutableStateOf(ProfileScreenState()) }

    // Memoize the events handler to avoid recreating it on every recomposition.
    val events = remember(coroutineScope, snackbarHostState) {
        ProfileScreenEvents(
            auth = FirebaseAuth.getInstance(),
            db = FirebaseFirestore.getInstance(),
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            state = state
        )
    }

    // Load user data when the screen is first displayed or user changes.
    LaunchedEffect(Unit) {
        events.loadProfileDataForCurrentUser()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        ProfileScreenContent(
            modifier = Modifier.padding(paddingValues),
            state = state.value,
            events = events
        )
    }
}

//================================================================================
// 4. STATELESS UI COMPOSABLES: Dumb components that just display state
// and pass events up.
//================================================================================
@Composable
private fun ProfileScreenContent(
    modifier: Modifier = Modifier,
    state: ProfileScreenState,
    events: ProfileScreenEvents
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        ProfileDataCard(profileText = state.profileText)
        Spacer(Modifier.height(24.dp))

        AuthenticationSection(
            email = state.email,
            password = state.password,
            isLoading = state.isLoading,
            onEmailChange = events::onEmailChange,
            onPasswordChange = events::onPasswordChange,
            onLoginClick = events::onLogin,
            onSignUpClick = events::onSignUp
        )
        Spacer(Modifier.height(24.dp))

        HealthDataSection(
            height = state.height,
            weight = state.weight,
            isLoading = state.isLoading,
            onHeightChange = events::onHeightChange,
            onWeightChange = events::onWeightChange,
            onSaveClick = events::onSaveProfile
        )
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
private fun AuthenticationSection(
    email: String,
    password: String,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        leadingIcon = { Icon(Icons.Default.Email, "Email") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        enabled = !isLoading
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        leadingIcon = { Icon(Icons.Default.Lock, "Password") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isLoading
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onLoginClick, modifier = Modifier.weight(1f), enabled = !isLoading) {
            Text("Login")
        }
        OutlinedButton(onClick = onSignUpClick, modifier = Modifier.weight(1f), enabled = !isLoading) {
            Text("Sign Up")
        }
    }
}

@Composable
private fun HealthDataSection(
    height: String,
    weight: String,
    isLoading: Boolean,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSaveClick: () -> Unit
) {
    OutlinedTextField(
        value = height,
        onValueChange = onHeightChange,
        label = { Text("Height (m)") },
        leadingIcon = { Icon(Icons.Default.Straighten, "Height") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !isLoading
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = weight,
        onValueChange = onWeightChange,
        label = { Text("Weight (kg)") },
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


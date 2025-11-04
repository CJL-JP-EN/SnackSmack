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

private data class ProfileScreenState(
    val email: String = "",
    val password: String = "",
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
    private val state: MutableState<ProfileScreenState>
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

    fun onEmailChange(newValue: String) {
        state.value = state.value.copy(email = newValue)
    }

    fun onPasswordChange(newValue: String) {
        state.value = state.value.copy(password = newValue)
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

    fun onLogout() {
        auth.signOut()
        showSnackbar("You have been logged out.")
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
            val userProfile = mapOf("height" to heightM, "weight" to weightKg, "bmi" to bmi)

            db.collection("users").document(uid).set(userProfile).await()
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
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val loadedHeightM = doc.getDouble("height")
                    val loadedWeightKg = doc.getDouble("weight")

                    if (loadedHeightM != null && loadedWeightKg != null) {
                        val totalInches = loadedHeightM * metersToInches
                        val feet = (totalInches / 12).toInt()
                        val inches = totalInches % 12
                        val weightLbs = loadedWeightKg / lbsToKg

                        state.value = state.value.copy(
                            feet = feet.toString(),
                            inches = (Math.round(inches * 10.0) / 10.0).toString(),
                            weight = (Math.round(weightLbs * 10.0) / 10.0).toString()
                        )

                        val bmi = calculateAndFormatBMI(loadedWeightKg, loadedHeightM)
                        displayProfile(feet.toDouble(), inches, weightLbs, bmi)
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

    private fun validateAuthInputs(): Boolean {
        if (state.value.email.isBlank() || state.value.password.isBlank()) {
            showSnackbar("Email and password cannot be empty.")
            return false
        }
        return true
    }
}

@Composable
fun ProfileScreen() {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state = remember { mutableStateOf(ProfileScreenState()) }

    val events = remember(coroutineScope, snackbarHostState) {
        ProfileScreenEvents(
            auth = FirebaseAuth.getInstance(),
            db = FirebaseFirestore.getInstance(),
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            state = state
        )
    }

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
            isLoggedIn = state.isLoggedIn,
            onEmailChange = events::onEmailChange,
            onPasswordChange = events::onPasswordChange,
            onLoginClick = events::onLogin,
            onSignUpClick = events::onSignUp,
            onLogoutClick = events::onLogout
        )
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
    isLoggedIn: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    if (!isLoggedIn) {
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
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isLoggedIn) {
            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Logout")
            }
        } else {
            Button(onClick = onLoginClick, modifier = Modifier.weight(1f), enabled = !isLoading) {
                Text("Login")
            }
            OutlinedButton(onClick = onSignUpClick, modifier = Modifier.weight(1f), enabled = !isLoading) {
                Text("Sign Up")
            }
        }
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




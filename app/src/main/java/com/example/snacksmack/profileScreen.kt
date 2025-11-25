package com.example.snacksmack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class ProfileScreenState(
    val username: String = "",
    val profileData: List<Pair<String, String>> = emptyList(),
    val message: String? = "Loading profile...",
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false
)

private class ProfileScreenEvents(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val state: MutableState<ProfileScreenState>
) {
    private var profileListener: ListenerRegistration? = null

    fun listenToProfileData(uid: String) {
        if (profileListener != null) return

        db.collection("users").document(uid).collection("userAccountInfo").document("account").get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    state.value = state.value.copy(username = doc.getString("username") ?: "")
                }
            }

        profileListener = db.collection("users").document(uid).collection("userPersonalInfo").document("personal")
            .addSnapshotListener { doc, error ->
                state.value = state.value.copy(isLoading = false)
                if (error != null) {
                    showSnackbar("Error loading profile: ${error.message}")
                    return@addSnapshotListener
                }

                if (doc != null && doc.exists()) {
                    val height = doc.get("height")
                    val loadedWeightLbs = doc.getDouble("weight_lbs")
                    val bmi = doc.getDouble("bmi")
                    val age = doc.getLong("age")
                    val sex = doc.getString("sex")
                    val dobMap = doc.get("dob") as? Map<String, Long>

                    if (height != null && loadedWeightLbs != null && bmi != null) {
                        var feet = 0.0
                        var inches = 0.0

                        if (height is String) {
                            val parts = height.replace("\"", "").split("'")
                            if (parts.size == 2) {
                                feet = parts[0].toDoubleOrNull() ?: 0.0
                                inches = parts[1].toDoubleOrNull() ?: 0.0
                            }
                        } else if (height is Number) {
                            val heightInInches = height.toDouble()
                            feet = (heightInInches / 12).toInt().toDouble()
                            inches = heightInInches % 12
                        }

                        val dobString = if (dobMap != null) "${dobMap["month"]}/${dobMap["day"]}/${dobMap["year"]}" else "N/A"
                        val data = createProfileData(feet, inches, loadedWeightLbs, bmi, age, sex, dobString)
                        state.value = state.value.copy(profileData = data, message = null)
                    } else {
                        state.value = state.value.copy(profileData = emptyList(), message = "Welcome! Please complete your profile from the home screen.")
                    }
                } else {
                     state.value = state.value.copy(profileData = emptyList(), message = "Welcome! Please complete your profile from the home screen.")
                }
            }
    }

    fun removeListener() {
        profileListener?.remove()
        profileListener = null
    }

    fun onLogout(navController: NavController) {
        auth.signOut()
        showSnackbar("You have been logged out.")
        navController.navigate("login") {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
        }
    }

    private fun showSnackbar(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun clearState() {
        state.value = ProfileScreenState(profileData = emptyList(), message = "Please sign up or log in.", isLoading = false)
    }

    private fun createProfileData(feet: Double, inches: Double, weightLbs: Double, bmiVal: Double, age: Long?, sex: String?, dob: String): List<Pair<String, String>> {
        val ageString = age?.toString() ?: "N/A"
        val sexString = sex ?: "N/A"
        return listOf(
            "📏 Height" to "${feet.toInt()}' ${inches.toInt()}\"",
            "⚖️ Weight" to "${Math.round(weightLbs * 10.0) / 10.0} lbs",
            "💪 BMI" to "$bmiVal",
            "🎂 DOB" to dob,
            "🧑 Age" to ageString,
            "ጾ Sex" to sexString
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state = remember { mutableStateOf(ProfileScreenState()) }
    val auth = FirebaseAuth.getInstance()

    val events = remember(coroutineScope, snackbarHostState) {
        ProfileScreenEvents(
            auth = auth,
            db = FirebaseFirestore.getInstance(),
            coroutineScope = coroutineScope,
            snackbarHostState = snackbarHostState,
            state = state
        )
    }

    DisposableEffect(auth) {
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            state.value = state.value.copy(isLoggedIn = user != null)
            if (user != null) {
                events.listenToProfileData(user.uid)
            } else {
                events.clearState()
                events.removeListener()
            }
        }
        auth.addAuthStateListener(authListener)

        onDispose {
            auth.removeAuthStateListener(authListener)
            events.removeListener()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = {  }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        ProfileScreenContent(
            modifier = Modifier.padding(paddingValues),
            state = state.value,
            onLogout = { events.onLogout(navController) }
        )
    }
}

@Composable
private fun ProfileScreenContent(
    modifier: Modifier = Modifier,
    state: ProfileScreenState,
    onLogout: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        if (state.username.isNotBlank()) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Card(
                        modifier = Modifier.size(70.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.size(50.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = state.username,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ProfileDataCard(data = state.profileData, message = state.message)
                }
            }
        }

        if (state.isLoggedIn) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLogout,
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
private fun ProfileDataCard(
    data: List<Pair<String, String>>,
    message: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (message != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                data.forEach { (label, value) ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

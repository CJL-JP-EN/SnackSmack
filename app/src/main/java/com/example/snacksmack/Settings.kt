package com.example.snacksmack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class SettingsScreenState(
    val username: String = "",
    val message: String? = "Loading settings...",
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val breakfastTime: String = "08:00",
    val lunchTime: String = "12:00",
    val dinnerTime: String = "18:00",
    val feet: Int = 5,
    val inches: Int = 10
)

private class SettingsScreenEvents(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val coroutineScope: CoroutineScope,
    private val snackbarHostState: SnackbarHostState,
    private val state: MutableState<SettingsScreenState>
) {
    private var settingsListener: ListenerRegistration? = null

    fun listenToSettingsData(uid: String) {
        if (settingsListener != null) return

        db.collection("users").document(uid).collection("userAccountInfo").document("account").get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    state.value = state.value.copy(username = doc.getString("username") ?: "")
                }
            }

        settingsListener = db.collection("users").document(uid).collection("userPersonalInfo").document("personal")
            .addSnapshotListener { doc, error ->
                state.value = state.value.copy(isLoading = false)
                if (error != null) {
                    showSnackbar("Error loading settings: ${error.message}")
                    return@addSnapshotListener
                }

                if (doc != null && doc.exists()) {
                    val height = doc.get("height")
                    val breakfastTime = doc.getString("breakfast_time") ?: "08:00"
                    val lunchTime = doc.getString("lunch_time") ?: "12:00"
                    val dinnerTime = doc.getString("dinner_time") ?: "18:00"

                    var feet = 5.0
                    var inches = 10.0

                    if (height is String) {
                        val parts = height.replace("", "").split("'")
                        if (parts.size == 2) {
                            feet = parts[0].toDoubleOrNull() ?: 5.0
                            inches = parts[1].toDoubleOrNull() ?: 10.0
                        }
                    } else if (height is Number) {
                        val heightInInches = height.toDouble()
                        feet = (heightInInches / 12).toInt().toDouble()
                        inches = heightInInches % 12
                    }

                    state.value = state.value.copy(
                        message = null,
                        breakfastTime = breakfastTime,
                        lunchTime = lunchTime,
                        dinnerTime = dinnerTime,
                        feet = feet.toInt(),
                        inches = inches.toInt()
                    )
                } else {
                    state.value = state.value.copy(message = "No settings found. Please configure them.")
                }
            }
    }

    fun saveEatingTimes(breakfast: String, lunch: String, dinner: String) {
        val uid = auth.currentUser?.uid ?: return
        val times = mapOf("breakfast_time" to breakfast, "lunch_time" to lunch, "dinner_time" to dinner)
        db.collection("users").document(uid).collection("userPersonalInfo").document("personal")
            .update(times)
            .addOnSuccessListener { showSnackbar("Preferred eating times have been updated.") }
            .addOnFailureListener { e -> showSnackbar("Error updating times: ${e.message}") }
    }

    fun saveUsername(username: String) {
        val uid = auth.currentUser?.uid ?: return
        if (username.isBlank()) {
            showSnackbar("Username cannot be empty.")
            return
        }
        db.collection("users").document(uid).collection("userAccountInfo").document("account")
            .update("username", username)
            .addOnSuccessListener {
                showSnackbar("Username updated successfully.")
                state.value = state.value.copy(username = username)
            }
            .addOnFailureListener { e -> showSnackbar("Error updating username: ${e.message}") }
    }

    fun saveHeight(feet: String, inches: String) {
        val uid = auth.currentUser?.uid ?: return
        val feetVal = feet.toDoubleOrNull()
        val inchesVal = inches.toDoubleOrNull()

        if (feetVal == null || inchesVal == null || feetVal < 0 || inchesVal < 0 || inchesVal >= 12) {
            showSnackbar("Please enter valid feet and inches.")
            return
        }

        val totalHeightInInches = (feetVal * 12) + inchesVal
        db.collection("users").document(uid).collection("userPersonalInfo").document("personal")
            .update("height", totalHeightInInches)
            .addOnSuccessListener { showSnackbar("Height updated successfully.") }
            .addOnFailureListener { e -> showSnackbar("Error updating height: ${e.message}") }
    }

    fun removeListener() {
        settingsListener?.remove()
        settingsListener = null
    }

    fun onLogout(navController: NavController) {
        auth.signOut()
        showSnackbar("You have been logged out.")
        navController.navigate("login") { popUpTo(navController.graph.startDestinationId) { inclusive = true } }
    }

    private fun showSnackbar(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun clearState() {
        state.value = SettingsScreenState(message = "Please sign up or log in.", isLoading = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state = remember { mutableStateOf(SettingsScreenState()) }
    val auth = FirebaseAuth.getInstance()

    var showMealTimeDialog by remember { mutableStateOf(false) }
    var showUpdateNameDialog by remember { mutableStateOf(false) }
    var showUpdateHeightDialog by remember { mutableStateOf(false) }

    val events = remember(coroutineScope, snackbarHostState) {
        SettingsScreenEvents(auth, FirebaseFirestore.getInstance(), coroutineScope, snackbarHostState, state)
    }

    DisposableEffect(auth) {
        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            state.value = state.value.copy(isLoggedIn = user != null)
            if (user != null) {
                events.listenToSettingsData(user.uid)
            } else {
                events.clearState()
                events.removeListener()
            }
        }
        auth.addAuthStateListener(authListener)
        onDispose { auth.removeAuthStateListener(authListener); events.removeListener() }
    }

    if (showMealTimeDialog) {
        MealTimesDialog(currentState = state.value, onDismiss = { showMealTimeDialog = false }, onSave = events::saveEatingTimes)
    }
    if (showUpdateNameDialog) {
        UpdateNameDialog(currentUsername = state.value.username, onDismiss = { showUpdateNameDialog = false }, onSave = events::saveUsername)
    }
    if (showUpdateHeightDialog) {
        UpdateHeightDialog(currentFeet = state.value.feet, currentInches = state.value.inches, onDismiss = { showUpdateHeightDialog = false }, onSave = events::saveHeight)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        SettingsScreenContent(
            modifier = Modifier.padding(paddingValues),
            state = state.value,
            onLogout = { events.onLogout(navController) },
            onUpdateMealTimes = { showMealTimeDialog = true },
            onUpdateName = { showUpdateNameDialog = true },
            onUpdateHeight = { showUpdateHeightDialog = true }
        )
    }
}

@Composable
private fun SettingsScreenContent(
    modifier: Modifier = Modifier,
    state: SettingsScreenState,
    onLogout: () -> Unit,
    onUpdateMealTimes: () -> Unit,
    onUpdateName: () -> Unit,
    onUpdateHeight: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.isLoggedIn) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    SettingsButton(text = "Update Meal Times", onClick = onUpdateMealTimes)
                    SettingsButton(text = "Update Name", onClick = onUpdateName)
                    SettingsButton(text = "Update Height", onClick = onUpdateHeight)
                }
            }
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Logout")
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message ?: "", textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text, fontSize = 16.sp)
    }
}

@Composable
private fun MealTimesDialog(currentState: SettingsScreenState, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val breakfastParts = currentState.breakfastTime.split(":")
    val lunchParts = currentState.lunchTime.split(":")
    val dinnerParts = currentState.dinnerTime.split(":")

    var breakfastHour by remember { mutableStateOf(if (breakfastParts.size == 2) breakfastParts[0].toIntOrNull() ?: 8 else 8) }
    var breakfastMinute by remember { mutableStateOf(if (breakfastParts.size == 2) breakfastParts[1].toIntOrNull() ?: 0 else 0) }
    var lunchHour by remember { mutableStateOf(if (lunchParts.size == 2) lunchParts[0].toIntOrNull() ?: 12 else 12) }
    var lunchMinute by remember { mutableStateOf(if (lunchParts.size == 2) lunchParts[1].toIntOrNull() ?: 0 else 0) }
    var dinnerHour by remember { mutableStateOf(if (dinnerParts.size == 2) dinnerParts[0].toIntOrNull() ?: 18 else 18) }
    var dinnerMinute by remember { mutableStateOf(if (dinnerParts.size == 2) dinnerParts[1].toIntOrNull() ?: 0 else 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred Meal Times") },
        text = {
            Column {
                Text("Breakfast Time", style = MaterialTheme.typography.titleMedium)
                ScrollableTimePicker(hour = breakfastHour, minute = breakfastMinute, onTimeChange = { h, m -> breakfastHour = h; breakfastMinute = m })
                Spacer(Modifier.height(16.dp))
                Text("Lunch Time", style = MaterialTheme.typography.titleMedium)
                ScrollableTimePicker(hour = lunchHour, minute = lunchMinute, onTimeChange = { h, m -> lunchHour = h; lunchMinute = m })
                Spacer(Modifier.height(16.dp))
                Text("Dinner Time", style = MaterialTheme.typography.titleMedium)
                ScrollableTimePicker(hour = dinnerHour, minute = dinnerMinute, onTimeChange = { h, m -> dinnerHour = h; dinnerMinute = m })
            }
        },
        confirmButton = { Button(onClick = { onSave(String.format("%02d:%02d", breakfastHour, breakfastMinute), String.format("%02d:%02d", lunchHour, lunchMinute), String.format("%02d:%02d", dinnerHour, dinnerMinute)); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UpdateNameDialog(currentUsername: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var username by remember { mutableStateOf(currentUsername) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Username") },
        text = { OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { onSave(username); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UpdateHeightDialog(currentFeet: Int, currentInches: Int, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var feet by remember { mutableStateOf(currentFeet.toString()) }
    var inches by remember { mutableStateOf(currentInches.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Height") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = feet,
                    onValueChange = { feet = it },
                    label = { Text("Height (ft)") },
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
        },
        confirmButton = { Button(onClick = { onSave(feet, inches); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ScrollableTimePicker(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        ScrollablePicker(
            value = hour,
            onValueChange = { onTimeChange(it, minute) },
            range = 0..23,
            modifier = Modifier.width(80.dp)
        )
        Text(":", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
        ScrollablePicker(
            value = minute,
            onValueChange = { onTimeChange(hour, it) },
            range = 0..59,
            modifier = Modifier.width(80.dp)
        )
    }
}

@Composable
fun ScrollablePicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier
) {
    val items = range.toList()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = items.indexOf(value).coerceAtLeast(0)
    )

    LaunchedEffect(value) {
        val index = items.indexOf(value)
        if (index != -1 && listState.firstVisibleItemIndex != index) {
            listState.animateScrollToItem(index)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.height(120.dp),
        contentPadding = PaddingValues(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(items) { i ->
            Text(
                text = String.format("%02d", i),
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth()
                    .clickable { onValueChange(i) },
                textAlign = TextAlign.Center,
                style = if (i == value) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = if (i == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

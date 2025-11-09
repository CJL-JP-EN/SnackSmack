package com.example.snacksmack

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snacksmack.ui.theme.SnackSmackTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(onSignUpSuccess: () -> Unit = {}) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Image(
                painter = painterResource(id = R.drawable.snacksmack_logo_icon),
                contentDescription = "App Logo",
                modifier = Modifier.height(180.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "Create an account",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 35.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(60.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, "Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = error?.contains("email", ignoreCase = true) == true
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase().trim() },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.AccountCircle, "Username") },
                isError = error?.contains("username", ignoreCase = true) == true
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, "Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null
            )

            error?.let {
                LaunchedEffect(it) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
                    }
                    error = null
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || username.isBlank()) {
                        error = "All fields are required."
                        return@Button
                    }

                    isLoading = true
                    error = null

                    db.collection("usernames").document(username).get()
                        .addOnSuccessListener { documentSnapshot ->
                            if (documentSnapshot.exists()) {
                                error = "This username already exists."
                                isLoading = false
                            } else {
                                auth.createUserWithEmailAndPassword(email, password)
                                    .addOnSuccessListener { authResult ->
                                        val firebaseUser = authResult.user
                                        if (firebaseUser != null) {
                                            val uid = firebaseUser.uid
                                            val batch = db.batch()

                                            // 1. Create username-to-UID mapping
                                            val usernameRef = db.collection("usernames").document(username)
                                            val usernameMapping = hashMapOf("uid" to uid)
                                            batch.set(usernameRef, usernameMapping)

                                            // 2. Create userAccountInfo sub-collection
                                            val accountRef = db.collection("users").document(uid).collection("userAccountInfo").document("account")
                                            val accountInfo = hashMapOf(
                                                "email" to email,
                                                "username" to username
                                            )
                                            batch.set(accountRef, accountInfo)

                                            // 3. Create userPersonalInfo sub-collection
                                            val personalRef = db.collection("users").document(uid).collection("userPersonalInfo").document("personal")
                                            val personalInfo = hashMapOf(
                                                "bmi" to 0.0,
                                                "height" to 0.0,
                                                "weight" to 0.0
                                            )
                                            batch.set(personalRef, personalInfo)

                                            // Commit the atomic batch write
                                            batch.commit().addOnCompleteListener { task ->
                                                isLoading = false
                                                if (task.isSuccessful) {
                                                    onSignUpSuccess()
                                                } else {
                                                    error = "Failed to save user data: ${task.exception?.message}"
                                                }
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        error = when (e) {
                                            is FirebaseAuthUserCollisionException -> "This email already has an associated account."
                                            else -> "Sign-up failed: ${e.message}"
                                        }
                                        isLoading = false
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            error = "Error checking username: ${e.message}"
                            isLoading = false
                        }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create an account")
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    SnackSmackTheme {
        SignUpScreen()
    }
}

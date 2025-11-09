package com.example.snacksmack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private class LoginState(
    private val coroutineScope: CoroutineScope,
    private val onLoginSuccess: () -> Unit
) {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    fun onEmailChange(newValue: String) {
        email = newValue
    }

    fun onPasswordChange(newValue: String) {
        password = newValue
    }

    fun performLogin(snackbarHostState: SnackbarHostState) {
        if (email.isBlank() || password.isBlank()) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Email and password cannot be empty.")
            }
            return
        }

        isLoading = true
        coroutineScope.launch {
            try {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                onLoginSuccess()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Login failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
}

@Composable
private fun rememberLoginState(
    onLoginSuccess: () -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): LoginState {
    return remember {
        LoginState(coroutineScope, onLoginSuccess)
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    val loginState = rememberLoginState(onLoginSuccess = onLoginSuccess)
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LoginScreenContent(
            modifier = Modifier.padding(paddingValues),
            state = loginState,
            onLoginClick = { loginState.performLogin(snackbarHostState) },
            onNavigateToSignUp = onNavigateToSignUp
        )
    }
}

@Composable
private fun LoginScreenContent(
    modifier: Modifier = Modifier,
    state: LoginState,
    onLoginClick: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome Back!", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))

        EmailTextField(
            email = state.email,
            onEmailChange = state::onEmailChange,
            isLoading = state.isLoading
        )
        Spacer(modifier = Modifier.height(8.dp))

        PasswordTextField(
            password = state.password,
            onPasswordChange = state::onPasswordChange,
            isLoading = state.isLoading
        )
        Spacer(modifier = Modifier.height(24.dp))

        LoginButton(
            isLoading = state.isLoading,
            onClick = onLoginClick
        )
        Spacer(modifier = Modifier.height(16.dp))

        SignUpButton(
            isLoading = state.isLoading,
            onClick = onNavigateToSignUp
        )
    }
}

@Composable
private fun EmailTextField(email: String, onEmailChange: (String) -> Unit, isLoading: Boolean) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        leadingIcon = { Icon(Icons.Default.Email, "Email") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        enabled = !isLoading,
        singleLine = true
    )
}

@Composable
private fun PasswordTextField(password: String, onPasswordChange: (String) -> Unit, isLoading: Boolean) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        leadingIcon = { Icon(Icons.Default.Lock, "Password") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isLoading,
        singleLine = true
    )
}

@Composable
private fun LoginButton(isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text("Login")
        }
    }
}

@Composable
private fun SignUpButton(isLoading: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !isLoading
    ) {
        Text("Don't have an account? Sign Up")
    }
}



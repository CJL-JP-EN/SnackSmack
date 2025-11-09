package com.example.snacksmack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.snacksmack.ui.theme.SnackSmackTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnackSmackTheme {
                val navController = rememberNavController()

                // Get the current route to determine if the nav bar should be shown
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val screensWithNavBar = listOf("home", "profile", "waterTracking", "calendar")

                val currentUser = FirebaseAuth.getInstance().currentUser
                val startDestination = if (currentUser != null) "home" else "login"

                Scaffold(
                    bottomBar = {
                        // Only show the bottom bar on specific screens
                        if (currentRoute in screensWithNavBar) {
                            NavigationButtons(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") { HomeScreen() }
                        composable("profile") { ProfileScreen(navController) }
                        composable("waterTracking") { waterTrackingScreen() }
                        composable("calendar") { CalendarScreen() }
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate("home") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = true
                                        }
                                    }
                                },
                                onNavigateToSignUp = {
                                    navController.navigate("SignUpScreen")
                                }
                            )
                        }
                        composable("SignUpScreen") {
                            SignUpScreen(
                                onSignUpSuccess = {
                                    navController.navigate("userInfo") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("userInfo") { UserInfoScreen() }
                    }
                }
            }
        }
    }
}

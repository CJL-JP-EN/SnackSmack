package com.example.snacksmack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.snacksmack.ui.theme.SnackSmackTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnackSmackTheme {
                val navController = rememberNavController()
                val waterViewModel: WaterViewModel = viewModel()
                val snackViewModel: SnackViewModel = viewModel()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val screensWithNavBar = listOf("home", "profile", "waterTracking", "calendar", "Snack")

                Scaffold(
                    bottomBar = {
                        if (currentRoute?.startsWith("calendar") == true || currentRoute in screensWithNavBar) {
                            NavigationButtons(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                onOpenCalendar = { navController.navigate("calendar?showCreate=true") },
                                waterViewModel = waterViewModel
                            )
                        }
                        composable("Snack") { SnackTrackingScreen(vm = snackViewModel) }

                        composable("profile") { ProfileScreen(navController) }
                        composable("settings") { SettingsScreen(navController) }
                        composable("waterTracking") { WaterTrackingScreen(waterViewModel) }
                        composable(
                            route = "calendar?showCreate={showCreate}",
                            arguments = listOf(navArgument("showCreate") {
                                type = NavType.BoolType
                                defaultValue = false
                            })
                        ) {
                            CalendarScreen(
                                showCreateEventDialog = it.arguments?.getBoolean("showCreate") ?: false,
                                snackViewModel = snackViewModel,
                                waterViewModel = waterViewModel
                            )
                        }

                        composable("login") {
                            val currentUser = FirebaseAuth.getInstance().currentUser
                            if (currentUser != null) {
                                LaunchedEffect(Unit) {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            } else {
                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate("home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    },
                                    onNavigateToSignUp = { navController.navigate("SignUpScreen") }
                                )
                            }
                        }

                        composable("SignUpScreen") {
                            SignUpScreen(
                                onSignUpSuccess = {
                                    navController.navigate("userInfo") {
                                        popUpTo("SignUpScreen") { inclusive = true }
                                    }
                                },
                                onNavigateToLogin = { navController.popBackStack() },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("userInfo") {
                            UserInfoScreen(
                                onSaveSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

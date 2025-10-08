package com.example.snacksmack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snacksmack.ui.theme.SnackSmackTheme

//Settings has not been implemented yet
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnackSmackTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { navigationButtons(navController = navController) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") { HomeScreen() }
                        composable("progress") { progressScreen() }
                        composable("profile") { profileScreen() }
                        composable("waterTracking") { waterTrackingScreen() }
                        composable("calendar") { calendarScreen() }
                    }
                }
            }
        }
    }
}

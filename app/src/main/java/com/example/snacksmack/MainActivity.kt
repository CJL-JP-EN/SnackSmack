package com.example.snacksmack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snacksmack.ui.theme.SnackSmackTheme

// Correctly import all the screen composables
import com.example.snacksmack.homeScreen
import com.example.snacksmack.CalendarScreen
import com.example.snacksmack.WaterTrackingScreen
import com.example.snacksmack.ProgressScreen
import com.example.snacksmack.ProfileScreen
import com.example.snacksmack.NavigationButtons

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnackSmackTheme {
                MyApp()
            }
        }
    }
}

@Composable
fun MyApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { NavigationButtons(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen() }
            composable("progress") { ProgressScreen() }
            composable("profile") { ProfileScreen() }
            composable("waterTracking") { WaterTrackingScreen() }
            composable("calendar") { CalendarScreen() }
        }
    }
}

package com.example.snacksmack

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@Composable
// Declares navigation buttons as well as associates an icon with each item
fun navigationButtons(navController: NavController)
{
    val items = listOf(
        NavigationItem("Home", Icons.Default.Home, "home"),
        NavigationItem("Progress", Icons.Default.Info, "progress"),
        NavigationItem("Profile", Icons.Default.AccountCircle, "profile"),
        NavigationItem("Water Tracking", Icons.Default.Star, "waterTracking"),
        NavigationItem("Calendar", Icons.Default.DateRange, "calendar")

    )

    // creates a navigation bar to navigate between screens
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = false,
                onClick = {
                    // keeps the home as the base screen
                    navController.navigate(item.route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

// Holds the info for each navigation button: its title, screen route, and icon.
data class NavigationItem(val title: String, val route: String, val icon: ImageVector)

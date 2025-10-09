package com.example.snacksmack

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

// In the future implement Painter so that We can use custom icons

@Composable
// Declares navigation buttons as well as associates an icon with each item
fun navigationButtons(navController: NavController) {
    val items = listOf(
        NavigationItem("", "home", Icons.Default.Home),
        NavigationItem("", "progress", Icons.Default.Info),
        NavigationItem("", "profile", Icons.Default.AccountCircle),
        NavigationItem("", "waterTracking", Icons.Default.Star),
        NavigationItem("", "calendar", Icons.Default.DateRange)
    )

    // creates a navigation bar to navigate between screens
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    // keeps the home as the base screen
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

// Holds the info for each navigation button: its title, screen route, and icon.
data class NavigationItem(val title: String, val route: String, val icon: ImageVector)

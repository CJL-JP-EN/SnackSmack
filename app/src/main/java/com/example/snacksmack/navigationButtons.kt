package com.example.snacksmack

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState


@Composable
fun NavigationButtons(navController: NavController) {
    // The items list now uses your custom PNG icons from the drawable folder.
    val items = listOf(
        NavigationItem("Home", "home", R.drawable.home_icon),
        NavigationItem("Water", "waterTracking", R.drawable.glass_icon),
        NavigationItem("Calendar", "calendar", R.drawable.calendar_icon),
        NavigationItem("Profile", "profile", R.drawable.user_icon)
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = item.iconResId),
                        contentDescription = item.title,
                        modifier = Modifier.size(26.dp) // Set icon size
                    )
                },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

// Holds info for each navigation button: title, route, and a drawable resource ID.
data class NavigationItem(
    val title: String,
    val route: String,
    @DrawableRes val iconResId: Int
)

package com.example.snacksmack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Progress Screen

@Composable
fun progressScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center, // Puts content at the top
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Progress",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

package com.example.snacksmack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun bmiLine(
    bmiValue: Float?,
    modifier: Modifier = Modifier,
    onInfoClick: () -> Unit
) {

    val minBmi = 1f
    val maxBmi = 40f

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .height(30.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // for gradiant
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF2196F3),
                            Color(0xFF4CAF50),
                            Color(0xFFFFEB3B),
                            Color(0xFFF44336)
                        )
                    )
                )

                if (bmiValue != null) {
                    val clampedBmi = bmiValue.coerceIn(minBmi, maxBmi)
                    val progress = (clampedBmi - minBmi) / (maxBmi - minBmi)
                    // line for bmi indication yerrr
                    val xPos = size.width * progress
                    drawLine(
                        color = Color.Black,
                        start = Offset(xPos, 0f),
                        end = Offset(xPos, size.height),
                        strokeWidth = 4f
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // BMI text display with clickable info icon
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val text = if (bmiValue != null) {
                // Determine BMI category
                val category = when {
                    bmiValue < 18.5f -> "Underweight"
                    bmiValue < 25f -> "Healthy"
                    bmiValue < 30f -> "Overweight"
                    else -> "Obese"
                }
                val bmiText = if (bmiValue > maxBmi) "BMI: 40+" else "BMI: %.1f".format(bmiValue)
                "$bmiText $category"
            } else {
                "BMI"
            }

            Text(
                text = text,
                fontSize = 16.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "BMI info",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
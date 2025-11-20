package com.example.snacksmack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DayView(
    modifier: Modifier = Modifier,
    day: Int,
    isSelected: Boolean,
    snackCounts: IntArray,
    snackColors: List<Color>,
    waterProgress: Float,
    waterGoalMet: Boolean
) {
    val totalSnacks = snackCounts.sum()

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF008577) else Color.LightGray,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        val ringWidth = 4.dp

        // Outer ring for snacks
        if (totalSnacks > 0) {
            val sweepAngle = 360f / totalSnacks
            var startAngle = -90f

            Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                snackCounts.forEachIndexed { i, count ->
                    if (count > 0) {
                        drawArc(
                            color = snackColors[i],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle * count,
                            useCenter = false,
                            style = Stroke(width = ringWidth.toPx())
                        )
                        startAngle += sweepAngle * count
                    }
                }
            }
        }

        // Inner ring for water
        if (waterProgress > 0) {
            val waterColor = if (waterGoalMet) Color(0xFF1976D2) else Color(0xFF6BAFD6)
            Canvas(modifier = Modifier.fillMaxSize().padding(ringWidth + 4.dp)) {
                drawArc(
                    color = waterColor,
                    startAngle = -90f,
                    sweepAngle = 360f * waterProgress,
                    useCenter = false,
                    style = Stroke(width = ringWidth.toPx())
                )
            }
        }

        Text(
            text = day.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun DayViewPreview() {
    DayView(
        modifier = Modifier.fillMaxSize(),
        day = 12,
        isSelected = true,
        snackCounts = intArrayOf(2, 1, 3, 1),
        snackColors = listOf(
            Color(0xFF7E57C2), // purple
            Color(0xFF4FC3F7), // light blue
            Color(0xFFFF7043), // orange red
            Color(0xFF81C784)  // light green
        ),
        waterProgress = 0.75f,
        waterGoalMet = false
    )
}

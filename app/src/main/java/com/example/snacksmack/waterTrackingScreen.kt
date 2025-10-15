package com.example.snacksmack

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun waterTrackingScreen() {
    // Persist across rotation/process death
    var progress by rememberSaveable { mutableStateOf(0f) }          // 0f..1f
    val animated = animateFloatAsState(progress, label = "fill").value
    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }

    // Auto-reset at midnight (checks once per minute while this screen is shown)
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000) // 1 minute
            val today = LocalDate.now().toEpochDay()
            if (today != epochDay) {
                progress = 0f
                epochDay = today
            }
        }
    }

    // Fade the congrats message in/out
    val congratsAlpha = animateFloatAsState(
        targetValue = if (animated >= 1f) 1f else 0f,
        label = "congratsAlpha"
    ).value

    // Layout: title near top, cup pinned to bottom
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
    ) {
        // Title
        Text(
            text = "Water Tracking",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )

        // Progress text
        Text(
            text = "Tap the cup to fill: ${(animated * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        )

        // 🎉 Congrats message (center)
        if (congratsAlpha > 0f) {
            Text(
                text = "🎉 Congrats! You reached your goal for the day!",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    color = Color(0xFF00897B)
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .graphicsLayer(alpha = congratsAlpha)
            )
        }

        // Cup at bottom; tap adds +10% (locked at full; no reset on tap)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(260.dp)
                .clickable {
                    if (progress < 1f) {
                        progress = (progress + 0.10f).coerceAtMost(1f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            WaterCupWithRing(
                progress = animated,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun WaterCupWithRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Ring
        val radius = minOf(w, h) * 0.45f
        val center = Offset(w / 2f, h / 2.2f)
        val ringStroke = w * 0.06f

        drawArc(
            color = Color(0xFF2F3A40).copy(alpha = 0.25f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF6BAFD6),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )

        // Cup (simple trapezoid)
        val cupTopWidth = w * 0.42f
        val cupBottomWidth = w * 0.30f
        val cupHeight = h * 0.42f
        val cupTopY = center.y - cupHeight * 0.45f
        val cupBottomY = cupTopY + cupHeight
        val leftTopX = center.x - cupTopWidth / 2f
        val rightTopX = center.x + cupTopWidth / 2f
        val leftBottomX = center.x - cupBottomWidth / 2f
        val rightBottomX = center.x + cupBottomWidth / 2f

        val cupPath = Path().apply {
            moveTo(leftTopX, cupTopY)
            lineTo(rightTopX, cupTopY)
            lineTo(rightBottomX, cupBottomY)
            lineTo(leftBottomX, cupBottomY)
            close()
        }
        drawPath(path = cupPath, color = Color(0xFF2F3A40), style = Stroke(width = w * 0.01f))

        // Water fill
        val fillTop = cupBottomY - (cupHeight * progress)
        val t = ((fillTop - cupTopY) / (cupBottomY - cupTopY)).coerceIn(0f, 1f)
        val topWidthAtFill = cupTopWidth + (cupBottomWidth - cupTopWidth) * t
        val leftAtFill = center.x - topWidthAtFill / 2f
        val rightAtFill = center.x + topWidthAtFill / 2f

        val water = Path().apply {
            moveTo(leftBottomX, cupBottomY)
            lineTo(rightBottomX, cupBottomY)
            lineTo(rightAtFill, fillTop)
            lineTo(leftAtFill, fillTop)
            close()
        }
        drawPath(path = water, color = Color(0xFF6BAFD6).copy(alpha = 0.9f))

        // Rim
        drawLine(
            color = Color(0xFF2F3A40),
            start = Offset(leftTopX, cupTopY),
            end = Offset(rightTopX, cupTopY),
            strokeWidth = w * 0.012f
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewWaterTrackingScreen() {
    waterTrackingScreen()
}

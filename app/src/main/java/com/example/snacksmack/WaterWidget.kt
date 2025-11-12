package com.example.snacksmack

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WaterCupWidget(modifier: Modifier = Modifier, vm: WaterViewModel = viewModel()) {
    Box(
        modifier = modifier.clickable { vm.addCup() },
        contentAlignment = Alignment.Center
    ) {
        val animatedProgress = rememberAnimatedProgress(vm.progress)
        val pulse = rememberPulse(animatedProgress >= 1f)
        Canvas(Modifier.fillMaxSize()) { drawCupWithRing(animatedProgress, pulse) }
        Text(
            text = "${vm.consumedOz} oz",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
            color = Color(0xFF2F3A40)
        )
    }
}

@Composable
private fun rememberAnimatedProgress(target: Float): Float {
    val anim = remember { Animatable(target) }
    var prevTarget by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        if (target > prevTarget && (target - anim.value) > 0.001f) {
            anim.animateTo(target, tween(600, easing = FastOutSlowInEasing))
        } else {
            anim.snapTo(target)
        }
        prevTarget = target
    }
    return anim.value
}

@Composable
private fun rememberPulse(enabled: Boolean): Float {
    if (!enabled) return 0f
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "p"
    )
    return p
}

private fun DrawScope.drawCupWithRing(progress: Float, pulse: Float) {
    val w = size.width
    val h = size.height
    val radius = minOf(w, h) * 0.45f
    val center = Offset(w / 2f, h / 2f) // Center the entire graphic
    val ringStroke = w * 0.06f

    // Outer ring
    drawArc(
        color = Color(0xFF2F3A40).copy(alpha = 0.18f),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )

    // Progress ring
    drawArc(
        color = Color(0xFF6BAFD6),
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )

    // Cup body and fill
    val cupTopWidth = w * 0.42f
    val cupBottomWidth = w * 0.30f
    val cupHeight = h * 0.42f
    val cupTopY = center.y - (cupHeight / 2f)
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
    drawPath(cupPath, Color(0xFF2F3A40), style = Stroke(w * 0.01f))

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
    drawPath(water, Color(0xFF6BAFD6).copy(alpha = 0.9f))
}

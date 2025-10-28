package com.example.snacksmack

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


/* ---------- PUBLIC (one-liner) ---------- */
@Composable
fun waterTrackingScreen(
    owner: ViewModelStoreOwner = LocalContext.current as ComponentActivity
) {
    val vm: WaterViewModel = viewModel(owner) // activity-scoped VM (persists across nav + restarts)
    WaterTrackingInternal(
        progress       = vm.progress,
        goalOz         = vm.goalOz,
        consumedOz     = vm.consumedOz,
        cupIncrementOz = vm.cupIncrementOz,
        onAddCup       = vm::addCup,
        onRemoveCup    = vm::removeCup,
        onSetGoal      = vm::updateGoalOz
    )
}

/* ---------- PRIVATE UI ---------- */
@Composable
private fun WaterTrackingInternal(
    progress: Float,
    goalOz: Int,
    consumedOz: Int,
    cupIncrementOz: Int,
    onAddCup: () -> Unit,
    onRemoveCup: () -> Unit,
    onSetGoal: (Int) -> Unit
) {
    val animated = rememberAnimatedProgress(progress)

// Show immediately based on the real state, not the animated one
    val showCongrats = progress >= 1f
// If you still want a tiny fade, keep this; or drop it and render directly
    val congratsAlpha by animateFloatAsState(targetValue = if (showCongrats) 1f else 0f, label = "congrats")
// Or: val congratsAlpha = if (showCongrats) 1f else 0f  // snaps, no fade
    val pulse = rememberPulse(animated >= 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Water Tracking",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp)
            )
            Spacer(Modifier.size(6.dp))
            Text("Goal: ${goalOz}oz • Drank: ${consumedOz}oz • Cup: +${cupIncrementOz}oz")
            Text("Progress: ${(animated * 100).toInt()}%")
        }

        if (congratsAlpha > 0f) {
            Spacer(Modifier.size(8.dp))
            Text(
                "🎉 Congrats! You reached your goal for the day!",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    color = Color(0xFF00897B)
                ),
                modifier = Modifier.graphicsLayer(alpha = congratsAlpha)
            )
        }

        Spacer(Modifier.size(16.dp))

        Box(
            modifier = Modifier
                .size(260.dp)
                .clickable { onAddCup() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) { drawCupWithRing(animated, pulse) }
        }
    }
}

/* ---------- PRIVATE helpers ---------- */
@Composable
private fun rememberAnimatedProgress(target: Float): Float {
    // Start at the current target so first composition shows the real value immediately
    val anim = remember { Animatable(target) }
    var prevTarget by remember { mutableStateOf(target) }

    LaunchedEffect(target) {
        if (target > prevTarget && (target - anim.value) > 0.001f) {
            // animate only on upward changes (user added water)
            anim.animateTo(
                targetValue = target,
                animationSpec = tween(600, easing = FastOutSlowInEasing)
            )
        } else {
            // snap on first load, same value, or decreases / midnight reset
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

/* ---------- PRIVATE drawing ---------- */
private fun DrawScope.drawCupWithRing(progress: Float, pulse: Float) {
    val w = size.width
    val h = size.height
    val radius = minOf(w, h) * 0.45f
    val center = Offset(w / 2f, h / 2.2f)
    val ringStroke = w * 0.06f

    // track
    drawArc(
        color = Color(0xFF2F3A40).copy(alpha = 0.18f),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )
    // progress
    drawArc(
        color = Color(0xFF6BAFD6),
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = ringStroke, cap = StrokeCap.Round)
    )
    // glow when full
    if (progress >= 1f) {
        val glow = ringStroke * (1f + 0.25f * pulse)
        drawArc(
            color = Color(0xFF6BAFD6).copy(alpha = 0.25f + 0.35f * pulse),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = glow, cap = StrokeCap.Round)
        )
    }

    // cup
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
    drawPath(cupPath, Color(0xFF2F3A40), style = Stroke(w * 0.01f))

    // water fill
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

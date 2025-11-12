package com.example.snacksmack

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun WaterTrackingScreen(
    vm: WaterViewModel
) {
    LaunchedEffect(Unit) {
        vm.resetIfNewDay()
    }

    WaterTrackingInternal(
        progress = vm.progress,
        goalOz = vm.goalOz,
        consumedOz = vm.consumedOz,
        cupIncrementOz = vm.cupIncrementOz,
        onAddCup = vm::addCup,
        onRemoveCup = vm::removeCup,
        onSetGoal = vm::updateGoalOz,
        shouldPromptGoal = vm.shouldPromptGoalOnce(),
        onPromptSeen = vm::markGoalPromptSeen,
        getWeeklyData = vm::getWeeklyData
    )
}

/* ---------- UI ---------- */
@Composable
private fun WaterTrackingInternal(
    progress: Float,
    goalOz: Int,
    consumedOz: Int,
    cupIncrementOz: Int,
    onAddCup: () -> Unit,
    onRemoveCup: () -> Unit,
    onSetGoal: (Int) -> Unit,
    shouldPromptGoal: Boolean,
    onPromptSeen: () -> Unit,
    getWeeklyData: (LocalDate) -> Map<LocalDate, Int>
) {
    val animated = rememberAnimatedProgress(progress)
    val showCongrats = progress >= 1f
    val congratsAlpha by animateFloatAsState(
        targetValue = if (showCongrats) 1f else 0f,
        label = "congrats"
    )
    val pulse = rememberPulse(animated >= 1f)
    var showGoalDialog by remember { mutableStateOf(shouldPromptGoal) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Water Tracking",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp)
            )
            Spacer(Modifier.height(6.dp))
            Text("Goal: ${goalOz}oz • Drank: ${consumedOz}oz • Cup: +${cupIncrementOz}oz")
            Text("Progress: ${(animated * 100).toInt()}%")

            if (congratsAlpha > 0f) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "🎉 Congrats! You reached your goal for the day!",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        color = Color(0xFF1976D2)
                    ),
                    modifier = Modifier.graphicsLayer(alpha = congratsAlpha)
                )
            }

            Spacer(Modifier.size(12.dp))

            // Cup progress ring (💧 your cup graphic)
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clickable { onAddCup() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) { drawCupWithRing(animated, pulse) }
                Text(
                    text = "${consumedOz} oz",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    color = Color(0xFF2F3A40)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Buttons ABOVE weekly progress
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRemoveCup) { Text("−${cupIncrementOz}oz") }
                Button(onClick = onAddCup) { Text("+${cupIncrementOz}oz") }
                OutlinedButton(onClick = { showGoalDialog = true }) { Text("Set goal") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Weekly progress section (bottom)
        WeeklyProgressSection(goalOz = goalOz, getWeeklyData = getWeeklyData)

        if (showGoalDialog) {
            GoalDialog(
                currentGoal = goalOz,
                step = cupIncrementOz,
                onDismiss = {
                    showGoalDialog = false
                    onPromptSeen()
                },
                onSave = { newGoal ->
                    onSetGoal(newGoal)
                    onPromptSeen()
                    showGoalDialog = false
                }
            )
        }
    }
}
/* ---------- GOAL DIALOG ---------- */
@Composable
private fun GoalDialog(
    currentGoal: Int,
    step: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Daily Goal") },
        text = {
            Column {
                Text(
                    "Enter your daily water goal in ounces.\nIt must be a multiple of $step oz.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.size(12.dp))
                var text by remember { mutableStateOf(currentGoal.toString()) }
                fun sanitized(s: String) = s.filter { it.isDigit() }.take(4)

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitized(it) },
                    singleLine = true,
                    label = { Text("Goal (oz)") }
                )
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(48, 64, 80, 96).forEach { preset ->
                        OutlinedButton(onClick = { text = preset.toString() }) {
                            Text("${preset}oz")
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val v = text.toIntOrNull() ?: currentGoal
                        text = (v - step).coerceAtLeast(step).toString()
                    }) { Text("−${step}oz") }
                    OutlinedButton(onClick = {
                        val v = text.toIntOrNull() ?: currentGoal
                        text = (v + step).toString()
                    }) { Text("+${step}oz") }
                }

                val value = text.toIntOrNull()
                val valid = value != null && value >= step && value % step == 0
                if (!valid) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Goal must be at least ${step}oz and a multiple of ${step}.",
                        color = Color(0xFFB00020),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { value?.let(onSave) }, enabled = valid) { Text("Save") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}


/* ---------- WEEKLY SECTION ---------- */
@Composable
private fun WeeklyProgressSection(
    goalOz: Int,
    getWeeklyData: (LocalDate) -> Map<LocalDate, Int>
) {
    var currentWeekOffset by remember { mutableStateOf(0) }

    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("MMM d")

    val startOfWeek = today.minusWeeks(currentWeekOffset.toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weeklyData = getWeeklyData(startOfWeek)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Weekly Progress", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Arrow controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentWeekOffset++ }) {
                Icon(Icons.Default.ArrowBackIos, contentDescription = "Previous week")
            }
            val endOfWeek = startOfWeek.plusDays(6)
            Text(
                text = "Week of ${startOfWeek.format(formatter)} - ${endOfWeek.format(formatter)}",
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = { if (currentWeekOffset > 0) currentWeekOffset-- }) {
                Icon(Icons.Default.ArrowForwardIos, contentDescription = "Next week")
            }
        }

        Spacer(Modifier.height(4.dp))

        // Bar graph
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            val sortedData = weeklyData.entries.sortedBy { it.key }
            val barMaxHeight = 100.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barMaxHeight),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom
            ) {
                (0..6).forEach { i ->
                    val day = startOfWeek.plusDays(i.toLong())
                    val value = weeklyData[day] ?: 0
                    val percent = if (goalOz > 0) (value.toFloat() / goalOz).coerceIn(0f, 1f) else 0f
                    val anim by animateFloatAsState(
                        targetValue = percent,
                        animationSpec = tween(700, easing = FastOutSlowInEasing),
                        label = "barAnim"
                    )

                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(barMaxHeight * anim)
                            .background(
                                if (value >= goalOz && goalOz > 0) Color(0xFF1976D2)
                                else Color(0xFF4FC3F7)
                            )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                (0..6).forEach { i ->
                    val day = startOfWeek.plusDays(i.toLong())
                    Text(
                        day.dayOfWeek.name.take(3),
                        fontSize = 12.sp,
                        color = Color(0xFF004D40)
                    )
                }
            }
        }
    }
}

/* ---------- HELPERS ---------- */
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
    val center = Offset(w / 2f, h / 2.2f)
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

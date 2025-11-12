package com.example.snacksmack

import android.app.Application
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.snacksmack.notifications.NotificationHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.min
import androidx.compose.ui.graphics.nativeCanvas

@Composable
fun SnackTrackingScreen() {
    val vm: SnackViewModel = viewModel()

    LaunchedEffect(Unit) { vm.resetIfNewDay() }

    val ctx = LocalContext.current
    val counts = vm.getTodayCounts() // [protein, salty, sweet, healthy]

    val colors = listOf(
        Color(0xFF7E57C2), // Protein - purple
        Color(0xFF4FC3F7), // Salty   - light blue
        Color(0xFFFF7043), // Sweet   - orange red
        Color(0xFF81C784)  // Healthy - light green
    )
    val labels = listOf("Protein 🍗", "Salty 🧂", "Sweet 🍬", "Healthy 🥗")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Snack Tracker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Tap buttons to record snacks • Segments grow as you log")

        Spacer(Modifier.height(16.dp))

        SnackWheel(
            counts = counts,
            colors = colors,
            diameterDp = 260.dp,
            ringWidth = 18.dp
        )

        Spacer(Modifier.height(16.dp))

        SnackButtons(
            onAdd = { vm.addSnack(it) },
            onRemove = { vm.removeSnack(it) }
        )

        Spacer(Modifier.height(16.dp))

        SnackBars(counts = counts, colors = colors, labels = labels)

        Spacer(Modifier.height(20.dp))

        Button(onClick = {
            NotificationHelper.createChannel(ctx)
            NotificationHelper.showSnackSummary(
                ctx,
                protein = counts.getOrNull(SnackType.PROTEIN.ordinal) ?: 0,
                salty   = counts.getOrNull(SnackType.SALTY.ordinal) ?: 0,
                sweet   = counts.getOrNull(SnackType.SWEET.ordinal) ?: 0,
                healthy = counts.getOrNull(SnackType.HEALTHY.ordinal) ?: 0
            )
        }) {
            Text("Send Daily Snack Summary")
        }

        Spacer(Modifier.height(24.dp))

        WeeklySnackColorBars(vm = vm, colors = colors)
        SnackLegend(colors = colors) // optional tiny legend under the chart

    }
}

@Composable
private fun SnackWheel(
    counts: IntArray,
    colors: List<Color>,
    diameterDp: Dp,
    ringWidth: Dp
) {
    val total = counts.sum().coerceAtLeast(1)
    val proportions = counts.map { it.toFloat() / total.toFloat() }
    val animProps = proportions.map {
        animateFloatAsState(
            targetValue = it,
            animationSpec = tween(700, easing = FastOutSlowInEasing),
            label = "slice"
        )
    }

    Canvas(modifier = Modifier.size(diameterDp)) {
        val stroke = Stroke(width = ringWidth.toPx())
        val d = min(size.width, size.height)
        val radius = d / 2f
        val leftTop = Offset(center.x - radius, center.y - radius)

        var startAngle = -90f
        animProps.forEachIndexed { idx, animated ->
            val sweep = animated.value * 360f
            drawArc(
                color = colors[idx],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = leftTop,
                size = androidx.compose.ui.geometry.Size(d, d),
                style = stroke
            )
            startAngle += sweep
        }

        // Center label
        val totalText = "${counts.sum()} snack" + if (counts.sum() == 1) "" else "s"
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#2F3A40")
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = radius * 0.35f
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT_BOLD,
                    android.graphics.Typeface.BOLD
                )
            }
            canvas.nativeCanvas.drawText(
                totalText,
                center.x,
                center.y + (paint.textSize / 3f),
                paint
            )
        }
    }
}

@Composable
private fun SnackButtons(
    onAdd: (SnackType) -> Unit,
    onRemove: (SnackType) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onAdd(SnackType.PROTEIN) }) { Text("🍗 Protein +") }
            Button(onClick = { onAdd(SnackType.SALTY) }) { Text("🧂 Salty +") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onAdd(SnackType.SWEET) }) { Text("🍬 Sweet +") }
            Button(onClick = { onAdd(SnackType.HEALTHY) }) { Text("🥗 Healthy +") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onRemove(SnackType.PROTEIN) }) { Text("−🍗") }
            OutlinedButton(onClick = { onRemove(SnackType.SALTY) }) { Text("−🧂") }
            OutlinedButton(onClick = { onRemove(SnackType.SWEET) }) { Text("−🍬") }
            OutlinedButton(onClick = { onRemove(SnackType.HEALTHY) }) { Text("−🥗") }
        }
    }
}
@Composable
private fun WeeklySnackColorBars(
    vm: SnackViewModel,
    colors: List<Color>
) {
    val today = LocalDate.now()
    val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    // Recompute every recomposition so today's updates reflect immediately
    val week: Map<LocalDate, IntArray> = vm.getWeekPerCategory(startOfWeek)
    val fmt = DateTimeFormatter.ofPattern("EEE")

    val EPS = 0.0001f // weight must be > 0

    Spacer(Modifier.height(8.dp))
    Text("Weekly Snapshot", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    // Keep the days ordered Sun..Sat
    week.toSortedMap().forEach { (date, counts) ->
        val dayLabel = date.format(fmt)
        val total = counts.sum().coerceAtLeast(1)
        val targetFractions: List<Float> = counts.map { it.toFloat() / total }

        // Animate each category segment independently
        val animatedFractions: List<Float> = targetFractions.mapIndexed { idx, f ->
            val anim by animateFloatAsState(
                targetValue = f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                label = "wkSeg-$dayLabel-$idx"
            )
            anim
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dayLabel, modifier = Modifier.width(46.dp))
            Spacer(Modifier.width(6.dp))

            // Stacked bar: 4 colored segments
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Color(0x11000000), RoundedCornerShape(3.dp))
                    .padding(1.dp)
            ) {
                animatedFractions.forEachIndexed { i, fAnim ->
                    // keep layout happy with EPS, hide color if actually zero
                    val wasZero = targetFractions[i] == 0f
                    val weightVal = if (fAnim > 0f) fAnim else EPS
                    val color = if (wasZero) colors[i].copy(alpha = 0f) else colors[i]

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(weightVal)
                            .background(color, RoundedCornerShape(2.dp))
                    )
                    if (i != animatedFractions.lastIndex) Spacer(Modifier.width(2.dp))
                }
            }

            // Optional: small total at the end
            Spacer(Modifier.width(6.dp))
            Text(counts.sum().toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SnackLegend(colors: List<Color>) {
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(colors[0], "Protein")
        LegendDot(colors[1], "Salty")
        LegendDot(colors[2], "Sweet")
        LegendDot(colors[3], "Healthy")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SnackBars(
    counts: IntArray,
    colors: List<Color>,
    labels: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Today’s Snack Mix", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val maxVal = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
        counts.forEachIndexed { i, v ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(28.dp)
                    .background(colors[i].copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            ) {
                val anim by animateFloatAsState(
                    targetValue = (v.toFloat() / maxVal),
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "snackBar"
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(anim)
                        .background(colors[i], RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("  ${labels[i]}: $v", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WeeklySnackMiniBars(
    vm: SnackViewModel,
    colors: List<Color>
) {
    val today = java.time.LocalDate.now()
    val startOfWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY))
    val week = vm.getWeekPerCategory(startOfWeek)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE")

    val EPS = 0.0001f // weight must be > 0

    Spacer(Modifier.height(8.dp))
    Text("Weekly Snapshot", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    week.toSortedMap().forEach { (date, counts) ->
        val dayLabel = date.format(fmt)
        val total = counts.sum().coerceAtLeast(1)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(dayLabel, modifier = Modifier.width(52.dp))
            Spacer(Modifier.width(6.dp))

            val fractions: List<Float> = counts.map { it.toFloat() / total }
            Row(modifier = Modifier.fillMaxWidth()) {
                fractions.forEachIndexed { i, f0 ->
                    val f = if (f0 > 0f) f0 else EPS
                    // Hide the color if this was actually zero to avoid visible slivers
                    val c = if (f0 > 0f) colors[i] else colors[i].copy(alpha = 0f)

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(f)
                            .background(c, RoundedCornerShape(3.dp))
                    )
                    if (i != fractions.lastIndex) Spacer(Modifier.width(2.dp))
                }
            }
        }
    }
}

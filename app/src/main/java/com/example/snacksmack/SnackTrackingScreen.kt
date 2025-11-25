package com.example.snacksmack
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters



@Composable
fun SnackTrackingScreen() {
    val vm: SnackViewModel = viewModel()

    LaunchedEffect(Unit) { vm.resetIfNewDay() }

    // This will re-read from VM on every recomposition
    val counts = vm.getTodayCounts()

    val colors = listOf(
        Color(0xFF7E57C2), // Protein - purple
        Color(0xFF4FC3F7), // Salty   - light blue
        Color(0xFFFF7043), // Sweet   - orange red
        Color(0xFF81C784)  // Healthy - light green
    )
    val labels = listOf("Protein 🍗", "Salty 🧂", "Sweet 🍬", "Healthy 🥗")

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Snack Tracker",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
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
            counts = counts,
            onAdd = { vm.addSnack(it) },
            onRemoveConfirmed = { vm.removeSnack(it) }
        )

        Spacer(Modifier.height(16.dp))

        SnackBars(counts = counts, colors = colors, labels = labels)

        Spacer(Modifier.height(24.dp))

        WeeklySnackProgressSection(vm = vm, colors = colors)

        Spacer(Modifier.height(12.dp))

        SnackLegend(colors = colors)
    }
}

/* ---------- WHEEL ---------- */

@Composable
private fun SnackWheel(
    counts: IntArray,
    colors: List<Color>,
    diameterDp: Dp,
    ringWidth: Dp
) {
    val context = LocalContext.current

    // 👇 Load your custom font ONCE per composition
    val customTypeface = remember {
        ResourcesCompat.getFont(context, R.font.sanur_beach)
    }

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
                size = Size(d, d),
                style = stroke
            )
            startAngle += sweep
        }

        // ------- Center label with custom font -------
        val totalText = "${counts.sum()} snack" + if (counts.sum() == 1) "" else "s"

        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.parseColor("#2F3A40")
                textAlign = Paint.Align.CENTER
                textSize = radius * 0.35f
                isAntiAlias = true
                typeface = customTypeface
                    ?: Typeface.create(
                        Typeface.DEFAULT_BOLD,
                        Typeface.BOLD
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

        /* ---------- BUTTONS (WITH HONESTY POPUP) ---------- */

@Composable
private fun SnackButtons(
    counts: IntArray,
    onAdd: (SnackType) -> Unit,
    onRemoveConfirmed: (SnackType) -> Unit
) {
    var showHonestyDialog by remember { mutableStateOf(false) }
    var pendingRemoveType by remember { mutableStateOf<SnackType?>(null) }

    fun handleRemove(type: SnackType) {
        val idx = type.ordinal
        val currentCount = counts.getOrNull(idx) ?: 0

        // Only nag for SWEET or SALTY and only if they've logged 5+
        if ((type == SnackType.SWEET || type == SnackType.SALTY) && currentCount >= 5) {
            pendingRemoveType = type
            showHonestyDialog = true
        } else {
            onRemoveConfirmed(type)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Row 1: Protein + Salty
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SnackToggleButton(
                label = "🍗 Protein",
                color = Color(0xFF7E57C2),
                onAdd = { onAdd(SnackType.PROTEIN) },
                onRemove = { handleRemove(SnackType.PROTEIN) },
                modifier = Modifier.weight(1f)
            )
            SnackToggleButton(
                label = "🧂 Salty",
                color = Color(0xFF4FC3F7),
                onAdd = { onAdd(SnackType.SALTY) },
                onRemove = { handleRemove(SnackType.SALTY) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Row 2: Sweet + Healthy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SnackToggleButton(
                label = "🍬 Sweet",
                color = Color(0xFFFF7043),
                onAdd = { onAdd(SnackType.SWEET) },
                onRemove = { handleRemove(SnackType.SWEET) },
                modifier = Modifier.weight(1f)
            )
            SnackToggleButton(
                label = "🥗 Healthy",
                color = Color(0xFF81C784),
                onAdd = { onAdd(SnackType.HEALTHY) },
                onRemove = { handleRemove(SnackType.HEALTHY) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showHonestyDialog && pendingRemoveType != null) {
        val type = pendingRemoveType!!
        val typeLabel = when (type) {
            SnackType.SWEET -> "sweet"
            SnackType.SALTY -> "salty"
            SnackType.PROTEIN -> "protein"
            SnackType.HEALTHY -> "healthy"
        }

        AlertDialog(
            onDismissRequest = {
                showHonestyDialog = false
                pendingRemoveType = null
            },
            title = { Text("Be honest with yourself 👀") },
            text = {
                Text(
                    "You’ve logged 5+ $typeLabel snacks today.\n" +
                            "Are you really removing one, or are you trying to erase it from history? Don’t lie to yourself."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveConfirmed(type)
                    showHonestyDialog = false
                    pendingRemoveType = null
                }) {
                    Text("Yes, remove it")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showHonestyDialog = false
                    pendingRemoveType = null
                }) {
                    Text("Keep it logged")
                }
            }
        )
    }
}


/**
 * Unified snack control: big chip with + and − inside.
 */
@Composable
private fun SnackToggleButton(
    label: String,
    color: Color,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // − button
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.size(width = 40.dp, height = 40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text("−")
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            // + button
            Button(
                onClick = onAdd,
                modifier = Modifier.size(width = 40.dp, height = 40.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = Color.White
                )
            ) {
                Text("+")
            }
        }
    }
}


/* ---------- TODAY BARS ---------- */

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

/* ---------- WEEKLY VERTICAL STACKED BARS ---------- */

@Composable
private fun WeeklySnackProgressSection(
    vm: SnackViewModel,
    colors: List<Color>
) {
    // Force recomposition when VM’s data changes
    val _version = vm.dataVersion

    var weekOffset by remember { mutableStateOf(0) } // 0 = current week; 1 = previous, etc.

    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("MMM d")

    val startOfWeek = today
        .minusWeeks(weekOffset.toLong())
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    val weekMap = vm.getWeekPerCategory(startOfWeek).toSortedMap()
    val totalsPerDay = weekMap.mapValues { (_, counts) -> counts.sum() }
    val maxForWeek = totalsPerDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    val barMaxHeight = 90.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Weekly progress", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekOffset++ }) {
                Icon(
                    imageVector = Icons.Filled.ArrowBackIos,
                    contentDescription = "Previous week"
                )
            }

            val endOfWeek = startOfWeek.plusDays(6)
            Text(
                text = "Week of ${startOfWeek.format(formatter)} - ${endOfWeek.format(formatter)}",
                style = MaterialTheme.typography.bodyMedium
            )

            IconButton(
                onClick = {
                    if (weekOffset > 0) weekOffset--
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = "Next week"
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barMaxHeight + 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            weekMap.forEach { (date, counts) ->
                val total = counts.sum()
                val totalFraction =
                    if (maxForWeek == 0) 0f
                    else (total.toFloat() / maxForWeek.toFloat())

                val categoryFractions: List<Float> =
                    if (total == 0) List(4) { 0f }
                    else counts.map { it.toFloat() / total.toFloat() }

                val dayLabel = date.dayOfWeek.name
                    .take(3)
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .height(barMaxHeight * totalFraction.coerceIn(0f, 1f))
                            .width(20.dp)
                            .background(
                                color = Color(0x11000000),
                                shape = RoundedCornerShape(3.dp)
                            )
                    ) {
                        if (total > 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                categoryFractions.forEachIndexed { index, f ->
                                    val segFraction = f.coerceAtLeast(0f)
                                    if (segFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(segFraction)
                                                .background(
                                                    colors[index],
                                                    shape = RoundedCornerShape(3.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/* ---------- LEGEND ---------- */

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

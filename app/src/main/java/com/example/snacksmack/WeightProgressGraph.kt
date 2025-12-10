package com.example.snacksmack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun WeightProgressGraph(weighIns: List<Pair<Long, Double>>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weight Progress",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (weighIns.isNotEmpty()) {
                LineGraph(weighIns)
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("You need at least one weigh-in to see a graph.", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun LineGraph(weighIns: List<Pair<Long, Double>>) {
    val textMeasurer = rememberTextMeasurer()
    var pan by remember { mutableFloatStateOf(0f) }

    val pointSpacing = 100.dp
    val pointSpacingPx = with(LocalDensity.current) { pointSpacing.toPx() }

    val scrollState = rememberScrollableState { delta ->
        pan += delta
        delta
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bodySmall = MaterialTheme.typography.bodySmall

    Canvas(modifier = Modifier
        .fillMaxSize()
        .scrollable(state = scrollState, orientation = Orientation.Horizontal)
    ) {
        val horizontalPadding = 32.dp.toPx()
        val bottomPadding = 40.dp.toPx()

        val contentWidth = pointSpacingPx * (weighIns.size - 1)
        val totalWidth = contentWidth + 2 * horizontalPadding

        pan = pan.coerceIn(-(totalWidth - size.width).coerceAtLeast(0f), 0f)

        translate(left = pan) {
            val (minWeight, maxWeight) = weighIns.map { it.second }.let { it.minOrNull()!! to it.maxOrNull()!! }
            // Add padding to y-axis to avoid drawing at the very top or bottom
            val yPadding = ((maxWeight - minWeight) * 0.2).takeIf { it > 0.0 } ?: 10.0
            val graphMinWeight = (minWeight - yPadding).coerceAtLeast(0.0)
            val graphMaxWeight = maxWeight + yPadding
            val graphHeight = size.height - bottomPadding

            val points = weighIns.mapIndexed { index, (_, weight) ->
                val x = horizontalPadding + index * pointSpacingPx
                val y = if (graphMaxWeight > graphMinWeight) {
                    // Y-axis is inverted in Canvas, so we subtract from height
                    graphHeight - (((weight - graphMinWeight) / (graphMaxWeight - graphMinWeight)) * graphHeight).toFloat()
                } else {
                    graphHeight / 2 // Center point if there is no weight range
                }
                Offset(x, y)
            }

            // Draw the line connecting points only if there are multiple weigh-ins
            if (points.size > 1) {
                val path = Path().apply {
                    points.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

            // Draw points and labels
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = primaryColor,
                    radius = 5.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = surfaceVariantColor,
                    radius = 3.dp.toPx(),
                    center = point
                )

                val weightText = weighIns[index].second.roundToInt().toString()
                val weightTextLayoutResult = textMeasurer.measure(
                    text = weightText,
                    style = bodySmall
                )
                drawText(
                    textLayoutResult = weightTextLayoutResult,
                    color = onSurfaceColor,
                    topLeft = Offset(point.x - weightTextLayoutResult.size.width / 2, point.y - 15.dp.toPx() - weightTextLayoutResult.size.height)
                )

                val date = Date(weighIns[index].first)
                val dateText = dateFormat.format(date)
                val dateTextLayoutResult = textMeasurer.measure(
                    text = dateText,
                    style = bodySmall
                )
                drawText(
                    textLayoutResult = dateTextLayoutResult,
                    color = onSurfaceVariantColor,
                    topLeft = Offset(point.x - dateTextLayoutResult.size.width / 2, graphHeight + 5.dp.toPx())
                )
            }
        }
    }
}

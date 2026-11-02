package com.niloy.focentra.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niloy.focentra.engine.BarChartItem

@Composable
fun StudyBarChart(
    items: List<BarChartItem>,
    modifier: Modifier = Modifier,
    chartHeight: Int = 180
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(chartHeight.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No study activity for this timeframe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "bar_chart_growth"
    )

    val maxVal = remember(items) {
        val maxStudy = items.maxOfOrNull { it.studyMinutes } ?: 60f
        val maxGoal = items.maxOfOrNull { it.goalMinutes } ?: 60f
        maxOf(maxStudy, maxGoal, 60f) * 1.15f
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val goalLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Tooltip if selected
        if (selectedIndex != null && selectedIndex!! < items.size) {
            val item = items[selectedIndex!!]
            val hrs = item.studyMinutes.toInt() / 60
            val mins = item.studyMinutes.toInt() % 60
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = "${item.label}: ${if (hrs > 0) "${hrs}h " else ""}${mins}m focused",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp)
                .pointerInput(items) {
                    detectTapGestures { offset ->
                        val barWidthWithSpacing = size.width / items.size
                        val clickedIndex = (offset.x / barWidthWithSpacing).toInt()
                        if (clickedIndex in items.indices) {
                            selectedIndex = if (selectedIndex == clickedIndex) null else clickedIndex
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height - 30.dp.toPx()
            val barCount = items.size
            val slotWidth = canvasWidth / barCount
            val barWidth = (slotWidth * 0.55f).coerceAtMost(36.dp.toPx())
            val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

            // Draw goal line if exists
            val goalVal = items.firstOrNull()?.goalMinutes ?: 0f
            if (goalVal > 0f) {
                val goalY = canvasHeight - (goalVal / maxVal * canvasHeight)
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, goalY),
                    end = Offset(canvasWidth, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
            }

            items.forEachIndexed { i, item ->
                val x = (i * slotWidth) + (slotWidth - barWidth) / 2
                val isSelected = selectedIndex == i

                // Background track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, canvasHeight),
                    cornerRadius = cornerRadius
                )

                // Active study bar (only if actual study time > 0)
                if (item.studyMinutes > 0f) {
                    val fullBarHeight = (item.studyMinutes / maxVal * canvasHeight).coerceAtLeast(8.dp.toPx())
                    val barHeight = fullBarHeight * animationProgress
                    val y = canvasHeight - barHeight

                    val brush = Brush.verticalGradient(
                        colors = if (isSelected) {
                            listOf(highlightColor, primaryColor)
                        } else {
                            listOf(primaryColor, secondaryColor)
                        },
                        startY = y,
                        endY = canvasHeight
                    )

                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEachIndexed { i, item ->
                val isSelected = selectedIndex == i
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

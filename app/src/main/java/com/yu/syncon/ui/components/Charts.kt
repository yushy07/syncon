package com.yu.syncon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.ui.theme.getCategoryColor

data class BarChartItem(
    val label: String,
    val valueMinutes: Long,
    val isHighlighted: Boolean = false
)

data class CategoryShare(
    val category: String,
    val minutes: Long,
    val percentage: Int
)

data class MiniBarItem(
    val label: String,
    val valueMinutes: Long,
    val isHighlighted: Boolean = false
)

@Composable
fun UsageBarChart(
    items: List<BarChartItem>,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 160
) {
    if (items.isEmpty()) return

    val maxVal = items.maxOfOrNull { it.valueMinutes }?.coerceAtLeast(60L) ?: 60L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeightDp.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEach { item ->
                val ratio = (item.valueMinutes.toFloat() / maxVal).coerceIn(0.06f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(20.dp)
                            .height((maxHeightDp * ratio).dp)
                    ) {
                        drawRoundRect(
                            color = if (item.isHighlighted) PrimaryIndigo else PrimaryIndigoLight,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = if (item.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            color = if (item.isHighlighted) PrimaryIndigo else TextSecondary
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MiniBarRow(
    items: List<MiniBarItem>,
    modifier: Modifier = Modifier
) {
    val maxVal = items.maxOfOrNull { it.valueMinutes }?.coerceAtLeast(30L) ?: 30L

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        items.take(7).forEach { item ->
            val ratio = (item.valueMinutes.toFloat() / maxVal).coerceIn(0.12f, 1f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height((48 * ratio).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (item.isHighlighted) PrimaryIndigo else PrimaryIndigoLight)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 10.sp,
                        color = if (item.isHighlighted) PrimaryIndigo else TextSecondary,
                        fontWeight = if (item.isHighlighted) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDonutChart(
    shares: List<CategoryShare>,
    totalTimeString: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(190.dp)) {
                val strokeWidth = 34.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val centerOffset = Offset(size.width / 2, size.height / 2)
                var startAngle = -90f

                if (shares.isEmpty()) {
                    drawCircle(
                        color = CardBorder,
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    shares.forEach { share ->
                        val sweep = (share.percentage / 100f) * 360f
                        drawArc(
                            color = getCategoryColor(share.category),
                            startAngle = startAngle,
                            sweepAngle = sweep.coerceAtLeast(2f),
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
            }

            // Center Duration Display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalTimeString,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Legend with colored dots & percentages
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shares.forEach { share ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(getCategoryColor(share.category))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${share.category} ${share.percentage}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

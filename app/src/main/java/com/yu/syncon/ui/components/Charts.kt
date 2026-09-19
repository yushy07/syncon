package com.yu.syncon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.ui.theme.AccentAmber
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight
import com.yu.syncon.ui.theme.AccentSage
import com.yu.syncon.ui.theme.AccentSageLight
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.CardSurfaceVariant
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.ui.theme.TextTertiary
import com.yu.syncon.ui.theme.getCategoryColor
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

/**
 * Gen-Z Category Emoji Mapper for modern playful labeling.
 */
fun getCategoryEmoji(category: String): String {
    return when (category.lowercase()) {
        "social media", "social" -> "💬"
        "entertainment" -> "🎬"
        "browser" -> "🌐"
        "communication" -> "📱"
        "productivity" -> "⚡"
        "gaming", "games" -> "🎮"
        "utilities", "tools" -> "🛠️"
        else -> "📦"
    }
}

// -----------------------------------------------------------------------------
// 1. GEN-Z ELECTRIC SPLINE AREA CHART
// -----------------------------------------------------------------------------
@Composable
fun UsageSplineAreaChart(
    items: List<BarChartItem>,
    modifier: Modifier = Modifier,
    dailyAverageMinutes: Long = 0L,
    heightDp: Int = 180
) {
    if (items.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxVal = items.maxOfOrNull { it.valueMinutes }?.coerceAtLeast(60L) ?: 60L
    val minVal = items.minOfOrNull { it.valueMinutes } ?: 0L

    var animTrigger by remember { mutableStateOf(0f) }
    LaunchedEffect(items) {
        animTrigger = 1f
    }
    val animProgress by animateFloatAsState(
        targetValue = animTrigger,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "spline_anim"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Gen-Z Floating Contextual Tooltip Callout
        AnimatedVisibility(
            visible = selectedIndex != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            val selected = selectedIndex?.let { items.getOrNull(it) }
            if (selected != null) {
                val h = selected.valueMinutes / 60
                val m = selected.valueMinutes % 60
                val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                val diffVsAvg = selected.valueMinutes - dailyAverageMinutes
                val isPeak = selected.valueMinutes == maxVal && maxVal > 0
                val isLowest = selected.valueMinutes == minVal

                val tagBadge = when {
                    isPeak -> "🔥 Peak Day"
                    isLowest -> "🌱 Cleanest Day"
                    diffVsAvg > 30 -> "⚡ Spiked (+${diffVsAvg}m vs avg)"
                    diffVsAvg < -30 -> "✨ Chill Vibe (${diffVsAvg}m vs avg)"
                    diffVsAvg >= 0 -> "• +${diffVsAvg}m vs avg"
                    else -> "• ${diffVsAvg}m vs avg"
                }

                val badgeBg = when {
                    isPeak -> AccentCoralLight
                    isLowest -> AccentSageLight
                    else -> PrimaryIndigoLight
                }
                val badgeColor = when {
                    isPeak -> AccentCoral
                    isLowest -> AccentSage
                    else -> PrimaryIndigo
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(badgeBg)
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${selected.label}: $timeStr  $tagBadge",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = badgeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas Area with Dual-Layer Glow & Scrubber Cursor
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items) {
                        detectTapGestures(
                            onTap = { offset ->
                                val count = items.size
                                if (count > 0) {
                                    val sidePad = 24.dp.toPx()
                                    val usableWidth = size.width - (sidePad * 2)
                                    val stepX = usableWidth / (count - 1).coerceAtLeast(1)
                                    val clampedX = (offset.x - sidePad).coerceIn(0f, usableWidth)
                                    val nearest = (clampedX / stepX).roundToInt().coerceIn(0, count - 1)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedIndex = if (selectedIndex == nearest) null else nearest
                                }
                            }
                        )
                    }
                    .pointerInput(items) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val count = items.size
                            if (count > 0) {
                                val sidePad = 24.dp.toPx()
                                val usableWidth = size.width - (sidePad * 2)
                                val stepX = usableWidth / (count - 1).coerceAtLeast(1)
                                val clampedX = (change.position.x - sidePad).coerceIn(0f, usableWidth)
                                val nearest = (clampedX / stepX).roundToInt().coerceIn(0, count - 1)
                                if (selectedIndex != nearest) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIndex = nearest
                                }
                            }
                        }
                    }
            ) {
                val sidePad = 24.dp.toPx()
                val topPad = 18.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val usableWidth = size.width - (sidePad * 2)
                val usableHeight = size.height - topPad - bottomPad
                val count = items.size
                val stepX = usableWidth / (count - 1).coerceAtLeast(1)

                // 1. Average Reference Dashed Baseline
                if (dailyAverageMinutes > 0 && maxVal > 0) {
                    val avgY = (topPad + usableHeight - (dailyAverageMinutes.toFloat() / maxVal) * usableHeight)
                        .coerceIn(topPad, topPad + usableHeight)
                    drawLine(
                        color = CardBorder,
                        start = Offset(sidePad, avgY),
                        end = Offset(size.width - sidePad, avgY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                }

                // Compute coordinate points with animation scale
                val points = items.mapIndexed { idx, item ->
                    val px = sidePad + idx * stepX
                    val ratio = (item.valueMinutes.toFloat() / maxVal).coerceIn(0f, 1f) * animProgress
                    val py = topPad + usableHeight - (ratio * usableHeight)
                    Offset(px, py)
                }

                if (points.isNotEmpty()) {
                    val strokePath = Path()
                    val fillPath = Path()

                    strokePath.moveTo(points.first().x, points.first().y)
                    fillPath.moveTo(points.first().x, size.height - bottomPad)
                    fillPath.lineTo(points.first().x, points.first().y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cp1X = (p0.x + p1.x) / 2f
                        val cp1Y = p0.y
                        val cp2X = (p0.x + p1.x) / 2f
                        val cp2Y = p1.y
                        strokePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p1.x, p1.y)
                        fillPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p1.x, p1.y)
                    }

                    fillPath.lineTo(points.last().x, size.height - bottomPad)
                    fillPath.close()

                    // Gradient Underfill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PrimaryIndigo.copy(alpha = 0.35f),
                                PrimaryIndigo.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = topPad,
                            endY = size.height - bottomPad
                        )
                    )

                    // Layer 1: Ambient Outer Glow Stroke
                    drawPath(
                        path = strokePath,
                        color = PrimaryIndigo.copy(alpha = 0.20f),
                        style = Stroke(
                            width = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // Layer 2: Core Crisp Stroke
                    drawPath(
                        path = strokePath,
                        color = PrimaryIndigo,
                        style = Stroke(
                            width = 2.8.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )

                    // 2. Vertical Cursor Line when Scrubbing
                    selectedIndex?.let { idx ->
                        if (idx in points.indices) {
                            val activePt = points[idx]
                            drawLine(
                                color = PrimaryIndigo.copy(alpha = 0.35f),
                                start = Offset(activePt.x, topPad),
                                end = Offset(activePt.x, size.height - bottomPad),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                            )
                        }
                    }

                    // 3. Points & Electric Radar Cursor Nodes
                    points.forEachIndexed { idx, pt ->
                        val isSelected = selectedIndex == idx
                        val item = items[idx]
                        val isPeak = item.valueMinutes == maxVal && maxVal > 0
                        val isLowest = item.valueMinutes == minVal

                        when {
                            isSelected -> {
                                // 3-ring Electric Radar Node
                                drawCircle(
                                    color = PrimaryIndigo.copy(alpha = 0.22f),
                                    radius = 13.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = PrimaryIndigo,
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                            }
                            isPeak -> {
                                // Peak Day Accent Node
                                drawCircle(
                                    color = AccentCoral.copy(alpha = 0.25f),
                                    radius = 8.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = AccentCoral,
                                    radius = 4.5.dp.toPx(),
                                    center = pt
                                )
                            }
                            isLowest -> {
                                // Chill Day Accent Node
                                drawCircle(
                                    color = AccentSage,
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                            }
                            item.isHighlighted -> {
                                drawCircle(
                                    color = PrimaryIndigo,
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                            }
                            else -> {
                                drawCircle(
                                    color = PrimaryIndigoLight,
                                    radius = 2.8.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = if (items.size > 14) (items.size / 6).coerceAtLeast(1) else 1
            items.forEachIndexed { idx, item ->
                val isSelected = selectedIndex == idx
                val shouldShow = (items.size <= 7) || (idx % step == 0) || (idx == items.size - 1) || isSelected
                if (shouldShow) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PrimaryIndigo else TextSecondary
                        ),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. GEN-Z CAPSULE PILLAR BAR CHART
// -----------------------------------------------------------------------------
@Composable
fun UsageBarChart(
    items: List<BarChartItem>,
    modifier: Modifier = Modifier,
    dailyAverageMinutes: Long = 0L,
    maxHeightDp: Int = 160
) {
    if (items.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxVal = items.maxOfOrNull { it.valueMinutes }?.coerceAtLeast(60L) ?: 60L
    val minVal = items.minOfOrNull { it.valueMinutes } ?: 0L

    var animTrigger by remember { mutableStateOf(0f) }
    LaunchedEffect(items) {
        animTrigger = 1f
    }
    val animProgress by animateFloatAsState(
        targetValue = animTrigger,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "bar_rise"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected Bar Tooltip Callout with Contextual Emoji Badges
        AnimatedVisibility(
            visible = selectedIndex != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            val selectedItem = selectedIndex?.let { items.getOrNull(it) }
            if (selectedItem != null) {
                val h = selectedItem.valueMinutes / 60
                val m = selectedItem.valueMinutes % 60
                val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                val diffVsAvg = selectedItem.valueMinutes - dailyAverageMinutes
                val isPeak = selectedItem.valueMinutes == maxVal && maxVal > 0
                val isLowest = selectedItem.valueMinutes == minVal

                val tagBadge = when {
                    isPeak -> "🔥 Peak Day"
                    isLowest -> "🌱 Cleanest Day"
                    diffVsAvg > 30 -> "⚡ Spiked (+${diffVsAvg}m)"
                    diffVsAvg < -30 -> "✨ Chill Vibe (${diffVsAvg}m)"
                    diffVsAvg >= 0 -> "• +${diffVsAvg}m vs avg"
                    else -> "• ${diffVsAvg}m vs avg"
                }

                val badgeBg = when {
                    isPeak -> AccentCoralLight
                    isLowest -> AccentSageLight
                    else -> PrimaryIndigoLight
                }
                val badgeColor = when {
                    isPeak -> AccentCoral
                    isLowest -> AccentSage
                    else -> PrimaryIndigo
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(badgeBg)
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${selectedItem.label}: $timeStr  $tagBadge",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = badgeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // High-Performance Capsule Track Canvas with Fluid Scrubbing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeightDp.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items) {
                        detectTapGestures(
                            onTap = { offset ->
                                val count = items.size
                                if (count > 0) {
                                    val sidePad = 16.dp.toPx()
                                    val usableWidth = size.width - (sidePad * 2)
                                    val barSlotWidth = usableWidth / count
                                    val clampedX = (offset.x - sidePad).coerceIn(0f, usableWidth)
                                    val clickedIdx = (clampedX / barSlotWidth).toInt().coerceIn(0, count - 1)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedIndex = if (selectedIndex == clickedIdx) null else clickedIdx
                                }
                            }
                        )
                    }
                    .pointerInput(items) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val count = items.size
                            if (count > 0) {
                                val sidePad = 16.dp.toPx()
                                val usableWidth = size.width - (sidePad * 2)
                                val barSlotWidth = usableWidth / count
                                val clampedX = (change.position.x - sidePad).coerceIn(0f, usableWidth)
                                val draggedIdx = (clampedX / barSlotWidth).toInt().coerceIn(0, count - 1)
                                if (selectedIndex != draggedIdx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIndex = draggedIdx
                                }
                            }
                        }
                    }
            ) {
                val sidePad = 16.dp.toPx()
                val topPad = 14.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val usableWidth = size.width - (sidePad * 2)
                val usableHeight = size.height - topPad - bottomPad
                val count = items.size
                val slotWidth = usableWidth / count

                // Dynamic bar track width based on item density
                val barTrackWidth = (slotWidth * 0.52f).coerceIn(4.dp.toPx(), 22.dp.toPx())
                val cornerRadiusPx = barTrackWidth / 2f

                // Draw Average Reference Dashed Line
                if (dailyAverageMinutes > 0 && maxVal > 0) {
                    val avgY = (topPad + usableHeight - (dailyAverageMinutes.toFloat() / maxVal) * usableHeight)
                        .coerceIn(topPad, topPad + usableHeight)
                    drawLine(
                        color = CardBorder,
                        start = Offset(sidePad, avgY),
                        end = Offset(size.width - sidePad, avgY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }

                items.forEachIndexed { idx, item ->
                    val isSelected = selectedIndex == idx
                    val isAnySelected = selectedIndex != null
                    val isPeak = item.valueMinutes == maxVal && maxVal > 0
                    val centerX = sidePad + (idx + 0.5f) * slotWidth
                    val left = centerX - (barTrackWidth / 2f)

                    // 1. Sleek Background Capsule Track
                    drawRoundRect(
                        color = CardSurfaceVariant,
                        topLeft = Offset(left, topPad),
                        size = Size(barTrackWidth, usableHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )

                    // 2. Proportional Filled Pillar with Vertical Gradient
                    val fillRatio = (item.valueMinutes.toFloat() / maxVal).coerceIn(0.04f, 1f) * animProgress
                    val fillHeight = (fillRatio * usableHeight).coerceAtLeast(barTrackWidth)
                    val fillTop = topPad + usableHeight - fillHeight

                    val barAlpha = if (isAnySelected && !isSelected) 0.40f else 1f

                    val topColor = when {
                        isSelected -> PrimaryIndigo
                        isPeak -> AccentCoral
                        item.isHighlighted -> PrimaryIndigo
                        else -> PrimaryIndigo
                    }
                    val bottomColor = when {
                        isSelected -> PrimaryIndigo.copy(alpha = 0.75f)
                        isPeak -> AccentCoral.copy(alpha = 0.65f)
                        else -> PrimaryIndigoLight
                    }

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                topColor.copy(alpha = barAlpha),
                                bottomColor.copy(alpha = barAlpha)
                            ),
                            startY = fillTop,
                            endY = topPad + usableHeight
                        ),
                        topLeft = Offset(left, fillTop),
                        size = Size(barTrackWidth, fillHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )

                    // Selected Bar Halo Indicator
                    if (isSelected) {
                        drawRoundRect(
                            color = PrimaryIndigo.copy(alpha = 0.20f),
                            topLeft = Offset(left - 3.dp.toPx(), fillTop - 3.dp.toPx()),
                            size = Size(barTrackWidth + 6.dp.toPx(), fillHeight + 6.dp.toPx()),
                            cornerRadius = CornerRadius(cornerRadiusPx + 3.dp.toPx(), cornerRadiusPx + 3.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Peak Day Accent Dot on Top
                    if (isPeak) {
                        drawCircle(
                            color = AccentCoral,
                            radius = (barTrackWidth * 0.20f).coerceAtLeast(2.5.dp.toPx()),
                            center = Offset(centerX, fillTop - 5.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // X-Axis Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = if (items.size > 14) (items.size / 6).coerceAtLeast(1) else 1
            items.forEachIndexed { idx, item ->
                val isSelected = selectedIndex == idx
                val shouldShow = (items.size <= 7) || (idx % step == 0) || (idx == items.size - 1) || isSelected
                if (shouldShow) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PrimaryIndigo else TextSecondary
                        ),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. GEN-Z INTERACTIVE MODULAR DONUT (PIE) CHART
// -----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDonutChart(
    shares: List<CategoryShare>,
    totalTimeString: String,
    modifier: Modifier = Modifier,
    selectedCategory: String? = null,
    onCategorySelect: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var internalSelectedCategory by remember { mutableStateOf<String?>(null) }
    val activeCategory = selectedCategory ?: internalSelectedCategory

    var animTrigger by remember { mutableStateOf(0f) }
    LaunchedEffect(shares) {
        animTrigger = 1f
    }
    val animProgress by animateFloatAsState(
        targetValue = animTrigger,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "donut_sweep"
    )

    val selectedShare = shares.find { it.category.equals(activeCategory, ignoreCase = true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(210.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(200.dp)
                    .pointerInput(shares) {
                        detectTapGestures(
                            onTap = { offset ->
                                val strokeWidth = 32.dp.toPx()
                                val minDim = Math.min(size.width, size.height).toFloat()
                                val radius = (minDim - strokeWidth) / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val dist = sqrt(dx * dx + dy * dy)

                                // Check if tapped inside donut ring
                                if (dist in (radius - strokeWidth / 1.5f)..(radius + strokeWidth / 1.5f)) {
                                    var touchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    // Normalize: 12 o'clock (-90 deg) is 0 deg
                                    touchAngle = (touchAngle + 90f + 360f) % 360f

                                    var cumulative = 0f
                                    var tappedShare: CategoryShare? = null
                                    for (share in shares) {
                                        val sweep = (share.percentage / 100f) * 360f
                                        if (touchAngle in cumulative..(cumulative + sweep)) {
                                            tappedShare = share
                                            break
                                        }
                                        cumulative += sweep
                                    }

                                    if (tappedShare != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val newSelection = if (activeCategory.equals(tappedShare.category, ignoreCase = true)) null else tappedShare.category
                                        internalSelectedCategory = newSelection
                                        onCategorySelect?.invoke(newSelection ?: "")
                                    }
                                } else if (dist < radius - strokeWidth) {
                                    // Tapped center hole -> reset inspection
                                    if (activeCategory != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        internalSelectedCategory = null
                                        onCategorySelect?.invoke("")
                                    }
                                }
                            }
                        )
                    }
            ) {
                val baseStrokeWidth = 30.dp.toPx()
                val selectedStrokeWidth = 38.dp.toPx()
                val radius = (size.minDimension - selectedStrokeWidth) / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                var startAngle = -90f

                if (shares.isEmpty()) {
                    drawCircle(
                        color = CardBorder,
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = baseStrokeWidth)
                    )
                } else {
                    val gapAngle = if (shares.size > 1) 3f else 0f

                    shares.forEach { share ->
                        val isSelected = activeCategory != null && activeCategory.equals(share.category, ignoreCase = true)
                        val isAnySelected = activeCategory != null
                        val sweep = ((share.percentage / 100f) * 360f * animProgress - gapAngle).coerceAtLeast(1f)

                        val currentStroke = if (isSelected) selectedStrokeWidth else baseStrokeWidth
                        val currentAlpha = if (isAnySelected && !isSelected) 0.35f else 1f

                        drawArc(
                            color = getCategoryColor(share.category).copy(alpha = currentAlpha),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = currentStroke, cap = StrokeCap.Round)
                        )

                        // Ambient Glow on selected arc
                        if (isSelected) {
                            drawArc(
                                color = getCategoryColor(share.category).copy(alpha = 0.25f),
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = currentStroke + 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        startAngle += (share.percentage / 100f) * 360f * animProgress
                    }
                }
            }

            // Interactive Center Hole with Smooth Crossfade
            Crossfade(
                targetState = selectedShare,
                animationSpec = tween(220),
                label = "center_hole_crossfade"
            ) { share ->
                if (share == null) {
                    // Default Total State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (activeCategory != null) {
                                internalSelectedCategory = null
                                onCategorySelect?.invoke("")
                            }
                        }
                    ) {
                        Text(
                            text = totalTimeString,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = "Total Screentime",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap slice to inspect",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextTertiary
                        )
                    }
                } else {
                    // Active Category Inspection State
                    val h = share.minutes / 60
                    val m = share.minutes % 60
                    val durStr = if (h > 0) "${h}h ${m}m" else "${m}m"

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            internalSelectedCategory = null
                            onCategorySelect?.invoke("")
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(getCategoryColor(share.category).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = getCategoryEmoji(share.category), fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = share.category,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "$durStr • ${share.percentage}%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = getCategoryColor(share.category)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap center to reset",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Gen-Z Interactive Pill Legend Chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            shares.forEach { share ->
                val isSelected = activeCategory != null && activeCategory.equals(share.category, ignoreCase = true)
                val catColor = getCategoryColor(share.category)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (isSelected) catColor.copy(alpha = 0.14f) else CardSurfaceVariant)
                        .border(
                            BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) catColor else CardBorder
                            ),
                            shape = RoundedCornerShape(50.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val newSelection = if (isSelected) null else share.category
                            internalSelectedCategory = newSelection
                            onCategorySelect?.invoke(newSelection ?: "")
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = getCategoryEmoji(share.category), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${share.category} ${share.percentage}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) TextPrimary else TextSecondary
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4. GEN-Z INTERACTIVE SEGMENTED CAPSULE BAR
// -----------------------------------------------------------------------------
@Composable
fun SegmentedCapsuleBar(
    shares: List<CategoryShare>,
    modifier: Modifier = Modifier,
    heightDp: Int = 14,
    selectedCategory: String? = null,
    onCategorySelect: ((String) -> Unit)? = null
) {
    if (shares.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val nonZeroShares = shares.filter { it.percentage > 0 }
    if (nonZeroShares.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(CardBorder)
    ) {
        nonZeroShares.forEachIndexed { index, share ->
            val isSelected = selectedCategory != null && selectedCategory.equals(share.category, ignoreCase = true)
            val isAnySelected = selectedCategory != null
            val alpha = if (isAnySelected && !isSelected) 0.35f else 1f

            // Ensure minimum visual presence (minimum 3% equivalent) so micro-slices are clickable
            val weightVal = share.percentage.coerceAtLeast(3).toFloat()

            Box(
                modifier = Modifier
                    .weight(weightVal)
                    .fillMaxHeight()
                    .background(getCategoryColor(share.category).copy(alpha = alpha))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val newSelection = if (isSelected) "" else share.category
                        onCategorySelect?.invoke(newSelection)
                    }
            )

            if (index < nonZeroShares.size - 1) {
                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. GEN-Z MINI BAR ROW (COMPACT STATS & APP DETAILS)
// -----------------------------------------------------------------------------
@Composable
fun MiniBarRow(
    items: List<MiniBarItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val maxVal = items.maxOfOrNull { it.valueMinutes }?.coerceAtLeast(30L) ?: 30L
    val haptic = LocalHapticFeedback.current
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    var animTrigger by remember { mutableStateOf(0f) }
    LaunchedEffect(items) {
        animTrigger = 1f
    }
    val animProgress by animateFloatAsState(
        targetValue = animTrigger,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "mini_bar_anim"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = selectedIndex != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            val selected = selectedIndex?.let { items.getOrNull(it) }
            if (selected != null) {
                val h = selected.valueMinutes / 60
                val m = selected.valueMinutes % 60
                val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                val isPeak = selected.valueMinutes == maxVal && maxVal > 0

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (isPeak) AccentCoralLight else PrimaryIndigoLight)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${selected.label}: $timeStr ${if (isPeak) "🔥" else ""}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isPeak) AccentCoral else PrimaryIndigo,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            items.take(7).forEachIndexed { idx, item ->
                val ratio = (item.valueMinutes.toFloat() / maxVal).coerceIn(0.08f, 1f) * animProgress
                val isSelected = selectedIndex == idx
                val isHighlighted = isSelected || (selectedIndex == null && item.isHighlighted)
                val isPeak = item.valueMinutes == maxVal && maxVal > 0

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedIndex = if (selectedIndex == idx) null else idx
                        }
                        .padding(horizontal = 2.dp)
                ) {
                    // Capsule Track Container
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CardSurfaceVariant),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Dynamic Gradient Fill
                        val barColor = when {
                            isSelected -> PrimaryIndigo
                            isPeak -> AccentCoral
                            isHighlighted -> PrimaryIndigo
                            else -> PrimaryIndigoLight
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((52 * ratio).dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            barColor,
                                            barColor.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            color = if (isHighlighted) PrimaryIndigo else TextSecondary,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

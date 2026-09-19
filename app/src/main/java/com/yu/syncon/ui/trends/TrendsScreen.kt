package com.yu.syncon.ui.trends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.components.AppIcon
import com.yu.syncon.ui.components.BarChartItem
import com.yu.syncon.ui.components.CategoryDonutChart
import com.yu.syncon.ui.components.CategoryShare
import com.yu.syncon.ui.components.SegmentedCapsuleBar
import com.yu.syncon.ui.components.TaglineItalicText
import com.yu.syncon.ui.components.UsageBarChart
import com.yu.syncon.ui.components.UsageSplineAreaChart
import com.yu.syncon.ui.theme.AccentAmber
import com.yu.syncon.ui.theme.AccentAmberLight
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight
import com.yu.syncon.ui.theme.AccentSage
import com.yu.syncon.ui.theme.AccentSageLight
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.CardShape
import com.yu.syncon.ui.theme.CardSurface
import com.yu.syncon.ui.theme.CardSurfaceVariant
import com.yu.syncon.ui.theme.FilterChipShape
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.ui.theme.getCategoryColor
import com.yu.syncon.util.UsageDayCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class TrendsViewMode {
    CATEGORIES,
    APPS
}

enum class TrendsChartType {
    CURVE,
    BARS
}

@Composable
fun TrendsScreen(repository: UsageRepository) {
    val haptic = LocalHapticFeedback.current
    var timeframeDays by remember { mutableIntStateOf(7) } // 7, 14, or 30
    var chartType by remember { mutableStateOf(TrendsChartType.CURVE) }
    var viewMode by remember { mutableStateOf(TrendsViewMode.CATEGORIES) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    var dailyTotals by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var barChartItems by remember { mutableStateOf<List<BarChartItem>>(emptyList()) }
    var topApps by remember { mutableStateOf<List<Pair<AppInfo, Long>>>(emptyList()) }
    var categoryShares by remember { mutableStateOf<List<CategoryShare>>(emptyList()) }
    var persona by remember { mutableStateOf<UsageRepository.ScreentimePersona?>(null) }

    LaunchedEffect(timeframeDays) {
        val dates = UsageDayCalculator.getRecentUsageDates(timeframeDays)
        val usageMap = repository.getUsageForDates(dates)
        dailyTotals = usageMap

        val todayStr = UsageDayCalculator.getTodayUsageDate()

        // Generate chart data items based on selected timeframe
        if (timeframeDays == 7) {
            barChartItems = dates.map { d ->
                val date = LocalDate.parse(d, DateTimeFormatter.ISO_LOCAL_DATE)
                val label = date.dayOfWeek.name.take(1)
                BarChartItem(
                    label = label,
                    valueMinutes = usageMap[d] ?: 0L,
                    isHighlighted = d == todayStr
                )
            }
        } else if (timeframeDays == 14) {
            barChartItems = dates.map { d ->
                val date = LocalDate.parse(d, DateTimeFormatter.ISO_LOCAL_DATE)
                val label = "${date.dayOfMonth}"
                BarChartItem(
                    label = label,
                    valueMinutes = usageMap[d] ?: 0L,
                    isHighlighted = d == todayStr
                )
            }
        } else {
            // Condense 30 days into 6 5-day intervals
            val chunkSize = 5
            barChartItems = dates.chunked(chunkSize).mapIndexed { _, chunk ->
                val sum = chunk.sumOf { usageMap[it] ?: 0L }
                val startDay = LocalDate.parse(chunk.first(), DateTimeFormatter.ISO_LOCAL_DATE).dayOfMonth
                val endDay = LocalDate.parse(chunk.last(), DateTimeFormatter.ISO_LOCAL_DATE).dayOfMonth
                BarChartItem(
                    label = "$startDay-$endDay",
                    valueMinutes = sum,
                    isHighlighted = chunk.contains(todayStr)
                )
            }
        }

        val startDate = dates.firstOrNull() ?: ""
        val endDate = dates.lastOrNull() ?: ""
        val apps = repository.getTopAppsBetweenDates(startDate, endDate)
        topApps = apps

        val totalMins = usageMap.values.sum().coerceAtLeast(1L)
        val catMap = mutableMapOf<String, Long>()
        for ((app, mins) in apps) {
            catMap[app.category] = (catMap[app.category] ?: 0L) + mins
        }
        categoryShares = catMap.map { (cat, mins) ->
            CategoryShare(
                category = cat,
                minutes = mins,
                percentage = ((mins.toFloat() / totalMins) * 100).toInt()
            )
        }.sortedByDescending { it.minutes }

        persona = repository.getTrendsInsights(dates)
    }

    val totalMinutes = dailyTotals.values.sum()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val totalTimeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // Section Header + 7D / 14D / 30D Timeframe Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trends",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier
                        .clip(FilterChipShape)
                        .background(CardSurfaceVariant)
                        .padding(3.dp)
                ) {
                    TrendsTimeframeChip(
                        text = "7D",
                        selected = timeframeDays == 7,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            timeframeDays = 7
                        }
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    TrendsTimeframeChip(
                        text = "14D",
                        selected = timeframeDays == 14,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            timeframeDays = 14
                        }
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    TrendsTimeframeChip(
                        text = "30D",
                        selected = timeframeDays == 30,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            timeframeDays = 30
                        }
                    )
                }
            }
        }

        // Empty State when no usage data is recorded yet
        if (totalMinutes == 0L) {
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PrimaryIndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Not enough data yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Use your phone as usual and your trends and digital persona will build over time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        TaglineItalicText(text = "\"Track. Limit. Focus. Live Better.\"")
                    }
                }
            }
        } else {
            // Gen-Z Feature 1: "Screentime Persona & Vibe" Hero Card
            persona?.let { p ->
                item {
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryIndigoLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = p.emoji,
                                            fontSize = 22.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "The ${p.title}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = p.tagline,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Gen-Z Feature 2: Touch Grass & Clean Days Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(AccentSageLight)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "🌱 ${p.touchGrassRatioPercent}% Unplugged",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentSage
                                    )
                                }

                                if (p.cleanDaysCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50.dp))
                                            .background(AccentAmberLight)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "✨ ${p.cleanDaysCount} Clean Days",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentAmber
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Daily Average & Delta Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "Daily Average",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    val avgH = p.dailyAverageMinutes / 60
                                    val avgM = p.dailyAverageMinutes % 60
                                    val avgStr = if (avgH > 0) "${avgH}h ${avgM}m" else "${avgM}m"
                                    Text(
                                        text = avgStr,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }

                                if (p.deltaPercentVsPreviousPeriod != 0) {
                                    val isUp = p.deltaPercentVsPreviousPeriod > 0
                                    val deltaAbs = Math.abs(p.deltaPercentVsPreviousPeriod)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50.dp))
                                            .background(if (isUp) AccentCoralLight else AccentSageLight)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isUp) "↑ +$deltaAbs% vs prev" else "↓ -$deltaAbs% vs prev",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isUp) AccentCoral else AccentSage
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Gen-Z Feature 3: Interactive Smooth Spline & Column Graph with Switcher
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Screen Time",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = totalTimeStr,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            // Chart Type Toggle [Curve] / [Bars]
                            Row(
                                modifier = Modifier
                                    .clip(FilterChipShape)
                                    .background(CardSurfaceVariant)
                                    .padding(3.dp)
                            ) {
                                TrendsTimeframeChip(
                                    text = "Curve",
                                    selected = chartType == TrendsChartType.CURVE,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        chartType = TrendsChartType.CURVE
                                    }
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                TrendsTimeframeChip(
                                    text = "Bars",
                                    selected = chartType == TrendsChartType.BARS,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        chartType = TrendsChartType.BARS
                                    }
                                )
                            }
                        }

                        // Peak Day & Best Day Highlight Chips
                        persona?.let { p ->
                            if (p.peakDayName != null || p.lowestDayName != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (p.peakDayName != null && p.peakDayMinutes > 0) {
                                        val peakH = p.peakDayMinutes / 60
                                        val peakM = p.peakDayMinutes % 60
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AccentCoralLight)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Peak: ${p.peakDayName} (${peakH}h ${peakM}m)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = AccentCoral
                                            )
                                        }
                                    }

                                    if (p.lowestDayName != null && p.lowestDayMinutes > 0) {
                                        val lowH = p.lowestDayMinutes / 60
                                        val lowM = p.lowestDayMinutes % 60
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AccentSageLight)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Best: ${p.lowestDayName} (${lowH}h ${lowM}m)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = AccentSage
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val avgMins = persona?.dailyAverageMinutes ?: 0L

                        if (chartType == TrendsChartType.CURVE) {
                            UsageSplineAreaChart(
                                items = barChartItems,
                                dailyAverageMinutes = avgMins,
                                heightDp = 175
                            )
                        } else {
                            UsageBarChart(
                                items = barChartItems,
                                dailyAverageMinutes = avgMins,
                                maxHeightDp = 150
                            )
                        }
                    }
                }
            }

            // Gen-Z Feature 4: "Unhinged Hours / Doomscroll Alert" Insight Card
            persona?.let { p ->
                val isLateNight = p.peakWindowText.contains("Late Night", ignoreCase = true) ||
                                  p.peakWindowText.contains("Doomscroll", ignoreCase = true)

                item {
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLateNight) AccentCoralLight else AccentAmberLight
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isLateNight) AccentCoral.copy(alpha = 0.35f) else AccentAmber.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isLateNight) AccentCoral.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isLateNight) Icons.Default.NightsStay else Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = if (isLateNight) AccentCoral else AccentAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isLateNight) "Doomscroll Alert" else "Peak Activity Window",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLateNight) AccentCoral else AccentAmber
                                    )
                                    Text(
                                        text = p.peakWindowText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "${p.peakWindowSharePercent}% of your screen time is concentrated in this window. ${if (isLateNight) "Revenge bedtime scrolling reduces sleep recovery. Try setting a 10 PM limit." else "Consider spacing out your high-intensity sessions with quick offline breaks."}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Category vs App Breakdown Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (viewMode == TrendsViewMode.CATEGORIES) "Category Breakdown" else "App Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier
                            .clip(FilterChipShape)
                            .background(CardSurfaceVariant)
                            .padding(3.dp)
                    ) {
                        TrendsTimeframeChip(
                            text = "Categories",
                            selected = viewMode == TrendsViewMode.CATEGORIES,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewMode = TrendsViewMode.CATEGORIES
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TrendsTimeframeChip(
                            text = "Apps",
                            selected = viewMode == TrendsViewMode.APPS,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewMode = TrendsViewMode.APPS
                            }
                        )
                    }
                }
            }

            // Category View with Segmented Capsule Bar & Donut Chart
            if (viewMode == TrendsViewMode.CATEGORIES) {
                item {
                    Card(
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Gen-Z Feature 5: Segmented Proportional Capsule Bar with Two-Way Selection
                            SegmentedCapsuleBar(
                                shares = categoryShares,
                                heightDp = 14,
                                selectedCategory = selectedCategory,
                                onCategorySelect = { cat ->
                                    selectedCategory = if (cat.isEmpty() || selectedCategory == cat) null else cat
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            CategoryDonutChart(
                                shares = categoryShares,
                                totalTimeString = totalTimeStr,
                                selectedCategory = selectedCategory,
                                onCategorySelect = { cat ->
                                    selectedCategory = if (cat.isEmpty() || selectedCategory == cat) null else cat
                                }
                            )
                        }
                    }
                }
            } else {
                // App Leaderboard View with Gen-Z Rank Badges
                if (topApps.isEmpty()) {
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No app usage recorded yet for this period.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    val maxMinutes = topApps.firstOrNull()?.second ?: 1L
                    itemsIndexed(topApps, key = { _, item -> item.first.packageName }) { index, (app, mins) ->
                        val color = getCategoryColor(app.category)
                        val appHours = mins / 60
                        val appMins = mins % 60
                        val appTimeStr = if (appHours > 0) "${appHours}h ${appMins}m" else "${appMins}m"
                        val rank = index + 1

                        val rankBadgeBg = when (rank) {
                            1 -> AccentAmberLight
                            2 -> PrimaryIndigoLight
                            3 -> AccentCoralLight
                            else -> CardSurfaceVariant
                        }
                        val rankBadgeColor = when (rank) {
                            1 -> AccentAmber
                            2 -> PrimaryIndigo
                            3 -> AccentCoral
                            else -> TextSecondary
                        }

                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Rank indicator badge
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(rankBadgeBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#$rank",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = rankBadgeColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        AppIcon(
                                            packageName = app.packageName,
                                            appName = app.appName,
                                            category = app.category,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = app.category,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = appTimeStr,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                val progress = (mins.toFloat() / maxMinutes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = color,
                                    trackColor = CardSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TrendsTimeframeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(FilterChipShape)
            .background(if (selected) PrimaryIndigo else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.HourglassEmpty
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
import com.yu.syncon.ui.components.TaglineItalicText
import com.yu.syncon.ui.components.UsageBarChart
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight
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

@Composable
fun TrendsScreen(repository: UsageRepository) {
    var timeframeDays by remember { mutableIntStateOf(7) } // 7 or 30
    var viewMode by remember { mutableStateOf(TrendsViewMode.CATEGORIES) }

    var dailyTotals by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var barChartItems by remember { mutableStateOf<List<BarChartItem>>(emptyList()) }
    var topApps by remember { mutableStateOf<List<Pair<AppInfo, Long>>>(emptyList()) }
    var categoryShares by remember { mutableStateOf<List<CategoryShare>>(emptyList()) }

    LaunchedEffect(timeframeDays) {
        val dates = UsageDayCalculator.getRecentUsageDates(timeframeDays)
        val usageMap = repository.getUsageForDates(dates)
        dailyTotals = usageMap

        val todayStr = UsageDayCalculator.getTodayUsageDate()

        // Generate Bar items
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
        } else {
            // Condense 30 days into 6 buckets or representative days
            val chunkSize = 5
            barChartItems = dates.chunked(chunkSize).mapIndexed { idx, chunk ->
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
            // Section Header + 7D/30D Toggle
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

                // 7 Days / 30 Days toggle
                Row(
                    modifier = Modifier
                        .clip(FilterChipShape)
                        .background(CardSurfaceVariant)
                        .padding(3.dp)
                ) {
                    TrendsTimeframeChip(
                        text = "7 Days",
                        selected = timeframeDays == 7,
                        onClick = { timeframeDays = 7 }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TrendsTimeframeChip(
                        text = "30 Days",
                        selected = timeframeDays == 30,
                        onClick = { timeframeDays = 30 }
                    )
                }
            }
        }

        // Screen 25: No Data State
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
                            text = "Use your phone as usual and your trends will build over time.",
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
            // Screens 21 & 22: Hero Trends Card with Bar Chart
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
                                    text = if (timeframeDays == 7) "Past 7 Days" else "Past 30 Days",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = totalTimeStr,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(PrimaryIndigoLight)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (timeframeDays == 7) "↑ 21% vs last wk" else "↑ 12% vs last mo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryIndigo,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        UsageBarChart(
                            items = barChartItems,
                            maxHeightDp = 130
                        )
                    }
                }
            }

            // Screens 23 & 24: Sub-view Toggle [Categories] / [Apps]
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
                            onClick = { viewMode = TrendsViewMode.CATEGORIES }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TrendsTimeframeChip(
                            text = "Apps",
                            selected = viewMode == TrendsViewMode.APPS,
                            onClick = { viewMode = TrendsViewMode.APPS }
                        )
                    }
                }
            }

            // Screen 23: Category Donut Chart
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
                            CategoryDonutChart(
                                shares = categoryShares,
                                totalTimeString = totalTimeStr
                            )
                        }
                    }
                }
            } else {
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
                    items(topApps, key = { it.first.packageName }) { (app, mins) ->
                        val color = getCategoryColor(app.category)
                        val appHours = mins / 60
                        val appMins = mins % 60
                        val appTimeStr = if (appHours > 0) "${appHours}h ${appMins}m" else "${appMins}m"

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

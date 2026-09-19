package com.yu.syncon.ui.dashboard

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
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.DailyUsage
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.components.AppListRowItem
import com.yu.syncon.ui.components.MiniBarItem
import com.yu.syncon.ui.components.MiniBarRow
import com.yu.syncon.ui.components.TaglineItalicText
import com.yu.syncon.ui.components.WarningAlertBanner
import com.yu.syncon.ui.theme.AccentAmber
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

enum class DashboardViewMode {
    ALL_APPS,
    CATEGORIES
}

@Composable
fun DashboardScreen(
    repository: UsageRepository,
    onAppClick: (String) -> Unit,
    onTrackingStatusClick: () -> Unit = {}
) {
    val totalMinutes by repository.getTodayTotalMinutesFlow().collectAsState(initial = 0L)
    val yesterdayMinutes by repository.getYesterdayTotalMinutesFlow().collectAsState(initial = 0L)
    val todayUsages by repository.getTodayUsageFlow().collectAsState(initial = emptyList())
    val allApps by repository.getAllAppsFlow().collectAsState(initial = emptyList())
    val activeLimits by repository.getAllLimitSettingsFlow().collectAsState(initial = emptyList())

    val appMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val limitsMap = remember(activeLimits) { activeLimits.associateBy { it.packageName } }

    var viewMode by remember { mutableStateOf(DashboardViewMode.ALL_APPS) }
    var hourlyBars by remember { mutableStateOf<List<com.yu.syncon.ui.components.BarChartItem>>(emptyList()) }

    // Screen 9: Load Today's 6 4-hour intervals (4AM, 8AM, 12PM, 4PM, 8PM, 12AM)
    LaunchedEffect(totalMinutes) {
        hourlyBars = repository.getTodayHourlyUsage()
    }

    val cleanDayStreak by produceState(initialValue = 0) {
        value = repository.calculateCleanDayStreak()
    }

    val categoryLimits by produceState<Map<String, UsageRepository.CategoryLimitSetting>>(initialValue = emptyMap(), key1 = totalMinutes) {
        value = repository.getAllCategoryLimits().associateBy { it.category }
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val positiveUsages = todayUsages.filter { it.durationMinutes > 0 }.sortedByDescending { it.durationMinutes }
    val todayMins = totalMinutes ?: 0L
    val yestMins = yesterdayMinutes ?: 0L

    // Screen 11: Identify approaching limit apps (within 80% of limit, or <= 5 min left)
    val approachingApps = positiveUsages.mapNotNull { usage ->
        val setting = limitsMap[usage.packageName]
        if (setting != null && setting.isEnabled && setting.dailyLimitMinutes != null) {
            val limit = setting.dailyLimitMinutes
            val used = usage.durationMinutes
            val remaining = limit - used
            if ((used in (limit * 4 / 5)..limit) || remaining in 1..5) {
                val app = appMap[usage.packageName] ?: AppInfo(usage.packageName, usage.packageName, "Other")
                Triple(app, used, limit)
            } else null
        } else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // Section Header + Tracking Active Status Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "4:00 AM – 4:00 AM",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                // Screen 41 Link: Status Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(AccentSageLight)
                        .clickable(onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onTrackingStatusClick()
                        })
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentSage)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tracking active",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentSage,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Screen 11: Dedicated Approaching Limit Card
        if (approachingApps.isNotEmpty()) {
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = AccentCoralLight),
                    border = BorderStroke(1.dp, AccentCoral.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(AccentCoral.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = AccentCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (approachingApps.size == 1) {
                                    "You're close to your limit on 1 app"
                                } else {
                                    "You're close to your limit on ${approachingApps.size} apps"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentCoral
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        approachingApps.forEach { (app, used, limit) ->
                            val remaining = (limit - used).coerceAtLeast(0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAppClick(app.packageName) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.yu.syncon.ui.components.AppIcon(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    category = app.category,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$used / ${limit}m",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCoral
                                    )
                                    Text(
                                        text = "$remaining min left",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentCoral
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hero Today Card (Screens 8 & 9)
        item {
            TodayHeroCard(
                todayMinutes = todayMins,
                yesterdayMinutes = yestMins,
                cleanDayStreak = cleanDayStreak,
                hourlyBars = hourlyBars
            )
        }

        // Screen 8: Empty State when 0 usage
        if (positiveUsages.isEmpty()) {
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
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PrimaryIndigoLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Your usage will appear here.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Use your phone as usual and we'll start tracking in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        TaglineItalicText(text = "\"Small changes. A brighter tomorrow.\"")
                    }
                }
            }
        } else {
            // Screen 10: Toggle Chips [All Apps] / [Categories]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (viewMode == DashboardViewMode.ALL_APPS) "Most Used Apps" else "Usage by Category",
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
                        FilterToggleChip(
                            text = "All Apps",
                            selected = viewMode == DashboardViewMode.ALL_APPS,
                            onClick = { viewMode = DashboardViewMode.ALL_APPS }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterToggleChip(
                            text = "Categories",
                            selected = viewMode == DashboardViewMode.CATEGORIES,
                            onClick = { viewMode = DashboardViewMode.CATEGORIES }
                        )
                    }
                }
            }

            // Body list according to viewMode
            if (viewMode == DashboardViewMode.ALL_APPS) {
                items(positiveUsages, key = { it.packageName }) { usage ->
                    val app = appMap[usage.packageName] ?: AppInfo(
                        packageName = usage.packageName,
                        appName = usage.packageName,
                        category = "Other"
                    )
                    val limit = limitsMap[usage.packageName]?.takeIf { it.isEnabled }?.dailyLimitMinutes

                    AppListRowItem(
                        app = app,
                        durationMinutes = usage.durationMinutes,
                        limitMinutes = limit,
                        onClick = { onAppClick(app.packageName) }
                    )
                }
            } else {
                // Group by Category
                val categoryGroups = positiveUsages.groupBy {
                    appMap[it.packageName]?.category ?: "Other"
                }.map { (category, usages) ->
                    val totalCatMinutes = usages.sumOf { it.durationMinutes }
                    val appCount = usages.size
                    val catLimit = categoryLimits[category]?.takeIf { it.isEnabled }?.dailyLimitMinutes
                    CategoryUsageItem(
                        category = category,
                        totalMinutes = totalCatMinutes,
                        appCount = appCount,
                        limitMinutes = catLimit
                    )
                }.sortedByDescending { it.totalMinutes }

                items(categoryGroups, key = { it.category }) { catItem ->
                    CategoryRowCard(item = catItem)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TodayHeroCard(
    todayMinutes: Long,
    yesterdayMinutes: Long,
    cleanDayStreak: Int,
    hourlyBars: List<com.yu.syncon.ui.components.BarChartItem>
) {
    val animatedMinutes by animateIntAsState(
        targetValue = todayMinutes.toInt(),
        animationSpec = tween(
            durationMillis = 650,
            easing = FastOutSlowInEasing
        ),
        label = "hero_screen_time"
    )
    val hours = animatedMinutes / 60
    val minutes = animatedMinutes % 60
    val diff = (todayMinutes - yesterdayMinutes)

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
                Text(
                    text = "Screen Time",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                if (cleanDayStreak > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(AccentSageLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AccentSage)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (cleanDayStreak == 1) "1 clean day" else "$cleanDayStreak clean days",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentSage,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(PrimaryIndigoLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "0 clean days",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryIndigo,
                            fontWeight = FontWeight.Medium
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
                Text(
                    text = "${hours}h ${String.format("%02d", minutes)}m",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (yesterdayMinutes > 0) {
                    val isUp = diff >= 0
                    val diffAbs = Math.abs(diff)
                    val diffHours = diffAbs / 60
                    val diffMins = diffAbs % 60
                    val deltaStr = if (diffHours > 0) "${diffHours}h ${diffMins}m" else "${diffMins}m"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (isUp) AccentCoralLight else PrimaryIndigoLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isUp) "↑ $deltaStr" else "↓ $deltaStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUp) AccentCoral else PrimaryIndigo,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Screen 9: Today's Hourly Bars (4AM, 8AM, 12PM, 4PM, 8PM, 12AM)
            if (hourlyBars.isNotEmpty()) {
                com.yu.syncon.ui.components.UsageBarChart(
                    items = hourlyBars,
                    maxHeightDp = 100,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FilterToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(FilterChipShape)
            .background(if (selected) PrimaryIndigo else Color.Transparent)
            .clickable(onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            })
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

private data class CategoryUsageItem(
    val category: String,
    val totalMinutes: Long,
    val appCount: Int,
    val limitMinutes: Int? = null
)

@Composable
private fun CategoryRowCard(item: CategoryUsageItem) {
    val color = getCategoryColor(item.category)
    val hours = item.totalMinutes / 60
    val mins = item.totalMinutes % 60
    val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.appCount} apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (item.limitMinutes != null) "$timeStr / ${item.limitMinutes}m" else timeStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.limitMinutes != null && item.totalMinutes >= item.limitMinutes) AccentCoral else TextPrimary
                    )
                    if (item.limitMinutes != null) {
                        val remaining = (item.limitMinutes - item.totalMinutes).coerceAtLeast(0)
                        Text(
                            text = if (remaining == 0L) "Limit reached" else "$remaining min left",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remaining == 0L) AccentCoral else TextSecondary
                        )
                    }
                }
            }

            if (item.limitMinutes != null && item.limitMinutes > 0) {
                val progress = (item.totalMinutes.toFloat() / item.limitMinutes).coerceIn(0f, 1f)
                val barColor = when {
                    progress >= 0.9f -> AccentCoral
                    progress >= 0.75f -> AccentAmber
                    else -> AccentSage
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = barColor,
                    trackColor = CardBorder
                )
            }
        }
    }
}

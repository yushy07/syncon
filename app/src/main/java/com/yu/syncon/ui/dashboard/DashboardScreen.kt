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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.yu.syncon.data.local.entity.AppLimitSettings
import com.yu.syncon.data.local.entity.DailyUsage
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.components.AppListRowItem
import com.yu.syncon.ui.components.MiniBarItem
import com.yu.syncon.ui.components.MiniBarRow
import com.yu.syncon.ui.components.TaglineItalicText
import com.yu.syncon.ui.components.WarningAlertBanner
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight
import com.yu.syncon.ui.theme.AccentSage
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
    onAppClick: (String) -> Unit
) {
    val totalMinutes by repository.getTodayTotalMinutesFlow().collectAsState(initial = 0L)
    val yesterdayMinutes by repository.getYesterdayTotalMinutesFlow().collectAsState(initial = 0L)
    val todayUsages by repository.getTodayUsageFlow().collectAsState(initial = emptyList())
    val allApps by repository.getAllAppsFlow().collectAsState(initial = emptyList())
    val activeLimits by repository.getAllLimitSettingsFlow().collectAsState(initial = emptyList())

    val appMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val limitsMap = remember(activeLimits) { activeLimits.associateBy { it.packageName } }

    var viewMode by remember { mutableStateOf(DashboardViewMode.ALL_APPS) }
    var miniBarData by remember { mutableStateOf<List<MiniBarItem>>(emptyList()) }

    // Load recent 7-day trend for mini-bars
    LaunchedEffect(totalMinutes) {
        val recentDates = UsageDayCalculator.getRecentUsageDates(7)
        val usageMap = repository.getUsageForDates(recentDates)
        val todayStr = UsageDayCalculator.getTodayUsageDate()

        miniBarData = recentDates.map { dateStr ->
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val dayLabel = date.dayOfWeek.name.take(1)
            val minutes = usageMap[dateStr] ?: 0L
            MiniBarItem(
                label = dayLabel,
                valueMinutes = minutes,
                isHighlighted = dateStr == todayStr
            )
        }
    }

    val positiveUsages = todayUsages.filter { it.durationMinutes > 0 }.sortedByDescending { it.durationMinutes }
    val todayMins = totalMinutes ?: 0L
    val yestMins = yesterdayMinutes ?: 0L

    // Screen 11: Identify approaching limit apps (within 80% of limit, or 5 min left)
    val approachingApps = positiveUsages.mapNotNull { usage ->
        val setting = limitsMap[usage.packageName]
        if (setting != null && setting.isEnabled && setting.dailyLimitMinutes != null) {
            val limit = setting.dailyLimitMinutes
            val used = usage.durationMinutes
            if (used in (limit * 4 / 5)..limit) {
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
            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        }

        // Screen 11: Approaching Limit Warning Banner
        if (approachingApps.isNotEmpty()) {
            item {
                val firstApproaching = approachingApps.first()
                val bannerText = if (approachingApps.size == 1) {
                    "You're close to your limit on ${firstApproaching.first.appName}."
                } else {
                    "You're close to your limit on ${approachingApps.size} apps."
                }
                WarningAlertBanner(
                    message = bannerText,
                    onClick = { onAppClick(firstApproaching.first.packageName) }
                )
            }
        }

        // Hero Today Card (Screens 8 & 9)
        item {
            TodayHeroCard(
                todayMinutes = todayMins,
                yesterdayMinutes = yestMins,
                miniBarData = miniBarData
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
                    CategoryUsageItem(
                        category = category,
                        totalMinutes = totalCatMinutes,
                        appCount = appCount
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
    miniBarData: List<MiniBarItem>
) {
    val hours = todayMinutes / 60
    val minutes = todayMinutes % 60
    val diff = (todayMinutes - yesterdayMinutes)

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Screen Time",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
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

            // Mini 7-day trend bars
            if (miniBarData.isNotEmpty()) {
                MiniBarRow(
                    items = miniBarData,
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

private data class CategoryUsageItem(
    val category: String,
    val totalMinutes: Long,
    val appCount: Int
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
            Text(
                text = timeStr,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

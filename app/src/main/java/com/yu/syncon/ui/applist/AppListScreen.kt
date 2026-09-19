package com.yu.syncon.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.components.AppListRowItem
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.CardSurface
import com.yu.syncon.ui.theme.CardSurfaceVariant
import com.yu.syncon.ui.theme.FilterChipShape
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.util.CategoryMapper

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight

enum class AppStatusFilter(val label: String) {
    ALL("All"),
    LIMITED("Limited"),
    APPROACHING("Approaching"),
    BLOCKED("Blocked")
}

enum class AppSortOrder(val label: String) {
    MOST_USED("Most used"),
    ALPHABETICAL("A–Z"),
    LIMIT("By limit")
}

@Composable
fun AppListScreen(
    repository: UsageRepository,
    onAppClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        repository.syncInstalledApps()
    }

    val allApps by repository.getAllAppsFlow().collectAsState(initial = emptyList())
    val todayUsages by repository.getTodayUsageFlow().collectAsState(initial = emptyList())
    val activeLimits by repository.getAllLimitSettingsFlow().collectAsState(initial = emptyList())

    val usageMap = remember(todayUsages) { todayUsages.associateBy({ it.packageName }, { it.durationMinutes }) }
    val limitsMap = remember(activeLimits) { activeLimits.associateBy({ it.packageName }, { it.dailyLimitMinutes }) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf(AppStatusFilter.ALL) }
    var selectedSortOrder by remember { mutableStateOf(AppSortOrder.MOST_USED) }

    val categories = listOf("All") + CategoryMapper.ALL_CATEGORIES

    val filteredApps = remember(allApps, searchQuery, selectedCategoryFilter, selectedStatusFilter, selectedSortOrder, usageMap, limitsMap) {
        allApps.filter { app ->
            val matchesCategory = selectedCategoryFilter == "All" || app.category == selectedCategoryFilter
            val matchesSearch = searchQuery.isBlank() || app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val limit = limitsMap[app.packageName]
            val used = usageMap[app.packageName] ?: 0L
            val matchesStatus = when (selectedStatusFilter) {
                AppStatusFilter.ALL -> true
                AppStatusFilter.LIMITED -> limit != null && limit > 0
                AppStatusFilter.APPROACHING -> limit != null && limit > 0 && used >= (limit * 0.8f) && used < limit
                AppStatusFilter.BLOCKED -> limit != null && limit > 0 && used >= limit
            }

            matchesCategory && matchesSearch && matchesStatus
        }.sortedWith { a, b ->
            when (selectedSortOrder) {
                AppSortOrder.MOST_USED -> {
                    val usedA = usageMap[a.packageName] ?: 0L
                    val usedB = usageMap[b.packageName] ?: 0L
                    if (usedA != usedB) usedB.compareTo(usedA) else a.appName.compareTo(b.appName, ignoreCase = true)
                }
                AppSortOrder.ALPHABETICAL -> a.appName.compareTo(b.appName, ignoreCase = true)
                AppSortOrder.LIMIT -> {
                    val limA = limitsMap[a.packageName] ?: Int.MAX_VALUE
                    val limB = limitsMap[b.packageName] ?: Int.MAX_VALUE
                    if (limA != limB) limA.compareTo(limB) else a.appName.compareTo(b.appName, ignoreCase = true)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen 12: Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Apps",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${filteredApps.size} apps",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Search Bar with icon and clear button
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search apps...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedBorderColor = PrimaryIndigo,
                unfocusedBorderColor = CardBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status Filter Chips: All | Limited | Approaching | Blocked
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppStatusFilter.entries.forEach { filter ->
                val isSelected = selectedStatusFilter == filter
                val chipColor = when {
                    isSelected && (filter == AppStatusFilter.APPROACHING || filter == AppStatusFilter.BLOCKED) -> AccentCoral
                    isSelected -> PrimaryIndigo
                    else -> CardSurface
                }
                val textColor = if (isSelected) Color.White else TextPrimary

                Box(
                    modifier = Modifier
                        .clip(FilterChipShape)
                        .background(chipColor)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedStatusFilter = filter
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Sort toggle chip
            Box(
                modifier = Modifier
                    .clip(FilterChipShape)
                    .background(CardSurfaceVariant)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val nextIdx = (selectedSortOrder.ordinal + 1) % AppSortOrder.entries.size
                        selectedSortOrder = AppSortOrder.entries[nextIdx]
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Sort: ${selectedSortOrder.label}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryIndigo
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategoryFilter == cat
                Box(
                    modifier = Modifier
                        .clip(FilterChipShape)
                        .background(if (isSelected) PrimaryIndigo else CardSurface)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedCategoryFilter = cat
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Apps List or Empty State
        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigoLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No apps found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try adjusting your search or category filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val todayMinutes = usageMap[app.packageName] ?: 0L
                    val limit = limitsMap[app.packageName]

                    AppListRowItem(
                        app = app,
                        durationMinutes = todayMinutes,
                        limitMinutes = limit,
                        onClick = { onAppClick(app.packageName) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

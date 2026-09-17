package com.yu.syncon.ui.appdetail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.yu.syncon.data.repository.UsageRepository
import com.yu.syncon.ui.components.AppIcon
import com.yu.syncon.ui.components.MiniBarItem
import com.yu.syncon.ui.components.MiniBarRow
import com.yu.syncon.ui.components.PrimaryPillButton
import com.yu.syncon.ui.components.SecondaryPillButton
import com.yu.syncon.ui.components.TaglineItalicText
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight
import com.yu.syncon.ui.theme.AccentSage
import com.yu.syncon.ui.theme.AccentSageLight
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.CardShape
import com.yu.syncon.ui.theme.CardSurface
import com.yu.syncon.ui.theme.FilterChipShape
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.ui.theme.getCategoryColor
import com.yu.syncon.util.CategoryMapper
import com.yu.syncon.util.UsageDayCalculator
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    repository: UsageRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var appInfo by remember { mutableStateOf<AppInfo?>(null) }
    val existingSettings by repository.getLimitSettingsFlow(packageName).collectAsState(initial = null)
    val todayUsages by repository.getTodayUsageFlow().collectAsState(initial = emptyList())

    val todayUsage = remember(todayUsages, packageName) {
        todayUsages.find { it.packageName == packageName }?.durationMinutes ?: 0L
    }

    // Modal Sheet states
    var showLimitSheet by remember { mutableStateOf(false) }
    var showBlockingStyleSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }

    // App trend mini bars
    var appMiniBars by remember { mutableStateOf<List<MiniBarItem>>(emptyList()) }

    LaunchedEffect(packageName, todayUsage) {
        appInfo = repository.getAppInfo(packageName)
        val recentDates = UsageDayCalculator.getRecentUsageDates(7)
        val todayStr = UsageDayCalculator.getTodayUsageDate()

        // Gather 7-day stats for this specific app from database
        val appUsageByDate = repository.getAppUsageForDates(packageName, recentDates)

        val items = recentDates.map { dateStr ->
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val dayLabel = date.dayOfWeek.name.take(1)
            val minutes = if (dateStr == todayStr) todayUsage else (appUsageByDate[dateStr] ?: 0L)
            MiniBarItem(
                label = dayLabel,
                valueMinutes = minutes,
                isHighlighted = dateStr == todayStr
            )
        }
        appMiniBars = items
    }

    val categoryColor = getCategoryColor(appInfo?.category ?: "Other")
    val limitMins = existingSettings?.takeIf { it.isEnabled }?.dailyLimitMinutes
    val blockingStyle = existingSettings?.blockingStyle ?: "STRICT"
    val snoozeMins = existingSettings?.snoozeMinutes ?: 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "App Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Hero Card
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(
                        packageName = packageName,
                        appName = appInfo?.appName ?: packageName,
                        category = appInfo?.category ?: "Other",
                        modifier = Modifier.size(72.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = appInfo?.appName ?: packageName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(categoryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = appInfo?.category ?: "Other",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Readout
                    val hours = todayUsage / 60
                    val mins = todayUsage % 60
                    val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                    Text(
                        text = "Today's Usage",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (appMiniBars.isNotEmpty()) {
                        MiniBarRow(items = appMiniBars, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Setting Navigation Rows (Screen 13)
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // Row 1: Daily limit
                    val limitText = if (limitMins != null) {
                        val h = limitMins / 60
                        val m = limitMins % 60
                        if (h > 0) "${h}h ${m}m" else "${m} minutes"
                    } else {
                        "No limit"
                    }
                    ClickableSettingRow(
                        label = "Daily limit",
                        value = limitText,
                        onClick = { showLimitSheet = true }
                    )

                    // Row 2: Blocking behavior
                    val styleText = if (blockingStyle == "STRICT") "Strict" else "Soft (${snoozeMins}m snooze)"
                    ClickableSettingRow(
                        label = "Blocking style",
                        value = styleText,
                        onClick = { showBlockingStyleSheet = true }
                    )

                    // Row 3: Category
                    ClickableSettingRow(
                        label = "Category",
                        value = appInfo?.category ?: "Other",
                        onClick = { showCategorySheet = true }
                    )
                }
            }

            // Explanatory footnote
            Text(
                text = "Usage and limits reset daily at 4:00 AM.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // -------------------------------------------------------------
    // Screen 14: Set Daily Limit Modal Sheet
    // -------------------------------------------------------------
    if (showLimitSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedNoLimit by remember { mutableStateOf(limitMins == null) }
        var currentMinutes by remember { mutableIntStateOf(limitMins ?: 60) }

        ModalBottomSheet(
            onDismissRequest = { showLimitSheet = false },
            sheetState = sheetState,
            containerColor = CardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Daily limit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Set how much time you can spend on ${appInfo?.appName ?: "this app"} before it's blocked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Option: No Limit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .clickable { selectedNoLimit = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedNoLimit,
                        onClick = { selectedNoLimit = true },
                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryIndigo)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No limit",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                // Option: Custom Limit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .clickable { selectedNoLimit = false }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !selectedNoLimit,
                        onClick = { selectedNoLimit = false },
                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryIndigo)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Set daily limit",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }

                if (!selectedNoLimit) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Preset chips: 15m, 30m, 45m, 60m, 90m, 120m
                    val presets = listOf(15, 30, 45, 60, 90, 120)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.take(3).forEach { m ->
                            PresetChip(
                                label = "${m}m",
                                isSelected = currentMinutes == m,
                                onClick = { currentMinutes = m },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.drop(3).forEach { m ->
                            val label = if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
                            PresetChip(
                                label = label,
                                isSelected = currentMinutes == m,
                                onClick = { currentMinutes = m },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display selected duration
                    val selHours = currentMinutes / 60
                    val selMins = currentMinutes % 60
                    val selStr = if (selHours > 0) "${selHours} hr ${selMins} min" else "${selMins} min"
                    Text(
                        text = "Selected limit: $selStr daily",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryIndigo,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                PrimaryPillButton(
                    text = "Save Limit",
                    onClick = {
                        scope.launch {
                            val newDailyLimit = if (selectedNoLimit) null else currentMinutes
                            val newEnabled = !selectedNoLimit
                            val updated = AppLimitSettings(
                                packageName = packageName,
                                dailyLimitMinutes = newDailyLimit,
                                isEnabled = newEnabled,
                                blockingStyle = existingSettings?.blockingStyle ?: "STRICT",
                                snoozeMinutes = existingSettings?.snoozeMinutes ?: 5
                            )
                            repository.saveLimitSettings(updated)
                            showLimitSheet = false
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // -------------------------------------------------------------
    // Screen 15: Blocking Style Modal Sheet
    // -------------------------------------------------------------
    if (showBlockingStyleSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedStyle by remember { mutableStateOf(blockingStyle) }
        var selectedSnooze by remember { mutableIntStateOf(snoozeMins) }

        ModalBottomSheet(
            onDismissRequest = { showBlockingStyleSheet = false },
            sheetState = sheetState,
            containerColor = CardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Blocking style",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose what happens when the daily screen time limit is reached.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Strict option
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedStyle == "STRICT") PrimaryIndigoLight else CardSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedStyle == "STRICT") PrimaryIndigo else CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStyle = "STRICT" }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedStyle == "STRICT",
                            onClick = { selectedStyle = "STRICT" },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryIndigo)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Strict Blocking",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Block completely until the next usage day at 4:00 AM. Cannot be snoozed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Soft option
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedStyle == "SOFT") PrimaryIndigoLight else CardSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedStyle == "SOFT") PrimaryIndigo else CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStyle = "SOFT" }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedStyle == "SOFT",
                                onClick = { selectedStyle = "SOFT" },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryIndigo)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Soft Blocking",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Allow extra time with a snooze button when limit is reached.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        if (selectedStyle == "SOFT") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Snooze duration:",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val snoozeOptions = listOf(5, 10, 15, 20)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                snoozeOptions.forEach { snz ->
                                    PresetChip(
                                        label = "${snz}m",
                                        isSelected = selectedSnooze == snz,
                                        onClick = { selectedSnooze = snz },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                PrimaryPillButton(
                    text = "Save Style",
                    onClick = {
                        scope.launch {
                            val updated = AppLimitSettings(
                                packageName = packageName,
                                dailyLimitMinutes = existingSettings?.dailyLimitMinutes,
                                isEnabled = existingSettings?.isEnabled ?: false,
                                blockingStyle = selectedStyle,
                                snoozeMinutes = selectedSnooze
                            )
                            repository.saveLimitSettings(updated)
                            showBlockingStyleSheet = false
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // -------------------------------------------------------------
    // Screen 16: Category Picker Modal Sheet
    // -------------------------------------------------------------
    if (showCategorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            sheetState = sheetState,
            containerColor = CardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Select Category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Assign a category to organize your usage stats and reports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                CategoryMapper.ALL_CATEGORIES.forEach { category ->
                    val isSelected = appInfo?.category == category
                    val catColor = getCategoryColor(category)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .clickable {
                                scope.launch {
                                    repository.updateAppCategory(packageName, category)
                                    appInfo = repository.getAppInfo(packageName)
                                    showCategorySheet = false
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(catColor)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = category,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ClickableSettingRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryIndigo,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FilterChipShape)
            .background(if (isSelected) PrimaryIndigo else PrimaryIndigoLight)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else PrimaryIndigo
        )
    }
}

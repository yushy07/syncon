package com.yu.syncon.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.R
import com.yu.syncon.service.worker.DailyResetWorker
import com.yu.syncon.ui.components.OutlinedPillButton
import com.yu.syncon.ui.components.PrimaryPillButton
import com.yu.syncon.ui.components.TaglineItalicText
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight
import com.yu.syncon.ui.theme.AccentSage
import com.yu.syncon.ui.theme.AccentSageLight
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.CardShape
import com.yu.syncon.ui.theme.CardSurface
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.util.PermissionUtils
import kotlinx.coroutines.launch

enum class SettingsSubScreen {
    HOME,
    PERMISSION_HEALTH,
    USAGE_REVOKED_WARNING,
    ACCESSIBILITY_REVOKED_WARNING,
    BACKGROUND_WARNING,
    DATA_RETENTION,
    ABOUT
}

@Composable
fun SettingsScreen(
    hasUsageAccess: Boolean,
    isAccessibilityEnabled: Boolean,
    isBatteryOptimizationIgnored: Boolean
) {
    var subScreen by remember { mutableStateOf(SettingsSubScreen.HOME) }

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "SettingsNav"
    ) { screen ->
        when (screen) {
            SettingsSubScreen.HOME -> Screen32SettingsHome(
                hasUsageAccess = hasUsageAccess,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onNavigate = { subScreen = it }
            )
            SettingsSubScreen.PERMISSION_HEALTH -> Screen33PermissionHealth(
                hasUsageAccess = hasUsageAccess,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                onBack = { subScreen = SettingsSubScreen.HOME },
                onNavigateWarning = { subScreen = it }
            )
            SettingsSubScreen.USAGE_REVOKED_WARNING -> Screen34UsageRevokedWarning(
                onBack = { subScreen = SettingsSubScreen.PERMISSION_HEALTH }
            )
            SettingsSubScreen.ACCESSIBILITY_REVOKED_WARNING -> Screen35AccessibilityRevokedWarning(
                onBack = { subScreen = SettingsSubScreen.PERMISSION_HEALTH }
            )
            SettingsSubScreen.BACKGROUND_WARNING -> Screen36BackgroundWarning(
                onBack = { subScreen = SettingsSubScreen.PERMISSION_HEALTH }
            )
            SettingsSubScreen.DATA_RETENTION -> Screen37DataRetention(
                onBack = { subScreen = SettingsSubScreen.HOME }
            )
            SettingsSubScreen.ABOUT -> Screen38About(
                onBack = { subScreen = SettingsSubScreen.HOME }
            )
        }
    }
}

// -------------------------------------------------------------
// Screen 32: Settings Home
// -------------------------------------------------------------
@Composable
private fun Screen32SettingsHome(
    hasUsageAccess: Boolean,
    isAccessibilityEnabled: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onNavigate: (SettingsSubScreen) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resetFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val allPermissionsGranted = hasUsageAccess && isAccessibilityEnabled && isBatteryOptimizationIgnored

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Navigation Card
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SettingsClickableRow(
                    title = "Permissions",
                    subtitle = if (allPermissionsGranted) "All permissions active" else "Attention required",
                    statusColor = if (allPermissionsGranted) AccentSage else AccentCoral,
                    onClick = { onNavigate(SettingsSubScreen.PERMISSION_HEALTH) }
                )
                SettingsClickableRow(
                    title = "Data retention",
                    subtitle = "3-year local storage",
                    onClick = { onNavigate(SettingsSubScreen.DATA_RETENTION) }
                )
                SettingsClickableRow(
                    title = "About",
                    subtitle = "Version 1.0.0",
                    onClick = { onNavigate(SettingsSubScreen.ABOUT) }
                )
            }
        }

        // Dedicated QA / Debug Controls Section
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "QA / Debug Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Developer triggers for testing daily reset and maintenance cycles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedPillButton(
                    text = "Trigger 4:00 AM Reset Now",
                    onClick = {
                        scope.launch {
                            DailyResetWorker.triggerImmediateReset(context)
                            resetFeedbackMessage = "4:00 AM daily reset executed successfully!"
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedPillButton(
                    text = "Reset Onboarding Flow (Debug)",
                    onClick = {
                        context.getSharedPreferences("syncon_prefs", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .remove("onboarding_completed")
                            .apply()
                        resetFeedbackMessage = "Onboarding reset! Reopen app to see onboarding."
                    }
                )

                resetFeedbackMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "✓ $msg",
                        color = AccentSage,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// -------------------------------------------------------------
// Screen 33: Permission Health
// -------------------------------------------------------------
@Composable
private fun Screen33PermissionHealth(
    hasUsageAccess: Boolean,
    isAccessibilityEnabled: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onBack: () -> Unit,
    onNavigateWarning: (SettingsSubScreen) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBarBack(title = "Permission Health", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Permission Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "All required permissions must remain active for reliable tracking and limit enforcement.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionDetailCard(
                title = "Usage Access",
                description = "Reads app foreground durations locally via UsageStatsManager.",
                isGranted = hasUsageAccess,
                onFix = { context.startActivity(PermissionUtils.getUsageAccessIntent()) },
                onPreviewWarning = { onNavigateWarning(SettingsSubScreen.USAGE_REVOKED_WARNING) }
            )

            PermissionDetailCard(
                title = "Accessibility Service",
                description = "Instantly detects foreground apps to show block screen when limit is hit.",
                isGranted = isAccessibilityEnabled,
                onFix = { context.startActivity(PermissionUtils.getAccessibilitySettingsIntent()) },
                onPreviewWarning = { onNavigateWarning(SettingsSubScreen.ACCESSIBILITY_REVOKED_WARNING) }
            )

            PermissionDetailCard(
                title = "Background Running",
                description = "Battery optimization must be ignored so tracking loop stays active.",
                isGranted = isBatteryOptimizationIgnored,
                onFix = { context.startActivity(PermissionUtils.getBatteryOptimizationIntent(context)) },
                onPreviewWarning = { onNavigateWarning(SettingsSubScreen.BACKGROUND_WARNING) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// Screen 34: Usage Revoked Warning
// -------------------------------------------------------------
@Composable
private fun Screen34UsageRevokedWarning(onBack: () -> Unit) {
    val context = LocalContext.current
    WarningStateTemplate(
        icon = Icons.Default.Warning,
        iconTint = AccentCoral,
        iconBg = AccentCoralLight,
        title = "Usage tracking is paused",
        description = "Usage Access was turned off. Grant access to continue tracking your usage.",
        buttonText = "Grant Usage Access",
        onButtonClick = { context.startActivity(PermissionUtils.getUsageAccessIntent()) },
        onBack = onBack
    )
}

// -------------------------------------------------------------
// Screen 35: Accessibility Revoked Warning
// -------------------------------------------------------------
@Composable
private fun Screen35AccessibilityRevokedWarning(onBack: () -> Unit) {
    val context = LocalContext.current
    WarningStateTemplate(
        icon = Icons.Default.Lock,
        iconTint = AccentCoral,
        iconBg = AccentCoralLight,
        title = "App blocking is paused",
        description = "Accessibility access is disabled. Your usage tracking still works, but limits cannot be enforced.",
        buttonText = "Enable Accessibility",
        onButtonClick = { context.startActivity(PermissionUtils.getAccessibilitySettingsIntent()) },
        onBack = onBack
    )
}

// -------------------------------------------------------------
// Screen 36: Background Tracking Warning
// -------------------------------------------------------------
@Composable
private fun Screen36BackgroundWarning(onBack: () -> Unit) {
    val context = LocalContext.current
    WarningStateTemplate(
        icon = Icons.Default.BatteryChargingFull,
        iconTint = AccentCoral,
        iconBg = AccentCoralLight,
        title = "Background tracking may be interrupted",
        description = "Battery optimization is enabled. Allow background running for reliable tracking.",
        buttonText = "Allow Background Running",
        onButtonClick = { context.startActivity(PermissionUtils.getBatteryOptimizationIntent(context)) },
        onBack = onBack
    )
}

// -------------------------------------------------------------
// Screen 37: Data Retention
// -------------------------------------------------------------
@Composable
private fun Screen37DataRetention(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBarBack(title = "Data", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PrimaryIndigoLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Your data",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Usage history is kept for 3 years (1,095 days). Older records are automatically removed. All data stays on your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(AccentSageLight)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "✓ 100% Offline • Zero Network Access",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentSage,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                PrimaryPillButton(
                    text = "Done",
                    onClick = onBack
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Screen 38: About (Verbatim copy per user instruction)
// -------------------------------------------------------------
@Composable
private fun Screen38About(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBarBack(title = "About", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_syncon_brand_logo),
                contentDescription = "SyncOn Logo",
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SyncOn",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            TaglineItalicText(text = "\"Built for a more intentional you.\"")

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation rows verbatim
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    AboutNavigationRow(label = "Open Source Libraries >", onClick = {})
                    AboutNavigationRow(label = "Privacy >", onClick = {})
                    AboutNavigationRow(label = "Feedback >", onClick = {})
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// -------------------------------------------------------------
// Shared Templates & Helpers
// -------------------------------------------------------------
@Composable
private fun WarningStateTemplate(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBarBack(title = "", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                PrimaryPillButton(
                    text = buttonText,
                    onClick = onButtonClick
                )
            }
        }
    }
}

@Composable
private fun TopBarBack(title: String, onBack: () -> Unit) {
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
        if (title.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
    statusColor: Color? = null,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor ?: TextSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AboutNavigationRow(
    label: String,
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
    }
}

@Composable
private fun PermissionDetailCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onFix: () -> Unit,
    onPreviewWarning: () -> Unit
) {
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (isGranted) AccentSageLight else AccentCoralLight)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isGranted) "✓ Active" else "Missing",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isGranted) AccentSage else AccentCoral,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(PrimaryIndigo)
                            .clickable(onClick = onFix)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Grant",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(PrimaryIndigoLight)
                        .clickable(onClick = onPreviewWarning)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Preview Warning Screen",
                        color = PrimaryIndigo,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

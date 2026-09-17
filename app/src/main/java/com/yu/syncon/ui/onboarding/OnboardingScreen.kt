package com.yu.syncon.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.R
import com.yu.syncon.ui.components.OutlinedPillButton
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
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.util.PermissionUtils

@Composable
fun OnboardingScreen(
    hasUsageAccess: Boolean,
    isAccessibilityEnabled: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }
    val grantedCount = (if (hasUsageAccess) 1 else 0) +
            (if (isAccessibilityEnabled) 1 else 0) +
            (if (isBatteryOptimizationIgnored) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Back Navigation bar if beyond step 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep in 2..6) {
                IconButton(onClick = { currentStep-- }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (currentStep in 3..6) {
                Text(
                    text = "Screen $currentStep of 7",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "OnboardingStep"
        ) { step ->
            when (step) {
                1 -> Screen1Welcome(onGetStarted = { currentStep = 2 })
                2 -> Screen2HowItWorks(onContinue = { currentStep = 3 })
                3 -> Screen3PermissionsOverview(
                    hasUsageAccess = hasUsageAccess,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                    grantedCount = grantedCount,
                    onContinue = { currentStep = 4 }
                )
                4 -> Screen4UsageAccess(
                    isGranted = hasUsageAccess,
                    onGrant = { context.startActivity(PermissionUtils.getUsageAccessIntent()) },
                    onNext = { currentStep = 5 }
                )
                5 -> Screen5Accessibility(
                    isGranted = isAccessibilityEnabled,
                    onGrant = { context.startActivity(PermissionUtils.getAccessibilitySettingsIntent()) },
                    onNext = { currentStep = 6 }
                )
                6 -> Screen6BackgroundRunning(
                    isGranted = isBatteryOptimizationIgnored,
                    onGrant = { context.startActivity(PermissionUtils.getBatteryOptimizationIntent(context)) },
                    onNext = { currentStep = 7 }
                )
                7 -> Screen7Ready(
                    onOpenApp = onAllGranted
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Screen 1: Welcome
// -------------------------------------------------------------
@Composable
private fun Screen1Welcome(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // App Logo Mark
            Image(
                painter = painterResource(id = R.drawable.ic_syncon_brand_logo),
                contentDescription = "SyncOn Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SyncOn",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            TaglineItalicText(text = "\"Same phone. A more intentional you.\"")

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Track your time. Set limits. Build better habits.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            PrimaryPillButton(
                text = "Get Started →",
                onClick = onGetStarted
            )
        }
    }
}

// -------------------------------------------------------------
// Screen 2: How It Works
// -------------------------------------------------------------
@Composable
private fun Screen2HowItWorks(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "A healthier digital routine.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "SyncOn helps you stay mindful of your digital habits through 4 core principles.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            CapabilityCard(
                icon = Icons.Default.BarChart,
                iconColor = PrimaryIndigo,
                title = "Track your usage",
                description = "See exact time spent on every app, updated in real time completely offline."
            )
            Spacer(modifier = Modifier.height(12.dp))
            CapabilityCard(
                icon = Icons.Default.HourglassEmpty,
                iconColor = AccentSage,
                title = "Set limits",
                description = "Choose daily limits for any app you want to use less."
            )
            Spacer(modifier = Modifier.height(12.dp))
            CapabilityCard(
                icon = Icons.Default.Notifications,
                iconColor = AccentCoral,
                title = "Get warned",
                description = "Receive a gentle warning when you're 5 minutes away from your daily limit."
            )
            Spacer(modifier = Modifier.height(12.dp))
            CapabilityCard(
                icon = Icons.Default.Lock,
                iconColor = PrimaryIndigo,
                title = "Get blocked",
                description = "When time is up, the app is blocked until 4:00 AM reset."
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            PrimaryPillButton(
                text = "Continue",
                onClick = onContinue
            )
        }
    }
}

@Composable
private fun CapabilityCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Screen 3: Permissions Overview
// -------------------------------------------------------------
@Composable
private fun Screen3PermissionsOverview(
    hasUsageAccess: Boolean,
    isAccessibilityEnabled: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    grantedCount: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryIndigo,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (grantedCount == 3) AccentSageLight else PrimaryIndigoLight)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$grantedCount / 3 complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (grantedCount == 3) AccentSage else PrimaryIndigo,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We need a few permissions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "To track usage and block apps offline, SyncOn needs these permissions. All data stays strictly on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            PermissionOverviewItem(
                title = "Usage Access",
                subtitle = "Reads foreground app usage durations locally",
                isGranted = hasUsageAccess
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionOverviewItem(
                title = "Accessibility",
                subtitle = "Detects foreground app transitions to enforce limits",
                isGranted = isAccessibilityEnabled
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionOverviewItem(
                title = "Battery Optimization",
                subtitle = "Allows tracking to run reliably in the background",
                isGranted = isBatteryOptimizationIgnored
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            PrimaryPillButton(
                text = if (grantedCount == 3) "Continue" else "Set Up Permissions →",
                onClick = onContinue
            )
        }
    }
}

@Composable
private fun PermissionOverviewItem(
    title: String,
    subtitle: String,
    isGranted: Boolean
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) AccentSageLight else PrimaryIndigoLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isGranted) AccentSage else PrimaryIndigo,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (isGranted) AccentSageLight else AccentCoralLight)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isGranted) "Granted" else "Required",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (isGranted) AccentSage else AccentCoral,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Screen 4: Usage Access Step
// -------------------------------------------------------------
@Composable
private fun Screen4UsageAccess(
    isGranted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Step 1 of 3",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryIndigo,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Usage Access",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This allows SyncOn to read foreground app durations via Android's local UsageStatsManager. No data ever leaves your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Visual preview illustration card
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
                        text = "SyncOn → Allow usage tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the button below, locate 'SyncOn' in the list, and turn the switch ON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Status pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (isGranted) AccentSageLight else AccentCoralLight)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isGranted) "✓ Usage Access Granted" else "Not Granted Yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isGranted) AccentSage else AccentCoral,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (!isGranted) {
                PrimaryPillButton(
                    text = "Grant Usage Access",
                    onClick = onGrant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedPillButton(
                    text = "Skip for Now",
                    onClick = onNext
                )
            } else {
                PrimaryPillButton(
                    text = "Next: Accessibility →",
                    onClick = onNext
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Screen 5: Accessibility Step
// -------------------------------------------------------------
@Composable
private fun Screen5Accessibility(
    isGranted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Step 2 of 3",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryIndigo,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Accessibility Service",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Allows instant detection when a blocked app is launched in the foreground so the block screen can appear immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

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
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigoLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Downloaded Apps → SyncOn",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Find 'SyncOn' under Downloaded / Installed apps, tap it, and enable the service switch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (isGranted) AccentSageLight else AccentCoralLight)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isGranted) "✓ Accessibility Enabled" else "Not Enabled Yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isGranted) AccentSage else AccentCoral,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (!isGranted) {
                PrimaryPillButton(
                    text = "Enable Accessibility",
                    onClick = onGrant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedPillButton(
                    text = "Skip for Now",
                    onClick = onNext
                )
            } else {
                PrimaryPillButton(
                    text = "Next: Background Running →",
                    onClick = onNext
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Screen 6: Background Running Step
// -------------------------------------------------------------
@Composable
private fun Screen6BackgroundRunning(
    isGranted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Step 3 of 3",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryIndigo,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Keep it running",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Battery optimization can silently kill background tracking. Allowing background running ensures continuous, accurate screen time stats.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

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
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigoLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ignore Battery Optimization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose 'Allow' or 'Don't Optimize' when prompted so SyncOn stays active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (isGranted) AccentSageLight else AccentCoralLight)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isGranted) "✓ Background Running Allowed" else "Not Allowed Yet",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isGranted) AccentSage else AccentCoral,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (!isGranted) {
                PrimaryPillButton(
                    text = "Allow Background Running",
                    onClick = onGrant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedPillButton(
                    text = "Skip for Now",
                    onClick = onNext
                )
            } else {
                PrimaryPillButton(
                    text = "Complete Setup →",
                    onClick = onNext
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Screen 7: You're Ready
// -------------------------------------------------------------
@Composable
private fun Screen7Ready(onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AccentSageLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = AccentSage,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "You're all set!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            TaglineItalicText(text = "\"Track. Limit. Focus. Live Better.\"")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SyncOn is ready to help you build better habits. Your data is stored locally and securely.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3 Ready Checkmarks
            ReadyCheckmarkRow(title = "Usage access granted")
            Spacer(modifier = Modifier.height(10.dp))
            ReadyCheckmarkRow(title = "Accessibility service active")
            Spacer(modifier = Modifier.height(10.dp))
            ReadyCheckmarkRow(title = "Background tracking enabled")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            PrimaryPillButton(
                text = "Open SyncOn",
                onClick = onOpenApp
            )
        }
    }
}

@Composable
private fun ReadyCheckmarkRow(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AccentSage,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary
        )
    }
}

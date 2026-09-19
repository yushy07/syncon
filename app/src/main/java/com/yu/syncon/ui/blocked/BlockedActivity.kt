package com.yu.syncon.ui.blocked

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.SyncOnApp
import com.yu.syncon.data.repository.UsageRepository
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
import com.yu.syncon.ui.theme.SyncOnTheme
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class BlockedActivity : ComponentActivity() {

    private lateinit var repository: UsageRepository

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_BLOCKING_STYLE = "extra_blocking_style"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_LIMIT_MINUTES = "extra_limit_minutes"
        const val EXTRA_USED_MINUTES = "extra_used_minutes"
        const val EXTRA_CATEGORY_LIMIT = "extra_category_limit"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = (applicationContext as? SyncOnApp)?.repository ?: UsageRepository(applicationContext)

        onBackPressedDispatcher.addCallback(this) {
            navigateHome()
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "This app"
        val blockingStyle = intent.getStringExtra(EXTRA_BLOCKING_STYLE)?.uppercase() ?: "STRICT"
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 60)
        val categoryLimit = intent.getStringExtra(EXTRA_CATEGORY_LIMIT)

        setContent {
            SyncOnTheme(darkTheme = false) {
                val scope = rememberCoroutineScope()
                var isSnoozeConfirmed by remember { mutableStateOf(false) }

                AnimatedContent(
                    targetState = isSnoozeConfirmed,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "BlockScreenState"
                ) { confirmed ->
                    if (confirmed) {
                        // Screen 29: Snooze Confirmation Screen
                        Screen29SnoozeConfirmation(
                            snoozeMinutes = snoozeMinutes,
                            newLimitMinutes = limitMinutes + snoozeMinutes,
                            onGotIt = { finish() }
                        )
                    } else if (blockingStyle == "SOFT") {
                        // Screen 28: Soft Block Screen
                        Screen28SoftBlock(
                            appName = appName,
                            snoozeMinutes = snoozeMinutes,
                            onSnooze = {
                                scope.launch {
                                    if (categoryLimit != null) {
                                        repository.snoozeCategory(categoryLimit, snoozeMinutes)
                                    } else {
                                        repository.snoozeApp(packageName, snoozeMinutes)
                                    }
                                    isSnoozeConfirmed = true
                                }
                            },
                            onGoHome = { navigateHome() }
                        )
                    } else {
                        // Screen 27: Strict Block Screen
                        Screen27StrictBlock(
                            appName = appName,
                            limitMinutes = limitMinutes,
                            onGoHome = { navigateHome() }
                        )
                    }
                }
            }
        }
    }

    private fun navigateHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

// -------------------------------------------------------------
// Screen 27: Strict Block Screen
// -------------------------------------------------------------
@Composable
private fun Screen27StrictBlock(
    appName: String,
    limitMinutes: Int,
    onGoHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Lock / Block Graphic
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AccentCoralLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Blocked",
                    tint = AccentCoral,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "$appName is blocked",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            val hours = limitMinutes / 60
            val mins = limitMinutes % 60
            val limitStr = if (hours > 0) "${hours}h ${mins}m" else "${mins} min"

            Text(
                text = "You've reached your $limitStr daily limit.\nAvailable again at 4:00 AM.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            TaglineItalicText(text = "\"Take a break. You'll be back stronger.\"")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            PrimaryPillButton(
                text = "Go Home",
                onClick = onGoHome
            )
        }
    }
}

// -------------------------------------------------------------
// Screen 28: Soft Block Screen
// -------------------------------------------------------------
@Composable
private fun Screen28SoftBlock(
    appName: String,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onGoHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                .background(PrimaryIndigoLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "You've reached today's limit",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "You've reached your limit on $appName. You can take a short break or snooze for a bit longer.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            TaglineItalicText(text = "\"A few extra minutes. Use them well.\"")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            PrimaryPillButton(
                text = "Snooze + $snoozeMinutes min",
                onClick = onSnooze
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedPillButton(
                text = "Go Home",
                onClick = onGoHome
            )
        }
    }
}

// -------------------------------------------------------------
// Screen 29: Snooze Confirmation Screen
// -------------------------------------------------------------
@Composable
private fun Screen29SnoozeConfirmation(
    snoozeMinutes: Int,
    newLimitMinutes: Int,
    onGotIt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AccentSageLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentSage,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "$snoozeMinutes extra minutes added",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            val hours = newLimitMinutes / 60
            val mins = newLimitMinutes % 60
            val newLimitStr = if (hours > 0) "${hours}h ${mins}m" else "${mins} minutes"

            Text(
                text = "New limit: $newLimitStr\nResets at 4:00 AM.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            PrimaryPillButton(
                text = "Got it",
                onClick = onGotIt
            )
        }
    }
}

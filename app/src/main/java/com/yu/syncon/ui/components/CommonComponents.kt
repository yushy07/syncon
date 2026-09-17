package com.yu.syncon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yu.syncon.data.local.entity.AppInfo
import com.yu.syncon.ui.theme.AccentCoral
import com.yu.syncon.ui.theme.AccentCoralLight
import com.yu.syncon.ui.theme.CardBorder
import com.yu.syncon.ui.theme.CardShape
import com.yu.syncon.ui.theme.CardSurface
import com.yu.syncon.ui.theme.PillButtonShape
import com.yu.syncon.ui.theme.PrimaryIndigo
import com.yu.syncon.ui.theme.PrimaryIndigoHover
import com.yu.syncon.ui.theme.PrimaryIndigoLight
import com.yu.syncon.ui.theme.SerifItalicTagline
import com.yu.syncon.ui.theme.TextPrimary
import com.yu.syncon.ui.theme.TextSecondary
import com.yu.syncon.ui.theme.getCategoryColor

@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryIndigo,
            contentColor = Color.White,
            disabledContainerColor = CardBorder,
            disabledContentColor = TextSecondary
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        )
    }
}

@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = PillButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryIndigoLight,
            contentColor = PrimaryIndigo
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        )
    }
}

@Composable
fun OutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = PillButtonShape,
        border = BorderStroke(1.dp, CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = TextPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
        )
    }
}

@Composable
fun WarningAlertBanner(
    message: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = AccentCoralLight),
        border = BorderStroke(1.dp, AccentCoral.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentCoral.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = AccentCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AccentCoral,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AccentCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TaglineItalicText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = SerifItalicTagline,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        if (actionIcon != null && onAction != null) {
            IconButton(onClick = onAction) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    tint = TextPrimary
                )
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
fun AppIcon(
    packageName: String,
    appName: String = "",
    category: String = "Other",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categoryColor = getCategoryColor(category)

    val iconBitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = appName,
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(categoryColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = categoryColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun AppListRowItem(
    app: AppInfo,
    durationMinutes: Long,
    limitMinutes: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(app.category)
    val isApproaching = limitMinutes != null && limitMinutes > 0 && durationMinutes >= (limitMinutes * 0.8f)
    val remaining = if (limitMinutes != null) (limitMinutes - durationMinutes).coerceAtLeast(0) else 0

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Avatar Icon (using real system icon or category fallback)
            AppIcon(
                packageName = app.packageName,
                appName = app.appName,
                category = app.category,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Name + Category Badge
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = app.category,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                    if (app.isSystemApp) {
                        Text(
                            text = " • System",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                    }
                }
            }

            // Usage or Warning
            Column(horizontalAlignment = Alignment.End) {
                if (isApproaching) {
                    Text(
                        text = "${durationMinutes}/${limitMinutes}m",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = AccentCoral,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "${remaining} min left",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AccentCoral,
                            fontSize = 11.sp
                        )
                    )
                } else {
                    val hours = durationMinutes / 60
                    val mins = durationMinutes % 60
                    val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    )
                }
            }
        }
    }
}

package com.yu.syncon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SyncOnColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = CardSurface,
    primaryContainer = PrimaryIndigoLight,
    onPrimaryContainer = PrimaryIndigoHover,
    secondary = PrimaryIndigo,
    onSecondary = CardSurface,
    background = WarmBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = CardBorder,
    error = AccentCoral,
    onError = CardSurface,
    errorContainer = AccentCoralLight,
    onErrorContainer = AccentCoral
)

@Composable
fun SyncOnTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SyncOnColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}


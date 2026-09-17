package com.yu.syncon.ui.theme

import androidx.compose.ui.graphics.Color

// Core Design Palette — Standardized to Guideline Images 7 & 8
// CALM • MODERN • MINIMAL • GEN Z • PURPOSEFUL

val WarmBackground = Color(0xFFFBF9F5)
val CardSurface = Color(0xFFFFFFFF)
val CardBorder = Color(0xFFEDE8E1)
val CardSurfaceVariant = Color(0xFFF5F2EC)

val PrimaryIndigo = Color(0xFF4F67E0)
val PrimaryIndigoHover = Color(0xFF3B53CC)
val PrimaryIndigoLight = Color(0xFFEDF0FD)

val TextPrimary = Color(0xFF1A1C1E)
val TextSecondary = Color(0xFF75777E)
val TextTertiary = Color(0xFFA0A3A9)

val AccentCoral = Color(0xFFE06D53)
val AccentCoralLight = Color(0xFFFDEEEB)

val AccentSage = Color(0xFF3E6B5C)
val AccentSageLight = Color(0xFFEAF3F0)

val AccentAmber = Color(0xFFE8A838)
val AccentAmberLight = Color(0xFFFEF6E7)

// Category Colors for Charts & Tags
val CategorySocial = Color(0xFF5B75E6)
val CategoryEntertainment = Color(0xFFE06D53)
val CategoryBrowser = Color(0xFFE8A838)
val CategoryCommunication = Color(0xFF3E9B78)
val CategoryProductivity = Color(0xFF8B6CE6)
val CategoryOther = Color(0xFF9E9E9E)

fun getCategoryColor(category: String): Color {
    return when (category.lowercase()) {
        "social media", "social" -> CategorySocial
        "entertainment" -> CategoryEntertainment
        "browser" -> CategoryBrowser
        "communication" -> CategoryCommunication
        "productivity" -> CategoryProductivity
        else -> CategoryOther
    }
}

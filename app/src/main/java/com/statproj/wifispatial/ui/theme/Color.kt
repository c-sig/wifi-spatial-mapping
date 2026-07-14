package com.statproj.wifispatial.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary palette (Cyan/Teal for data/science aesthetic) ──────────
val Primary = Color(0xFF00BCD4)
val PrimaryDark = Color(0xFF0097A7)
val PrimaryLight = Color(0xFF4DD0E1)
val OnPrimary = Color(0xFF003738)

// ── Secondary palette (Amber accents for warnings/highlights) ───────
val Secondary = Color(0xFFFFB300)
val SecondaryDark = Color(0xFFC68400)
val SecondaryLight = Color(0xFFFFE54C)
val OnSecondary = Color(0xFF3E2700)

// ── Tertiary palette (Purple for statistics/reports) ────────────────
val Tertiary = Color(0xFF7C4DFF)
val TertiaryDark = Color(0xFF5B2FD6)
val TertiaryLight = Color(0xFFA47AFF)
val OnTertiary = Color(0xFFFFFFFF)

// ── Semantic colors ─────────────────────────────────────────────────
val Success = Color(0xFF4CAF50)
val SuccessLight = Color(0xFF81C784)
val Error = Color(0xFFEF5350)
val ErrorDark = Color(0xFFD32F2F)
val ErrorLight = Color(0xFFFF8A80)
val Warning = Color(0xFFFF9800)
val WarningLight = Color(0xFFFFCC02)

// ── Dark theme surface hierarchy (AMOLED-optimized) ─────────────────
val SurfaceBlack = Color(0xFF000000)         // True black for AMOLED
val SurfaceDim = Color(0xFF0A0A0F)           // Slightly lifted black
val Surface = Color(0xFF0F1318)              // Default surface
val SurfaceContainer = Color(0xFF151A21)     // Cards, sheets
val SurfaceContainerHigh = Color(0xFF1C2129) // Elevated cards
val SurfaceBright = Color(0xFF252B33)        // Highest elevation
val OnSurface = Color(0xFFE2E8F0)           // Primary text
val OnSurfaceVariant = Color(0xFF94A3B8)    // Secondary text
val Outline = Color(0xFF334155)             // Borders, dividers
val OutlineVariant = Color(0xFF1E293B)      // Subtle borders

// ── Chart/visualization colors ──────────────────────────────────────
val ChartBlue = Color(0xFF64B5F6)
val ChartGreen = Color(0xFF81C784)
val ChartOrange = Color(0xFFFFB74D)
val ChartPurple = Color(0xFFBA68C8)
val ChartRed = Color(0xFFE57373)
val ChartTeal = Color(0xFF4DB6AC)
val ChartPink = Color(0xFFF06292)
val ChartYellow = Color(0xFFFFD54F)

val ChartColors = listOf(
    ChartBlue, ChartGreen, ChartOrange, ChartPurple,
    ChartRed, ChartTeal, ChartPink, ChartYellow
)

// ── Speed test status colors ────────────────────────────────────────
val DownloadColor = Color(0xFF42A5F5)
val UploadColor = Color(0xFF66BB6A)
val ProximityDangerBg = Color(0x33EF5350)   // Semi-transparent red
val ProximityDangerBorder = Color(0xFFEF5350)
val ProximitySafeBg = Color(0x334CAF50)     // Semi-transparent green
val ProximitySafeBorder = Color(0xFF4CAF50)

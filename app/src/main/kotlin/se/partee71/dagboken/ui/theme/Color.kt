package se.partee71.dagboken.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Chrome blue family ───────────────────────────────────────────────────────
val ChromeBlue300  = Color(0xFF93C5FD)   // light chrome blue
val ChromeBlue400  = Color(0xFF60A5FA)   // electric chrome blue (dark primary)
val ChromeBlue500  = Color(0xFF3B82F6)   // bright cobalt
val ChromeBlue600  = Color(0xFF2563EB)   // rich cobalt
val ChromeBlue700  = Color(0xFF1D4ED8)   // deep cobalt (light primary)
val ChromeBlue800  = Color(0xFF1E40AF)
val ChromeBlue900  = Color(0xFF1E3A8A)   // midnight steel

// ─── Steel cyan (secondary) ───────────────────────────────────────────────────
val CyanSteel300   = Color(0xFF67E8F9)   // light chrome cyan
val CyanSteel400   = Color(0xFF22D3EE)   // bright chrome cyan (dark secondary)
val CyanSteel600   = Color(0xFF0891B2)   // steel teal (light secondary)
val CyanSteel700   = Color(0xFF0E7490)

// ─── Violet / amethyst (backward-compat + dark tertiary) ─────────────────────
val Violet300    = Color(0xFFC4B5FD)
val Violet400    = Color(0xFFA78BFA)   // chrome amethyst (dark tertiary)
val Violet500    = Color(0xFF8B5CF6)   // kept for existing imports
val Violet600    = Color(0xFF7C3AED)
val Violet900    = Color(0xFF2E1065)

// ─── Semantic / indicator colors (energy, gradient, error) ───────────────────
val Amber300     = Color(0xFFFCD34D)
val Amber400     = Color(0xFFFBBF24)   // energy indicator – neutral
val Amber600     = Color(0xFFD97706)
val Amber700     = Color(0xFFB45309)
val Amber900     = Color(0xFF451A03)

val Emerald300   = Color(0xFF6EE7B7)
val Emerald400   = Color(0xFF34D399)   // energy indicator – good
val Emerald700   = Color(0xFF047857)
val Emerald900   = Color(0xFF022C22)

val Rose500      = Color(0xFFF43F5E)   // energy indicator – low / gradient
val Rose900      = Color(0xFF4C0519)
val Crimson700   = Color(0xFFBE123C)   // light tertiary (deep ruby accent)

// ─── Midnight steel surfaces (dark theme) ─────────────────────────────────────
private val GunBg           = Color(0xFF080B11)   // midnight steel
private val GunSurface      = Color(0xFF0F1219)   // dark forged steel
private val GunSurfaceVar   = Color(0xFF171C27)   // mid gunmetal
private val GunOnBg         = Color(0xFFCAD3DF)   // cool silver text
private val GunOnSurface    = Color(0xFFCAD3DF)
private val GunOnSurfaceVar = Color(0xFF8496AA)   // muted steel
private val GunOutline      = Color(0xFF3A5070)
private val GunOutlineVar   = Color(0xFF162033)

// ─── Brushed steel surfaces (light theme) ─────────────────────────────────────
private val SteelBg           = Color(0xFFF0F3F8)   // cool silver with blue tint
private val SteelSurface      = Color(0xFFF5F8FD)   // near-white, faint blue
private val SteelSurfaceVar   = Color(0xFFDDE4F0)   // brushed steel
private val SteelOnBg         = Color(0xFF0A0E1A)   // near-black
private val SteelOnSurface    = Color(0xFF0A0E1A)
private val SteelOnSurfaceVar = Color(0xFF3D4E63)   // steel slate
private val SteelOutline      = Color(0xFF7A90AA)
private val SteelOutlineVar   = Color(0xFFC0CEDF)

// ─── Color schemes ─────────────────────────────────────────────────────────────
val DagbokenDarkColorScheme = darkColorScheme(
    primary              = ChromeBlue400,             // electric chrome blue
    onPrimary            = Color(0xFF000D1F),
    primaryContainer     = Color(0xFF0F2A52),         // deep navy-steel
    onPrimaryContainer   = Color(0xFFB8D9FF),         // chrome blue highlight
    secondary            = CyanSteel400,              // bright chrome cyan
    onSecondary          = Color(0xFF001B22),
    secondaryContainer   = Color(0xFF073344),         // deep teal-steel
    onSecondaryContainer = Color(0xFFA5F3FC),         // light chrome cyan
    tertiary             = Violet400,                 // chrome amethyst
    onTertiary           = Color(0xFF150030),
    tertiaryContainer    = Color(0xFF2D1B69),         // deep amethyst
    onTertiaryContainer  = Color(0xFFDDD6FE),         // light violet
    error                = Rose500,
    onError              = Color.White,
    errorContainer       = Color(0xFF4A0011),
    onErrorContainer     = Color(0xFFFFB4BB),
    background           = GunBg,
    onBackground         = GunOnBg,
    surface              = GunSurface,
    onSurface            = GunOnSurface,
    surfaceVariant       = GunSurfaceVar,
    onSurfaceVariant     = GunOnSurfaceVar,
    outline              = GunOutline,
    outlineVariant       = GunOutlineVar,
    inverseSurface       = SteelSurface,
    inverseOnSurface     = GunSurface,
    inversePrimary       = ChromeBlue700,
    surfaceTint          = ChromeBlue400,
)

val DagbokenLightColorScheme = lightColorScheme(
    primary              = ChromeBlue700,             // deep cobalt
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF1A4D98),         // rich royal blue — light enough to gradient, dark enough to feel metallic
    onPrimaryContainer   = Color(0xFFDCEBFF),         // light chrome blue
    secondary            = CyanSteel600,              // steel teal
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF0C4A62),         // rich teal (not near-black)
    onSecondaryContainer = Color(0xFFB8EEF8),         // light cyan
    tertiary             = Crimson700,                // deep ruby (no purple, warm contrast)
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFF6B0F2A),         // deep crimson (brighter than near-black)
    onTertiaryContainer  = Color(0xFFFFCDD5),         // light rose
    error                = Color(0xFFDC2626),
    onError              = Color.White,
    errorContainer       = Color(0xFF450A0A),
    onErrorContainer     = Color(0xFFFFBFBF),
    background           = SteelBg,                  // cool silver-blue
    onBackground         = SteelOnBg,
    surface              = SteelSurface,
    onSurface            = SteelOnSurface,
    surfaceVariant       = SteelSurfaceVar,
    onSurfaceVariant     = SteelOnSurfaceVar,
    outline              = SteelOutline,
    outlineVariant       = SteelOutlineVar,
    inverseSurface       = GunSurface,
    inverseOnSurface     = GunOnSurface,
    inversePrimary       = ChromeBlue400,
)

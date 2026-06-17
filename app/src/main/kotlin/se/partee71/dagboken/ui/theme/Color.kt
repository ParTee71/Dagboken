package se.partee71.dagboken.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Chrome blue family ───────────────────────────────────────────────────────
val ChromeBlue400  = Color(0xFF60A5FA)   // electric chrome blue (dark primary)
val ChromeBlue600  = Color(0xFF2563EB)   // rich cobalt
val ChromeBlue700  = Color(0xFF1D4ED8)   // deep cobalt (light primary)

// ─── Steel cyan (secondary) ───────────────────────────────────────────────────
val CyanSteel400   = Color(0xFF22D3EE)   // bright chrome cyan (dark secondary)
val CyanSteel600   = Color(0xFF0891B2)   // steel teal (light secondary)

// ─── Violet / amethyst (dark tertiary) ───────────────────────────────────────
val Violet400    = Color(0xFFA78BFA)   // chrome amethyst (dark tertiary)

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
private val SteelBg                    = Color(0xFFD9E2F0)   // brushed steel plate — clearly cool-silver
private val SteelSurface               = Color(0xFFECF2FF)   // polished chrome — light above steel bg
private val SteelSurfaceVar            = Color(0xFFC1CEDF)   // anodized steel panel — visible depth layer
private val SteelOnBg                  = Color(0xFF080C18)   // near-black
private val SteelOnSurface             = Color(0xFF080C18)
private val SteelOnSurfaceVar          = Color(0xFF354D66)   // steel-slate text
private val SteelOutline               = Color(0xFF5F7C9A)   // stronger steel edge
private val SteelOutlineVar            = Color(0xFFA4BBCE)   // crisp chrome border
// Surface container tiers — prevents M3 baseline pink bleeding in (e.g. NavigationBar)
private val SteelContainerLowest       = Color(0xFFF4F8FF)
private val SteelContainerLow          = Color(0xFFECF2FF)   // = SteelSurface
private val SteelContainer             = Color(0xFFE3EBF8)
private val SteelContainerHigh         = Color(0xFFDAE3F2)
private val SteelContainerHighest      = Color(0xFFD1DBEC)
private val SteelSurfaceDim            = Color(0xFFC8D5E6)
private val SteelSurfaceBright         = Color(0xFFF4F8FF)

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
    primaryContainer     = Color(0xFF1A4D98),         // rich royal blue
    onPrimaryContainer   = Color(0xFFDCEBFF),         // light chrome blue
    secondary            = CyanSteel600,              // steel teal
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF0C4A62),         // rich teal
    onSecondaryContainer = Color(0xFFB8EEF8),         // light cyan
    tertiary             = Crimson700,                // deep ruby accent
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFF6B0F2A),         // deep crimson
    onTertiaryContainer  = Color(0xFFFFCDD5),         // light rose
    error                = Color(0xFFDC2626),
    onError              = Color.White,
    errorContainer       = Color(0xFF450A0A),
    onErrorContainer     = Color(0xFFFFBFBF),
    background              = SteelBg,
    onBackground            = SteelOnBg,
    surface                 = SteelSurface,
    onSurface               = SteelOnSurface,
    surfaceVariant          = SteelSurfaceVar,
    onSurfaceVariant        = SteelOnSurfaceVar,
    surfaceContainerLowest  = SteelContainerLowest,
    surfaceContainerLow     = SteelContainerLow,
    surfaceContainer        = SteelContainer,
    surfaceContainerHigh    = SteelContainerHigh,
    surfaceContainerHighest = SteelContainerHighest,
    surfaceDim              = SteelSurfaceDim,
    surfaceBright           = SteelSurfaceBright,
    outline                 = SteelOutline,
    outlineVariant          = SteelOutlineVar,
    inverseSurface          = GunSurface,
    inverseOnSurface        = GunOnSurface,
    inversePrimary          = ChromeBlue400,
)

package se.partee71.dagboken.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Sunrise Garden palette ───────────────────────────────────────────────────
val Violet400    = Color(0xFFA78BFA)
val Violet500    = Color(0xFF8B5CF6)
val Violet600    = Color(0xFF7C3AED)
val Violet900    = Color(0xFF2E1065)
val VioletCont   = Color(0xFFEDE9FE)

val Amber300     = Color(0xFFFCD34D)
val Amber400     = Color(0xFFFBBF24)
val Amber600     = Color(0xFFD97706)
val Amber900     = Color(0xFF451A03)
val AmberCont    = Color(0xFFFEF3C7)

val Emerald300   = Color(0xFF6EE7B7)
val Emerald400   = Color(0xFF34D399)
val Emerald700   = Color(0xFF047857)
val Emerald900   = Color(0xFF022C22)
val EmeraldCont  = Color(0xFFD1FAE5)

val Rose500      = Color(0xFFF43F5E)
val Rose900      = Color(0xFF4C0519)
val RoseCont     = Color(0xFFFFE4E6)

// ─── Dark backgrounds (warm dark coffee) ──────────────────────────────────────
private val DarkBg           = Color(0xFF15100E)
private val DarkSurface      = Color(0xFF1E1814)
private val DarkSurfaceVar   = Color(0xFF2C2219)
private val DarkOnBg         = Color(0xFFF5EEE4)
private val DarkOnSurface    = Color(0xFFF5EEE4)
private val DarkOnSurfaceVar = Color(0xFFCDBAA6)
private val DarkOutline      = Color(0xFF7B6655)
private val DarkOutlineVar   = Color(0xFF3D2F24)

// ─── Light backgrounds (warm cream) ───────────────────────────────────────────
private val LightBg           = Color(0xFFFFF9F0)
private val LightSurface      = Color(0xFFFFFEF9)
private val LightSurfaceVar   = Color(0xFFF0EAE0)
private val LightOnBg         = Color(0xFF1C1208)
private val LightOnSurface    = Color(0xFF1C1208)
private val LightOnSurfaceVar = Color(0xFF4F3D2C)
private val LightOutline      = Color(0xFFB09880)

// ─── Color schemes ────────────────────────────────────────────────────────────
val DagbokenDarkColorScheme = darkColorScheme(
    primary              = Violet500,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF3B1D8A),
    onPrimaryContainer   = VioletCont,
    secondary            = Amber400,
    onSecondary          = Color(0xFF1A0A00),
    secondaryContainer   = Color(0xFF5C3700),
    onSecondaryContainer = AmberCont,
    tertiary             = Emerald400,
    onTertiary           = Color(0xFF002110),
    tertiaryContainer    = Color(0xFF003A20),
    onTertiaryContainer  = EmeraldCont,
    error                = Rose500,
    onError              = Color.White,
    errorContainer       = Color(0xFF530014),
    onErrorContainer     = RoseCont,
    background           = DarkBg,
    onBackground         = DarkOnBg,
    surface              = DarkSurface,
    onSurface            = DarkOnSurface,
    surfaceVariant       = DarkSurfaceVar,
    onSurfaceVariant     = DarkOnSurfaceVar,
    outline              = DarkOutline,
    outlineVariant       = DarkOutlineVar,
    inverseSurface       = LightSurface,
    inverseOnSurface     = DarkSurface,
    inversePrimary       = Violet600,
    surfaceTint          = Violet500,
)

val DagbokenLightColorScheme = lightColorScheme(
    primary              = Violet600,
    onPrimary            = Color.White,
    primaryContainer     = VioletCont,
    onPrimaryContainer   = Violet900,
    secondary            = Amber600,
    onSecondary          = Color.White,
    secondaryContainer   = AmberCont,
    onSecondaryContainer = Amber900,
    tertiary             = Emerald700,
    onTertiary           = Color.White,
    tertiaryContainer    = EmeraldCont,
    onTertiaryContainer  = Emerald900,
    error                = Rose500,
    onError              = Color.White,
    errorContainer       = RoseCont,
    onErrorContainer     = Rose900,
    background           = LightBg,
    onBackground         = LightOnBg,
    surface              = LightSurface,
    onSurface            = LightOnSurface,
    surfaceVariant       = LightSurfaceVar,
    onSurfaceVariant     = LightOnSurfaceVar,
    outline              = LightOutline,
    outlineVariant       = Color(0xFFD4C0AD),
    inverseSurface       = DarkSurface,
    inverseOnSurface     = DarkOnSurface,
    inversePrimary       = Violet500,
)

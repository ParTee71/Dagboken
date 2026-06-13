package se.partee71.dagboken.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── Brand palette (mirrors src/utils/theme.js) ──────────────────────────────
val Blue400    = Color(0xFF3b82f6)  // primary accent – Aktiviteter
val Blue700    = Color(0xFF1d3f7a)  // primary container dark
val Blue900    = Color(0xFF0f2448)
val BlueLight  = Color(0xFFd8e6ff)

val Cyan400    = Color(0xFF06b6d4)  // secondary – Mediciner
val Cyan800    = Color(0xFF023f4a)
val CyanLight  = Color(0xFFb9f0fc)

val Green400   = Color(0xFF34d399)  // tertiary – positive
val Green800   = Color(0xFF004d30)
val GreenLight = Color(0xFF9bf0c5)

val Red400     = Color(0xFFf87171)  // error
val Red900     = Color(0xFF4a0000)
val RedLight   = Color(0xFFffd9d9)

val Amber400   = Color(0xFFf97316)  // warning/energy
val Teal400    = Color(0xFF14b8a6)  // screening

// ─── Dark backgrounds ─────────────────────────────────────────────────────────
val DarkBg          = Color(0xFF060b0e)
val DarkSurface     = Color(0xFF0c1b2b)
val DarkSurfaceVar  = Color(0xFF132236)
val DarkOnBg        = Color(0xFFf2faff)
val DarkOnSurface   = Color(0xFFf2faff)
val DarkOnSurfaceVar= Color(0xFFa4c0d4)
val DarkOutline     = Color(0xFF2a4d6b)
val DarkOutlineVar  = Color(0xFF1a3248)

// ─── Light backgrounds ────────────────────────────────────────────────────────
val LightBg         = Color(0xFFe8f0f7)
val LightSurface    = Color(0xFFf4f8fc)
val LightSurfaceVar = Color(0xFFdde8f2)
val LightOnBg       = Color(0xFF1a2535)
val LightOnSurface  = Color(0xFF1a2535)
val LightOnSurfaceVar = Color(0xFF415b75)
val LightOutline    = Color(0xFF9ab8cc)

// ─── Color schemes ────────────────────────────────────────────────────────────
val DagbokenDarkColorScheme = darkColorScheme(
    primary              = Blue400,
    onPrimary            = Color.White,
    primaryContainer     = Blue700,
    onPrimaryContainer   = BlueLight,
    secondary            = Cyan400,
    onSecondary          = Color.White,
    secondaryContainer   = Cyan800,
    onSecondaryContainer = CyanLight,
    tertiary             = Green400,
    onTertiary           = Color(0xFF002818),
    tertiaryContainer    = Green800,
    onTertiaryContainer  = GreenLight,
    error                = Red400,
    onError              = Color(0xFF280000),
    errorContainer       = Red900,
    onErrorContainer     = RedLight,
    background           = DarkBg,
    onBackground         = DarkOnBg,
    surface              = DarkSurface,
    onSurface            = DarkOnSurface,
    surfaceVariant       = DarkSurfaceVar,
    onSurfaceVariant     = DarkOnSurfaceVar,
    outline              = DarkOutline,
    outlineVariant       = DarkOutlineVar,
    inverseSurface       = Color(0xFFd8e6f0),
    inverseOnSurface     = DarkBg,
    inversePrimary       = Blue700,
    surfaceTint          = Blue400,
)

val DagbokenLightColorScheme = lightColorScheme(
    primary              = Color(0xFF3178d6),
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFd8e6ff),
    onPrimaryContainer   = Color(0xFF0a2452),
    secondary            = Color(0xFF0284c7),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFcceeff),
    onSecondaryContainer = Color(0xFF00334d),
    tertiary             = Color(0xFF059669),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFb7f5e0),
    onTertiaryContainer  = Color(0xFF002818),
    error                = Color(0xFFdc2626),
    onError              = Color.White,
    errorContainer       = Color(0xFFffd9d9),
    onErrorContainer     = Color(0xFF410002),
    background           = LightBg,
    onBackground         = LightOnBg,
    surface              = LightSurface,
    onSurface            = LightOnSurface,
    surfaceVariant       = LightSurfaceVar,
    onSurfaceVariant     = LightOnSurfaceVar,
    outline              = LightOutline,
    outlineVariant       = Color(0xFFc4d8e8),
    inverseSurface       = DarkSurface,
    inverseOnSurface     = DarkOnSurface,
    inversePrimary       = Blue400,
)

package se.partee71.dagboken.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Färgtoner för pills, sektionsrubriker och kort – en per betydelse (skill `ui-style`, DSN-1). */
enum class Tone {
    /** Teal – det markerade, valda och aktiva. */
    Primary,

    /** Dämpad – information utan värdering, t.ex. "Synkas…" och typ-pills. */
    Neutral,

    /** Solgul – gör-något: "Snart", dagens punkt, framstegsraden när dagen är klar. */
    Sun,

    /** Grön – klart och positivt, t.ex. dagen klar. */
    Positive,

    /** Terrakotta – varning: försenat, periodslut. */
    Warning,
}

/** Behållare och innehåll för en [Tone]; innehållet klarar 4,5:1 mot behållaren. */
@Immutable
data class ToneColors(val container: Color, val content: Color)

/** Energiskalan (DSN-1): låg, mitt och hög – för diagram och energimarkeringar, inte för text. */
@Immutable
data class EnergyColors(val low: Color, val mid: Color, val high: Color)

/**
 * Sömnstadierna i det staplade sömndiagrammet (TRD-16): djup, REM, lätt och vaken – bara för grafik,
 * aldrig för text. Djupsömnen är mest mättad; vaken tid är dämpad, inte solgul (solgult är gör-något).
 * Alla fyra klarar 3:1 mot kortet i båda temana (WCAG 1.4.11, `ThemeContrastTest`).
 */
@Immutable
data class SleepStageColors(val deep: Color, val light: Color, val rem: Color, val awake: Color)

/**
 * Appens färger utöver Material 3-rollerna: kort, radtoning, spår, verktygsrad, toner, energiskalan och
 * sömnstadierna. [track] är det ofyllda spåret i allt som visar framsteg (framstegsraden, stegprickarna).
 */
@Immutable
data class ExtendedColors(
    val card: Color,
    val rowTint: Color,
    val track: Color,
    val toolbar: Color,
    val onToolbar: Color,
    val primaryTone: ToneColors,
    val neutralTone: ToneColors,
    val sunTone: ToneColors,
    val positiveTone: ToneColors,
    val warningTone: ToneColors,
    val energy: EnergyColors,
    val sleepStages: SleepStageColors,
)

/**
 * Den enda paletten (skill `ui-style`, ARKITEKTUR.md → Designspråk "I · Papper och teal"): teal på
 * papper, solgul som gör-något-färg, terrakotta för varning; mörkt tema med samma roller på djup
 * skogsgrön botten. Ingen dynamic color – appen har en egen identitet. Kontrasten kontrolleras av
 * `ThemeContrastTest`. Terrakottan är något mörkare än mockupens `#B85C38` (`#A4502E`), som annars
 * inte klarar 4,5:1 som text på papper.
 */
object AppColors {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF0B6E66),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3EDE9),
        onPrimaryContainer = Color(0xFF00413C),
        inversePrimary = Color(0xFF7FD3C8),
        secondary = Color(0xFFF5B631),
        onSecondary = Color(0xFF1C1A14),
        secondaryContainer = Color(0xFFFCEBC4),
        onSecondaryContainer = Color(0xFF4D3500),
        tertiary = Color(0xFFA4502E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF8E1D6),
        onTertiaryContainer = Color(0xFF7A3519),
        background = Color(0xFFFBF7EE),
        onBackground = Color(0xFF1C1A14),
        surface = Color(0xFFFBF7EE),
        onSurface = Color(0xFF1C1A14),
        surfaceVariant = Color(0xFFF1EBDD),
        onSurfaceVariant = Color(0xFF6B655A),
        surfaceTint = Color(0xFF0B6E66),
        inverseSurface = Color(0xFF2E2B24),
        inverseOnSurface = Color(0xFFF3F0E8),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF8C1D18),
        outline = Color(0xFF8A8273),
        outlineVariant = Color(0xFFE6DFCF),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFBF7EE),
        surfaceDim = Color(0xFFEDE7D9),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F2E6),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFFFFFFF),
        surfaceContainerHighest = Color(0xFFF1EBDD),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF7FD3C8),
        onPrimary = Color(0xFF003731),
        primaryContainer = Color(0xFF0B4F49),
        onPrimaryContainer = Color(0xFFBFEDE6),
        inversePrimary = Color(0xFF0B6E66),
        secondary = Color(0xFFF5B631),
        onSecondary = Color(0xFF1C1A14),
        secondaryContainer = Color(0xFF5A4100),
        onSecondaryContainer = Color(0xFFFCEBC4),
        tertiary = Color(0xFFF0A27E),
        onTertiary = Color(0xFF4A1800),
        tertiaryContainer = Color(0xFF6A2F17),
        onTertiaryContainer = Color(0xFFFFD9C9),
        background = Color(0xFF0F1C17),
        onBackground = Color(0xFFE9EEE7),
        surface = Color(0xFF0F1C17),
        onSurface = Color(0xFFE9EEE7),
        surfaceVariant = Color(0xFF1E3029),
        onSurfaceVariant = Color(0xFFAEB9AF),
        surfaceTint = Color(0xFF7FD3C8),
        inverseSurface = Color(0xFFE9EEE7),
        inverseOnSurface = Color(0xFF1C2B25),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF601410),
        onErrorContainer = Color(0xFFF9DEDC),
        outline = Color(0xFF7F8C83),
        outlineVariant = Color(0xFF2C3F37),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF243630),
        surfaceDim = Color(0xFF0F1C17),
        surfaceContainerLowest = Color(0xFF0A1511),
        surfaceContainerLow = Color(0xFF14231D),
        surfaceContainer = Color(0xFF182822),
        surfaceContainerHigh = Color(0xFF182822),
        surfaceContainerHighest = Color(0xFF1E3029),
    )

    val lightExtended = ExtendedColors(
        card = Color(0xFFFFFFFF),
        rowTint = Color(0xFFF4EFE3),
        track = Color(0xFFD3EDE9),
        toolbar = Color(0xFF16302B),
        onToolbar = Color(0xFFF3F1EA),
        primaryTone = ToneColors(Color(0xFFD3EDE9), Color(0xFF00413C)),
        neutralTone = ToneColors(Color(0xFFEFEADF), Color(0xFF4F4A40)),
        sunTone = ToneColors(Color(0xFFFCEBC4), Color(0xFF4D3500)),
        positiveTone = ToneColors(Color(0xFFDCEFE3), Color(0xFF1F5A40)),
        warningTone = ToneColors(Color(0xFFF8E1D6), Color(0xFF7A3519)),
        energy = EnergyColors(low = Color(0xFFB5443A), mid = Color(0xFFC98A1B), high = Color(0xFF2F7D5B)),
        sleepStages = SleepStageColors(deep = Color(0xFF0B4F49), light = Color(0xFF4E9A90), rem = Color(0xFF3B6EA5), awake = Color(0xFF958B78)),
    )

    val darkExtended = ExtendedColors(
        card = Color(0xFF182822),
        rowTint = Color(0xFF1E3029),
        track = Color(0xFF0B4F49),
        toolbar = Color(0xFFE9EEE7),
        onToolbar = Color(0xFF0F1C17),
        primaryTone = ToneColors(Color(0xFF0B4F49), Color(0xFFBFEDE6)),
        neutralTone = ToneColors(Color(0xFF2A3A33), Color(0xFFDCE3DA)),
        sunTone = ToneColors(Color(0xFF5A4100), Color(0xFFFCEBC4)),
        positiveTone = ToneColors(Color(0xFF1C4A35), Color(0xFFC3EBD3)),
        warningTone = ToneColors(Color(0xFF6A2F17), Color(0xFFFFD9C9)),
        energy = EnergyColors(low = Color(0xFFE38A7F), mid = Color(0xFFE9B54F), high = Color(0xFF6CC79A)),
        sleepStages = SleepStageColors(deep = Color(0xFF7FD3C8), light = Color(0xFF3F8F86), rem = Color(0xFF8FB4E0), awake = Color(0xFF8E897B)),
    )

    /** Färgerna man kan välja bland (`ColorSwatchPicker`) som hex – det som lagras. Harmonierar med paletten. */
    val SWATCH_HEX: List<String> = listOf(
        "#0B6E66", "#F5B631", "#A4502E", "#2F7D5B", "#3B6EA5", "#8E5BA8", "#C98A1B", "#B5443A",
    )

    /** Valbar färg nummer [index] (varvar efter åtta). */
    fun swatch(index: Int): Color = parse(SWATCH_HEX[Math.floorMod(index, SWATCH_HEX.size)])

    /** Den valbara färgen för ett lagrat hex-värde; ett oläsbart värde ger första förvalet. */
    fun swatch(hex: String): Color = runCatching { parse(hex) }.getOrElse { swatch(0) }

    private fun parse(hex: String): Color {
        val digits = hex.removePrefix("#")
        require(digits.length == 6) { "Förväntade #RRGGBB" }
        return Color(0xFF000000 or digits.toLong(16))
    }

    val extended: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current

    /** Färgerna för en [Tone] i aktuellt tema. */
    @Composable
    @ReadOnlyComposable
    fun tone(tone: Tone): ToneColors = when (tone) {
        Tone.Primary -> extended.primaryTone
        Tone.Neutral -> extended.neutralTone
        Tone.Sun -> extended.sunTone
        Tone.Positive -> extended.positiveTone
        Tone.Warning -> extended.warningTone
    }
}

internal val LocalExtendedColors = staticCompositionLocalOf { AppColors.lightExtended }

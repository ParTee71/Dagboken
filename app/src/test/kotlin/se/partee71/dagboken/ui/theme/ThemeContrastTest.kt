package se.partee71.dagboken.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Varje färg som bär text klarar 4,5:1 mot sin bakgrund, i båda temana (skill `ui-style`, DSN-1, DSN-5). */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun pairs(scheme: ColorScheme, extended: ExtendedColors) = mapOf(
        "text på bakgrund" to (scheme.onBackground to scheme.background),
        "dämpad text på bakgrund" to (scheme.onSurfaceVariant to scheme.background),
        "text på kort" to (scheme.onSurface to extended.card),
        "dämpad text på kort" to (scheme.onSurfaceVariant to extended.card),
        "dämpad text på tonad rad" to (scheme.onSurfaceVariant to extended.rowTint),
        "primär text på kort" to (scheme.primary to extended.card),
        "primär text på bakgrund" to (scheme.primary to scheme.background),
        "text på primärknapp" to (scheme.onPrimary to scheme.primary),
        "text på primärbehållare" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "text på sekundärknapp" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
        "text på gör-något-knappen (solgul)" to (scheme.onSecondary to scheme.secondary),
        "varning på kort" to (scheme.tertiary to extended.card),
        "varning på bakgrund" to (scheme.tertiary to scheme.background),
        "text på varningsknapp" to (scheme.onTertiary to scheme.tertiary),
        "text på varningsbehållare" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
        "felknapp" to (scheme.onError to scheme.error),
        "fel på kort" to (scheme.error to extended.card),
        "text på felbehållare" to (scheme.onErrorContainer to scheme.errorContainer),
        "snackbar" to (scheme.inverseOnSurface to scheme.inverseSurface),
        "verktygsrad" to (extended.onToolbar to extended.toolbar),
        "tealton" to (extended.primaryTone.content to extended.primaryTone.container),
        "dämpad ton" to (extended.neutralTone.content to extended.neutralTone.container),
        "solgul ton" to (extended.sunTone.content to extended.sunTone.container),
        "grön ton" to (extended.positiveTone.content to extended.positiveTone.container),
        "terrakottaton" to (extended.warningTone.content to extended.warningTone.container),
    )

    @Test
    fun `ljust tema`() = check("ljust", pairs(AppColors.light, AppColors.lightExtended))

    @Test
    fun `mörkt tema`() = check("mörkt", pairs(AppColors.dark, AppColors.darkExtended))

    /**
     * Idags komponenter (etapp 4.2): tonerna mot sin text där de bär text – "Snart", "Försenat",
     * värdechipsen och det gröna kortet "Allt klart för idag" – och grafiken (3:1, WCAG 1.4.11) som bär
     * läget i datumremsan och framstegsraden. Statusen står alltid också som text.
     */
    private fun todayPairs(scheme: ColorScheme, extended: ExtendedColors) = mapOf(
        "Snart (solgul ton)" to (extended.sunTone.content to extended.sunTone.container),
        "Försenat (terrakottaton)" to (extended.warningTone.content to extended.warningTone.container),
        "Allt klart för idag (grön ton)" to (extended.positiveTone.content to extended.positiveTone.container),
        "Logga nu (primär knapp)" to (scheme.onPrimary to scheme.primary),
        "valt datumchip" to (scheme.onPrimary to scheme.primary),
        "veckodag på datumchip" to (scheme.onSurfaceVariant to extended.card),
        "initialer i avataren" to (scheme.onPrimaryContainer to scheme.primaryContainer),
    )

    private fun todayGraphics(scheme: ColorScheme, extended: ExtendedColors) = mapOf(
        "valt datumchip mot bakgrund" to (scheme.primary to scheme.background),
        "punkt och bock på datumchip" to (scheme.primary to extended.card),
        "punkt, bock och idag-ring på valt datumchip" to (scheme.onPrimary to scheme.primary),
        "idag-ringen kring solgul punkt mot kortet" to (extended.sunTone.content to extended.card),
        "framstegsradens fyllnad mot spåret" to (scheme.primary to scheme.primaryContainer),
        "framstegsradens kontur vid klart mot kortet" to (extended.sunTone.content to extended.card),
        "framstegsradens kontur vid klart mot bakgrunden" to (extended.sunTone.content to scheme.background),
    )

    @Test
    fun `Idags komponenter - ljust tema`() {
        check("ljust", todayPairs(AppColors.light, AppColors.lightExtended))
        check("ljust", todayGraphics(AppColors.light, AppColors.lightExtended), GRAPHICS_MIN)
    }

    @Test
    fun `Idags komponenter - mörkt tema`() {
        check("mörkt", todayPairs(AppColors.dark, AppColors.darkExtended))
        check("mörkt", todayGraphics(AppColors.dark, AppColors.darkExtended), GRAPHICS_MIN)
    }

    @Test
    fun `valbara färger varvar och ett oläsbart värde ger första förvalet`() {
        assertTrue(AppColors.swatch(8) == AppColors.swatch(0))
        assertTrue(AppColors.swatch("inte en färg") == AppColors.swatch(0))
        assertTrue(AppColors.swatch("#F5B631") == AppColors.swatch(1))
    }

    @Test
    fun `Papper och teal - mockupens nyckelfärger (ARKITEKTUR-md, Designspråk)`() {
        assertEquals(Color(0xFFFBF7EE), AppColors.light.background, "papper")
        assertEquals(Color(0xFFFFFFFF), AppColors.lightExtended.card, "vita kort")
        assertEquals(Color(0xFF0B6E66), AppColors.light.primary, "teal")
        assertEquals(Color(0xFFF5B631), AppColors.light.secondary, "solgul")
        assertEquals(Color(0xFF1C1A14), AppColors.light.onBackground, "text")
        assertEquals(Color(0xFF6B655A), AppColors.light.onSurfaceVariant, "dämpad")
        assertEquals(
            EnergyColors(Color(0xFFB5443A), Color(0xFFC98A1B), Color(0xFF2F7D5B)),
            AppColors.lightExtended.energy,
            "energiskalan",
        )
    }

    private fun check(theme: String, pairs: Map<String, Pair<Color, Color>>, min: Double = MIN) {
        val failures = pairs.mapNotNull { (name, colors) ->
            val ratio = contrast(colors.first, colors.second)
            "$theme: $name har ${"%.2f".format(ratio)}:1".takeIf { ratio < min }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private companion object {
        const val MIN = 4.5

        /** Grafik som bär information (WCAG 1.4.11). */
        const val GRAPHICS_MIN = 3.0
    }
}

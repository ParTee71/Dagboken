package se.partee71.dagboken.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings

/** Inställningsarkets regler: temats timmar (SET-1, SET-2, DSN-5) och alternativens dubbletter (SET-5, SET-6, SET-9). */
class SettingsRulesTest {

    private fun auto(light: Int = 7, dark: Int = 21) = ThemeSettings(mode = ThemeMode.AUTO, lightStartHour = light, darkStartHour = dark)

    @Test fun `ljust och mörkt gäller dygnet runt`() {
        for (hour in 0..23) {
            assertFalse(ThemeSettings(mode = ThemeMode.LIGHT).isDarkAt(hour))
            assertTrue(ThemeSettings(mode = ThemeMode.DARK).isDarkAt(hour))
        }
    }

    @Test fun `auto är ljust från ljusstarten och mörkt från mörkerstarten`() {
        val theme = auto(light = 7, dark = 21)
        assertEquals(
            (0..23).map { it < 7 || it >= 21 },
            (0..23).map(theme::isDarkAt),
        )
    }

    @Test fun `auto byter exakt på starttimmen`() {
        val theme = auto(light = 6, dark = 18)
        assertTrue(theme.isDarkAt(5))
        assertFalse(theme.isDarkAt(6))
        assertFalse(theme.isDarkAt(17))
        assertTrue(theme.isDarkAt(18))
    }

    @Test fun `ljus före mörk är giltigt, lika eller omvänt är det inte (SET-2)`() {
        assertTrue(auto(7, 21).hasValidHours())
        assertTrue(auto(0, 23).hasValidHours())
        assertFalse(auto(21, 21).hasValidHours())
        assertFalse(auto(22, 6).hasValidHours())
        assertFalse(auto(-1, 21).hasValidHours())
        assertFalse(auto(7, 24).hasValidHours())
    }

    @Test fun `ogiltiga timmar ger standardtimmarna i stället för ett tema som aldrig byter`() {
        val broken = auto(light = 22, dark = 6)
        val standard = ThemeSettings()
        assertEquals((0..23).map(standard::isDarkAt), (0..23).map(broken::isDarkAt))
    }

    private val options = listOf(
        Option("a", OptionKind.ACTIVITY, "Promenad"),
        Option("b", OptionKind.ACTIVITY, "Yoga", archived = true),
    )

    @Test fun `samma namn oavsett skiftläge och mellanslag är en dubblett`() {
        assertTrue(options.hasActiveName("Promenad"))
        assertTrue(options.hasActiveName("  promenad "))
        assertFalse(options.hasActiveName("Promenader"))
    }

    @Test fun `arkiverade räknas inte och namnbytet jämförs inte med sig självt`() {
        assertFalse(options.hasActiveName("Yoga"))
        assertFalse(options.hasActiveName("PROMENAD", exceptId = "a"))
    }
}

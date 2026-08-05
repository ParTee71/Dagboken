package se.partee71.dagboken.ui.health

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Enhetstest för sträckformateringen på Hälsa-skärmen (HLS-8, §19). */
class FormatKilometersTest {

    private val swedish = Locale.forLanguageTag("sv-SE")
    private val english = Locale.forLanguageTag("en-US")

    @Test fun `formats metres as kilometres with one decimal`() {
        assertEquals("5,2", formatKilometers(5234.0, swedish))
    }

    @Test fun `uses the locale decimal separator`() {
        assertEquals("5.2", formatKilometers(5234.0, english))
    }

    @Test fun `short distances keep the leading zero`() {
        assertEquals("0,4", formatKilometers(430.0, swedish))
    }

    @Test fun `rounds to the nearest hundred metres`() {
        assertEquals("1,0", formatKilometers(963.0, swedish))
    }
}

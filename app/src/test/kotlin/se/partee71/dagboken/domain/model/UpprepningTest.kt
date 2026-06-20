package se.partee71.dagboken.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UpprepningTest {

    @Test fun `dagligen is the default for unknown strings`() {
        assertEquals(Upprepning.DAGLIGEN, Upprepning.fromString("okänd"))
        assertEquals(Upprepning.DAGLIGEN, Upprepning.fromString(""))
        assertEquals(Upprepning.DAGLIGEN, Upprepning.fromString("dagligen"))
    }

    @Test fun `vardagar maps correctly`() {
        assertEquals(Upprepning.VARDAGAR, Upprepning.fromString("vardagar"))
        assertEquals(Upprepning.VARDAGAR, Upprepning.fromString("VARDAGAR"))
    }

    @Test fun `helger maps correctly`() {
        assertEquals(Upprepning.HELGER, Upprepning.fromString("helger"))
        assertEquals(Upprepning.HELGER, Upprepning.fromString("Helger"))
    }

    @Test fun `anpassad maps for both Swedish variants`() {
        assertEquals(Upprepning.ANPASSAD, Upprepning.fromString("anpassad"))
        assertEquals(Upprepning.ANPASSAD, Upprepning.fromString("specifika dagar"))
        assertEquals(Upprepning.ANPASSAD, Upprepning.fromString("Specifika Dagar"))
    }

    @Test fun `intervall maps for both Swedish variants`() {
        assertEquals(Upprepning.INTERVALL, Upprepning.fromString("intervall"))
        assertEquals(Upprepning.INTERVALL, Upprepning.fromString("var x:e dag"))
        assertEquals(Upprepning.INTERVALL, Upprepning.fromString("VAR X:E DAG"))
    }

    @Test fun `fromString is case-insensitive for all values`() {
        assertEquals(Upprepning.DAGLIGEN,  Upprepning.fromString("DAGLIGEN"))
        assertEquals(Upprepning.VARDAGAR,  Upprepning.fromString("Vardagar"))
        assertEquals(Upprepning.HELGER,    Upprepning.fromString("HELGER"))
        assertEquals(Upprepning.ANPASSAD,  Upprepning.fromString("ANPASSAD"))
        assertEquals(Upprepning.INTERVALL, Upprepning.fromString("INTERVALL"))
    }
}

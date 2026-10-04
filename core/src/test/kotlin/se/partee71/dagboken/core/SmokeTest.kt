package se.partee71.dagboken.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

/** Bevisar att :core bygger och att dess beroenden (serialisering, datum) går att använda. */
class SmokeTest {

    @Test
    fun `datum serialiseras som ISO-text`() {
        val date = LocalDate(2026, 9, 30)

        val json = Json.encodeToString(LocalDate.serializer(), date)

        assertEquals("\"2026-09-30\"", json)
        assertEquals(date, Json.decodeFromString(LocalDate.serializer(), json))
    }
}

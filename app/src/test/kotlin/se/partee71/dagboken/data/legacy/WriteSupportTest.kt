package se.partee71.dagboken.data.legacy

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test
import se.partee71.dagboken.data.common.DataError

/** Importens likhet mot servern (BCK-14): den kanoniska hashen, "landat" efter en merge och felkoderna. Syntetisk data. */
class WriteSupportTest {

    @Test
    fun `hashen är oberoende av nyckelordning - också i mappar inne i listor - och tal normaliseras`() {
        val a = mapOf("name" to "x", "boosts" to listOf(mapOf("from" to "2026-01-01", "to" to "2026-01-05", "dose" to "2")), "schedule" to mapOf("kind" to "daily", "days" to listOf(1, 2)))
        val b = mapOf("schedule" to mapOf("days" to listOf(1L, 2L), "kind" to "daily"), "boosts" to listOf(mapOf("dose" to "2", "to" to "2026-01-05", "from" to "2026-01-01")), "name" to "x")
        assertEquals(hashOf(a), hashOf(b))
        assertNotEquals(hashOf(a), hashOf(a + ("boosts" to listOf(mapOf("from" to "2026-01-01", "to" to "2026-01-05", "dose" to "3")))))
        assertNotEquals(hashOf(a), hashOf(a - "name"), "ett tömt fält är en ändring")
        assertEquals(64, hashOf(a).length)
    }

    @Test
    fun `landat - varje skrivet fält har samma värde på servern, fält bara på servern rör inte svaret`() {
        val written = mapOf("name" to "x", "theme" to mapOf("lightStartHour" to 7))
        assertTrue(landed(mapOf("name" to "x", "theme" to mapOf("lightStartHour" to 7L, "framtida" to 1), "extra" to true), written))
        assertFalse(landed(mapOf("name" to "x"), written), "en nästlad grupp som saknas")
        assertFalse(landed(mapOf("name" to "y", "theme" to mapOf("lightStartHour" to 7)), written), "ett annat värde")
    }

    @Test
    fun `felkoderna är Firestores namn, aldrig innehåll`() {
        assertEquals("PERMISSION_DENIED", codeOf(DataError.PermissionDenied))
        assertEquals("RESOURCE_EXHAUSTED", codeOf(DataError.QuotaExceeded))
        assertEquals("UNAVAILABLE", codeOf(DataError.Offline))
        assertEquals("UNKNOWN", codeOf(DataError.Unknown))
    }
}

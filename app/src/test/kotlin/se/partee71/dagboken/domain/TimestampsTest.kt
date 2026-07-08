package se.partee71.dagboken.domain

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampsTest {

    @Test fun `converts local date and time using the given zone offset`() {
        val zone = ZoneId.of("Europe/Stockholm") // UTC+2 in July (DST)
        val result = Timestamps.of("2026-07-08", "14:30", zone)
        assertEquals("2026-07-08T12:30:00Z", result)
    }

    @Test fun `is parseable back to the same instant with Instant-parse`() {
        val zone = ZoneId.of("Europe/Stockholm")
        val result = Timestamps.of("2026-07-08", "14:30", zone)
        val parsed = Instant.parse(result)
        assertEquals(Instant.parse("2026-07-08T12:30:00Z"), parsed)
    }

    @Test fun `UTC zone leaves the wall clock time unchanged`() {
        val result = Timestamps.of("2026-01-01", "00:00", ZoneId.of("UTC"))
        assertEquals("2026-01-01T00:00:00Z", result)
    }
}

package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate
import org.junit.Test

/** Klockdatans datatyper (HLS-12): ett dygn utan mätning är en lucka, och historiken har ett dygn per datum. */
class HealthTest {

    private val from = LocalDate(2026, 10, 1)
    private val to = LocalDate(2026, 10, 4)

    @Test
    fun `ett dygn utan något mått är tomt, ett enda mått räcker för att inte vara det`() {
        assertTrue(DailyHealth(from).isEmpty)
        assertTrue(SleepStages().isEmpty)
        assertFalse(DailyHealth(from, steps = 0).isEmpty, "noll steg är en mätning, inte en lucka")
        assertFalse(DailyHealth(from, sleepStages = SleepStages(deep = 1.hours)).isEmpty)
        assertFalse(DailyHealth(from, bloodPressure = BloodPressure(120, 80)).isEmpty)
    }

    @Test
    fun `historiken har ett dygn per datum med luckor där mätning saknas`() {
        val history = HealthHistory.of(from, to, mapOf(LocalDate(2026, 10, 2) to DailyHealth(LocalDate(2026, 10, 2), steps = 8_000, sleepDuration = 7.hours + 30.minutes)))
        assertEquals(listOf(1, 2, 3, 4), history.dates.map { it.day })
        assertEquals(listOf(null, 8_000f, null, null), history.series { it.steps })
        assertEquals(listOf(null, 7.5f, null, null), history.series { it.sleepDuration?.inWholeMinutes?.toFloat()?.div(60) })
        assertTrue(history.hasAnyData)
        assertFalse(HealthHistory.empty(from, to).hasAnyData)
        assertEquals(4, HealthHistory.empty(from, to).days.size)
        assertEquals(emptyList(), HealthHistory.empty(to, from).days, "baklänges är tomt")
    }

    @Test
    fun `en mätning på fel datum i kartan hamnar på kartans datum`() {
        val history = HealthHistory.of(from, from, mapOf(from to DailyHealth(to, steps = 10)))
        assertEquals(from, history.days.single().date)
    }
}

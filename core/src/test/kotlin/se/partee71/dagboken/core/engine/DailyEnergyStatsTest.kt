package se.partee71.dagboken.core.engine

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Screening

/**
 * Portad från 3.x `domain/usecase/DailyEnergyStatsTest` (HEM-7, TRD-8). I 4.0 är screeningarna en
 * egen samling, så 3.x-testet "non-screening entries are ignored" ersätts av att en screening utan
 * datum inte hör till någon dag.
 */
class DailyEnergyStatsTest {

    private fun screening(id: String, date: LocalDate?, energy: Int, occasion: Occasion = Occasion.LUNCH) = Screening(
        id = id,
        date = date,
        time = LocalTime(9, 0),
        occasion = occasion,
        energy = energy,
        stress = 3,
    )

    private val day = LocalDate(2026, 7, 10)

    @Test fun `empty input yields empty result`() {
        assertEquals(emptyList<DailyEnergyStats>(), computeDailyEnergyStats(emptyList()))
    }

    @Test fun `a screening without a date belongs to no day`() {
        val result = computeDailyEnergyStats(listOf(screening("s0", null, energy = 5)))
        assertTrue(result.isEmpty())
    }

    @Test fun `a single screening sets min, avg and max to the same value`() {
        val result = computeDailyEnergyStats(listOf(screening("s1", day, energy = 6)))
        assertEquals(1, result.size)
        assertEquals(6f, result[0].min)
        assertEquals(6f, result[0].avg)
        assertEquals(6f, result[0].max)
    }

    @Test fun `multiple screenings the same day compute min, average and max`() {
        val result = computeDailyEnergyStats(
            listOf(
                screening("s1", day, energy = 2, occasion = Occasion.BREAKFAST),
                screening("s2", day, energy = 8, occasion = Occasion.LUNCH),
                screening("s3", day, energy = 5, occasion = Occasion.DINNER),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(2f, result[0].min)
        assertEquals(5f, result[0].avg, 0.001f)
        assertEquals(8f, result[0].max)
    }

    @Test fun `days are sorted ascending by date`() {
        val result = computeDailyEnergyStats(
            listOf(
                screening("s1", LocalDate(2026, 7, 12), energy = 5),
                screening("s2", LocalDate(2026, 7, 10), energy = 5),
                screening("s3", LocalDate(2026, 7, 11), energy = 5),
            ),
        )
        assertEquals(listOf(LocalDate(2026, 7, 10), LocalDate(2026, 7, 11), LocalDate(2026, 7, 12)), result.map { it.date })
    }

    @Test fun `matches the average that HomeViewModel previously computed inline`() {
        // Regressionsskydd (3.x #141): dagsvärdet (avg) ska förbli identiskt med den tidigare
        // inline-uträkningen i HomeViewModel (screeningDailyAvg) efter extraktionen.
        val screenings = listOf(
            screening("s1", day, energy = 3),
            screening("s2", day, energy = 7),
        )
        val previousInlineAvg = screenings
            .groupBy { it.date }
            .entries
            .map { (_, entries) -> entries.map { it.energy.toFloat() }.average().toFloat() }

        val result = computeDailyEnergyStats(screenings)
        assertEquals(previousInlineAvg, result.map { it.avg })
    }

    // ─── Kantfall med fasta datum ────────────────────────────────────────────

    @Test fun `the average of a day is not rounded, it is the exact mean`() {
        val result = computeDailyEnergyStats(
            listOf(screening("a", day, energy = 3), screening("b", day, energy = 4), screening("c", day, energy = 4)),
        )
        assertEquals(11f / 3f, result.single().avg, 0.0001f)
    }

    @Test fun `zero energy is a value, not a missing day`() {
        val result = computeDailyEnergyStats(listOf(screening("a", day, energy = 0)))
        assertEquals(0f, result.single().min)
        assertEquals(0f, result.single().avg)
    }

    @Test fun `days are grouped by calendar date across a month and a year boundary`() {
        val result = computeDailyEnergyStats(
            listOf(
                screening("a", LocalDate(2027, 1, 1), energy = 4),
                screening("b", LocalDate(2026, 12, 31), energy = 6),
                screening("c", LocalDate(2026, 12, 31), energy = 8),
            ),
        )
        assertEquals(listOf(LocalDate(2026, 12, 31), LocalDate(2027, 1, 1)), result.map { it.date })
        assertEquals(7f, result[0].avg)
    }

    @Test fun `the daylight saving change does not split or merge a day`() {
        // Sommartid börjar söndag 29 mars 2026 i Sverige – dagen ska ändå vara en dag.
        val dst = LocalDate(2026, 3, 29)
        val result = computeDailyEnergyStats(
            listOf(
                Screening("a", date = dst, time = LocalTime(1, 30), energy = 2),
                Screening("b", date = dst, time = LocalTime(3, 30), energy = 4),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(3f, result.single().avg)
    }

    @Test fun `alignTo lays the days out with a gap where nothing was logged`() {
        val stats = computeDailyEnergyStats(
            listOf(screening("a", LocalDate(2026, 10, 1), energy = 4), screening("b", LocalDate(2026, 10, 3), energy = 6)),
        )
        val week = (0..2).map { LocalDate(2026, 10, 1 + it) }
        val aligned = stats.alignTo(week)
        assertEquals(4f, aligned[0]!!.avg)
        assertNull(aligned[1])
        assertEquals(6f, aligned[2]!!.avg)
    }
}

package se.partee71.dagboken.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.partee71.dagboken.domain.model.Aktivitet

class DailyEnergyStatsTest {

    private fun screening(id: String, datum: String, energy: Int, slot: String = "Lunch") = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = slot, energy = energy, stress = 3, somatiska = 0,
        symptom = "", type = "screening", spentTime = 0,
    )

    private fun aktivitet(id: String, datum: String, energy: Int) = Aktivitet(
        id = id, timestamp = "${datum}T09:00:00.000Z", datum = datum, tid = "09:00",
        aktivitet = "Promenad", energy = energy, stress = 3, somatiska = 0,
        symptom = "", type = "aktivitet", spentTime = 0,
    )

    @Test fun `empty input yields empty result`() {
        assertEquals(emptyList<DailyEnergyStats>(), computeDailyEnergyStats(emptyList()))
    }

    @Test fun `non-screening entries are ignored`() {
        val result = computeDailyEnergyStats(listOf(aktivitet("a1", "2026-07-10", energy = 5)))
        assertTrue(result.isEmpty())
    }

    @Test fun `a single screening sets min, avg and max to the same value`() {
        val result = computeDailyEnergyStats(listOf(screening("s1", "2026-07-10", energy = 6)))
        assertEquals(1, result.size)
        assertEquals(6f, result[0].min)
        assertEquals(6f, result[0].avg)
        assertEquals(6f, result[0].max)
    }

    @Test fun `multiple screenings the same day compute min, average and max`() {
        val result = computeDailyEnergyStats(
            listOf(
                screening("s1", "2026-07-10", energy = 2, slot = "Efter frukost"),
                screening("s2", "2026-07-10", energy = 8, slot = "Lunch"),
                screening("s3", "2026-07-10", energy = 5, slot = "Kvällsmat"),
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
                screening("s1", "2026-07-12", energy = 5),
                screening("s2", "2026-07-10", energy = 5),
                screening("s3", "2026-07-11", energy = 5),
            ),
        )
        assertEquals(listOf("2026-07-10", "2026-07-11", "2026-07-12"), result.map { it.datum })
    }

    @Test fun `matches the average that HomeViewModel previously computed inline`() {
        // Regressionsskydd (#141): dagsvärdet (avg) ska förbli identiskt med den tidigare
        // inline-uträkningen i HomeViewModel (screeningDailyAvg) efter extraktionen.
        val screenings = listOf(
            screening("s1", "2026-07-10", energy = 3),
            screening("s2", "2026-07-10", energy = 7),
        )
        val previousInlineAvg = screenings
            .groupBy { it.datum }
            .entries
            .sortedBy { it.key }
            .map { (datum, entries) -> datum to entries.map { it.energy.toFloat() }.average().toFloat() }

        val result = computeDailyEnergyStats(screenings)
        assertEquals(previousInlineAvg.map { it.second }, result.map { it.avg })
    }
}

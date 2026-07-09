package se.partee71.dagboken.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Medicin
import java.time.LocalDate

class WeekSummaryTest {

    private val today = LocalDate.of(2026, 7, 6)

    private fun screening(daysAgo: Long, energy: Int) = Aktivitet(
        id        = "s-$daysAgo-$energy",
        timestamp = "x",
        datum     = today.minusDays(daysAgo).toString(),
        tid       = "09:00",
        aktivitet = "Efter frukost",
        energy    = energy,
        stress    = 0,
        somatiska = 0,
        symptom   = "",
        type      = "screening",
    )

    private fun med(
        daysAgo: Long,
        tagen: Boolean,
        tidpunkt: String = "Morgon",
        skipped: Boolean = false,
    ) = Medicin(
        id        = "m-$daysAgo-$tagen-$tidpunkt-$skipped",
        timestamp = "x",
        datum     = today.minusDays(daysAgo).toString(),
        tid       = "08:00",
        namn      = "Ibuprofen",
        dos       = "400",
        enhet     = "mg",
        tidpunkt  = tidpunkt,
        tagen     = tagen,
        skipped   = skipped,
    )

    @Test fun `energy trend is UP when this week averages clearly higher`() {
        val screenings = listOf(
            screening(1, 8), screening(2, 8),   // this week ≈ 8
            screening(8, 3), screening(9, 3),   // prev week ≈ 3
        )
        assertEquals(EnergyTrend.UP, computeWeekSummary(today, screenings, emptyList())?.energyTrend)
    }

    @Test fun `energy trend is DOWN when this week averages clearly lower`() {
        val screenings = listOf(screening(1, 2), screening(8, 9))
        assertEquals(EnergyTrend.DOWN, computeWeekSummary(today, screenings, emptyList())?.energyTrend)
    }

    @Test fun `energy trend is FLAT within half a point`() {
        val screenings = listOf(screening(1, 5), screening(8, 5))
        assertEquals(EnergyTrend.FLAT, computeWeekSummary(today, screenings, emptyList())?.energyTrend)
    }

    @Test fun `energy trend is FLAT when a week has no screenings`() {
        val screenings = listOf(screening(1, 7))   // only this week
        assertEquals(EnergyTrend.FLAT, computeWeekSummary(today, screenings, emptyList())?.energyTrend)
    }

    @Test fun `doses percent counts taken over scheduled non-skipped doses within the week`() {
        val meds = listOf(
            med(0, tagen = true), med(1, tagen = true), med(2, tagen = false),  // 2 of 3 taken
            med(3, tagen = false, skipped = true),                              // skipped → excluded
            med(4, tagen = false, tidpunkt = "Vid behov"),                      // vid behov → excluded
            med(10, tagen = false),                                             // outside week → excluded
        )
        assertEquals(66, computeWeekSummary(today, emptyList(), meds)?.dosesTakenPercent)
    }

    @Test fun `returns null when there is no underlying data`() {
        assertNull(computeWeekSummary(today, emptyList(), emptyList()))
    }
}

package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode

/** Sjukdomsdetaljens huvud (SJ-4, SJ-5, SJ-13, #240): status, varaktighet, senaste svårighetsgrad och dag N. Fasta datum. */
class IllnessSummaryTest {
    private val today = day("2026-10-06")
    private val created = Instant.fromEpochSeconds(1_790_000_000)

    private fun episode(start: String?, end: String? = null) =
        IllnessEpisode("flu", "Förkylning", start?.let(::day), end?.let(::day), createdAt = created)

    private fun checkin(id: String, date: String?, hour: Int, severity: Int, createdAt: Instant = created) =
        Checkin(id, date?.let(::day), LocalTime(hour, 0), severity = severity, createdAt = createdAt)

    @Test fun `pågående episod räknar dagarna till idag och visar senaste incheckningens svårighetsgrad`() {
        val first = checkin("a", "2026-10-03", 9, severity = 6)
        val latest = checkin("b", "2026-10-05", 8, severity = 4)
        val sameDayEarlier = checkin("c", "2026-10-05", 7, severity = 9)

        val summary = illnessSummary(episode("2026-10-03"), listOf(first, latest, sameDayEarlier), today)

        assertTrue(summary.ongoing)
        assertEquals(4, summary.durationDays, "3–6 oktober, båda inräknade = dag 4")
        assertEquals(4, summary.latestSeverity, "senaste på datum och klockslag, inte i listans ordning")
        assertEquals(listOf("b", "c", "a"), summary.checkins.map { it.id }, "senaste först")
        assertEquals(1, summary.dayOf(first))
        assertEquals(3, summary.dayOf(latest))
    }

    @Test fun `avslutad episod har varaktigheten start till slut inräknade, oberoende av idag (SJ-5, som 3x)`() {
        val summary = illnessSummary(episode("2026-09-20", end = "2026-09-26"), listOf(checkin("a", "2026-09-24", 10, severity = 2)), today)

        assertFalse(summary.ongoing)
        assertEquals(7, summary.durationDays)
        assertEquals(2, summary.latestSeverity)
        assertEquals(5, summary.dayOf(summary.checkins.single()))
    }

    @Test fun `en episod som börjar och slutar samma dag varar en dag`() {
        val ended = illnessSummary(episode("2026-10-01", end = "2026-10-01"), emptyList(), today)
        assertEquals(1, ended.durationDays)
        assertEquals(1, illnessSummary(episode("2026-10-06"), emptyList(), today).durationDays, "pågående sedan idag = dag 1")
    }

    @Test fun `ingen incheckning ger ingen svårighetsgrad och en tom lista`() {
        val summary = illnessSummary(episode("2026-10-01"), emptyList(), today)

        assertNull(summary.latestSeverity)
        assertEquals(emptyList(), summary.checkins)
        assertEquals(6, summary.durationDays)
    }

    @Test fun `utan start, med slut före start eller med start efter idag finns ingen varaktighet`() {
        assertNull(illnessSummary(episode(null), emptyList(), today).durationDays)
        assertNull(illnessSummary(episode("2026-10-05", end = "2026-10-01"), emptyList(), today).durationDays)
        assertNull(illnessSummary(episode("2026-10-08"), emptyList(), today).durationDays)
    }

    @Test fun `dag N saknas för en incheckning utan datum, före starten eller i en episod utan start`() {
        val summary = illnessSummary(episode("2026-10-03"), emptyList(), today)
        assertNull(summary.dayOf(checkin("a", null, 9, severity = 1)))
        assertNull(summary.dayOf(checkin("b", "2026-10-02", 9, severity = 1)))
        assertNull(checkinDay(episode(null), checkin("c", "2026-10-04", 9, severity = 1)))
    }

    @Test fun `lika dag och klockslag avgörs av skapandetiden och sedan id, som kortet på Idag`() {
        val older = checkin("z", "2026-10-05", 9, severity = 3, createdAt = created)
        val newer = checkin("a", "2026-10-05", 9, severity = 8, createdAt = created + 1.minutes)
        val summary = illnessSummary(episode("2026-10-03"), listOf(older, newer), today)

        assertEquals(8, summary.latestSeverity)
        assertEquals(latestCheckin(listOf(older, newer)), summary.checkins.first())
    }
}

/** SJ-4, SJ-12: slutdatumet ligger i start…idag, båda inräknade ([endDateError]). */
class EndDateErrorTest {
    private val start = day("2026-10-03")
    private val today = day("2026-10-06")

    @Test fun `slut från starten till idag godtas, båda inräknade, och inget slut är pågående`() {
        assertNull(endDateError(start, start, today))
        assertNull(endDateError(start, day("2026-10-05"), today))
        assertNull(endDateError(start, today, today))
        assertNull(endDateError(start, null, today))
    }

    @Test fun `slut före starten och efter idag nekas`() {
        assertEquals(EndDateError.BEFORE_START, endDateError(start, day("2026-10-02"), today))
        assertEquals(EndDateError.AFTER_TODAY, endDateError(start, day("2026-10-07"), today))
        assertEquals(EndDateError.BEFORE_START, endDateError(day("2026-10-08"), day("2026-10-07"), today), "före starten går före")
    }

    @Test fun `utan start gäller bara taket idag`() {
        assertNull(endDateError(null, day("2020-01-01"), today))
        assertEquals(EndDateError.AFTER_TODAY, endDateError(null, day("2026-10-07"), today))
    }
}

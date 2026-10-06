package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.SymptomScore

/**
 * Idags kort under checklistan: pågående sjukdom (HEM-12), 7-dagarstrenden (HEM-7) och formulärets
 * symptomval (SCR-2, AKT-6). Fasta datum, aldrig "nu".
 */
class TodayCardsTest {

    // ── Pågående sjukdom (HEM-12) ─────────────────────────────────────────

    @Test fun `den pågående episoden är den senast skapade utan slut, som 3x`() {
        val ended = IllnessEpisode("a", "Förkylning", start = day("2026-10-01"), end = day("2026-10-03"), createdAt = Instant.fromEpochSeconds(500))
        val older = IllnessEpisode("b", "Migrän", start = day("2026-10-02"), createdAt = Instant.fromEpochSeconds(100))
        val newer = IllnessEpisode("c", "Influensa", start = day("2026-09-20"), createdAt = Instant.fromEpochSeconds(200))
        assertEquals(newer, ongoingEpisode(listOf(ended, older, newer)), "skapandetiden avgör, inte starten")
        assertEquals(older, ongoingEpisode(listOf(ended, older)))
        assertNull(ongoingEpisode(listOf(ended)))
        assertNull(ongoingEpisode(emptyList()))
    }

    @Test fun `en episod med start efter idag pågår, men har ingen dag än`() {
        val future = IllnessEpisode("b", "Operation", start = day("2026-10-10"), createdAt = Instant.fromEpochSeconds(300))
        val current = IllnessEpisode("a", "Migrän", start = day("2026-10-01"), createdAt = Instant.fromEpochSeconds(100))
        assertEquals(future, ongoingEpisode(listOf(current, future)))
        assertNull(ongoingIllness(future, emptyList(), day("2026-10-04")).day)
    }

    @Test fun `samma skapandetid – id avgör, och utan skapandetid räknas den som äldst`() {
        val x = IllnessEpisode("x", "Förkylning", createdAt = Instant.fromEpochSeconds(100))
        val y = IllnessEpisode("y", "Halsont", createdAt = Instant.fromEpochSeconds(100))
        val undated = IllnessEpisode("z", "Hosta")
        assertEquals(y, ongoingEpisode(listOf(y, x, undated)))
    }

    @Test fun `startdagen är dag 1, och utan start eller före den finns ingen dag`() {
        assertEquals(1, illnessDay(day("2026-10-04"), day("2026-10-04")))
        assertEquals(4, illnessDay(day("2026-10-01"), day("2026-10-04")))
        assertEquals(35, illnessDay(day("2026-09-01"), day("2026-10-05")), "över månadsskiftet")
        assertNull(illnessDay(null, day("2026-10-04")))
        assertNull(illnessDay(day("2026-10-05"), day("2026-10-04")))
    }

    @Test fun `den senaste incheckningen på datum och sedan klockslag`() {
        val morning = Checkin("1", day("2026-10-03"), LocalTime(8, 0), severity = 6)
        val evening = Checkin("2", day("2026-10-03"), LocalTime(20, 0), severity = 4)
        val earlier = Checkin("3", day("2026-10-02"), LocalTime(22, 0), severity = 7)
        assertEquals(evening, latestCheckin(listOf(morning, evening, earlier)))
        assertNull(latestCheckin(emptyList()))
        // Samma ordning som måendeloggarna (latestBy): datum, klockslag, skapandetid, id; saknat = äldst.
        val undated = Checkin("4", createdAt = Instant.fromEpochSeconds(9_999_999_999))
        assertEquals(evening, latestCheckin(listOf(undated, evening)))
        assertEquals(undated, latestCheckin(listOf(undated, Checkin("0", createdAt = Instant.fromEpochSeconds(1)))))
        val sameTime = Checkin("5", day("2026-10-03"), LocalTime(20, 0), createdAt = Instant.fromEpochSeconds(10))
        assertEquals(sameTime, latestCheckin(listOf(evening, sameTime)), "samma dag och tid – skapandetiden")
        val episode = IllnessEpisode("e", "Förkylning", start = day("2026-10-01"))
        assertEquals(OngoingIllness(episode, 4, evening), ongoingIllness(episode, listOf(earlier, evening, morning), day("2026-10-04")))
    }

    // ── Senaste posten (HEM-5, HEM-12) och Din vecka (HEM-13) ──────────────

    @Test fun `tillfällets senaste logg – samma klockslag avgörs av skapandetiden, sedan id, saknat är äldst`() {
        fun state(vararg screenings: Screening) =
            OccasionState(Occasion.LUNCH, LocalTime(12, 0), OccasionStatus.LOGGED, screenings.toList())
        val early = Screening("a", day("2026-10-04"), LocalTime(12, 0), Occasion.LUNCH, createdAt = Instant.fromEpochSeconds(100))
        val late = Screening("b", day("2026-10-04"), LocalTime(12, 0), Occasion.LUNCH, createdAt = Instant.fromEpochSeconds(200))
        val noCreated = Screening("z", day("2026-10-04"), LocalTime(12, 0), Occasion.LUNCH)
        val undated = Screening("y", null, LocalTime(23, 0), Occasion.LUNCH, createdAt = Instant.fromEpochSeconds(900))
        assertEquals(late, state(late, early, noCreated).latest)
        assertEquals(early, state(early, undated).latest, "en daterad går före en odaterad")
        assertEquals(Screening("d", createdAt = null), state(Screening("c"), Screening("d")).latest, "bara id kvar")
        assertNull(state().latest)
    }

    @Test fun `Din vecka bara när den visade dagen är idag ur samma klocka, söndag och måndag`() {
        val zone = TimeZone.of("Europe/Stockholm")
        fun at(date: String, hour: Int, minute: Int = 0) = LocalDateTime(day(date), LocalTime(hour, minute)).toInstant(zone)
        val screenings = listOf(Screening("s", day("2026-10-02"), energy = 6))
        val monday = day("2026-10-05")
        assertNotNull(weekSummaryOn(monday, at("2026-10-05", 23, 59), zone, screenings, emptyList()))
        assertNull(weekSummaryOn(monday, at("2026-10-06", 0, 0), zone, screenings, emptyList()), "efter midnatt är det tisdag")
        assertNull(weekSummaryOn(day("2026-10-06"), at("2026-10-06", 9), zone, screenings, emptyList()), "tisdag")
        assertNull(weekSummaryOn(day("2026-10-04"), at("2026-10-05", 9), zone, screenings, emptyList()), "en tidigare dag")
        assertNull(weekSummaryOn(monday, at("2026-10-05", 9), zone, emptyList(), emptyList()), "utan underlag")
    }

    // ── 7-dagarstrenden (HEM-7) ───────────────────────────────────────────

    @Test fun `sju dagar till och med idag med dagsvärdet och luckor`() {
        val days = daysEnding(day("2026-10-04"))
        assertEquals((28..30).map { day("2026-09-$it") } + (1..4).map { day("2026-10-0$it") }, days)
        val screenings = listOf(
            Screening("1", day("2026-10-04"), energy = 6),
            Screening("2", day("2026-10-04"), energy = 8),
            Screening("3", day("2026-10-01"), energy = 3),
            Screening("4", day("2026-09-27"), energy = 9),
        )
        assertEquals(listOf(null, null, null, 3f, null, null, 7f), dailyEnergyAverages(screenings, days))
        // Samma dagsvärde som Trender (TRD-8): computeDailyEnergyStats.
        assertEquals(computeDailyEnergyStats(screenings).last().avg, dailyEnergyAverages(screenings, days).last())
    }

    // ── Symptomvalen (SCR-2, AKT-6) ───────────────────────────────────────

    @Test fun `aktiva symptom och de arkiverade som posten redan har`() {
        val headache = Option("h", OptionKind.SYMPTOM, "Huvudvärk", sortOrder = 0)
        val dizzy = Option("d", OptionKind.SYMPTOM, "Yrsel", sortOrder = 1, archived = true)
        val nausea = Option("n", OptionKind.SYMPTOM, "Illamående", sortOrder = 2, archived = true)
        val walk = Option("w", OptionKind.ACTIVITY, "Promenad")
        assertEquals(listOf(headache), symptomChoices(listOf(headache, dizzy, nausea, walk), emptyList()))
        assertEquals(listOf(headache, dizzy), symptomChoices(listOf(headache, dizzy, nausea, walk), listOf(SymptomScore("d", 3))))
    }

    @Test fun `Övrigt har samma id som konverteraren ger det`() {
        assertEquals(OptionIds.of(OptionKind.SYMPTOM, "Övrigt"), OTHER_SYMPTOM_ID)
    }
}

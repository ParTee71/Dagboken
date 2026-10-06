package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot

/**
 * Dagbokens tidslinje (HIST-1, HIST-7, HIST-8, HIST-9) och filterregeln (HIST-2). Fasta datum i
 * Europe/Stockholm: idag är tisdag 6 oktober 2026.
 */
class DiaryTimelineTest {

    private val zone = TimeZone.of("Europe/Stockholm")
    private val today = LocalDate(2026, 10, 6)
    private val yesterday = LocalDate(2026, 10, 5)
    private val saturday = LocalDate(2026, 10, 3)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun time(hour: Int, minute: Int = 0) = LocalTime(hour, minute)

    private val flu = IllnessEpisode("flu", "Förkylning", start = saturday, end = today)

    private val sources = DiarySources(
        screenings = listOf(Screening("s1", today, time(8, 15), energy = 6)),
        activities = listOf(Activity("a1", yesterday, time(17, 30), optionId = "promenad", energy = 3)),
        doses = listOf(
            Dose("taken", today, Slot.MORNING, "Levaxin", status = DoseStatus.TAKEN, plannedTime = time(8), takenAt = at(today, 7, 42)),
            Dose("planned", today, Slot.EVENING, "Levaxin", status = DoseStatus.PLANNED, plannedTime = time(20)),
            Dose("skipped", yesterday, Slot.MORNING, "Levaxin", status = DoseStatus.SKIPPED, plannedTime = time(8)),
            Dose("old", saturday, Slot.MORNING, "Levaxin", status = DoseStatus.TAKEN, plannedTime = time(8)),
        ),
        events = listOf(Event("e1", saturday, time(14), optionId = "migran", severity = 7)),
        episodes = listOf(flu),
        checkins = mapOf("flu" to listOf(Checkin("c1", yesterday, time(9), severity = 4))),
    )

    @Test
    fun `alla typer i ett flöde, nyast först – bara tagna doser med tagningstiden (HIST-1, HIST-7, MED-14)`() {
        val entries = diaryEntries(sources, zone)
        assertEquals(
            listOf(
                "episode-end:flu", "screening:s1", "dose:taken",
                "activity:a1", "checkin:flu/c1",
                "event:e1", "dose:old", "episode-start:flu",
            ),
            entries.map { it.id },
        )
        val taken = entries.filterIsInstance<DiaryEntry.TakenDose>()
        assertEquals(listOf(time(7, 42), time(8)), taken.map { it.time }, "tagningstiden, annars den planerade tiden")
        assertTrue(entries.none { it.id == "dose:planned" || it.id == "dose:skipped" }, "planerade och överhoppade hör till Idag")
    }

    @Test
    fun `episodens slut är nyast och starten äldst på sin dag, före klockslaget (HIST-9)`() {
        val oneDay = IllnessEpisode("kort", "Migrän", start = today, end = today, createdAt = at(today, 23))
        val entries = diaryEntries(
            DiarySources(
                episodes = listOf(oneDay),
                checkins = mapOf("kort" to listOf(Checkin("tidig", today, time(0, 5)), Checkin("utan-tid", today), Checkin("sen", today, time(23, 50)))),
            ),
            zone,
        )
        assertEquals(
            listOf("episode-end:kort", "checkin:kort/sen", "checkin:kort/tidig", "checkin:kort/utan-tid", "episode-start:kort"),
            entries.map { it.id },
        )
    }

    @Test
    fun `en dos tagen efter midnatt hör till tagningsdagen (HIST-7, MED-14)`() {
        val late = Dose("kvall", yesterday, Slot.EVENING, "Atarax", status = DoseStatus.TAKEN, plannedTime = time(22), takenAt = at(today, 0, 30))
        val entry = diaryEntries(DiarySources(doses = listOf(late)), zone).single()
        assertEquals(today, entry.date)
        assertEquals(time(0, 30), entry.time)
        assertEquals(setOf(today), listOf(entry).dates())
    }

    @Test
    fun `episodens start och slut och incheckningen har dag N (HIST-9)`() {
        val illness = diaryEntries(sources, zone).filter { it.type == DiaryType.ILLNESS }
        assertEquals(1, (illness.single { it is DiaryEntry.EpisodeStart } as DiaryEntry.EpisodeStart).day)
        assertEquals(3, (illness.single { it is DiaryEntry.CheckIn } as DiaryEntry.CheckIn).day)
        assertEquals(4, (illness.single { it is DiaryEntry.EpisodeEnd } as DiaryEntry.EpisodeEnd).day)
    }

    @Test
    fun `en pågående episod har bara sin start, och en post utan dag har ingen plats`() {
        val ongoing = flu.copy(end = null)
        val entries = diaryEntries(
            DiarySources(screenings = listOf(Screening("odaterad")), episodes = listOf(ongoing), checkins = mapOf("flu" to listOf(Checkin("utan-dag")))),
            zone,
        )
        assertEquals(listOf("episode-start:flu"), entries.map { it.id })
    }

    @Test
    fun `flera poster samma minut står alltid i samma ordning – skapandetid och sedan id`() {
        val same = listOf(
            Screening("b", today, time(9), createdAt = at(today, 9, 1)),
            Screening("a", today, time(9), createdAt = at(today, 9, 1)),
            Screening("c", today, time(9), createdAt = at(today, 9, 2)),
        )
        val forward = diaryEntries(DiarySources(screenings = same), zone).map { it.id }
        val backward = diaryEntries(DiarySources(screenings = same.reversed()), zone).map { it.id }
        assertEquals(listOf("screening:c", "screening:b", "screening:a"), forward)
        assertEquals(forward, backward)
    }

    @Test
    fun `dagarna grupperas med Idag, Igår och annars bara datum – en dag utan poster finns inte (HIST-1)`() {
        val days = diaryDays(diaryEntries(sources, zone), today)
        assertEquals(listOf(today, yesterday, saturday), days.map { it.date }, "söndag 4 oktober har inga poster")
        assertEquals(listOf(DayLabel.TODAY, DayLabel.YESTERDAY, DayLabel.OTHER), days.map { it.label })
        assertEquals(listOf("episode-end:flu", "screening:s1", "dose:taken"), days.first().entries.map { it.id })
        assertTrue(diaryDays(emptyList(), today).isEmpty())
    }

    @Test
    fun `filtret, kalenderns dagar och den valda dagens poster`() {
        val entries = diaryEntries(sources, zone)
        val illness = DiaryFilter().toggle(DiaryType.ILLNESS)
        assertEquals(listOf("episode-end:flu", "checkin:flu/c1", "episode-start:flu"), entries.shownBy(illness).map { it.id })
        assertEquals(setOf(today, yesterday, saturday), entries.dates())
        assertEquals(listOf("event:e1", "dose:old", "episode-start:flu"), entries.on(saturday).map { it.id })
        assertTrue(entries.on(LocalDate(2026, 10, 4)).isEmpty())
    }

    // ── Filterregeln (HIST-2) ────────────────────────────────────────────────

    @Test
    fun `från början visas alla typer och Alla är markerat`() {
        val filter = DiaryFilter()
        assertTrue(filter.showsAll)
        assertEquals(DiaryType.entries.toSet(), filter.types)
    }

    @Test
    fun `en typ när alla visas ger bara den typen`() {
        val events = DiaryFilter().toggle(DiaryType.EVENT)
        assertEquals(setOf(DiaryType.EVENT), events.types)
        assertEquals(false, events.showsAll)
    }

    @Test
    fun `annars slås en typ av och på, och Alla markeras när alla är på igen`() {
        val two = DiaryFilter().toggle(DiaryType.EVENT).toggle(DiaryType.DOSE)
        assertEquals(setOf(DiaryType.EVENT, DiaryType.DOSE), two.types)
        assertEquals(setOf(DiaryType.DOSE), two.toggle(DiaryType.EVENT).types)
        val all = DiaryType.entries.fold(DiaryFilter(setOf(DiaryType.EVENT))) { filter, type -> if (filter.shows(type)) filter else filter.toggle(type) }
        assertTrue(all.showsAll)
    }

    @Test
    fun `den sista typen går inte att slå av, och Alla slår på samtliga`() {
        val one = DiaryFilter(setOf(DiaryType.ILLNESS))
        assertEquals(one, one.toggle(DiaryType.ILLNESS))
        assertTrue(one.showAll().showsAll)
        assertFailsWith<IllegalArgumentException> { DiaryFilter(emptySet()) }
    }

    // ── Fönstret (HIST-8) ────────────────────────────────────────────────────

    @Test
    fun `fönstret är ett år till och med idag, och Visa äldre lägger till ett år i taget utan glapp`() {
        val window = DiaryWindow(today)
        assertEquals(LocalDate(2025, 10, 7)..today, window.year(0))
        assertEquals(LocalDate(2025, 10, 7), window.from)
        val older = window.older()
        assertEquals(LocalDate(2024, 10, 7)..LocalDate(2025, 10, 6), older.year(1))
        assertEquals(LocalDate(2024, 10, 7), older.from)
    }

    @Test
    fun `fönstret täcker en äldre månad med så många år som behövs, och posterna utanför tas bort`() {
        val window = DiaryWindow(today)
        assertEquals(window, window.covering(LocalDate(2025, 11, 1)))
        assertEquals(3, window.covering(LocalDate(2023, 12, 1)).years)
        val old = Screening("gammal", LocalDate(2025, 10, 6), time(9))
        val entries = diaryEntries(sources.copy(screenings = sources.screenings + old), zone)
        assertTrue(window.within(entries).none { it.id == "screening:gammal" })
        assertTrue(window.older().within(entries).any { it.id == "screening:gammal" })
    }

    @Test
    fun `skottdagen ger sammanhängande år`() {
        val window = DiaryWindow(LocalDate(2028, 2, 29), years = 2)
        assertEquals(LocalDate(2027, 3, 1)..LocalDate(2028, 2, 29), window.year(0))
        assertEquals(LocalDate(2026, 3, 1)..LocalDate(2027, 2, 28), window.year(1))
    }

    @Test
    fun `årsgränserna står still när dagarna går – bara år 0 växer (HIST-8)`() {
        val created = DiaryWindow(today, years = 2)
        val later = created.copy(today = LocalDate(2026, 10, 8))
        assertEquals(created.year(1), later.year(1))
        assertEquals(LocalDate(2025, 10, 7)..LocalDate(2026, 10, 8), later.year(0))
        assertTrue(LocalDate(2026, 10, 8) in later)
    }

    @Test
    fun `en dos som läses av två år kommer med en gång`() {
        val dose = Dose("d", today, Slot.MORNING, "Levaxin", status = DoseStatus.TAKEN, takenAt = at(today, 8))
        assertEquals(1, diaryEntries(DiarySources(doses = listOf(dose)) + DiarySources(doses = listOf(dose)), zone).size)
    }
}

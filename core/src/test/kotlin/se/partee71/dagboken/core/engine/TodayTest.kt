package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Slot

/**
 * Idags beräkningar: framsteg (HEM-18), belöningsläge och jämförelse (HEM-19), dagar i rad (HEM-20),
 * veckosammanfattningen (HEM-13, port av 3.x `WeekSummaryTest`) och när något är att göra (HEM-4,
 * MED-13). Fasta datum och tider i Europe/Stockholm, aldrig "nu".
 */
class TodayTest {

    private fun dose(
        date: String,
        status: DoseStatus = DoseStatus.PLANNED,
        slot: Slot = Slot.MORNING,
        prnId: String? = null,
        id: String = "$date-$slot-$status-$prnId",
    ) = Dose(id, day(date), slot, name = "Levaxin", status = status, prnId = prnId)

    private fun screening(date: String, occasion: Occasion?, energy: Int = 5, id: String = "$date-$occasion-$energy") =
        Screening(id, day(date), occasion = occasion, energy = energy)

    private val both = listOf(Occasion.BREAKFAST, Occasion.BEDTIME)

    /** Påminnelserna med frukost 08:00 och läggdags 21:00 aktiverade, lunch och kvällsmat avstängda. */
    private val reminders = ReminderSettings(
        screeningOccasions = Occasion.entries.map { OccasionReminder(it, enabled = it in both) },
    )

    /** En helt klar dag: morgondosen tagen och båda tillfällena loggade. */
    private fun completeDay(date: String) = listOf(dose(date, DoseStatus.TAKEN)) to both.map { screening(date, it) }

    // ── Framsteg (HEM-18, HEM-19) ────────────────────────────────────────────

    @Test fun `framsteg räknar schemalagda doser och aktiverade tillfällen, inte vid behov`() {
        val doses = listOf(
            dose("2026-05-04", DoseStatus.TAKEN),
            dose("2026-05-04", DoseStatus.SKIPPED, Slot.EVENING),
            dose("2026-05-04", slot = Slot.NIGHT),
            dose("2026-05-04", DoseStatus.TAKEN, Slot.AS_NEEDED),
            dose("2026-05-04", DoseStatus.TAKEN, Slot.MORNING, prnId = "p1"), // 3.x-favorit med tidpunkt
            dose("2026-05-03", DoseStatus.TAKEN), // en annan dag
        )
        val screenings = listOf(screening("2026-05-04", Occasion.BREAKFAST), screening("2026-05-04", Occasion.LUNCH), screening("2026-05-04", null))
        val progress = dayProgress(day("2026-05-04"), doses, screenings, reminders.enabledOccasions)
        assertEquals(DayProgress(done = 3, total = 5), progress, "tagen + överhoppad + frukost av tre doser och två tillfällen")
        assertFalse(progress.isComplete)
    }

    @Test fun `bara överhoppade doser och loggade tillfällen är en klar dag`() {
        val doses = listOf(dose("2026-05-04", DoseStatus.SKIPPED), dose("2026-05-04", DoseStatus.SKIPPED, Slot.EVENING))
        val progress = dayProgress(day("2026-05-04"), doses, both.map { screening("2026-05-04", it) }, both)
        assertEquals(DayProgress(4, 4), progress)
        assertTrue(progress.isComplete)
    }

    @Test fun `en dag utan poster är inte klar`() {
        val progress = dayProgress(day("2026-05-04"), emptyList(), emptyList(), emptyList())
        assertEquals(DayProgress(0, 0), progress)
        assertFalse(progress.isComplete)
        assertFalse(dayProgress(day("2026-05-04"), listOf(dose("2026-05-04", DoseStatus.TAKEN, Slot.AS_NEEDED)), emptyList(), emptyList()).isComplete, "bara vid behov")
    }

    @Test fun `ett tillfälle räknas en gång även med flera loggar`() {
        val screenings = listOf(screening("2026-05-04", Occasion.BREAKFAST, 3), screening("2026-05-04", Occasion.BREAKFAST, 7))
        assertEquals(DayProgress(1, 2), dayProgress(day("2026-05-04"), emptyList(), screenings, both))
    }

    // ── Dagar i rad (HEM-20) ─────────────────────────────────────────────────

    @Test fun `dagar i rad räknar klara dagar till och med idag`() {
        val days = listOf("2026-05-01", "2026-05-02", "2026-05-03", "2026-05-04").map(::completeDay)
        assertEquals(4, dayStreak(day("2026-05-04"), days.flatMap { it.first }, days.flatMap { it.second }, both))
    }

    @Test fun `idag räknas bara när den är klar – annars från igår`() {
        val days = listOf("2026-05-02", "2026-05-03").map(::completeDay)
        val todayHalf = listOf(dose("2026-05-04", DoseStatus.TAKEN))
        assertEquals(2, dayStreak(day("2026-05-04"), days.flatMap { it.first } + todayHalf, days.flatMap { it.second }, both))
    }

    @Test fun `en icke klar dag nollställer`() {
        val days = listOf("2026-05-01", "2026-05-03", "2026-05-04").map(::completeDay)
        val gap = listOf(dose("2026-05-02", DoseStatus.PLANNED))
        assertEquals(2, dayStreak(day("2026-05-04"), days.flatMap { it.first } + gap, days.flatMap { it.second }, both))
        val yesterdayNotDone = listOf("2026-05-01", "2026-05-02").map(::completeDay)
        assertEquals(0, dayStreak(day("2026-05-04"), yesterdayNotDone.flatMap { it.first }, yesterdayNotDone.flatMap { it.second }, both), "igår utan poster bryter")
    }

    @Test fun `utan data är sviten noll`() {
        assertEquals(0, dayStreak(day("2026-05-04"), emptyList(), emptyList(), both))
        assertEquals(0, dayStreak(day("2026-05-04"), emptyList(), emptyList(), emptyList()))
    }

    @Test fun `sviten går över månadsskifte och sommartidsbyte`() {
        val days = listOf("2026-03-28", "2026-03-29", "2026-03-30", "2026-03-31", "2026-04-01").map(::completeDay)
        assertEquals(5, dayStreak(day("2026-04-01"), days.flatMap { it.first }, days.flatMap { it.second }, both))
    }

    // ── Jämförelse med igår (HEM-19) ─────────────────────────────────────────

    @Test fun `dagens snittenergi och skillnad mot igår`() {
        val screenings = listOf(
            screening("2026-05-03", Occasion.BREAKFAST, 4),
            screening("2026-05-03", Occasion.BEDTIME, 6),
            screening("2026-05-04", Occasion.BREAKFAST, 6),
            screening("2026-05-04", Occasion.BEDTIME, 8),
        )
        assertEquals(DayComparison(7f, 2f), dayComparison(day("2026-05-04"), screenings))
    }

    @Test fun `utan logg igår ingen skillnad, utan logg idag ingen jämförelse`() {
        val today = listOf(screening("2026-05-04", Occasion.BREAKFAST, 6))
        assertEquals(DayComparison(6f, null), dayComparison(day("2026-05-04"), today))
        assertNull(dayComparison(day("2026-05-05"), today))
    }

    // ── Veckosammanfattning (HEM-13, port av 3.x WeekSummaryTest) ────────────

    /** Måndag kväll: alla måndagens doser har förfallit. */
    private val mondayNight = at("2026-05-04T23:30")

    private fun energyWeeks(thisWeek: Int, previousWeek: Int) = listOf(
        screening("2026-05-04", Occasion.BREAKFAST, thisWeek),
        screening("2026-04-28", Occasion.BREAKFAST, thisWeek), // sista dagen i veckan (today - 6)
        screening("2026-04-27", Occasion.BREAKFAST, previousWeek), // today - 7
        screening("2026-04-21", Occasion.BREAKFAST, previousWeek), // today - 13
        screening("2026-04-20", Occasion.BREAKFAST, 0), // today - 14: utanför
    )

    @Test fun `energitrend upp, ner och oförändrad vid gränsen 0,5`() {
        assertEquals(EnergyTrend.UP, weekSummary(mondayNight, STOCKHOLM, energyWeeks(7, 6), emptyList())?.energyTrend)
        assertEquals(EnergyTrend.DOWN, weekSummary(mondayNight, STOCKHOLM, energyWeeks(5, 6), emptyList())?.energyTrend)
        val halfUp = listOf(screening("2026-05-04", Occasion.BREAKFAST, 6), screening("2026-05-04", Occasion.BEDTIME, 7), screening("2026-04-27", Occasion.BREAKFAST, 6))
        assertEquals(EnergyTrend.SAME, weekSummary(mondayNight, STOCKHOLM, halfUp, emptyList())?.energyTrend, "6,5 mot 6 är inte mer än 0,5")
    }

    @Test fun `utan förra veckan är trenden oförändrad`() {
        val summary = weekSummary(mondayNight, STOCKHOLM, listOf(screening("2026-05-04", Occasion.BREAKFAST, 9)), emptyList())
        assertEquals(WeekSummary(EnergyTrend.SAME, null), summary)
    }

    @Test fun `dosandel av schemalagda doser som inte hoppats över`() {
        val doses = listOf(
            dose("2026-05-04", DoseStatus.TAKEN),
            dose("2026-05-03", DoseStatus.TAKEN),
            dose("2026-05-02", DoseStatus.PLANNED),
            dose("2026-05-01", DoseStatus.SKIPPED),
            dose("2026-05-01", DoseStatus.TAKEN, Slot.AS_NEEDED),
            dose("2026-04-27", DoseStatus.PLANNED), // förra veckan
        )
        assertEquals(66, weekSummary(mondayNight, STOCKHOLM, emptyList(), doses)?.dosesTakenPercent, "2 av 3, avrundat nedåt")
    }

    @Test fun `måndag morgon räknas bara förfallna doser – kommande sänker inte andelen`() {
        val doses = listOf(
            dose("2026-05-04", DoseStatus.TAKEN, Slot.MORNING), // 07:00, tagen
            dose("2026-05-04", DoseStatus.PLANNED, Slot.EVENING), // 19:00, inte förfallen
            dose("2026-05-03", DoseStatus.PLANNED, Slot.MORNING), // igår, missad
        )
        assertEquals(50, weekSummary(at("2026-05-04T08:00"), STOCKHOLM, emptyList(), doses)?.dosesTakenPercent, "1 av 2 – kvällsdosen räknas inte än")
        val takenEarly = doses + dose("2026-05-04", DoseStatus.TAKEN, Slot.NIGHT)
        assertEquals(66, weekSummary(at("2026-05-04T08:00"), STOCKHOLM, emptyList(), takenEarly)?.dosesTakenPercent, "en dos tagen i förväg räknas")
        assertEquals(33, weekSummary(at("2026-05-04T19:00"), STOCKHOLM, emptyList(), doses)?.dosesTakenPercent, "kvällsdosen förfallen kl 19")
    }

    @Test fun `utan underlag ingen sammanfattning`() {
        assertNull(weekSummary(mondayNight, STOCKHOLM, emptyList(), emptyList()))
        assertNull(weekSummary(mondayNight, STOCKHOLM, energyWeeks(5, 5).filter { it.date!! < day("2026-04-28") }, listOf(dose("2026-05-01", DoseStatus.SKIPPED))), "bara förra veckan och överhoppade")
    }

    @Test fun `sammanfattningen visas söndag och måndag`() {
        assertTrue(day("2026-05-03").showsWeekSummary)
        assertTrue(day("2026-05-04").showsWeekSummary)
        assertFalse(day("2026-05-05").showsWeekSummary)
    }

    // ── När något är att göra (HEM-4, MED-13) ───────────────────────────────

    @Test fun `försenat när tiden är nådd, snart inom tre timmar, annars kommande`() {
        val today = day("2026-05-04")
        assertEquals(Due.LATE, dueAt(today, LocalTime(8, 0), at("2026-05-04T08:00"), STOCKHOLM), "tiden nådd")
        assertEquals(Due.SOON, dueAt(today, LocalTime(11, 0), at("2026-05-04T08:00"), STOCKHOLM), "exakt tre timmar")
        assertEquals(Due.UPCOMING, dueAt(today, LocalTime(11, 1), at("2026-05-04T08:00"), STOCKHOLM))
    }

    @Test fun `en tidigare dag är aldrig försenad och en senare dag är kommande`() {
        assertEquals(Due.PAST, dueAt(day("2026-05-03"), LocalTime(21, 0), at("2026-05-04T08:00"), STOCKHOLM))
        assertEquals(Due.UPCOMING, dueAt(day("2026-05-05"), LocalTime(8, 0), at("2026-05-04T23:00"), STOCKHOLM))
    }

    @Test fun `vid midnatt blir gårdagens tillfälle ej loggat, inte försenat`() {
        val yesterday = day("2026-05-03")
        assertEquals(OccasionStatus.LATE, occasionStates(reminders, emptyList(), yesterday, at("2026-05-03T23:59"), STOCKHOLM).last().status)
        val afterMidnight = occasionStates(reminders, emptyList(), yesterday, at("2026-05-04T00:01"), STOCKHOLM)
        assertEquals(listOf(OccasionStatus.NOT_LOGGED, OccasionStatus.NOT_LOGGED), afterMidnight.map { it.status })
        assertEquals(OccasionStatus.UPCOMING, occasionStates(reminders, emptyList(), day("2026-05-04"), at("2026-05-04T00:01"), STOCKHOLM).last().status)
    }

    @Test fun `plusknappens tillfällesväljare har alla fyra – de aktiverade med Idags status, övriga aldrig försenade (HEM-8b)`() {
        val today = day("2026-05-04")
        val lunch = screening("2026-05-04", Occasion.LUNCH)
        val choices = occasionChoices(reminders, listOf(lunch, screening("2026-05-03", Occasion.DINNER)), today, at("2026-05-04T19:00"), STOCKHOLM)
        assertEquals(Occasion.entries.toList(), choices.map { it.occasion })
        assertEquals(
            listOf(OccasionStatus.LATE, OccasionStatus.LOGGED, OccasionStatus.NOT_LOGGED, OccasionStatus.SOON),
            choices.map { it.status },
            "frukost försenad och läggdags snart som på Idag; lunch loggad och kvällsmat utan påminnelse inte försenad",
        )
        assertEquals(listOf(lunch), choices[1].screenings)
        assertEquals(Occasion.DINNER.defaultTime, choices[2].time)
        assertEquals(occasionStates(reminders, listOf(lunch), today, at("2026-05-04T19:00"), STOCKHOLM), choices.filter { it.occasion in both })
    }

    @Test fun `snart räknas i verkliga timmar över sommartidsbytet`() {
        // 29 mars 2026: klockan går från 02:00 till 03:00. 00:30 → 04:00 är 2,5 verkliga timmar.
        val springForward = day("2026-03-29")
        assertEquals(Due.SOON, dueAt(springForward, LocalTime(4, 0), at("2026-03-29T00:30"), STOCKHOLM))
        // 25 oktober 2026: klockan går från 03:00 till 02:00. 00:30 → 03:00 är 3,5 verkliga timmar.
        assertEquals(Due.UPCOMING, dueAt(day("2026-10-25"), LocalTime(3, 0), at("2026-10-25T00:30"), STOCKHOLM))
    }

    @Test fun `tillfällesstatus följer påminnelsernas tider och bara aktiverade tillfällen`() {
        val custom = reminders.copy(
            screeningOccasions = Occasion.entries.map { OccasionReminder(it, enabled = it != Occasion.DINNER, time = if (it == Occasion.LUNCH) LocalTime(10, 30) else it.defaultTime) },
        )
        val screenings = listOf(screening("2026-05-04", Occasion.BREAKFAST, 6), screening("2026-05-03", Occasion.LUNCH))
        val states = occasionStates(custom, screenings, day("2026-05-04"), at("2026-05-04T09:00"), STOCKHOLM)
        assertEquals(listOf(Occasion.BREAKFAST, Occasion.LUNCH, Occasion.BEDTIME), states.map { it.occasion })
        assertEquals(listOf(OccasionStatus.LOGGED, OccasionStatus.SOON, OccasionStatus.UPCOMING), states.map { it.status })
        assertEquals(LocalTime(10, 30), states[1].time)
        assertEquals(listOf(6), states[0].screenings.map { it.energy }, "dagens logg följer med, inte gårdagens")
    }

    @Test fun `samma regel för vilka tillfällen som visas och räknas – ett per tillfälle`() {
        // Två rader för frukost (bara i trasig data; codecen ger en per tillfälle): den första gäller.
        val doubled = ReminderSettings(
            screeningOccasions = listOf(
                OccasionReminder(Occasion.BREAKFAST, enabled = true, time = LocalTime(7, 0)),
                OccasionReminder(Occasion.BREAKFAST, enabled = true, time = LocalTime(9, 0)),
                OccasionReminder(Occasion.LUNCH, enabled = false),
            ),
        )
        val states = occasionStates(doubled, emptyList(), day("2026-05-04"), at("2026-05-04T08:00"), STOCKHOLM)
        assertEquals(listOf(Occasion.BREAKFAST to LocalTime(7, 0)), states.map { it.occasion to it.time })
        assertEquals(doubled.enabledOccasions, states.map { it.occasion })
        assertEquals(DayProgress(0, 1), dayProgress(day("2026-05-04"), emptyList(), emptyList(), doubled.enabledOccasions))
    }

    @Test fun `ett loggat tillfälle är loggat också en tidigare dag`() {
        val states = occasionStates(reminders, listOf(screening("2026-05-03", Occasion.BEDTIME)), day("2026-05-03"), at("2026-05-04T12:00"), STOCKHOLM)
        assertEquals(listOf(OccasionStatus.NOT_LOGGED, OccasionStatus.LOGGED), states.map { it.status })
    }
}

package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Feeling
import se.partee71.dagboken.core.model.FeelingCatalog
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Screening

/** Känslornas sammanställning (#320): räkning per kärna och nyans, ordningen, meningens struktur och dagarna. */
class FeelingStatsTest {

    private val day = LocalDate(2026, 10, 14)

    private fun entry(vararg tokens: String, date: LocalDate = day, occasion: Occasion? = null) = FeelingEntry(date, tokens.map { checkNotNull(Feeling.parse(it)) }, occasion = occasion)

    private fun FeelingTally.counts() = cores.associate { it.core to it.count }

    @Test fun `inga poster ger noll för alla kärnor i hjulets ordning och ingen mening`() {
        val stats = computeFeelingStats(emptyList())
        assertEquals(0, stats.tally.total)
        assertTrue(stats.tally.isEmpty)
        assertEquals(FeelingCatalog.cores, stats.tally.cores.map { it.core })
        assertTrue(stats.tally.cores.all { it.count == 0 && it.share == 0f && it.weight == 0f && it.nuances.isEmpty() })
        assertTrue(stats.tally.ranked.isEmpty())
        assertEquals(FeelingSummary(emptyList(), null, emptyList()), stats.summary)
        assertTrue(stats.summary.isEmpty)
        assertTrue(stats.days.isEmpty())
        assertEquals(FeelingTally.EMPTY, stats.tally)
    }

    @Test fun `poster utan känslor räknas som inga val`() {
        val stats = computeFeelingStats(listOf(entry(), entry()))
        assertTrue(stats.tally.isEmpty)
        assertTrue(stats.summary.isEmpty)
        assertEquals(listOf(day), stats.days.map { it.date })
    }

    @Test fun `en dag med flera loggar - antal, andel, vikt och nyanser per kärna`() {
        val stats = computeFeelingStats(
            listOf(
                entry("glad.creative.curious", occasion = Occasion.BREAKFAST),
                entry("scared.anxious", occasion = Occasion.LUNCH),
                entry("calm.grateful", "glad.content", "calm", occasion = Occasion.DINNER)
            )
        )
        val tally = stats.tally
        assertEquals(5, tally.total)
        assertEquals(mapOf("glad" to 2, "strong" to 0, "calm" to 2, "sad" to 0, "angry" to 0, "scared" to 1), tally.counts())
        val calm = checkNotNull(tally.of("calm"))
        assertEquals(0.4f, calm.share, 1e-6f)
        assertEquals(1f, calm.weight, 1e-6f)
        assertEquals(listOf(NuanceCount("grateful", 1)), calm.nuances, "ett val utan nyans räknas bara på kärnan")
        assertEquals(listOf("creative", "content"), checkNotNull(tally.of("glad")).topNuances(), "lika många → katalogens ordning")
        assertEquals(0.5f, checkNotNull(tally.of("scared")).weight, 1e-6f)
        assertEquals(1, stats.days.size)
        assertEquals(tally, stats.days.single().tally)
    }

    @Test fun `nyanserna i fallande ordning och högst tre som ord`() {
        val stats = computeFeelingStats(
            listOf(
                entry("calm.relaxed", "calm.present.aware"),
                entry("calm.present", "calm.caring", "calm.grateful"),
                entry("calm.present", "calm.relaxed")
            )
        )
        val calm = checkNotNull(stats.tally.of("calm"))
        assertEquals(listOf(NuanceCount("present", 3), NuanceCount("relaxed", 2), NuanceCount("grateful", 1), NuanceCount("caring", 1)), calm.nuances, "ett precisare ord räknas på sin nyans")
        assertEquals(listOf("present", "relaxed", "grateful"), calm.topNuances())
    }

    @Test fun `stabil ordning vid lika antal - hjulets ordning`() {
        val stats = computeFeelingStats(listOf(entry("scared", "sad", "calm", "glad")))
        assertEquals(listOf("glad", "calm", "sad", "scared"), stats.tally.ranked.map { it.core })
        assertEquals(listOf("glad", "calm", "sad", "scared", "strong", "angry"), stats.tally.byCount.map { it.core })
        assertEquals(listOf("glad", "calm"), stats.summary.most.map { it.core })
        assertEquals("sad", stats.summary.little?.core)
    }

    @Test fun `okänd kärna räknas inte, okänd nyans räknas bara på kärnan`() {
        val stats = computeFeelingStats(listOf(entry("joyful", "calm.unknownword")))
        assertEquals(1, stats.tally.total)
        assertEquals(1, stats.tally.of("calm")?.count)
        assertTrue(checkNotNull(stats.tally.of("calm")).nuances.isEmpty())
        assertNull(stats.tally.of("joyful"))
    }

    @Test fun `mest och lite med tillfället den tredje hör till`() {
        val summary = computeFeelingStats(
            listOf(
                entry("glad.creative.curious", occasion = Occasion.BREAKFAST),
                entry("scared.anxious", occasion = Occasion.LUNCH),
                entry("calm.grateful", "glad.content", occasion = Occasion.DINNER),
                entry("calm", occasion = Occasion.BEDTIME)
            )
        ).summary
        assertEquals(listOf(FeelingRole("glad"), FeelingRole("calm")), summary.most, "två tillfällen → inget tillfälle")
        assertEquals(FeelingRole("scared", occasion = Occasion.LUNCH), summary.little)
        assertTrue(summary.untouched.isEmpty(), "under tio val sägs inget om orörda kärnor")
    }

    @Test fun `med en eller två kärnor finns ingen lite, och ett ensamt tillfälle följer med`() {
        val one = computeFeelingStats(listOf(entry("calm", occasion = Occasion.LUNCH), entry("calm.grateful", occasion = Occasion.LUNCH))).summary
        assertEquals(listOf(FeelingRole("calm", occasion = Occasion.LUNCH)), one.most)
        assertNull(one.little)
        val two = computeFeelingStats(listOf(entry("calm", "glad"))).summary
        assertEquals(listOf("glad", "calm"), two.most.map { it.core })
        assertNull(two.little)
    }

    @Test fun `aktiviteter har inget tillfälle`() {
        val summary = computeFeelingStats(listOf(entry("calm", "glad", "sad"), entry("sad", occasion = Occasion.LUNCH))).summary
        assertEquals(FeelingRole("calm"), summary.little, "lika många: hjulets ordning; posten utan tillfälle")
        assertEquals(FeelingRole("sad"), summary.most[0], "både utan tillfälle och vid lunch → inget tillfälle")
    }

    @Test fun `perioden - nästan alltid som nyansen och knappt rörd kärna`() {
        val entries = List(5) { entry("calm.grateful", "glad.content") } +
            listOf(entry("scared.anxious", "scared.anxious.tense"), entry("scared.insecure", "sad.tired"), entry("scared.anxious", "angry"))
        val summary = computeFeelingStats(entries).summary
        assertEquals(listOf("glad", "calm"), summary.most.map { it.core })
        assertEquals(FeelingRole("scared", nuance = "anxious"), summary.little, "3 av 4 som Orolig ≥ 2/3")
        assertEquals(listOf("strong"), summary.untouched, "16 val, Stark aldrig")
        assertEquals("content", summary.most[0].nuance, "5 av 5")
    }

    @Test fun `ingen vana under två tredjedelar eller med för få val`() {
        val split = computeFeelingStats(listOf(entry("scared.anxious", "scared.insecure", "scared.confused"))).summary
        assertNull(split.most.single().nuance, "1 av 3")
        val few = computeFeelingStats(listOf(entry("scared.anxious", "scared.anxious"))).summary
        assertNull(few.most.single().nuance, "två val räcker inte för nästan alltid")
        val twoThirds = computeFeelingStats(listOf(entry("scared.anxious", "scared.anxious", "scared"))).summary
        assertEquals("anxious", twoThirds.most.single().nuance, "exakt 2/3")
    }

    @Test fun `orörda kärnor nämns bara när de är högst två`() {
        val many = computeFeelingStats(List(10) { entry("calm") }).summary
        assertTrue(many.untouched.isEmpty(), "fem orörda: meningen säger redan allt")
        val two = computeFeelingStats(List(3) { entry("calm", "glad", "sad", "scared") }).summary
        assertEquals(listOf("strong", "angry"), two.untouched, "hjulets ordning")
    }

    @Test fun `dagsfördelning per datum i stigande ordning och utlagd på veckan`() {
        val monday = LocalDate(2026, 10, 12)
        val wednesday = LocalDate(2026, 10, 14)
        val stats = computeFeelingStats(listOf(entry("sad", date = wednesday), entry("glad", "glad.content", date = monday), entry("sad.tired", date = wednesday)))
        assertEquals(listOf(monday, wednesday), stats.days.map { it.date })
        assertEquals(2, stats.days[0].tally.of("glad")?.count)
        assertEquals(1f, stats.days[0].tally.of("glad")?.share)
        assertEquals(2, stats.days[1].tally.of("sad")?.count)
        assertEquals(4, stats.tally.total)
        val week = stats.alignTo((0..6).map { LocalDate(2026, 10, 12 + it) })
        assertEquals(7, week.size)
        assertEquals(stats.days[0], week[0])
        assertEquals(FeelingTally.EMPTY, week[1].tally)
        assertEquals(LocalDate(2026, 10, 13), week[1].date)
        assertEquals(stats.days[1], week[2])
    }

    @Test fun `måendeloggen som indata - utan datum hör den inte till någon dag`() {
        val logged = Screening("s1", date = day, time = LocalTime(12, 10), occasion = Occasion.LUNCH, feelings = listOf(Feeling("scared", "anxious")))
        assertEquals(FeelingEntry(day, listOf(Feeling("scared", "anxious")), LocalTime(12, 10), Occasion.LUNCH), logged.feelingEntry())
        assertNull(logged.copy(date = null).feelingEntry())
        assertEquals(FeelingEntry(day, logged.feelings, LocalTime(9, 0)), logged.toFeelingEntry(LocalTime(9, 0)), "en post utan tillfälle (aktivitet)")
    }

    @Test fun `en dag för sig - orden i tidsordning, varje ord en gång, andra dagar räknas inte`() {
        val other = LocalDate(2026, 10, 13)
        fun at(hour: Int?, vararg tokens: String, date: LocalDate = day) = FeelingEntry(date, tokens.map { checkNotNull(Feeling.parse(it)) }, hour?.let { LocalTime(it, 0) })
        val entries = listOf(
            at(18, "calm.grateful", "glad.content"),
            at(null, "sad.tired"),
            at(8, "glad.creative.curious"),
            at(12, "scared.anxious", "calm.grateful"),
            at(9, "angry", date = other)
        )
        val shown = feelingDay(entries, day)
        assertEquals(day, shown.date)
        assertEquals(
            listOf("glad.creative.curious", "scared.anxious", "calm.grateful", "glad.content", "sad.tired"),
            shown.words.map { it.token },
            "efter klockslag, utan klockslag sist, ett upprepat ord en gång"
        )
        assertEquals(6, shown.stats.tally.total, "räkningen tar varje val, också upprepningen")
        assertEquals(0, shown.stats.tally.of("angry")?.count, "en annan dag räknas inte")
        assertFalse(shown.isEmpty)
        val empty = feelingDay(entries, LocalDate(2026, 10, 15))
        assertTrue(empty.isEmpty)
        assertTrue(empty.words.isEmpty())
    }
}

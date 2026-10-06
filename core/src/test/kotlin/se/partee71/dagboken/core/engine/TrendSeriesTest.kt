package se.partee71.dagboken.core.engine

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Test
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.SymptomScore

/**
 * Trenders serier i gruppen Mående (TRD-1, TRD-8, TRD-15, TRD-21), portade från 3.x
 * `computeCategoryDataFor`: luckor, ett värde, dag utan poster och händelser/sjukdom per dag. Fasta datum.
 */
class TrendSeriesTest {

    private val d1 = LocalDate(2026, 10, 1)
    private val d2 = LocalDate(2026, 10, 2)
    private val d3 = LocalDate(2026, 10, 3)
    private val days = listOf(d1, d2, d3)

    private fun mood(id: String, date: LocalDate?, energy: Int, stress: Int = 0, occasion: Occasion? = Occasion.LUNCH, symptoms: List<SymptomScore> = emptyList()) =
        Screening(id, date, LocalTime(12, 0), occasion, energy = energy, stress = stress, symptoms = symptoms)

    private fun activity(id: String, date: LocalDate, stress: Int = 0, recovering: Boolean = false, drain: Boolean = false, symptoms: List<SymptomScore> = emptyList()) =
        Activity(id, date, LocalTime(15, 0), optionId = "promenad", stress = stress, recovering = recovering, drain = drain, symptoms = symptoms)

    // ── Energi per dag (TRD-8) ──────────────────────────────────────────────

    @Test
    fun `energi per dag ger spann och dagsvärde per dag och en lucka utan logg`() {
        val points = dailyEnergyPoints(listOf(mood("a", d1, 4), mood("b", d1, 8), mood("c", d3, 6)), days)
        assertEquals(listOf(IntervalPoint(4f, 6f, 8f), null, IntervalPoint(6f, 6f, 6f)), points)
        assertEquals(6f, dayComparison(d1, listOf(mood("a", d1, 4), mood("b", d1, 8)))!!.average, "samma dagsvärde som Idag (HEM-7, HEM-19)")
    }

    // ── Energi per tillfälle (TRD-1) ────────────────────────────────────────

    @Test
    fun `energi per tillfälle har en serie per tillfälle med dagens snitt och luckor`() {
        val series = energyByOccasion(
            listOf(
                mood("a", d1, 5, occasion = Occasion.BREAKFAST),
                mood("b", d1, 7, occasion = Occasion.BREAKFAST),
                mood("c", d2, 3, occasion = Occasion.LUNCH),
                mood("d", d3, 9, occasion = null),
                mood("e", null, 9, occasion = Occasion.BEDTIME),
            ),
            days,
        )
        assertEquals(Occasion.entries.map { it.wire }, series.map { it.key })
        assertEquals(listOf(6f, null, null), series[0].points, "frukost: snittet dag 1, luckor sedan")
        assertEquals(listOf(null, 3f, null), series[1].points)
        assertEquals(listOf(null, null, null), series[3].points, "utan tillfälle eller dag räknas loggen inte")
    }

    @Test
    fun `en enda logg ger ett värde och resten luckor`() {
        val series = energyByOccasion(listOf(mood("a", d2, 6, occasion = Occasion.DINNER)), days)
        assertEquals(listOf(null, 6f, null), series.first { it.key == Occasion.DINNER.wire }.points)
        assertEquals(1, knownCount(series.first { it.key == Occasion.DINNER.wire }.points))
    }

    // ── Stress och belastning (TRD-1) ──────────────────────────────────────

    @Test
    fun `stress och belastning räknar över måendeloggar och aktiviteter som 3x`() {
        val symptoms = listOf(SymptomScore("huvudvark", 4), SymptomScore("yrsel", 2))
        val series = stressSeries(
            screenings = listOf(mood("a", d1, 5, stress = 6, symptoms = symptoms)),
            activities = listOf(activity("x", d1, stress = 2, recovering = true), activity("y", d1, stress = 4, drain = true), activity("z", d1, recovering = true)),
            days = days,
        )
        assertEquals(StressSeries.entries.map { it.name }, series.map { it.key })
        val byKey = series.associate { it.key to it.points }
        assertEquals(listOf(3f, null, null), byKey.getValue("STRESS"), "snitt av 6, 2, 4 och 0")
        assertEquals(listOf(1.5f, null, null), byKey.getValue("SOMATIC"), "symptomsumman 6 över fyra poster")
        assertEquals(listOf(5f, null, null), byKey.getValue("RECOVERING"), "två av fyra poster × 10")
        assertEquals(listOf(2.5f, null, null), byKey.getValue("DRAIN"), "en av fyra × 10")
    }

    @Test
    fun `en dag utan poster är en lucka i alla fyra serierna`() {
        stressSeries(emptyList(), emptyList(), days).forEach { assertEquals(listOf(null, null, null), it.points, it.key) }
    }

    // ── Symptom (TRD-1) ────────────────────────────────────────────────────

    @Test
    fun `symptomserierna är de loggade symptomen i perioden med dagens snitt`() {
        val series = symptomSeries(
            screenings = listOf(mood("a", d1, 5, symptoms = listOf(SymptomScore("yrsel", 6))), mood("b", d1, 5, symptoms = listOf(SymptomScore("yrsel", 2)))),
            activities = listOf(
                activity("x", d3, symptoms = listOf(SymptomScore("huvudvark", 7), SymptomScore("yrsel", 3))),
                activity("y", LocalDate(2026, 9, 1), symptoms = listOf(SymptomScore("gammalt", 9))),
            ),
            days = days,
        )
        assertEquals(listOf("huvudvark", "yrsel"), series.map { it.key }, "i id-ordning, bara periodens symptom")
        assertEquals(listOf(null, null, 7f), series[0].points)
        assertEquals(listOf(4f, null, 3f), series[1].points)
    }

    @Test
    fun `utan symptom finns inga serier`() {
        assertEquals(emptyList(), symptomSeries(listOf(mood("a", d1, 5)), emptyList(), days))
    }

    // ── Händelser och sjukdom (TRD-21) ─────────────────────────────────────

    private val flu = IllnessEpisode("flu", "Förkylning", start = LocalDate(2026, 9, 30), createdAt = Instant.fromEpochSeconds(10))
    private val migraine = IllnessEpisode("mig", "Migrän", start = d2, end = d2, createdAt = Instant.fromEpochSeconds(20))

    @Test
    fun `händelser per dag som snitt, incheckningar som linje och episoder som fält kapade till perioden`() {
        val trend = eventIllnessTrend(
            events = listOf(Event("e1", d1, severity = 4), Event("e2", d1, severity = 7), Event("e3", d3, severity = 6), Event("old", LocalDate(2026, 9, 1), severity = 10), Event("none", null, severity = 10)),
            episodes = listOf(flu, migraine, IllnessEpisode("gone", "Influensa", start = LocalDate(2026, 9, 1), end = LocalDate(2026, 9, 10)), IllnessEpisode("nostart", "Okänd")),
            checkins = mapOf("flu" to listOf(Checkin("c1", d1, severity = 5), Checkin("c2", d1, severity = 7), Checkin("c3", d3, severity = 3)), "mig" to listOf(Checkin("c4", d2, severity = 8))),
            days = days,
        )
        assertEquals(listOf(5.5f, null, 6f), trend.events)
        assertEquals(listOf(6f, 8f, 3f), trend.checkins)
        assertEquals(3, trend.eventCount, "bara periodens händelser")
        assertEquals(5.6667f, trend.averageSeverity!!, 0.001f)
        assertEquals(listOf("flu", "mig"), trend.episodes.map { it.episode.id }, "episoder i perioden, i startordning; utan start eller före perioden räknas inte")
        assertEquals(EpisodeSpan(flu, 0, 2), trend.episodes[0], "pågående från före perioden: hela perioden")
        assertTrue(trend.episodes[0].ongoing)
        assertEquals(EpisodeSpan(migraine, 1, 1), trend.episodes[1])
    }

    @Test
    fun `utan händelser och sjukdom är allt luckor och tomt`() {
        val trend = eventIllnessTrend(emptyList(), emptyList(), emptyMap(), days)
        assertEquals(listOf(null, null, null), trend.events)
        assertEquals(listOf(null, null, null), trend.checkins)
        assertEquals(emptyList(), trend.episodes)
        assertEquals(0, trend.eventCount)
        assertNull(trend.averageSeverity)
    }

    @Test
    fun `en tom period ger tomma serier`() {
        val trend = eventIllnessTrend(listOf(Event("e1", d1, severity = 4)), listOf(flu), emptyMap(), emptyList())
        assertEquals(emptyList(), trend.events)
        assertEquals(0, trend.eventCount)
        assertEquals(emptyList(), trend.episodes)
    }
}

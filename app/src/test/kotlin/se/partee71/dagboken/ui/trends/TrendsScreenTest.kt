package se.partee71.dagboken.ui.trends

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.CompareKey
import se.partee71.dagboken.core.engine.ComparedSerie
import se.partee71.dagboken.core.engine.EpisodeSpan
import se.partee71.dagboken.core.engine.EventIllnessTrend
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.StackedPoint
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.engine.TrendSerie
import se.partee71.dagboken.core.engine.WATCH_COMPARE_KEYS
import se.partee71.dagboken.core.engine.WatchMetric
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.time.datesBetween
import se.partee71.dagboken.data.health.HealthStatus
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.testing.pixels
import se.partee71.dagboken.ui.common.distinctSeriesColors
import se.partee71.dagboken.ui.components.GalleryCharts
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Fliken Trender (TRD-3, TRD-12, TRD-14, TRD-16–TRD-21, NFR-18): grupperna, de stängda korten, ett utfällt kort med
 * periodväljare, serieval och diagram, Klocka med och utan Health Connect, Jämför, och skärmdumpar bredvid mockupen
 * (canvas avsnitt 11 · Trender, NFR-20). Påhittad data; idag är tisdag 6 oktober 2026.
 */
@RunWith(RobolectricTestRunner::class)
class TrendsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 6)
    private val days = datesBetween(LocalDate(2026, 9, 23), today)
    private val energy = days.indices.map { i -> listOf(IntervalPoint(3f, 4.5f, 6f), IntervalPoint(5f, 6f, 7f), null, IntervalPoint(6f, 7f, 8f), IntervalPoint(4f, 5.5f, 7f), IntervalPoint(7f, 7.5f, 8f), IntervalPoint(5f, 6.3f, 8f))[i % 7] }
    private val flu = IllnessEpisode("flu", "Förkylning", start = LocalDate(2026, 9, 30), createdAt = Instant.fromEpochSeconds(1))
    private val eventsTrend = EventIllnessTrend(
        events = listOf(null, 6f, null, null, 4f, null, null, null, 7f, 5f, null, null, null, 3f),
        checkins = listOf(null, null, null, null, null, null, null, 6f, 7f, 5f, 4f, 3f, null, 2f),
        episodes = listOf(EpisodeSpan(flu, 7, 13)),
        eventCount = 4,
        averageSeverity = 5.5f,
    )

    private val closed = TrendsUiState()

    private fun TrendsUiState.with(card: TrendCard, controls: CardControls, data: CardData?) =
        copy(cards = cards + (card to TrendCardState(controls, data)))

    private val energyOpen = closed.with(TrendCard.ENERGY_DAY, CardControls(expanded = true, range = TrendRange.FOURTEEN_DAYS), CardData.EnergyDay(days, energy))
    private val eventsOpen = closed.with(TrendCard.EVENTS_ILLNESS, CardControls(expanded = true, range = TrendRange.FOURTEEN_DAYS), CardData.EventsIllness(days, eventsTrend))
    private val occasionLines = CardData.Lines(
        days,
        Occasion.entries.map { SeriesInfo(it.wire, null) },
        shown = listOf(TrendSerie(Occasion.BREAKFAST.wire, listOf(5f, 6f, 5.5f, null, 6f, 7f, 6.5f, null, null, 6f, 7f, 6.5f, 7.5f, 8f))),
        previous = emptyList(),
    )
    private val occasionOpen = closed.with(TrendCard.ENERGY_OCCASION, CardControls(expanded = true, range = TrendRange.FOURTEEN_DAYS, selected = setOf(Occasion.BREAKFAST.wire)), occasionLines)
    private val nothingSelected = closed.with(TrendCard.SYMPTOMS, CardControls(expanded = true), CardData.Lines(days, listOf(SeriesInfo("yrsel", "Yrsel")), emptyList(), emptyList()))

    private val watchMissing = closed.copy(group = TrendGroup.WATCH, healthStatus = HealthStatus.UNAVAILABLE)
    /** Fjorton nätter: galleriets sju och sju till med en natt utan stadier (TRD-16). */
    private val stages = GalleryCharts.sleep + listOf(
        StackedPoint(listOf(1.1f, 1.5f, 3.9f, 0.7f)), StackedPoint(listOf(null, null, null, null)), StackedPoint(listOf(1.2f, 1.3f, 3.6f, 0.9f)),
        StackedPoint(listOf(1.0f, 1.6f, 4.3f, 0.5f)), StackedPoint(listOf(1.3f, 1.8f, 4.1f, 0.4f)), StackedPoint(listOf(0.9f, 1.2f, 3.5f, 1.0f)), StackedPoint(listOf(1.4f, 1.7f, 4.0f, 0.6f)),
    )
    private val stagesOpen = closed.copy(group = TrendGroup.WATCH, healthStatus = HealthStatus.AVAILABLE)
        .with(TrendCard.SLEEP_STAGES, CardControls(expanded = true, range = TrendRange.FOURTEEN_DAYS), CardData.Stacked(days, stages))

    private val compareAvailable = listOf(SeriesInfo(CompareKey.EnergyDay.wire, null), SeriesInfo(CompareKey.Stress(se.partee71.dagboken.core.engine.StressSeries.STRESS).wire, null), SeriesInfo(CompareKey.Symptom("yrsel").wire, "Yrsel")) +
        WATCH_COMPARE_KEYS.map { SeriesInfo(it.wire, null) }
    private val compareTwo = CardData.Compare(
        days,
        compareAvailable,
        listOf(
            ComparedSerie(CompareKey.EnergyDay.wire, listOf(33f, 56f, 44f, null, 56f, 78f, 67f, null, null, 56f, 78f, 67f, 89f, 100f), 3.5f, 8f),
            ComparedSerie(CompareKey.Watch(WatchMetric.STEPS).wire, listOf(10f, 35f, null, 100f, 60f, 0f, 45f, 70f, null, 55f, 80f, 65f, 90f, 75f), 2_350f, 11_020f),
        ),
        selectedCount = 2,
    )
    private val compareOpen = closed.copy(group = TrendGroup.COMPARE)
        .with(TrendCard.COMPARE, CardControls(expanded = true, range = TrendRange.FOURTEEN_DAYS, selected = compareTwo.shown.map { it.key }.toSet()), compareTwo)
    private val compareOne = closed.copy(group = TrendGroup.COMPARE)
        .with(TrendCard.COMPARE, CardControls(expanded = true, range = TrendRange.FOURTEEN_DAYS, selected = setOf(CompareKey.EnergyDay.wire)), compareTwo.copy(shown = compareTwo.shown.take(1), selectedCount = 1))
    private val sleepLines = CardData.Lines(
        days,
        listOf(WatchMetric.SLEEP_TOTAL, WatchMetric.SLEEP_DEEP, WatchMetric.SLEEP_REM, WatchMetric.SLEEP_LIGHT, WatchMetric.SLEEP_AWAKE).map { SeriesInfo(it.name, null) },
        shown = listOf(
            TrendSerie(WatchMetric.SLEEP_TOTAL.name, listOf(7.4f, 7f, null, 8f, 5.1f, 7.6f, 8f, 7.2f, null, 7f, 7.4f, 7.6f, 6.6f, 7.7f)),
            TrendSerie(WatchMetric.SLEEP_DEEP.name, listOf(1.2f, 1f, null, 1.4f, 0.8f, 1.3f, 1.5f, 1.1f, null, 1.2f, 1f, 1.3f, 0.9f, 1.4f)),
        ),
        previous = emptyList(),
    )

    private fun show(state: TrendsUiState, onEvent: (TrendsEvent) -> Unit = {}) = rule.setContent { DagbokenTheme { TrendsScreen(state, onEvent) } }

    @Test
    fun `rubriken, grupperna och fem stängda kort utan diagram eller periodväljare (TRD-14, TRD-19)`() {
        val events = mutableListOf<TrendsEvent>()
        show(closed) { events += it }
        rule.onNodeWithText("Trender").assertIsDisplayed()
        listOf("Mående", "Klocka", "Jämför").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        listOf("Energi per dag", "Energi per tillfälle", "Stress och belastning", "Symptom", "Händelser och sjukdom").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Månad").assertDoesNotExist()
        rule.onNodeWithText("Föregående period").assertDoesNotExist()
        rule.onNodeWithText("För lite data än").assertDoesNotExist()
        rule.onNodeWithText("Energi per dag").performClick()
        rule.onNodeWithText("Jämför").performClick()
        assertEquals(listOf(TrendsEvent.Toggle(TrendCard.ENERGY_DAY), TrendsEvent.ShowGroup(TrendGroup.COMPARE)), events)
    }

    @Test
    fun `Klocka utan Health Connect visar bannern överst och tio stängda kort (HLS-4, TRD-19, TRD-20)`() {
        val events = mutableListOf<TrendsEvent>()
        show(watchMissing) { events += it }
        rule.onNodeWithText("Health Connect saknas").assertIsDisplayed()
        rule.onNodeWithText("Klockans data visas när Health Connect är kopplat.").assertIsDisplayed()
        rule.onNodeWithText("Energi per dag").assertDoesNotExist()
        val list = rule.onNode(hasScrollAction())
        listOf("Steg", "Vilopuls", "Sömn", "Sömnstadier", "Sömnkvalitet", "Träning", "Aktiva kalorier", "Sträcka", "Syremättnad", "Blodtryck").forEach {
            list.performScrollToNode(hasText(it))
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        rule.onNodeWithText("Månad").assertDoesNotExist()
        list.performScrollToNode(hasText("Steg"))
        rule.onNodeWithText("Steg").performClick()
        assertEquals(listOf<TrendsEvent>(TrendsEvent.Toggle(TrendCard.STEPS)), events)
    }

    @Test
    fun `med Health Connect finns ingen banner`() {
        show(watchMissing.copy(healthStatus = HealthStatus.AVAILABLE))
        rule.onNodeWithText("Health Connect saknas").assertDoesNotExist()
        rule.onNodeWithText("Steg").assertIsDisplayed()
    }

    @Test
    fun `Sömnstadier utfällt staplar nätterna med teckenförklaring och utan serieval (TRD-16)`() {
        show(stagesOpen)
        rule.onNodeWithText("Health Connect saknas").assertDoesNotExist()
        val list = rule.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Vaken"))
        listOf("Djup", "REM", "Lätt", "Vaken", "Trend").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Visa:").assertDoesNotExist()
        rule.onNodeWithText("Föregående period").assertDoesNotExist()
        rule.onNodeWithContentDescription("Sömnstadier: 12 staplar", substring = true).assertIsDisplayed()
    }

    @Test
    fun `ett klockkort utan data visar sin egen uppmaning (TRD-11)`() {
        val empty = closed.copy(group = TrendGroup.WATCH).with(TrendCard.STEPS, CardControls(expanded = true), CardData.Lines(days, listOf(SeriesInfo(WatchMetric.STEPS.name, null)), listOf(TrendSerie(WatchMetric.STEPS.name, days.map { null })), emptyList()))
        show(empty)
        rule.onNodeWithText("För lite data än").assertIsDisplayed()
        rule.onNodeWithText("Ingen stegdata för vald period.").assertIsDisplayed()
        rule.onNodeWithText("Visa:").assertDoesNotExist()
        rule.onNodeWithText("Föregående period").assertIsDisplayed()
    }

    @Test
    fun `Jämför med två serier – legenden bär verkligt spann med enhet, fast axel utan bildtext och fotnot (TRD-17)`() {
        val events = mutableListOf<TrendsEvent>()
        show(compareOpen) { events += it }
        rule.onNodeWithText("Föregående period").assertDoesNotExist()
        // Vicos diagram är också rullbart i sidled – listan är den yttersta rullbara noden.
        val list = rule.onAllNodes(hasScrollAction()).onFirst()
        list.performScrollToNode(hasText("Varje serie visas 0–100 mot sitt eget lägsta och högsta värde."))
        rule.onNodeWithText("Energi (dag) · 3,5–8 skala").assertIsDisplayed()
        rule.onNodeWithText("Steg · 2350–11020 steg").assertIsDisplayed()
        rule.onNodeWithText("Lägst", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Steg · 2350–11020 steg: 12 värden, lägsta 0, högsta 100", substring = true).assertIsDisplayed()

        rule.onNodeWithText("Energi (dag) · Steg").performClick()
        listOf("MÅENDE", "KLOCKA").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Sömnlängd").assertIsDisplayed()
        rule.onNodeWithText("Dygnssnittspuls").assertIsDisplayed()
        rule.onNodeWithText("Yrsel").performClick()
        assertEquals(listOf<TrendsEvent>(TrendsEvent.ToggleSeries(TrendCard.COMPARE, CompareKey.Symptom("yrsel").wire)), events)
    }

    @Test
    fun `Sömn-linjerna har stadiernas färger från Sömnstadier, Total sin egen (TRD-15, TRD-16)`() {
        show(closed.copy(group = TrendGroup.WATCH).with(TrendCard.SLEEP, CardControls(expanded = true, range = TrendRange.FOURTEEN_DAYS, selected = sleepLines.shown.map { it.key }.toSet()), sleepLines))
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Djup"))
        val chart = rule.onNodeWithContentDescription("Total:", substring = true)
        assertTrue(chart.pixels(AppColors.lightExtended.sleepStages.deep) > 0, "djupsömnen i Sömnstadiers färg")
        assertTrue(chart.pixels(AppColors.swatch(0)) > 0, "Total i sin egen färg")
    }

    @Test
    fun `Jämförs serier behåller färgen från sitt kort och krockar löses (TRD-17)`() {
        // Energi (dag) och Steg är båda ensamma (teal) i sina kort – den andra får nästa lediga färg.
        show(compareOpen)
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Steg · 2350–11020 steg"))
        val colors = distinctSeriesColors(listOf(null, null), AppColors.light.primary)
        assertEquals(AppColors.light.primary, colors[0])
        assertTrue(colors[1] != colors[0])
        val chart = rule.onNodeWithContentDescription("Energi (dag)", substring = true)
        colors.forEach { assertTrue(chart.pixels(it) > 0, "serien ritas i $it") }
    }

    @Test
    fun `Jämför med två valda men bara en med data säger För lite data (TRD-17)`() {
        show(compareOpen.with(TrendCard.COMPARE, CardControls(expanded = true, selected = compareTwo.shown.map { it.key }.toSet()), compareTwo.copy(shown = compareTwo.shown.take(1))))
        rule.onNodeWithText("För lite data för de valda serierna").assertIsDisplayed()
        rule.onNodeWithText("Välj en annan period eller andra serier.").assertIsDisplayed()
        rule.onNodeWithText("Välj minst två serier").assertDoesNotExist()
    }

    @Test
    fun `sömnkvalitet utan födelseår ber om Profil – i kortet och i Jämförs fotnot (HLS-11)`() {
        val quality = CardData.Lines(days, listOf(SeriesInfo("SCORE", null)), listOf(TrendSerie("SCORE", days.map { null })), emptyList(), needsBirthYear = true)
        show(closed.copy(group = TrendGroup.WATCH).with(TrendCard.SLEEP_QUALITY, CardControls(expanded = true, selected = setOf("SCORE")), quality))
        rule.onNodeWithText("Fyll i födelseår i Profil för att se sömnkvalitet.").assertIsDisplayed()
        rule.onNodeWithText("Ingen sömnkvalitet för vald period.").assertDoesNotExist()
    }

    @Test
    fun `Jämförs fotnot nämner födelseåret när sömnkvaliteten utelämnats (HLS-11, TRD-17)`() {
        show(compareOpen.with(TrendCard.COMPARE, CardControls(expanded = true, selected = compareTwo.shown.map { it.key }.toSet() + CompareKey.SleepQuality.wire), compareTwo.copy(selectedCount = 3, needsBirthYear = true)))
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Sömnkvalitet visas när födelseår fyllts i under Profil.", substring = true))
        rule.onNodeWithText("Sömnkvalitet visas när födelseår fyllts i under Profil.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `Jämför med färre än två serier visar Välj minst två serier (TRD-17)`() {
        show(compareOne)
        rule.onNodeWithText("Välj minst två serier").assertIsDisplayed()
        rule.onNodeWithText("Jämför mående och klockdata i samma diagram.").assertIsDisplayed()
        rule.onNodeWithText("För lite data än").assertDoesNotExist()
        rule.onNodeWithText("Energi (dag)").assertIsDisplayed()
    }

    @Test
    fun `ett ihopfällt Sömnstadier visar snitt och trend över totalen (TRD-14, TRD-16)`() {
        show(closed.copy(group = TrendGroup.WATCH).with(TrendCard.SLEEP_STAGES, CardControls(), CardData.Stacked(days, stages)))
        rule.onNodeWithText("Snitt 7,2", substring = true).assertIsDisplayed()
    }

    @Test
    fun `ett ihopfällt Jämför med en serie säger Välj minst två serier (TRD-14, TRD-17)`() {
        show(closed.copy(group = TrendGroup.COMPARE).with(TrendCard.COMPARE, CardControls(), compareTwo.copy(shown = compareTwo.shown.take(1), selectedCount = 1)))
        rule.onNodeWithText("Välj minst två serier").assertIsDisplayed()
    }

    @Test
    fun `ett ihopfällt Jämför med två valda men en med data säger För lite data (TRD-14, TRD-17)`() {
        show(closed.copy(group = TrendGroup.COMPARE).with(TrendCard.COMPARE, CardControls(), compareTwo.copy(shown = compareTwo.shown.take(1))))
        rule.onNodeWithText("För lite data för de valda serierna").assertIsDisplayed()
    }

    @Test
    fun `ett utfällt kort har periodväljaren i titelraden och byter period via menyn (TRD-3, TRD-12)`() {
        val events = mutableListOf<TrendsEvent>()
        show(energyOpen) { events += it }
        rule.onNodeWithText("14 dagar").assertIsDisplayed().performClick()
        rule.onNodeWithText("3 månader").performClick()
        assertEquals(listOf<TrendsEvent>(TrendsEvent.SetRange(TrendCard.ENERGY_DAY, TrendRange.THREE_MONTHS)), events)
        rule.onNodeWithText("Föregående period").assertDoesNotExist()
        rule.onNodeWithContentDescription("Energi per dag:", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Lägst 3 · Högst 8 · Snitt 6,1").assertIsDisplayed()
    }

    @Test
    fun `serieval med kryssrader och Föregående period – inte vid Allt (TRD-2, TRD-18)`() {
        val events = mutableListOf<TrendsEvent>()
        show(occasionOpen) { events += it }
        rule.onNodeWithText("Visa:").assertIsDisplayed()
        rule.onNodeWithText("Föregående period").assertIsOff().performClick()
        rule.onNodeWithText("Efter frukost").performClick()
        rule.onNodeWithText("Lunch").performClick()
        rule.onNodeWithText("Läggdags").assertIsDisplayed()
        assertEquals(listOf(TrendsEvent.SetPrevious(TrendCard.ENERGY_OCCASION, true), TrendsEvent.ToggleSeries(TrendCard.ENERGY_OCCASION, Occasion.LUNCH.wire)), events)
    }

    @Test
    fun `vid Allt finns inget tillval för föregående period (TRD-18)`() {
        show(closed.with(TrendCard.ENERGY_OCCASION, CardControls(expanded = true, range = TrendRange.ALL, selected = setOf(Occasion.BREAKFAST.wire)), occasionLines))
        rule.onNodeWithText("Allt").assertIsDisplayed()
        rule.onNodeWithText("Föregående period").assertDoesNotExist()
    }

    @Test
    fun `utan vald serie står Välj minst en dataserie i det tomma läget`() {
        show(nothingSelected)
        rule.onNodeWithText("För lite data än").assertIsDisplayed()
        rule.onNodeWithText("Välj minst en dataserie").assertIsDisplayed()
    }

    @Test
    fun `innan kortet lästs finns inget serieval`() {
        show(closed.with(TrendCard.SYMPTOMS, CardControls(expanded = true), null))
        rule.onNodeWithText("Visa:").assertDoesNotExist()
        rule.onNodeWithText("Föregående period").assertIsDisplayed()
    }

    @Test
    fun `ett symptom utan namn i Listor heter Symptom – aldrig ett rått id`() {
        val unnamed = CardData.Lines(days, listOf(SeriesInfo("okand", null)), listOf(TrendSerie("okand", listOf(3f, 4f, 5f))), emptyList())
        show(closed.with(TrendCard.SYMPTOMS, CardControls(expanded = true, selected = setOf("okand")), unnamed))
        rule.onNodeWithText("okand").assertDoesNotExist()
        rule.onAllNodesWithText("Symptom").assertCountEquals(2)
        rule.onNodeWithContentDescription("Symptom: 3 värden", substring = true).assertIsDisplayed()
    }

    @Test
    fun `ett ihopfällt kort visar sin sammanfattning när det lästs (TRD-14, TRD-21)`() {
        show(
            closed
                .with(TrendCard.ENERGY_DAY, CardControls(), CardData.EnergyDay(days, energy))
                .with(TrendCard.EVENTS_ILLNESS, CardControls(), CardData.EventsIllness(days, eventsTrend))
                .with(TrendCard.ENERGY_OCCASION, CardControls(selected = setOf(Occasion.BREAKFAST.wire, Occasion.LUNCH.wire)), occasionLines.copy(shown = occasionLines.shown + TrendSerie(Occasion.LUNCH.wire, listOf(5f)))),
        )
        rule.onNodeWithText("Snitt 6,1 · Trend uppåt").assertIsDisplayed()
        rule.onNodeWithText("2 serier valda").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("4 händelser · snitt 5,5 · Förkylning 30 sep – pågår"))
        rule.onNodeWithText("Energi per dag:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `händelser och sjukdom ritar staplar, linje och fält med teckenförklaring (TRD-21)`() {
        show(eventsOpen)
        val list = rule.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Incheckningar"))
        listOf("Händelser", "Incheckningar", "Förkylning", "Trend").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithContentDescription("Incheckningar: 6 värden", substring = true).assertIsDisplayed()
        list.performScrollToNode(hasText("4 händelser · snitt 5,5 · Förkylning 30 sep – pågår"))
    }

    // ── Skärmdumpar (ljust + mörkt) ──────────────────────────────────────────

    @Test
    fun `skärmdump - Mående stängd`() = rule.captureLightAndDark("Trends_maende_stangd") { TrendsScreen(closed, {}) }

    @Test
    fun `skärmdump - Energi per dag utfälld`() = rule.captureLightAndDark("Trends_energi_per_dag") { TrendsScreen(energyOpen, {}) }

    @Test
    fun `skärmdump - Händelser och sjukdom utfälld`() = rule.captureLightAndDark(
        "Trends_handelser_sjukdom",
        settle = {
            waitForIdle()
            onNode(hasScrollAction()).performScrollToNode(hasText("Händelser och sjukdom"))
            waitForIdle()
        },
    ) { TrendsScreen(eventsOpen, {}) }

    @Test
    fun `skärmdump - tomt`() = rule.captureLightAndDark("Trends_tomt") { TrendsScreen(nothingSelected, {}) }

    @Test
    fun `skärmdump - Klocka utan Health Connect`() = rule.captureLightAndDark("Trends_klocka_saknas") { TrendsScreen(watchMissing, {}) }

    @Test
    fun `skärmdump - Klocka med Sömnstadier utfällt`() = rule.captureLightAndDark(
        "Trends_klocka_somnstadier",
        settle = {
            waitForIdle()
            onNode(hasScrollAction()).performScrollToNode(hasText("Sömnstadier"))
            waitForIdle()
        },
    ) { TrendsScreen(stagesOpen, {}) }

    @Test
    fun `skärmdump - Jämför med två serier`() = rule.captureLightAndDark("Trends_jamfor") { TrendsScreen(compareOpen, {}) }

    @Test
    fun `skärmdump - Jämför med färre än två serier`() = rule.captureLightAndDark("Trends_jamfor_for_fa") { TrendsScreen(compareOne, {}) }

    @Test
    fun `skärmdump - Jämförs serieval`() = rule.captureScreenLightAndDark(
        "Trends_jamfor_serieval",
        open = { onNodeWithText("Energi (dag) · Steg").clickWithoutRipple() },
    ) { TrendsScreen(compareOpen, {}) }

    @Test
    fun `skärmdump - serievalets meny`() = rule.captureScreenLightAndDark(
        "Trends_serieval",
        open = { onNodeWithText("Efter frukost").clickWithoutRipple() },
    ) { TrendsScreen(occasionOpen, {}) }
}

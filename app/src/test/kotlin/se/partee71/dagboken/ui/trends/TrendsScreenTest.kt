package se.partee71.dagboken.ui.trends

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.EpisodeSpan
import se.partee71.dagboken.core.engine.EventIllnessTrend
import se.partee71.dagboken.core.engine.IntervalPoint
import se.partee71.dagboken.core.engine.TrendRange
import se.partee71.dagboken.core.engine.TrendSerie
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.time.datesBetween
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Fliken Trender (TRD-3, TRD-12, TRD-14, TRD-18, TRD-19, TRD-21, NFR-18): grupperna, de stängda korten, ett
 * utfällt kort med periodväljare, serieval och diagram, och skärmdumpar bredvid mockupen (canvas avsnitt 11 ·
 * Trender, NFR-20). Påhittad data; idag är tisdag 6 oktober 2026.
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
    fun `Klocka och Jämför säger Snart här`() {
        show(closed.copy(group = TrendGroup.WATCH))
        rule.onNodeWithText("Snart här").assertIsDisplayed()
        rule.onNodeWithText("Energi per dag").assertDoesNotExist()
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
    fun `skärmdump - serievalets meny`() = rule.captureScreenLightAndDark(
        "Trends_serieval",
        open = { onNodeWithText("Efter frukost").clickWithoutRipple() },
    ) { TrendsScreen(occasionOpen, {}) }
}

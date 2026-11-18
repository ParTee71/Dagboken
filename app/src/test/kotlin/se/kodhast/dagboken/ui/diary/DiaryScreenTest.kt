package se.kodhast.dagboken.ui.diary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.kodhast.dagboken.core.engine.DiaryEntry
import se.kodhast.dagboken.core.engine.DiaryFilter
import se.kodhast.dagboken.core.engine.DiarySources
import se.kodhast.dagboken.core.engine.DiaryType
import se.kodhast.dagboken.core.engine.dayLabel
import se.kodhast.dagboken.core.engine.diaryDays
import se.kodhast.dagboken.core.engine.diaryEntries
import se.kodhast.dagboken.core.model.Activity
import se.kodhast.dagboken.core.model.Checkin
import se.kodhast.dagboken.core.model.Dose
import se.kodhast.dagboken.core.model.DoseStatus
import se.kodhast.dagboken.core.model.Event
import se.kodhast.dagboken.core.model.Feeling
import se.kodhast.dagboken.core.model.IllnessEpisode
import se.kodhast.dagboken.core.model.Occasion
import se.kodhast.dagboken.core.model.Screening
import se.kodhast.dagboken.core.model.Slot
import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.testing.captureLightAndDark
import se.kodhast.dagboken.testing.captureScreenLightAndDark
import se.kodhast.dagboken.testing.clickWithoutRipple
import se.kodhast.dagboken.ui.DagbokenContracts
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.dagboken.ui.theme.DagbokenTheme
import se.kodhast.ui.common.ListUiState
import se.kodhast.ui.components.APP_HEADER_TAG
import se.kodhast.ui.testing.runListScreenContract

/**
 * Fliken Dagbok (HIST-1, HIST-2, HIST-3, HIST-5, HIST-6, HIST-7, HIST-8, HIST-9, NFR-15, NFR-16): ramens
 * kontrakt, det unika och skärmdumpar bredvid mockupen (canvas avsnitt 10 · Dagbok, NFR-20). Påhittad data;
 * idag är tisdag 6 oktober 2026.
 */
@RunWith(RobolectricTestRunner::class)
class DiaryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 6)
    private val yesterday = LocalDate(2026, 10, 5)
    private val saturday = LocalDate(2026, 10, 3)
    private val flu = IllnessEpisode("flu", "Förkylning", start = saturday, note = "Halsont först")
    private val zone = TimeZone.of("Europe/Stockholm")

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)
    private val sources = DiarySources(
        screenings = listOf(Screening("s1", today, LocalTime(8, 15), Occasion.BREAKFAST, energy = 7, stress = 3, note = "Sov gott")),
        activities = listOf(Activity("a1", yesterday, LocalTime(17, 30), optionId = "promenad", energy = 4, minutes = 45)),
        doses = listOf(Dose("d1", today, Slot.MORNING, "Levaxin", "100", "µg", status = DoseStatus.TAKEN, takenAt = at(today, 7, 42))),
        events = listOf(Event("e1", saturday, LocalTime(14, 0), optionId = "migran", severity = 7, durationMinutes = 90)),
        episodes = listOf(flu),
        checkins = mapOf("flu" to listOf(Checkin("c1", yesterday, LocalTime(9, 0), severity = 4)))
    )
    private val names = mapOf("promenad" to "Promenad", "migran" to "Migrän")
    private val entries = diaryEntries(sources, zone)

    private fun row(entry: DiaryEntry) = DiaryRow.Entry(
        entry,
        dayLabel(entry.date, today),
        when (entry) {
            is DiaryEntry.Action -> names[entry.activity.optionId]
            is DiaryEntry.Happening -> names[entry.event.optionId]
            else -> null
        }
    )

    private val listRows = diaryDays(entries, today).flatMap { day -> day.entries.map(::row) }
    private val controls = DiaryControls(today, datesWithEntries = persistentSetOf(today, yesterday, saturday))

    @Test
    fun `fliken har Kodhäst-sidhuvudet med flikens namn som rubrik (NAV-12)`() {
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(listRows.toImmutableList()), controls, null, {}, {}) } }
        rule.onNodeWithTag(APP_HEADER_TAG).assertIsDisplayed()
        rule.onNode(hasText("Dagbok") and isHeading()).assertIsDisplayed()
    }

    @Test
    fun `fliken uppfyller listkontraktet utan lägg till`() = rule.runListScreenContract<DiaryRow>(DagbokenContracts, listRows.first(), "Efter frukost", "Inga poster än", null) { state, _, onRetry ->
        DiaryScreen(state, controls, null, { if (it == DiaryEvent.Retry) onRetry() }, {})
    }

    @Test
    fun `dagarna har Idag, Igår och datum, och korten klockslaget först och värdet som pill (HIST-1, HIST-7, HIST-9)`() {
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(listRows.toImmutableList()), controls, null, {}, {}) } }
        rule.onNodeWithText("Idag · tisdag 6 oktober").assertIsDisplayed()
        rule.onNodeWithText("08:15 · Stress 3").assertIsDisplayed()
        rule.onNodeWithText("Energi 7").assertIsDisplayed()
        rule.onNodeWithText("Levaxin 100 µg").assertIsDisplayed()
        rule.onNodeWithText("07:42 · Morgon").assertIsDisplayed()
        rule.onNodeWithText("Igår · måndag 5 oktober").assertIsDisplayed()
        rule.onNodeWithText("17:30 · 45 min").assertIsDisplayed()
        rule.onNodeWithText("Energi +4").assertIsDisplayed()
        val list = rule.onNode(hasScrollAction())
        list.performScrollToNode(hasText("09:00 · Incheckning · Svårighet 4"))
        rule.onNodeWithText("Dag 3").assertIsDisplayed()
        list.performScrollToNode(hasText("Lördag 3 oktober"))
        list.performScrollToNode(hasText("Svårighet 7"))
        list.performScrollToNode(hasText("Sjukdomen började"))
        list.performScrollToNode(hasText("Visa äldre än ett år"))
    }

    @Test
    fun `måendeloggen och aktiviteten visar sina känslor som pills, högst tre och sedan +N (HIST-10)`() {
        val withFeelings = sources.copy(
            screenings = sources.screenings.map { it.copy(feelings = listOf(Feeling("calm", "grateful"))) },
            activities = sources.activities.map { it.copy(feelings = listOf(Feeling("strong"), Feeling("glad"), Feeling("sad"), Feeling("calm"), Feeling("angry"))) }
        )
        val rows = diaryEntries(withFeelings, zone).filter { it is DiaryEntry.Mood || it is DiaryEntry.Action }.map(::row)
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(rows.toImmutableList()), controls, null, {}, {}) } }
        // Måendeloggen: känslan i kärnans ton; aktiviteten: tre och "+2".
        rule.onNodeWithText("Lugn › Tacksam", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Ledsen", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Lugn", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("2 känslor till", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Välj dag, filterchipsen och Visa äldre skickar sina händelser (HIST-2, HIST-6, HIST-8)`() {
        val events = mutableListOf<DiaryEvent>()
        val filtered = controls.copy(filter = DiaryFilter(setOf(DiaryType.EVENT)))
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(listRows.toImmutableList()), filtered, null, { events += it }, {}) } }
        rule.onNodeWithText("Alla").assertIsNotSelected().performClick()
        rule.onNodeWithText("Händelser").assertIsSelected()
        rule.onNodeWithText("Doser").assertIsNotSelected().performClick()
        rule.onNodeWithText("Välj dag").performClick()
        rule.onNodeWithText("Oktober 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("ons 7 okt 2026").assertIsNotEnabled()
        rule.onNodeWithContentDescription("lör 3 okt 2026, har poster").performClick()
        rule.onNodeWithText("Oktober 2026").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Visa äldre än ett år"))
        rule.onNodeWithText("Visa äldre än ett år").performClick()
        assertEquals(
            listOf(DiaryEvent.ShowAll, DiaryEvent.Toggle(DiaryType.DOSE), DiaryEvent.JumpTo(saturday), DiaryEvent.ShowOlder),
            events
        )
    }

    @Test
    fun `när alla typer visas är bara Alla markerat`() {
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(listRows.toImmutableList()), controls, null, {}, {}) } }
        rule.onNodeWithText("Alla").assertIsSelected()
        listOf("Mående", "Aktiviteter", "Doser", "Händelser", "Sjukdom").forEach { rule.onNodeWithText(it).assertIsNotSelected() }
    }

    @Test
    fun `tryck öppnar posten, och Radera bekräftas innan den raderas (HIST-3, HIST-5, NFR-15)`() {
        val events = mutableListOf<DiaryEvent>()
        val opened = mutableListOf<DiaryEntry>()
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(listRows.toImmutableList()), controls, null, { events += it }, { opened += it }) } }
        rule.onNodeWithText("Levaxin 100 µg").performClick()
        assertEquals(listOf("dose:d1"), opened.map { it.id })

        rule.onNodeWithText("Levaxin 100 µg").performTouchInput { longClick() }
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Radera posten?").assertIsDisplayed()
        rule.onNodeWithText("Dosen Levaxin 100 µg, 6 okt kl. 07:42 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
        assertEquals(emptyList(), events, "inget raderas före bekräftelsen")
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<DiaryEvent>(DiaryEvent.Delete(entries.single { it.id == "dose:d1" })), events)
    }

    @Test
    fun `episodens start har Redigera men inte Radera, och en episod öppnas (HIST-5, HIST-9, SJ-9)`() {
        val opened = mutableListOf<DiaryEntry>()
        val start = listOf(row(entries.single { it is DiaryEntry.EpisodeStart }))
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(start.toImmutableList()), controls, null, {}, { opened += it }) } }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Redigera").assertIsDisplayed()
        rule.onNodeWithText("Radera").assertDoesNotExist()
        rule.onNodeWithText("Redigera").performClick()
        assertEquals(listOf(DiaryType.ILLNESS), opened.map { it.type })
    }

    @Test
    fun `Välj dag rullar listan så att dagens rubrik syns, säger när den valda dagen saknar poster, och listan släpper begäran (HIST-6)`() {
        val many = (1..20).flatMap { n ->
            val day = LocalDate(2026, 9, 31 - n)
            listOf(row(DiaryEntry.Action(Activity("a$n", day, LocalTime(12, 0), optionId = "promenad"), day)))
        }
        var shown by mutableStateOf(controls)
        val events = mutableListOf<DiaryEvent>()
        rule.setContent {
            DagbokenTheme {
                DiaryScreen(ListUiState.Content(many.toImmutableList()), shown, null, {
                    events += it
                    if (it == DiaryEvent.JumpDone) shown = shown.copy(jump = null)
                }, {})
            }
        }
        rule.onNodeWithText("Tisdag 15 september").assertDoesNotExist()
        shown = shown.copy(jump = DiaryJump("activity:a16", 1, emptyDay = LocalDate(2026, 9, 16)))
        rule.waitForIdle()
        rule.onNodeWithText("Tisdag 15 september").assertIsDisplayed()
        rule.onNodeWithText("Inga poster onsdag 16 september").assertIsDisplayed()
        assertEquals(listOf<DiaryEvent>(DiaryEvent.JumpDone), events)
    }

    @Test
    fun `incheckningen och receptdosen namnges med typ och tid i bekräftelsen (HIST-5, MED-15)`() {
        val recept = Dose("r1", yesterday, Slot.EVENING, "Atarax", "25", "mg", status = DoseStatus.TAKEN, prescriptionId = "atarax", takenAt = at(yesterday, 21, 5))
        val rows = diaryEntries(sources.copy(doses = listOf(recept), screenings = emptyList(), activities = emptyList(), events = emptyList()), zone)
            .filter { it is DiaryEntry.CheckIn || it is DiaryEntry.TakenDose }.map(::row)
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Content(rows.toImmutableList()), controls, null, {}, {}) } }
        rule.onAllNodesWithContentDescription("Fler val")[0].performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Dosen Atarax 25 mg, 5 okt kl. 21:05 markeras som överhoppad och försvinner ur Dagbok. Receptet och anteckningen står kvar.").assertIsDisplayed()
        rule.onNodeWithText("Avbryt").performClick()
        rule.onAllNodesWithContentDescription("Fler val")[1].performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Incheckningen för Förkylning, 5 okt kl. 09:00 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
    }

    @Test
    fun `tomt för filtret har Visa äldre (HIST-8)`() {
        val events = mutableListOf<DiaryEvent>()
        rule.setContent { DagbokenTheme { DiaryScreen(ListUiState.Empty, controls.copy(filter = DiaryFilter(setOf(DiaryType.EVENT))), null, { events += it }, {}) } }
        rule.onNodeWithText("Inga händelser än").assertIsDisplayed()
        rule.onNodeWithText("Visa äldre än ett år").performClick()
        assertEquals(listOf<DiaryEvent>(DiaryEvent.ShowOlder), events)
    }

    // ── Skärmdumpar (ljust + mörkt) ──────────────────────────────────────────

    @Test
    fun `skärmdump - listan`() = rule.captureLightAndDark("Diary_lista") {
        DiaryScreen(ListUiState.Content(listRows.toImmutableList()), controls, null, {}, {})
    }

    @Test
    fun `skärmdump - Välj dag öppet`() = rule.captureScreenLightAndDark(
        "Diary_valj_dag",
        open = { onNodeWithText("Välj dag").clickWithoutRipple() }
    ) {
        DiaryScreen(ListUiState.Content(listRows.toImmutableList()), controls, null, {}, {})
    }

    @Test
    fun `skärmdump - tomt för filtret`() = rule.captureLightAndDark("Diary_tomt_filter") {
        DiaryScreen(ListUiState.Empty, controls.copy(filter = DiaryFilter(setOf(DiaryType.EVENT))), null, {}, {})
    }

    @Test
    fun `skärmdump - läsfel`() = rule.captureLightAndDark("Diary_fel") {
        DiaryScreen(ListUiState.Error(AppErrors.map(DataError.Offline)), controls, null, {}, {})
    }

    @Test
    fun `skärmdump - ta bort post`() = rule.captureScreenLightAndDark(
        "Diary_radera",
        open = {
            onAllNodesWithContentDescription("Fler val")[0].clickWithoutRipple()
            waitForIdle()
            onNodeWithText("Radera").clickWithoutRipple()
        }
    ) {
        DiaryScreen(ListUiState.Content(listRows.toImmutableList()), controls, null, {}, {})
    }
}

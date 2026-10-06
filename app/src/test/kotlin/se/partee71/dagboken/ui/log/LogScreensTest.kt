package se.partee71.dagboken.ui.log

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.OTHER_ACTIVITY_ID
import se.partee71.dagboken.core.engine.OTHER_SYMPTOM_ID
import se.partee71.dagboken.core.engine.OccasionState
import se.partee71.dagboken.core.engine.OccasionStatus
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EntryEditEvent
import se.partee71.dagboken.ui.common.EntryForm
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.components.LogChoice
import se.partee71.dagboken.ui.runEditScreenContract
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Plusknappens formulär (AKT-1–AKT-11, HAN-1, HEM-8b): ramens kontrakt, det som är unikt för formulären och
 * skärmdumpar bredvid mockupens tavlor (canvas avsnitt 12 – "Ny aktivitet", "Aktivitet: Övrigt", "Mående: välj
 * tillfälle", "Ny händelse"). Påhittad data.
 */
@RunWith(RobolectricTestRunner::class)
class LogScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(2026, 10, 6)
    private val created = Instant.fromEpochSeconds(1_791_270_000)

    private val activityTypes = listOf(
        Option("walk", OptionKind.ACTIVITY, "Promenad", favorite = true, sortOrder = 0),
        Option("work", OptionKind.ACTIVITY, "Jobb", favorite = true, sortOrder = 1),
        Option("gym", OptionKind.ACTIVITY, "Träning", favorite = true, sortOrder = 2),
        Option("rest", OptionKind.ACTIVITY, "Vila", sortOrder = 3),
        Option("read", OptionKind.ACTIVITY, "Läsning", sortOrder = 4),
    )
    private val symptoms = listOf(
        Option("headache", OptionKind.SYMPTOM, "Huvudvärk", favorite = true),
        Option("tired", OptionKind.SYMPTOM, "Trötthet", sortOrder = 1),
        Option(OTHER_SYMPTOM_ID, OptionKind.SYMPTOM, "Övrigt", sortOrder = 2),
    )
    private val eventTypes = listOf(
        Option("migraine", OptionKind.EVENT, "Ögonmigrän", favorite = true, sortOrder = 0),
        Option("dizzy", OptionKind.EVENT, "Yrsel", favorite = true, sortOrder = 1),
        Option("palpitations", OptionKind.EVENT, "Hjärtklappning", sortOrder = 2),
    )

    private val walk = Activity("a1", day, LocalTime(14, 20), optionId = "walk", minutes = 45, createdAt = created)
    private val other = walk.copy(optionId = OTHER_ACTIVITY_ID, customText = "Svamplockning", recovering = true, energy = 3, stress = 1)
    private val migraine = Event("e1", day, LocalTime(9, 40), optionId = "migraine", severity = 5, durationMinutes = 30, createdAt = created)

    private fun <T> form(value: T, isNew: Boolean = true, stored: T? = null, onEvent: (EntryEditEvent<T>) -> Unit = {}) =
        EntryForm(isNew, EditorUiState(value), emptyFlow(), onEvent, stored)

    // ── Aktivitet ─────────────────────────────────────────────────────────

    @Test
    fun `aktivitetsformuläret uppfyller redigeringskontraktet (AKT-9, NFR-10)`() {
        val editor = EditorState(walk.copy(optionId = ""), activityValidator)
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(ActivityField.TYPE, ActivityField.DESCRIPTION) { it.copy(optionId = OTHER_ACTIVITY_ID) } },
            makeValid = { update(ActivityField.DESCRIPTION) { it.copy(customText = "Svamplockning") } },
            invalidMessage = "Beskriv aktiviteten",
        ) { state, effects, onSave, onClose ->
            ActivityEditScreen(EntryForm(true, state, effects, { if (it == EntryEditEvent.Save) onSave() }), onClose, activityTypes)
        }
    }

    @Test
    fun `typen väljs bland chips eller under Fler typer, där Övrigt står sist och ger beskrivningen (AKT-1, AKT-2)`() {
        var value by mutableStateOf(walk)
        rule.setContent {
            DagbokenTheme {
                ActivityEditScreen(form(value) { event -> if (event is EntryEditEvent.Changed) value = event.change(value) }, {}, activityTypes)
            }
        }
        rule.onNodeWithText("Beskriv aktivitet").assertDoesNotExist()
        rule.onNodeWithText("Jobb").performClick()
        assertEquals("work", value.optionId)
        rule.onNodeWithContentDescription("Fler typer, Välj typ").performClick()
        rule.onNodeWithText("Övrigt").performClick()
        assertEquals(OTHER_ACTIVITY_ID, value.optionId)
        rule.onNodeWithText("Beskriv aktivitet").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler typer, Övrigt").assertExists()
        rule.onNodeWithText("Återhämtande").performClick()
        assertEquals(true, value.recovering)
    }

    @Test
    fun `mätvärdena är ihopfällda med värdena i stängt läge (AKT-4, AKT-5, AKT-8)`() {
        rule.setContent { DagbokenTheme { ActivityEditScreen(form(other, isNew = false), {}, activityTypes) } }
        rule.onNodeWithText("Energi +3 · Stress 1").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Mätvärden").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Energi").assertExists()
        rule.onNodeWithContentDescription("Stress").assertExists()
    }

    @Test
    fun `en sparad aktivitet raderas efter bekräftelsen med typ och tid (HIST-5)`() {
        val events = mutableListOf<EntryEditEvent<Activity>>()
        // Formuläret visar en ändrad typ; bekräftelsen namnger den lagrade posten.
        val edited = other.copy(optionId = "walk", customText = null, time = LocalTime(16, 0))
        rule.setContent { DagbokenTheme { ActivityEditScreen(form(edited, isNew = false, stored = other) { events += it }, {}, activityTypes) } }
        rule.onNodeWithText("Redigera aktivitet").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Aktiviteten Svamplockning, 6 okt kl. 14:20 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
        assertEquals(emptyList(), events, "inget raderas utan bekräftelse")
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<EntryEditEvent<Activity>>(EntryEditEvent.Delete), events)
    }

    @Test
    fun `en anteckning över taket och ett fel utan eget fält syns (regel 1 – rules)`() {
        val tooLong = other.copy(note = "x".repeat(10))
        rule.setContent {
            DagbokenTheme {
                ActivityEditScreen(
                    EntryForm(false, EditorUiState(tooLong, errors = mapOf(ActivityField.NOTE to R.string.field_not_savable, "minutes" to R.string.field_not_savable), isValid = false), emptyFlow(), {}),
                    {},
                    activityTypes,
                )
            }
        }
        rule.onNodeWithText("Ett värde går inte att spara – ändra det för att kunna spara.").assertIsDisplayed()
        rule.onNodeWithText("Värdet går inte att spara – korta eller ändra det").performScrollTo().assertIsDisplayed()
    }

    // ── Händelse ──────────────────────────────────────────────────────────

    @Test
    fun `händelseformuläret uppfyller redigeringskontraktet (HAN-1, NFR-10)`() {
        val editor = EditorState(migraine.copy(optionId = "dizzy"), eventValidator)
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(EventField.TYPE) { it.copy(optionId = "") } },
            makeValid = { update(EventField.TYPE) { it.copy(optionId = "migraine") } },
            invalidMessage = "Välj en typ",
        ) { state, effects, onSave, onClose ->
            EventEditScreen(EntryForm(true, state, effects, { if (it == EntryEditEvent.Save) onSave() }), onClose, eventTypes)
        }
    }

    @Test
    fun `händelsens typ väljs bland favoriterna eller under Fler typer, och utan typer sägs var de läggs till (SET-9)`() {
        var value by mutableStateOf(migraine)
        var types by mutableStateOf(eventTypes)
        rule.setContent {
            DagbokenTheme {
                EventEditScreen(form(value) { event -> if (event is EntryEditEvent.Changed) value = event.change(value) }, {}, types)
            }
        }
        rule.onNodeWithText("Yrsel").performClick()
        assertEquals("dizzy", value.optionId)
        rule.onNodeWithContentDescription("Fler typer, Välj typ").performClick()
        rule.onNodeWithText("Hjärtklappning").performClick()
        assertEquals("palpitations", value.optionId)
        types = emptyList()
        rule.onNodeWithText("Inga typer än – lägg till dem under Listor i inställningarna.").assertIsDisplayed()
    }

    // ── Plusknappen ───────────────────────────────────────────────────────

    private val picker = OccasionPicker(
        day,
        isToday = true,
        listOf(
            OccasionState(Occasion.BREAKFAST, LocalTime(8, 0), OccasionStatus.LOGGED, listOf(Screening("s1", day, LocalTime(8, 15), Occasion.BREAKFAST, energy = 7, stress = 2))),
            OccasionState(Occasion.LUNCH, LocalTime(12, 0), OccasionStatus.LATE, emptyList()),
            OccasionState(Occasion.DINNER, LocalTime(17, 0), OccasionStatus.SOON, emptyList()),
            OccasionState(Occasion.BEDTIME, LocalTime(21, 0), OccasionStatus.NOT_LOGGED, emptyList()),
        ),
    )

    @Test
    fun `tillfällesväljaren loggar ett nytt tillfälle från raden och från Logga nu, också ett loggat (HEM-8b)`() {
        val events = mutableListOf<LogEvent>()
        rule.setContent { DagbokenTheme { OccasionPickerSheet(picker) { events += it } } }
        rule.onNodeWithText("Välj tillfälle").assertIsDisplayed()
        rule.onNodeWithText("Efter frukost").performClick()
        rule.onNodeWithText("Lunch").performClick()
        assertEquals(listOf<LogEvent>(LogEvent.LogOccasion(picker.occasions[0]), LogEvent.LogOccasion(picker.occasions[1])), events)
    }

    @Test
    fun `Dos och Sjukdom visar Snart här med tillbakapil tills formulären finns`() {
        var back = 0
        rule.setContent { DagbokenTheme { LogUpcomingScreen(LogChoice.Dose, onBack = { back++ }) } }
        rule.onNodeWithText("Snart här").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1, back)
    }

    // ── Skärmdumpar bredvid mockupen ──────────────────────────────────────

    @Test
    fun `skärmdump - ny aktivitet`() = rule.captureLightAndDark("Log_aktivitet_ny") {
        ActivityEditScreen(form(walk), {}, activityTypes, symptoms)
    }

    @Test
    fun `skärmdump - aktivitet Övrigt`() = rule.captureLightAndDark("Log_aktivitet_ovrigt") {
        ActivityEditScreen(
            EntryForm(false, EditorUiState(other.copy(symptoms = listOf(SymptomScore("headache", 3)), note = "Regn hela vägen"), isDirty = true), emptyFlow(), {}),
            {},
            activityTypes,
            symptoms,
        )
    }

    @Test
    fun `skärmdump - ny händelse`() = rule.captureLightAndDark("Log_handelse_ny") {
        EventEditScreen(form(migraine.copy(triggers = "Starkt ljus")), {}, eventTypes)
    }

    @Test
    fun `skärmdump - händelse utan typ efter sparförsök`() = rule.captureLightAndDark("Log_handelse_utan_typ") {
        EventEditScreen(EntryForm(true, EditorUiState(migraine.copy(optionId = ""), errors = mapOf(EventField.TYPE to R.string.entry_type_missing), isValid = false), emptyFlow(), {}), {}, eventTypes)
    }

    @Test
    fun `skärmdump - mående välj tillfälle`() = rule.captureScreenLightAndDark("Log_maende_tillfalle") {
        OccasionPickerSheet(picker) {}
    }

    @Test
    fun `skärmdump - mående välj tillfälle en tidigare dag`() = rule.captureScreenLightAndDark("Log_maende_tillfalle_tidigare") {
        OccasionPickerSheet(picker.copy(date = LocalDate(2026, 10, 3), isToday = false, occasions = picker.occasions.map { it.copy(status = OccasionStatus.NOT_LOGGED, screenings = emptyList()) })) {}
    }
}

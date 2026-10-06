package se.partee71.dagboken.ui.medicines

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.PeriodChoice
import se.partee71.dagboken.core.engine.RepeatChoice
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.runEditScreenContract
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Receptformuläret: ramens kontrakt, valen och skärmdumpar bredvid mockupens lägen (NFR-20). Påhittad data. */
@RunWith(RobolectricTestRunner::class)
class PrescriptionEditScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 6)

    private val sertralin = Prescription(
        "s", "Sertralin", "50", "mg", listOf(Slot.MORNING), Schedule.Repeating(), Period(LocalDate(2026, 9, 1)),
        boosts = listOf(Boost("b", LocalDate(2026, 9, 29), LocalDate(2026, 10, 12), "25", "mg")),
        note = "Tas med frukost",
    )

    private val new = newPrescription(today).copy(name = "Kåvepenin", dose = "1", unit = "g", slots = listOf(Slot.MORNING, Slot.EVENING))

    private fun show(value: Prescription, period: PeriodChoice = value.period.end?.let { PeriodChoice.END_DATE } ?: PeriodChoice.UNTIL_FURTHER_NOTICE, canAdd: Boolean = true, onEvent: (PrescriptionEditEvent) -> Unit = {}) =
        rule.setContent { DagbokenTheme { PrescriptionEditScreen(false, EditorUiState(value), period, canAdd, emptyFlow(), onEvent, {}) } }

    @Test
    fun `receptformuläret uppfyller redigeringskontraktet (NFR-10)`() {
        val editor = EditorState(newPrescription(today), prescriptionValidator)
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(PrescriptionField.NAME) { it.copy(name = " ") } },
            makeValid = { update(PrescriptionField.NAME) { it.copy(name = "Sertralin") } },
            invalidMessage = "Ange ett namn",
        ) { state, effects, onSave, onClose ->
            PrescriptionEditScreen(true, state, PeriodChoice.UNTIL_FURTHER_NOTICE, true, effects, { if (it == PrescriptionEditEvent.Save) onSave() }, onClose)
        }
    }

    @Test
    fun `bara det valda upprepningsläget och periodläget har sin kontroll`() {
        val events = mutableListOf<PrescriptionEditEvent>()
        show(new.copy(schedule = Schedule.Repeating(Repeat.CUSTOM, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))), onEvent = { events += it })
        rule.onNodeWithText("Måndag, onsdag och fredag. Mån–fre = vardagar, lör–sön = helger.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Tis").performClick()
        assertEquals(PrescriptionEditEvent.DayToggled(DayOfWeek.TUESDAY), events.last())
        rule.onNodeWithText("Var X:e dag").performClick()
        assertEquals(PrescriptionEditEvent.RepeatChosen(RepeatChoice.INTERVAL), events.last())
        rule.onNodeWithText("Längd").performScrollTo().performClick()
        assertEquals(PrescriptionEditEvent.PeriodChosen(PeriodChoice.LENGTH), events.last())
        rule.onNodeWithText("Slutdatum").assertDoesNotExist()
        rule.onNodeWithText("Ingen slutdag – receptet fortsätter tills du stänger av det.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `intervall och längd visas med sina texter`() {
        show(new.copy(schedule = Schedule.Repeating(Repeat.INTERVAL, intervalDays = 3), period = Period(today, LocalDate(2026, 10, 15))), period = PeriodChoice.LENGTH)
        rule.onNodeWithText("Var 3:e dag").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Räknas från startdatum (6 okt): 6, 9, 12 okt …").assertIsDisplayed()
        rule.onNodeWithText("10 dagar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("T.o.m. 15 okt – 10 dagar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Mån").assertDoesNotExist()
    }

    @Test
    fun `tidpunkter som inte kan visas visas som text utan val`() {
        show(sertralin.copy(slots = listOf(Slot.AS_NEEDED)))
        rule.onNodeWithText("Tidpunkterna kan inte visas. De sparas som de är.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Förmiddag").assertDoesNotExist()
    }

    @Test
    fun `en okänd upprepning visas som text utan val`() {
        show(sertralin.copy(schedule = Schedule.Unknown(mapOf("repeat" to "biweekly"))))
        rule.onNodeWithText("Upprepningen kan inte visas. Den sparas som den är.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Varje dag").assertDoesNotExist()
    }

    @Test
    fun `två höjningar har var sina namn för TalkBack, och slutet kan rensas till periodens slut (REC-9)`() {
        val events = mutableListOf<PrescriptionEditEvent>()
        show(sertralin.copy(boosts = sertralin.boosts + Boost("c", LocalDate(2026, 10, 13), null, "", "mg")), canAdd = false, onEvent = { events += it })
        rule.onNodeWithText("Total dos under perioden: 75 mg").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Höjning, doshöjning 1").assertExists()
        rule.onNodeWithContentDescription("Höjning, doshöjning 2").assertExists()
        rule.onNodeWithContentDescription("Startdatum, doshöjning 2, tis 13 okt 2026").assertExists()
        rule.onNodeWithContentDescription("Slutdatum, doshöjning 2, Periodens slut").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Till periodens slut, doshöjning 2").assertDoesNotExist()
        rule.onNodeWithText("Lägg till doshöjning").performScrollTo().assertIsNotEnabled()

        rule.onNodeWithContentDescription("Till periodens slut, doshöjning 1").performScrollTo().performClick()
        assertEquals(PrescriptionEditEvent.BoostEndChanged(0, null), events.last())
        rule.onNodeWithContentDescription("Ta bort doshöjning 2").performScrollTo().performClick()
        assertEquals(PrescriptionEditEvent.BoostRemoved(1), events.last())
        rule.onNodeWithText("Aktiv").performScrollTo().performClick()
        assertEquals(PrescriptionEditEvent.ActiveChanged(false), events.last())
    }

    @Test
    fun `veckodagarna läses med hela namnet`() {
        show(new.copy(schedule = Schedule.Repeating(Repeat.CUSTOM, setOf(DayOfWeek.MONDAY))))
        rule.onNodeWithContentDescription("måndag").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("söndag").assertExists()
    }

    // ── Skärmdumpar bredvid mockupen ──────────────────────────────────────────

    /** Rullar så att [text] syns – för lägen längre ned i formuläret. */
    private fun scrolledTo(text: String): ComposeContentTestRule.() -> Unit = {
        waitForIdle()
        onNodeWithText(text).performScrollTo()
        waitForIdle()
    }

    private fun capture(name: String, value: Prescription, period: PeriodChoice, scrollTo: String? = null, isNew: Boolean = false, errors: Map<String, Int> = emptyMap()) =
        rule.captureLightAndDark(name, settle = scrollTo?.let(::scrolledTo) ?: { waitForIdle() }) {
            PrescriptionEditScreen(isNew, EditorUiState(value, errors = errors, isValid = errors.isEmpty(), isDirty = true), period, true, emptyFlow(), {}, {})
        }

    @Test
    fun `skärmdump - redigera recept`() = capture("Medicines_recept", sertralin, PeriodChoice.UNTIL_FURTHER_NOTICE)

    @Test
    fun `skärmdump - redigera recept, nedre delen`() = capture("Medicines_recept_nedre", sertralin, PeriodChoice.UNTIL_FURTHER_NOTICE, scrollTo = "Aktiv")

    @Test
    fun `skärmdump - upprepning varje dag`() = capture("Medicines_recept_dagligen", new, PeriodChoice.UNTIL_FURTHER_NOTICE, scrollTo = "DOSHÖJNINGAR", isNew = true)

    @Test
    fun `skärmdump - upprepning veckodagar`() = capture(
        "Medicines_recept_veckodagar",
        new.copy(schedule = Schedule.Repeating(Repeat.CUSTOM, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))),
        PeriodChoice.UNTIL_FURTHER_NOTICE,
        scrollTo = "DOSHÖJNINGAR",
        isNew = true,
    )

    @Test
    fun `skärmdump - upprepning var X-e dag`() = capture(
        "Medicines_recept_intervall",
        new.copy(schedule = Schedule.Repeating(Repeat.INTERVAL, intervalDays = 3)),
        PeriodChoice.UNTIL_FURTHER_NOTICE,
        scrollTo = "DOSHÖJNINGAR",
        isNew = true,
    )

    @Test
    fun `skärmdump - period längd`() = capture("Medicines_recept_langd", new.copy(period = Period(today, LocalDate(2026, 10, 15))), PeriodChoice.LENGTH, scrollTo = "DOSHÖJNINGAR", isNew = true)

    @Test
    fun `skärmdump - period tom`() = capture("Medicines_recept_tom", new.copy(period = Period(today, LocalDate(2026, 10, 15))), PeriodChoice.END_DATE, scrollTo = "DOSHÖJNINGAR", isNew = true)

    @Test
    fun `skärmdump - doshöjning utan värde`() = capture(
        "Medicines_recept_hojning_fel",
        sertralin.copy(period = Period(LocalDate(2026, 9, 1)), boosts = listOf(Boost("c", LocalDate(2026, 10, 13), null, "", "mg"))),
        PeriodChoice.UNTIL_FURTHER_NOTICE,
        scrollTo = "Lägg till doshöjning",
        errors = mapOf(PrescriptionField.boostDose(0) to R.string.prescription_error_boost_without_dose),
    )

    @Test
    fun `skärmdump - tidpunkter och upprepning som inte kan visas`() = capture(
        "Medicines_recept_okant",
        sertralin.copy(slots = listOf(Slot.AS_NEEDED), schedule = Schedule.Unknown(mapOf("repeat" to "biweekly"))),
        PeriodChoice.UNTIL_FURTHER_NOTICE,
        scrollTo = "PERIOD",
    )
}

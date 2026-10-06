package se.partee71.dagboken.ui.medicines

import se.partee71.dagboken.ui.components.MEDICINE_UNITS
import se.partee71.dagboken.ui.components.UnitChoice
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.engine.PeriodEnding
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.SampleMedicines
import se.partee71.dagboken.ui.runEditScreenContract
import se.partee71.dagboken.ui.runListScreenContract
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Fliken Mediciner och vid behov-formuläret (receptformuläret i `PrescriptionEditScreenTest`): ramarnas kontrakt, det unika och skärmdumpar bredvid mockupen (NFR-20). Påhittad data. */
@RunWith(RobolectricTestRunner::class)
class MedicinesScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 6)

    private val levaxin = SampleMedicines.levaxin
    private val sertralin = SampleMedicines.sertralin.copy(note = "Tas med frukost.")
    private val kavepenin = Prescription(
        "k", "Kåvepenin", "1", "g", listOf(Slot.MORNING, Slot.LUNCH, Slot.EVENING), Schedule.Repeating(), Period(LocalDate(2026, 9, 28), LocalDate(2026, 10, 7)),
    )
    private val atarax = Prescription("a", "Atarax", "25", "mg", listOf(Slot.NIGHT), Schedule.Repeating(Repeat.CUSTOM, setOf(kotlinx.datetime.DayOfWeek.MONDAY, kotlinx.datetime.DayOfWeek.THURSDAY)), active = false)
    private val amoxicillin = Prescription("x", "Amoxicillin", "750", "mg", listOf(Slot.MORNING, Slot.EVENING), Schedule.Repeating(), Period(LocalDate(2026, 9, 14), LocalDate(2026, 9, 21)), active = false)
    private val prednisolon = Prescription("p", "Prednisolon", "5", "mg", listOf(Slot.MORNING), Schedule.Repeating(), Period(LocalDate(2026, 7, 20), LocalDate(2026, 8, 2)), active = false)
    private val alvedon = PrnMedicine("1", "Alvedon", "500", "mg", maxPerDay = 8, favorite = true)
    private val imigran = PrnMedicine("2", "Imigran", "50", "mg", minHoursBetween = 0, maxPerDay = 2, favorite = true)
    private val loratadin = PrnMedicine("3", "Loratadin", "10", "mg", minHoursBetween = 0)

    private val items = medicineItems(listOf(levaxin, sertralin, kavepenin, amoxicillin, prednisolon), listOf(alvedon, imigran, loratadin), today)
    private val endings = listOf(
        PeriodEnding.PrescriptionEnds("k", "Kåvepenin", LocalDate(2026, 10, 7)),
    )

    @Test
    fun `fliken uppfyller listkontraktet (NFR-1)`() = rule.runListScreenContract(MedicineItem.AsNeeded(alvedon), "Alvedon 500 mg", "Inga mediciner än", "Nytt recept") { state, onAdd, onRetry ->
        MedicinesScreen(state, emptyList(), today, null, { if (it == MedicinesEvent.Retry) onRetry() }, { if (it == null) onAdd() }, {}, {})
    }

    @Test
    fun `receptkortet visar dagens dos med höjningen, perioden och slutet, och reglaget och menyn växlar (REC-5, REC-12, REC-13)`() {
        val events = mutableListOf<MedicinesEvent>()
        val opened = mutableListOf<String?>()
        rule.setContent { DagbokenTheme { MedicinesScreen(ListUiState.Content(items), endings, today, null, { events += it }, { opened += it }, {}, {}) } }

        rule.onNodeWithText("Idag 75 mg (+25)").assertIsDisplayed()
        rule.onNodeWithText("Höjning 29 sep – 12 okt").assertIsDisplayed()
        rule.onNodeWithText("Period 28 sep – 7 okt").assertIsDisplayed()
        rule.onNodeWithText("Slutar i morgon").assertIsDisplayed()
        rule.onNodeWithText("Morgon · Lunch · Kväll · dagligen").assertIsDisplayed()
        rule.onNodeWithText("3 aktiva").assertIsDisplayed()

        rule.onNodeWithContentDescription("Levaxin aktivt").performClick()
        assertEquals(MedicinesEvent.ActiveChanged(levaxin, false), events.last())

        rule.onNodeWithText("Levaxin 100 µg").performTouchInput { longClick() }
        rule.onNodeWithText("Avaktivera").performClick()
        assertEquals(MedicinesEvent.ActiveChanged(levaxin, false), events.last())

        rule.onNodeWithText("Levaxin 100 µg").performClick()
        rule.onNodeWithText("Kåvepenin slutar i morgon.").performClick()
        assertEquals(listOf<String?>("l", "k"), opened)
    }

    @Test
    fun `doshöjningarna under chevronen har samma total som formuläret, och ingen när dosen inte är ett tal (REC-9, REC-12)`() {
        val tablets = sertralin.copy(id = "t", name = "Tablett", dose = "1 tablett", note = null)
        val items = listOf(MedicineItem.Recipe(sertralin), MedicineItem.Recipe(tablets))
        rule.setContent { DagbokenTheme { MedicinesScreen(ListUiState.Content(items), emptyList(), today, null, {}, {}, {}, {}) } }
        repeat(2) { rule.onAllNodesWithContentDescription("Fäll ut")[0].performClick() }
        rule.onNodeWithText("29 sep – 12 okt: +25 mg (totalt 75 mg)").assertIsDisplayed()
        rule.onNodeWithText("29 sep – 12 okt: +25 mg").assertIsDisplayed()
    }

    @Test
    fun `stjärnan och raden på en vid behov-medicin har var sin åtgärd (SET-10, NFR-17)`() {
        val events = mutableListOf<MedicinesEvent>()
        val opened = mutableListOf<String?>()
        val prn = listOf(alvedon, imigran, loratadin).map { MedicineItem.AsNeeded(it) }
        rule.setContent { DagbokenTheme { MedicinesScreen(ListUiState.Content(prn), emptyList(), today, null, { events += it }, {}, { opened += it }, {}) } }
        rule.onNodeWithText("Minst 4 h mellan · högst 8 per dag").assertIsDisplayed()
        rule.onNodeWithText("Högst 2 per dag").assertIsDisplayed()
        rule.onNodeWithText("Ingen gräns").assertIsDisplayed()
        rule.onNodeWithContentDescription("Markera Loratadin som favorit").performClick()
        assertEquals(listOf<MedicinesEvent>(MedicinesEvent.FavoriteToggled(loratadin)), events)
        rule.onNodeWithText("Loratadin 10 mg").performClick()
        assertEquals(listOf<String?>("3"), opened)
    }

    @Test
    fun `avslutade recept är hopfällda med antal och visar slutdatum utfällda, utan reglage men med Förläng och aktivera (MEDF-5)`() {
        val extended = mutableListOf<String>()
        val events = mutableListOf<MedicinesEvent>()
        rule.setContent {
            DagbokenTheme {
                MedicinesScreen(ListUiState.Content(items.filter { it !is MedicineItem.Recipe || it.ended }), emptyList(), today, null, { events += it }, {}, {}, { extended += it })
            }
        }
        rule.onNodeWithText("Avslutade recept").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTextCount("Amoxicillin 750 mg"))
        rule.onNodeWithText("Avslutade recept").performClick()
        rule.onNodeWithText("Avslutat 21 sep").assertIsDisplayed()
        rule.onNodeWithContentDescription("Amoxicillin aktivt").assertDoesNotExist()

        rule.onNodeWithText("Amoxicillin 750 mg").performTouchInput { longClick() }
        rule.onNodeWithText("Avaktivera").assertDoesNotExist()
        rule.onNodeWithText("Förläng och aktivera").performClick()
        assertEquals(listOf("x"), extended)
        assertEquals(emptyList(), events)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size

    @Test
    fun `vid behov-formuläret uppfyller redigeringskontraktet (NFR-10)`() {
        val editor = EditorState(newPrnMedicine(), prnValidator)
        rule.runEditScreenContract(
            editor,
            makeInvalid = { update(PrnField.NAME) { it.copy(name = "") } },
            makeValid = { update(PrnField.NAME, PrnField.DOSE) { it.copy(name = "Alvedon", dose = "500") } },
            invalidMessage = "Ange ett namn",
        ) { state, effects, onSave, onClose ->
            PrnMedicineEditScreen(true, state, effects, { if (it == PrnEditEvent.Save) onSave() }, onClose)
        }
    }

    @Test
    fun `vid behov-formuläret visar enheter, spärrtid och dagsgräns som text`() {
        val events = mutableListOf<PrnEditEvent>()
        rule.setContent { DagbokenTheme { PrnMedicineEditScreen(false, EditorUiState(alvedon.copy(minHoursBetween = 0, maxPerDay = 0, unit = "tablett")), emptyFlow(), { events += it }, {}) } }
        rule.onNodeWithText("Ingen spärr").assertIsDisplayed()
        rule.onNodeWithText("Obegränsat").assertIsDisplayed()
        rule.onNodeWithText("tablett").assertIsDisplayed()
        rule.onNodeWithText("sprut").performClick()
        val change = events.single() as PrnEditEvent.Changed
        assertEquals("sprut", change.change(alvedon).unit)
    }

    @Test
    fun `en tom lagrad enhet ger inget extra chip och inget val`() {
        rule.setContent { DagbokenTheme { PrnMedicineEditScreen(false, EditorUiState(alvedon.copy(unit = "")), emptyFlow(), {}, {}) } }
        val chips = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes()
        assertEquals(MEDICINE_UNITS.size, chips.size, "bara listans enheter – inget tomt chip")
        assertEquals(0, rule.onAllNodes(isSelected()).fetchSemanticsNodes().size, "inget val markerat")
    }

    @Test
    fun `skärmdump - listan`() = rule.captureLightAndDark("Medicines_lista") {
        MedicinesScreen(ListUiState.Content(items + MedicineItem.Recipe(atarax)), endings + PeriodEnding.BoostEnds("s", "Sertralin", LocalDate(2026, 10, 7), "50", "mg"), today, null, {}, {}, {}, {})
    }

    @Test
    fun `skärmdump - avslutade utfällda`() {
        var opened = false
        rule.captureLightAndDark(
            "Medicines_avslutade",
            settle = {
                waitForIdle()
                if (!opened) {
                    onNodeWithText("Avslutade recept").clickWithoutRipple()
                    opened = true
                    waitForIdle()
                }
            },
        ) {
            MedicinesScreen(ListUiState.Content(items.filter { it !is MedicineItem.Recipe || it.ended }), emptyList(), today, null, {}, {}, {}, {})
        }
    }

    @Test
    fun `skärmdump - ny vid behov-medicin`() = rule.captureLightAndDark("Medicines_vidbehov") {
        PrnMedicineEditScreen(true, EditorUiState(alvedon.copy(note = "Inte på fastande mage", maxPerDay = 8), isDirty = true), emptyFlow(), {}, {})
    }

    @Test
    fun `skärmdump - spray`() = rule.captureLightAndDark("Medicines_spray") {
        PrnMedicineEditScreen(true, EditorUiState(PrnMedicine("", "Nasonex", "2", "sprut", minHoursBetween = 0), isDirty = true), emptyFlow(), {}, {})
    }

    @Test
    fun `skärmdump - avslutade med menyn öppen (MEDF-5)`() = rule.captureScreenLightAndDark(
        "Medicines_avslutade_meny",
        open = {
            onNodeWithText("Avslutade recept").clickWithoutRipple()
            waitForIdle()
            onAllNodesWithContentDescription("Fler val")[1].clickWithoutRipple()
        },
    ) {
        MedicinesScreen(ListUiState.Content(items.filter { it !is MedicineItem.Recipe || it.ended }), emptyList(), today, null, {}, {}, {}, {})
    }
}

package se.partee71.dagboken.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.Spacing
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import kotlin.math.abs
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Beteende och tillgänglighet för Dagbokens portade komponenter (etapp 4.1). Postkortets gestmönster
 * och trailing-ordning (NFR-15, NFR-16) bevisas här en gång – skärmarna som använder kortet testar
 * bara sitt eget.
 */
@RunWith(RobolectricTestRunner::class)
class DiaryComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    private fun SemanticsNodeInteraction.left() = fetchSemanticsNode().boundsInRoot.left
    private fun SemanticsNodeInteraction.top() = fetchSemanticsNode().boundsInRoot.top
    private fun state(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private var opened = 0
    private var deleted = 0
    private val log = mutableListOf<String>()

    @Composable
    private fun EntryCard() = DagbokenEntryCard(
        "Promenad",
        onClick = { opened++ },
        subtitle = "08:30 · 45 min",
        status = { InfoPill("+3") },
        note = "Gick i skogen med hunden.",
        expandedContent = { androidx.compose.material3.Text("Huvudvärk 2") },
        onEdit = { log += "redigera" },
        actions = listOf(AppMenuItem("Kopiera", { log += "kopiera" })),
        delete = DeleteAction("Radera Promenad?", "Det går inte att ångra.") { deleted++ },
    )

    @Test
    fun `postkortet - tryck öppnar och expanderar aldrig, chevronen fäller ut (NFR-15, NFR-16)`() {
        show { EntryCard() }
        rule.onNodeWithText("Promenad").performClick()
        assertEquals(1, opened)
        rule.onNodeWithText("Huvudvärk 2").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fäll ut").assert(state("Hopfälld")).performClick()
        rule.onNodeWithText("Huvudvärk 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fäll ihop").assert(state("Utfälld"))
        assertEquals(1, opened)
    }

    @Test
    fun `postkortet - långtryck ger samma meny som ⋮ i ordningen Redigera, eget, Radera`() {
        show { EntryCard() }
        rule.onNodeWithText("Promenad").performTouchInput { longClick() }
        val order = listOf("Redigera", "Kopiera", "Radera").map { rule.onNodeWithText(it).top() }
        assertEquals(order.sorted(), order)
        rule.onNodeWithText("Kopiera").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Redigera").performClick()
        assertEquals(listOf("kopiera", "redigera"), log)
        assertEquals(0, opened)
    }

    @Test
    fun `postkortet - svep från höger begär radering som alltid bekräftas, kortet står kvar`() {
        show { EntryCard() }
        rule.onNodeWithText("Promenad").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Radera Promenad?").assertIsDisplayed()
        assertEquals(0, deleted)
        rule.onNodeWithText("Avbryt").performClick()
        rule.onNodeWithText("Radera Promenad?").assertDoesNotExist()
        rule.onNodeWithText("Promenad").assertIsDisplayed()
        assertEquals(0, deleted)
        // Samma bekräftelse från menyn – ingen åtgärd finns bara via svep.
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Radera Promenad?").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `postkortet - svep åt höger gör ingenting`() {
        show { EntryCard() }
        rule.onNodeWithText("Promenad").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithText("Radera Promenad?").assertDoesNotExist()
        assertEquals(0, opened + deleted)
    }

    @Test
    fun `postkortet - trailing i ordningen status, anteckning, chevron, ⋮ med 48 dp tryckytor`() {
        show { EntryCard() }
        val parts = listOf(
            rule.onNodeWithText("+3", useUnmergedTree = true),
            rule.onNodeWithContentDescription("Visa anteckning"),
            rule.onNodeWithContentDescription("Fäll ut"),
            rule.onNodeWithContentDescription("Fler val"),
        )
        val lefts = parts.map { it.left() }
        assertEquals(lefts.sorted(), lefts)
        parts.drop(1).forEach { it.assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp) }
    }

    @Test
    fun `anteckningsikonen visar anteckningen att läsa och stängs med Stäng`() {
        show { EntryCard() }
        rule.onNodeWithContentDescription("Visa anteckning").performClick()
        rule.onNodeWithText("Gick i skogen med hunden.").assertIsDisplayed()
        rule.onNodeWithText("Avbryt").assertDoesNotExist()
        rule.onNodeWithText("Stäng").performClick()
        rule.onNodeWithText("Gick i skogen med hunden.").assertDoesNotExist()
    }

    @Test
    fun `postkort utan anteckning, detaljer och meny har bara titeln`() {
        show { DagbokenEntryCard("Yoga", onClick = { opened++ }) }
        rule.onNodeWithContentDescription("Visa anteckning").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
        rule.onNodeWithText("Yoga").performTouchInput { longClick() }
        rule.onNodeWithText("Redigera").assertDoesNotExist()
        rule.onNodeWithText("Yoga").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Yoga").assertIsDisplayed()
    }

    @Test
    fun `sektionskortet - hela titelraden växlar och läses som knapp med läge (NFR-18)`() {
        var expanded by mutableStateOf(false)
        show { Foldout("Mätvärden", expanded, { expanded = !expanded }, summary = "Energi 7") { androidx.compose.material3.Text("Reglagen") } }
        rule.onNodeWithText("Energi 7").assertIsDisplayed()
        rule.onNodeWithText("Mätvärden")
            .assert(button)
            .assert(state("Hopfälld"))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertTrue(expanded)
        rule.onNodeWithText("Reglagen").assertIsDisplayed()
        rule.onNodeWithText("Energi 7").assertDoesNotExist()
        rule.onNodeWithText("Mätvärden").assert(state("Utfälld"))
    }

    @Test
    fun `reglaget läser etikett, värde och nivå och går att ställa med TalkBack`() {
        var energy by mutableStateOf(7)
        var stress by mutableStateOf(8)
        var activity by mutableStateOf(3)
        show {
            Column {
                ValueSlider("Energi", energy, { energy = it })
                ValueSlider("Stress", stress, { stress = it }, higherIsBetter = false)
                ValueSlider("Aktivitetens energi", activity, { activity = it }, valueRange = -10..10)
            }
        }
        rule.onNodeWithContentDescription("Energi").assert(state("7, Hög"))
        rule.onNodeWithContentDescription("Stress").assert(state("8, Svår"))
        rule.onNodeWithContentDescription("Aktivitetens energi").assert(state("+3, Medel"))
        rule.onNodeWithContentDescription("Energi").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        assertEquals(2, energy)
        rule.onNodeWithContentDescription("Energi").assert(state("2, Låg"))
        rule.onNodeWithContentDescription("Aktivitetens energi").performSemanticsAction(SemanticsActions.SetProgress) { it(-6f) }
        assertEquals(-6, activity)
        rule.onNodeWithContentDescription("Aktivitetens energi").assert(state("−6, Låg")).assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun `hjulväljaren läser valt värde och stegar med TalkBack-åtgärderna`() {
        var index by mutableStateOf(2)
        show { WheelPicker((0..23).map { it.toString() }, index, { index = it }, "timmar") }
        fun act(label: String) = rule.runOnIdle {
            rule.onNodeWithContentDescription("timmar").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }.action()
        }
        rule.onNodeWithContentDescription("timmar").assert(state("2"))
        act("Öka timmar")
        rule.waitForIdle()
        assertEquals(3, index, "en animerad rullning till det nya värdet rapporterar inte värdena den passerar")
        rule.onNodeWithContentDescription("timmar").assert(state("3"))
        act("Minska timmar")
        rule.waitForIdle()
        assertEquals(2, index)
    }

    @Test
    fun `hjulväljaren rullar tillbaka när anroparen avböjer värdet`() {
        show { WheelPicker((0..23).map { it.toString() }, 2, {}, "timmar") }
        rule.onNodeWithContentDescription("timmar").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val wheel = rule.onNodeWithContentDescription("timmar").fetchSemanticsNode().boundsInRoot
        val chosen = rule.onNodeWithText("2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(abs(wheel.center.y - chosen.center.y) < 2f, "det valda värdet ska stå mitt i hjulet efter ett avböjt val")
        rule.onNodeWithContentDescription("timmar").assert(state("2"))
    }

    @Test
    fun `kalendern - dagar med poster läses upp, tryck väljer dagen och pilarna byter månad`() {
        var picked by mutableStateOf<LocalDate?>(null)
        var month by mutableStateOf(LocalDate(2026, 10, 1))
        show { DagbokenCalendar(month, { month = it }, setOf(LocalDate(2026, 10, 4)), picked, { picked = it }) }
        rule.onNodeWithText("Oktober 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("sön 4 okt 2026, har poster")
            .assertTouchHeightIsEqualTo(48.dp)
            .assertHasClickAction()
            .performClick()
        assertEquals(LocalDate(2026, 10, 4), picked)
        rule.onNodeWithContentDescription("sön 4 okt 2026, har poster").assertIsSelected()
        rule.onNodeWithContentDescription("mån 5 okt 2026").assertExists()
        rule.onNodeWithContentDescription("Nästa månad").performClick()
        assertEquals(LocalDate(2026, 11, 1), month)
        rule.onNodeWithText("November 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("Föregående månad").performClick()
        rule.onNodeWithContentDescription("Föregående månad").performClick()
        assertEquals(LocalDate(2026, 9, 1), month)
    }

    private val symptoms = listOf(
        Option("huvudvark", OptionKind.SYMPTOM, "Huvudvärk"),
        Option("yrsel", OptionKind.SYMPTOM, "Yrsel", sortOrder = 1),
        Option("ovrigt", OptionKind.SYMPTOM, "Övrigt", sortOrder = 2),
    )

    @Test
    fun `mående i steg - energi, stress och symptom, Spara på sista steget`() {
        var saved = 0
        var energy by mutableStateOf(5)
        show {
            StepwiseScreeningForm(energy, { energy = it }, 3, {}, symptoms, emptyList(), {}, onSave = { saved++ })
        }
        rule.onNodeWithText("Steg 1 av 3").assertIsDisplayed()
        rule.onNodeWithContentDescription("Energi").assert(state("5, Medel"))
        rule.onNodeWithText("Föregående").assertDoesNotExist()
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Steg 2 av 3").assertIsDisplayed()
        rule.onNodeWithContentDescription("Stress").assert(state("3, Lätt"))
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Steg 3 av 3").assertIsDisplayed()
        rule.onNodeWithText("Huvudvärk").assertIsDisplayed()
        rule.onNodeWithText("Spara").performClick()
        assertEquals(1, saved)
        rule.onNodeWithText("Föregående").performClick()
        rule.onNodeWithText("Steg 2 av 3").assertIsDisplayed()
    }

    @Test
    fun `mående i steg utan symptomalternativ har två steg`() {
        show { StepwiseScreeningForm(5, {}, 3, {}, emptyList(), emptyList(), {}, onSave = {}) }
        rule.onNodeWithText("Steg 1 av 2").assertIsDisplayed()
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Spara").assertIsDisplayed()
    }

    @Test
    fun `symptomkortet - ett reglage per valt symptom, egen text vid Övrigt och summan under`() {
        var scores by mutableStateOf(listOf(SymptomScore("huvudvark", 4)))
        show { SymptomLogCard(symptoms, scores, { scores = it }, otherOptionId = "ovrigt") }
        rule.onNodeWithContentDescription("Huvudvärk").assert(state("4, Måttlig"))
        rule.onNodeWithText("Summa symptom: 4").assertIsDisplayed()
        rule.onNodeWithText("Övrigt").performClick()
        assertEquals(listOf(SymptomScore("huvudvark", 4), SymptomScore("ovrigt", 1)), scores)
        rule.onNodeWithText("Beskriv symptomet").performTextReplacement("Ont i knät")
        assertEquals("Ont i knät", scores[1].customText)
        rule.onNodeWithContentDescription("Ont i knät").assert(state("1, Lätt"))
        rule.onNodeWithText("Summa symptom: 5").assertIsDisplayed()
        rule.onNodeWithText("Huvudvärk").performClick()
        assertEquals(listOf("ovrigt"), scores.map { it.optionId })
        rule.onNodeWithText("Symptom").assert(button).performClick()
        rule.onNodeWithText("Summa symptom: 1").assertIsDisplayed()
        rule.onNodeWithText("Yrsel").assertDoesNotExist()
    }

    @Test
    fun `anteckningen visar början av texten och skrivs utfälld`() {
        var note by mutableStateOf("Sov dåligt.")
        show { NoteField(note, { note = it }) }
        rule.onNodeWithText("Sov dåligt.").assertIsDisplayed()
        rule.onNodeWithText("Anteckning").assert(state("Hopfälld")).performClick()
        rule.onNodeWithText("Sov dåligt.").performTextReplacement("Sov bra.")
        assertEquals("Sov bra.", note)
        note = ""
        rule.onNodeWithText("Lägg till en anteckning").assertIsDisplayed()
    }

    @Test
    fun `mätvärdet läses som en enhet och är en knapp med 48 dp bara med onClick`() {
        var clicks = 0
        show {
            Column {
                StatPill(se.partee71.dagboken.R.drawable.ic_activity, "7 842", "Steg")
                StatPill(se.partee71.dagboken.R.drawable.ic_sun, "—", "Sömn", onClick = { clicks++ }, onClickLabel = "Begär åtkomst")
            }
        }
        rule.onNodeWithText("Steg").fetchSemanticsNode().let { node ->
            assertEquals(null, node.config.getOrNull(SemanticsActions.OnClick))
        }
        rule.onNodeWithText("Sömn").assert(button).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `datum och tid på en rad läses som två fält`() {
        show { DateTimeRow(LocalDate(2026, 10, 4), LocalTime(8, 30), {}, {}) }
        rule.onNodeWithContentDescription("Datum, sön 4 okt 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tid, 08:30").assertIsDisplayed()
    }

    @Test
    fun `tidsåtgången stegar om fem minuter och snabbvalen sätter värdet`() {
        var minutes by mutableStateOf(45)
        show { DurationRow(minutes, { minutes = it }) }
        rule.onNodeWithContentDescription("Öka minuter").performClick()
        assertEquals(50, minutes)
        rule.onNodeWithText("1 tim").performClick()
        assertEquals(60, minutes)
        rule.onNodeWithText("1 tim 30 min").performClick()
        assertEquals(90, minutes)
    }

    @Test
    fun `påminnelseraden - raden väljer tid och reglaget slår av och på (NOT-18)`() {
        var on by mutableStateOf(true)
        var time by mutableStateOf(LocalTime(8, 0))
        show { ReminderTimeRow("Efter frukost", time, { time = it }, enabled = on, onEnabledChange = { on = it }) }
        rule.onNodeWithContentDescription("Efter frukost")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .performClick()
        assertEquals(false, on)
        rule.onNodeWithText("08:00").performClick()
        rule.onNodeWithText("OK").performClick()
        assertEquals(LocalTime(8, 0), time)
        assertEquals(false, on)
    }

    @Test
    fun `mående i steg står kvar på sitt steg när symptomalternativen laddas sent`() {
        var options by mutableStateOf(emptyList<Option>())
        show { StepwiseScreeningForm(5, {}, 3, {}, options, emptyList(), {}, onSave = {}) }
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Steg 2 av 2").assertIsDisplayed()
        options = symptoms
        rule.onNodeWithText("Steg 2 av 3").assertIsDisplayed()
        rule.onNodeWithText("Nästa").performClick()
        rule.onNodeWithText("Steg 3 av 3").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `kalenderns dagar har 48 dp tryckyta även på en 360 dp bred skärm (NFR-14)`() {
        show {
            Column(Modifier.padding(Spacing.l)) {
                AppCard { DagbokenCalendar(LocalDate(2026, 10, 1), {}, emptySet(), null, {}) }
            }
        }
        listOf("mån 5 okt 2026", "tis 6 okt 2026", "sön 11 okt 2026").forEach {
            rule.onNodeWithContentDescription(it).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun `stegaren med steg stannar vid intervallets gränser`() {
        var value by mutableStateOf(97)
        show { QuantityStepper(value, { value = it }, "minuter", range = 0..100, step = 5) }
        rule.onNodeWithContentDescription("Öka minuter").performClick()
        assertEquals(100, value)
        rule.onNodeWithContentDescription("Öka minuter").assertIsNotEnabled()
        value = 3
        rule.onNodeWithContentDescription("Minska minuter").performClick()
        assertEquals(0, value)
        rule.onNodeWithContentDescription("Minska minuter").assertIsNotEnabled()
    }

    @Test
    fun `bekräftelse utan nej-knapp har bara en knapp`() {
        var closed = 0
        show { ConfirmDialog("Promenad", "Gick i skogen.", "Stäng", { closed++ }, {}, dismissLabel = null) }
        rule.onNodeWithText("Avbryt").assertDoesNotExist()
        rule.onAllNodes(button).assertCountEquals(1)
        rule.onNodeWithText("Stäng").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun `postkortets accent ritas i vänsterkanten och ett inaktivt kort tonas ner`() {
        val accent = AppColors.lightExtended.energy.high
        show {
            Column {
                DagbokenEntryCard("Yoga", onClick = {}, accent = accent)
                DagbokenEntryCard("Pilates", onClick = {})
                DagbokenEntryCard("Simning", onClick = {}, inactive = true)
            }
        }
        val card = rule.onNodeWithText("Yoga").captureToImage().toPixelMap()
        assertEquals(accent, card[1, card.height / 2])
        val plain = rule.onNodeWithText("Pilates").captureToImage().toPixelMap()
        assertTrue(plain[1, plain.height / 2] != accent, "utan accent ingen kant")
        fun darkest(text: String) = rule.onNodeWithText(text, useUnmergedTree = true).captureToImage().toPixelMap().let { map ->
            (0 until map.width).minOf { x -> (0 until map.height).minOf { y -> map[x, y].red } }
        }
        assertTrue(darkest("Simning") > darkest("Pilates") + 0.2f, "inaktiv titel ska vara nedtonad")
    }
}

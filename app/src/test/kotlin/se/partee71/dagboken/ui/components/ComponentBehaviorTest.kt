package se.partee71.dagboken.ui.components

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.engine.TypeChoices
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.schema.TextLimits
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.DagbokenTheme

/** Beteende och tillgänglighet i katalogens komponenter – en gång här, inte per skärm. */
@RunWith(RobolectricTestRunner::class)
class ComponentBehaviorTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) = rule.setContent { DagbokenTheme(content = content) }

    private companion object {
        const val SETTLE_MILLIS = 2_000L
    }

    @Test
    fun `typvalet väljer bland chips och under Fler typer, där Övrigt står sist, och säger var typer läggs till`() {
        val types = TypeChoices(
            listOf(Option("walk", OptionKind.ACTIVITY, "Promenad", favorite = true)),
            listOf(Option("rest", OptionKind.ACTIVITY, "Vila")),
            other = "other",
        )
        var selected by mutableStateOf("")
        var choices by mutableStateOf(types)
        show { TypeChoiceField(choices, selected, { selected = it }, otherLabel = "Övrigt", error = "Välj en typ".takeIf { selected.isEmpty() }) }
        rule.onNodeWithText("Välj en typ").assertIsDisplayed()
        rule.onNodeWithText("Promenad").performClick()
        assertEquals("walk", selected)
        rule.onNodeWithText("Välj en typ").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fler typer, Välj typ").performClick()
        rule.onNodeWithText("Övrigt").performClick()
        assertEquals("other", selected)
        rule.onNodeWithContentDescription("Fler typer, Övrigt").assertExists()
        choices = TypeChoices(emptyList(), emptyList())
        rule.onNodeWithText("Inga typer än – lägg till dem under Listor i inställningarna.").assertIsDisplayed()
        rule.onNodeWithText("Fler typer").assertDoesNotExist()
    }

    @Test
    fun `valchips - tryck på det valda anropar onClear om det finns, annars onSelect`() {
        val events = mutableListOf<String>()
        var withClear by mutableStateOf(true)
        show { ChoiceChips(listOf("A", "B"), "A", { events += "select $it" }, { it }, onClear = if (withClear) ({ events += "clear" }) else null) }
        rule.onNodeWithText("B").performClick()
        rule.onNodeWithText("A").performClick()
        assertEquals(listOf("select B", "clear"), events)
        withClear = false
        rule.onNodeWithText("A").performClick()
        assertEquals("select A", events.last(), "utan onClear är beteendet som förut")
    }

    @Test
    fun `anteckningens fel syns också i stängt läge`() {
        show { NoteField("Sov dåligt.", {}, error = "Värdet går inte att spara") }
        rule.onNodeWithText("Värdet går inte att spara").assertIsDisplayed()
    }

    @Test
    fun `textfältets tangentbord börjar med stor bokstav`() {
        lateinit var view: View
        show {
            view = LocalView.current
            AppTextField("", {}, "Namn")
        }
        rule.onNodeWithText("Namn").performClick()
        rule.waitForIdle()
        val info = EditorInfo()
        rule.runOnIdle { view.onCreateInputConnection(info) }
        assertTrue(info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0, "inputType=${info.inputType}")
    }

    @Test
    fun `textfältet växer inte förbi taket men en lagrad längre text går att korta`() {
        var text by mutableStateOf("x".repeat(TextLimits.SHORT))
        var notes by mutableStateOf("y".repeat(TextLimits.LONG + 5))
        show {
            Column {
                AppTextField(text, { text = it }, "Namn")
                AppTextField(notes, { notes = it }, "Anteckning", singleLine = false)
            }
        }
        rule.onNodeWithText("Namn").performTextInput("z")
        assertEquals(TextLimits.SHORT, text.length)
        rule.onNodeWithText("Anteckning").performTextReplacement("y".repeat(TextLimits.LONG + 4))
        assertEquals(TextLimits.LONG + 4, notes.length, "kortare är tillåtet, inget kapas")
        rule.onNodeWithText("Anteckning").performTextReplacement("kort")
        assertEquals("kort", notes)
    }

    @Test
    fun `kryssraden växlar med tryck på hela raden och läses som kryssruta`() {
        var checked by mutableStateOf(false)
        show { CheckRow("Enalapril 10 mg", checked, { checked = it }, subtitle = "18 tabletter") }
        rule.onNodeWithText("Enalapril 10 mg").performClick()
        assertTrue(checked)
        rule.onNodeWithText("Enalapril 10 mg")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `med egen tryckåtgärd är krysset en egen knapp med namn och mängd`() {
        var checked by mutableStateOf(false)
        var opened = 0
        show { CheckRow("Enalapril 10 mg", checked, { checked = it }, subtitle = "18 tabletter", onClick = { opened++ }, onClickLabel = "Ändra mängd") }
        rule.onNodeWithText("Enalapril 10 mg").performClick()
        assertEquals(1, opened)
        assertFalse(checked)
        rule.onNodeWithContentDescription("Enalapril 10 mg, 18 tabletter")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .performClick()
        assertTrue(checked)
    }

    @Test
    fun `konfettin säger till när regnet är över`() {
        var finished = 0
        show { Confetti(onFinished = { finished++ }) }
        rule.waitForIdle()
        assertEquals(1, finished)
    }

    @Test
    fun `växelraden växlar med tryck på raden`() {
        var on by mutableStateOf(false)
        show { SwitchRow("Medicinpåminnelser", on, { on = it }) }
        rule.onNodeWithText("Medicinpåminnelser").performClick()
        assertTrue(on)
    }

    @Test
    fun `stegaren ökar och minskar inom intervallet och beskriver värdet`() {
        var value by mutableStateOf(1)
        show { QuantityStepper(value, { value = it }, "dagar", range = 1..2) }
        rule.onNodeWithContentDescription("Minska dagar").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Öka dagar").performClick()
        assertEquals(2, value)
        rule.onNodeWithContentDescription("Öka dagar").assertIsNotEnabled()
        rule.onNodeWithContentDescription("2 dagar").assertIsDisplayed()
    }

    @Test
    fun `stegarens knappar är 48 dp stora, inte bara i tryckytan (NFR-14)`() {
        show { QuantityStepper(1, {}, "dagar") }
        listOf("Minska dagar", "Öka dagar").forEach {
            rule.onNodeWithContentDescription(it).assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun `listraden och postkortet radbryter titel och undertext likadant (NFR-17)`() {
        val longTitle = "Promenad runt sjön med hunden och grannen en lång söndagsförmiddag i oktober"
        val longSubtitle = "08:30 · 45 min · lugnt tempo i solsken, raster vid bryggan, fikat och tittade på fåglarna vid vassen"
        show {
            Column(Modifier.width(240.dp)) {
                ItemRow("Rad: $longTitle", subtitle = "Rad: $longSubtitle")
                DagbokenEntryCard("Kort: $longTitle", {}, subtitle = "Kort: $longSubtitle")
            }
        }
        listOf("Rad", "Kort").forEach { prefix ->
            assertEquals(2, lineCount("$prefix: $longTitle"), "$prefix: titeln högst två rader")
            assertTrue(lineCount("$prefix: $longSubtitle") > 2, "$prefix: undertexten visas hela")
        }
    }

    private fun lineCount(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().lineCount
    }

    @Test
    fun `menyn öppnas från Fler val och kör valet`() {
        var archived = 0
        show { AppMenu(listOf(AppMenuItem("Arkivera", { archived++ }), AppMenuItem("Radera", {}, destructive = true))) }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, archived)
        rule.onNodeWithText("Radera").assertDoesNotExist()
    }

    @Test
    fun `en kryssrad i menyn läses som kryssruta, växlar och håller menyn öppen (TRD-12)`() {
        var toggled = 0
        show { AppMenu(listOf(AppMenuItem("Frukost", { toggled++ }, checked = true), AppMenuItem("Lunch", { toggled++ }, checked = false))) }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Frukost").assertIsOn().performClick()
        assertEquals(1, toggled)
        rule.onNodeWithText("Lunch").assertIsOff().assertIsDisplayed()
    }

    @Test
    fun `split-knappen lägger till och pilen öppnar fler val`() {
        var added = 0
        var recalculated = 0
        show { AddSplitButton("Lägg till", { added++ }, menuItems = listOf(AppMenuItem("Räkna om", { recalculated++ }))) }
        rule.onNodeWithText("Lägg till").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Räkna om").performClick()
        assertEquals(1 to 1, added to recalculated)
    }

    @Test
    fun `bekräftelsen anropar bekräfta eller avbryt`() {
        var result = ""
        show { ConfirmDialog("Radera Alvedon?", "Det går inte att ångra.", "Radera", { result = "radera" }, { result = "avbryt" }, destructive = true) }
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals("avbryt", result)
        rule.onNodeWithText("Radera").performClick()
        assertEquals("radera", result)
    }

    @Test
    fun `datumfältet öppnar väljaren och OK behåller valt datum`() {
        var date by mutableStateOf<LocalDate?>(LocalDate(2026, 12, 19))
        var changes = 0
        show { DateField("Startdatum", date, { date = it; changes++ }) }
        rule.onNodeWithContentDescription("Startdatum, lör 19 dec 2026").assertIsDisplayed().performClick()
        rule.onNodeWithText("OK").performClick()
        assertEquals(1, changes)
        assertEquals(LocalDate(2026, 12, 19), date)
    }

    @Test
    fun `textfältets suffix står efter värdet och följer inte med i värdet`() {
        var text by mutableStateOf("25")
        show { AppTextField(text, { text = it }, "Höjning", suffix = "mg") }
        rule.onNodeWithText("mg", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Höjning").performTextReplacement("50")
        assertEquals("50", text)
    }

    @Test
    fun `datumfältet utan datum visar sin tomma text och läser upp den`() {
        var date by mutableStateOf<LocalDate?>(null)
        show { DateField("Slutdatum", date, { date = it }, emptyLabel = "Periodens slut") }
        rule.onNodeWithContentDescription("Slutdatum, Periodens slut").assertIsDisplayed()
        date = LocalDate(2026, 10, 12)
        rule.onNodeWithContentDescription("Slutdatum, mån 12 okt 2026").assertIsDisplayed()
        rule.onNodeWithContentDescription("Till periodens slut").assertDoesNotExist()
    }

    @Test
    fun `datumfältets rensa-knapp finns bara med datum, tömmer det till den tomma texten och fältet byter inte bredd`() {
        var date by mutableStateOf<LocalDate?>(LocalDate(2026, 10, 12))
        show { DateField("Slutdatum", date, { date = it }, emptyLabel = "Periodens slut", onClear = { date = null }, context = "doshöjning 1") }
        val withDate = rule.onNodeWithContentDescription("Slutdatum, doshöjning 1, mån 12 okt 2026").assertIsDisplayed().getBoundsInRoot()
        rule.onNodeWithContentDescription("Till periodens slut, doshöjning 1").performClick()
        assertEquals(null, date)
        rule.onNodeWithContentDescription("Till periodens slut, doshöjning 1").assertDoesNotExist()
        val empty = rule.onNodeWithContentDescription("Slutdatum, doshöjning 1, Periodens slut").assertIsDisplayed().getBoundsInRoot()
        assertEquals(withDate.right - withDate.left, empty.right - empty.left, "platsen för rensa-knappen hålls")
    }

    @Test
    fun `enhetsvalet behåller en lagrad enhet utanför listan som val`() {
        var unit by mutableStateOf("tablett")
        show { UnitChoice(unit, { unit = it }) }
        rule.onNodeWithText("tablett").assertIsDisplayed()
        rule.onNodeWithText("sprut").performClick()
        assertEquals("sprut", unit)
    }

    @Test
    fun `tidsfältet öppnar väljaren och OK behåller vald tid`() {
        var time by mutableStateOf(LocalTime(18, 0))
        var changes = 0
        show { TimeField("Tid", time, { time = it; changes++ }) }
        rule.onNodeWithContentDescription("Tid, 18:00").assertIsDisplayed().performClick()
        rule.onNodeWithText("OK").performClick()
        assertEquals(1, changes)
        assertEquals(LocalTime(18, 0), time)
        rule.onNodeWithContentDescription("Tid, 18:00").performClick()
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals(1, changes)
    }

    @Test
    fun `förslagsfältet visar förslagen med antal, och ett tryck väljer rätt förslag`() {
        var text by mutableStateOf("alv")
        var picked = -1
        val suggestions = listOf(Suggestion("Alvedon 500 mg", "Filmdragerad tablett", 0..2), Suggestion("Alvedon forte 1 g", "Filmdragerad tablett", 0..2))
        show { SuggestionField(text, { text = it }, "Namn", suggestions.takeIf { text == "alv" }.orEmpty(), { picked = it }, footer = "Från listan") }
        rule.onNodeWithContentDescription("2 förslag").assertExists()
        rule.onNodeWithText("Från listan").assertIsDisplayed()
        rule.onNodeWithText("Alvedon forte 1 g").performClick()
        assertEquals(1, picked)
        text = "Alvedon"
        rule.onNodeWithText("Alvedon forte 1 g").assertDoesNotExist()
        rule.onNodeWithText("Från listan").assertDoesNotExist()
    }

    @Test
    fun `en markering utanför titeln klipps eller ignoreras i stället för att krascha`() {
        show {
            Column {
                ItemRow("Ipren", titleHighlight = 2..40)
                ItemRow("Alvedon", titleHighlight = -3..1)
                ItemRow("Levaxin", titleHighlight = 4..2)
                ItemRow("Zyrtec", titleHighlight = 10..12)
            }
        }
        listOf("Ipren", "Alvedon", "Levaxin", "Zyrtec").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `växelrad med egen tryckåtgärd öppnar raden och växlar med växeln`() {
        var on by mutableStateOf(true)
        var opened = 0
        show { SwitchRow("Måendepåminnelse", on, { on = it }, subtitle = "Frukost · 08:00", onClick = { opened++ }) }
        rule.onNodeWithText("Måendepåminnelse").performClick()
        assertEquals(1, opened)
        assertTrue(on)
        rule.onNodeWithContentDescription("Måendepåminnelse").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)).performClick()
        assertFalse(on)
        assertEquals(1, opened)
    }

    @Test
    fun `valchips och segmentval rapporterar valet`() {
        var chip = 0
        var segment by mutableStateOf(2)
        show {
            Column {
                AppFilterChip("Tablett", false, { chip++ })
                AppSegmentedChoice(listOf("Ljust", "Mörkt", "System"), segment, { segment = it })
            }
        }
        rule.onNodeWithText("Tablett").performClick()
        rule.onNodeWithText("Mörkt").performClick()
        assertEquals(1, chip)
        assertEquals(1, segment)
        rule.onNodeWithText("Mörkt").assertIsSelected()
    }

    @Test
    fun `emojivalet väljer en emoji och läses som ett av flera alternativ`() {
        var emoji by mutableStateOf("🚶")
        show { EmojiPicker(emoji, { emoji = it }) }
        rule.onNodeWithContentDescription("🧘").performClick()
        assertEquals("🧘", emoji)
        rule.onNodeWithContentDescription("🧘").assertIsSelected()
        rule.onNodeWithContentDescription("🧘").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `färgvalet väljer en färg och läser färgens namn`() {
        var color by mutableStateOf(AppColors.SWATCH_HEX[0])
        show { ColorSwatchPicker(color, { color = it }) }
        rule.onNodeWithContentDescription("Färg: grön").performClick()
        assertEquals(AppColors.SWATCH_HEX[3], color)
        rule.onNodeWithContentDescription("Färg: grön").assertIsSelected()
    }

    @Test
    fun `färgvalet känner igen en lagrad färg med små bokstäver och har 48 dp tryckytor`() {
        show { ColorSwatchPicker(AppColors.SWATCH_HEX[1].lowercase(), {}) }
        rule.onNodeWithContentDescription("Färg: solgul").assertIsSelected()
        rule.onNodeWithContentDescription("Färg: solgul").assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp)
    }

    @Test
    fun `exempel med samma text väljer var sitt objekt`() {
        val picked = mutableListOf<Int>()
        show { ExampleChips(listOf(1, 2), { picked += it }, label = { "Solkräm" }) }
        rule.onAllNodesWithText("Solkräm")[1].performClick()
        assertEquals(listOf(2), picked)
    }

    @Test
    fun `en rad utan svep har ingen arkivera-åtgärd`() {
        show { SwipeToHide(onHide = {}, enabled = false) { ItemRow("Solkräm", it, onClick = {}) } }
        rule.onNodeWithText("Solkräm").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    }

    @Test
    fun `svep åt vänster arkiverar`() {
        var hidden = 0
        show { SwipeToHide(onHide = { hidden++ }) { ItemRow("Solkräm", it, onClick = {}) } }
        rule.onNodeWithText("Solkräm").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(1, hidden)
    }

    @Test
    fun `en svept rad som står kvar glider tillbaka`() {
        show { SwipeToHide(onHide = {}) { ItemRow("Solkräm", it, onClick = {}) } }
        val x = { rule.onNodeWithText("Solkräm").fetchSemanticsNode().positionInRoot.x }
        val start = x()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Solkräm").performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(RESET_DELAY_MILLIS / 2) // undan, men inte tillbaka än
        assertTrue(x() < start, "raden ska ha svepts åt vänster innan den glider tillbaka")
        rule.mainClock.advanceTimeBy(RESET_DELAY_MILLIS + SETTLE_MILLIS)
        rule.waitForIdle()
        assertEquals(start, x())
    }

    @Test
    fun `arkivera-åtgärden för TalkBack ligger på raden som TalkBack läser`() {
        var hidden = 0
        show { SwipeToHide(onHide = { hidden++ }) { ItemRow("Solkräm", it, subtitle = "1 tub", onClick = {}) } }
        // Den sammanslagna noden är den TalkBack fokuserar; åtgärden ska finnas just där.
        val actions = rule.onNodeWithText("Solkräm").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.single { it.label == "Arkivera" }.action()
        assertEquals(1, hidden)
    }

    @Test
    fun `bottenradens flikar väljs och plusknappen har sin etikett (NAV-8, NAV-10)`() {
        var selected by mutableStateOf(0)
        var logged = 0
        show {
            AppFloatingToolbar(
                listOf(ToolbarItem("Idag", R.drawable.ic_sun), ToolbarItem("Dagbok", R.drawable.ic_book)),
                selected,
                { selected = it },
                action = ToolbarAction("Logga", R.drawable.ic_add) { logged++ },
            )
        }
        rule.onNodeWithContentDescription("Dagbok").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        assertEquals(1, selected)
        rule.onNodeWithContentDescription("Dagbok").assertIsSelected()
        rule.onNodeWithContentDescription("Logga").performClick()
        assertEquals(1, logged)
    }

    @Test
    fun `ikonknappen har sin beskrivning och anropar onClick`() {
        var clicks = 0
        show { AppIconButton(R.drawable.ic_arrow_back, "Tillbaka", { clicks++ }) }
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `en klickbar pill är en knapp med 48 dp tryckyta`() {
        var clicks = 0
        show { InfoPill("Väntar på synk", icon = R.drawable.ic_cloud_upload, onClick = { clicks++ }) }
        rule.onNodeWithText("Väntar på synk")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertTouchHeightIsEqualTo(48.dp)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `toppraden visar synkindikatorn bara när ändringar väntar (NFR-22)`() {
        var indicator by mutableStateOf(SyncIndicator())
        var clicks = 0
        show {
            CompositionLocalProvider(LocalSyncIndicator provides indicator) {
                AppTopBar("Idag") { AppIconButton(R.drawable.ic_more_vert, "Fler val", {}) }
            }
        }
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").assertDoesNotExist()
        indicator = SyncIndicator(pending = true) { clicks++ }
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .performClick()
        assertEquals(1, clicks)
        rule.onNodeWithContentDescription("Fler val").assertIsDisplayed()
        indicator = SyncIndicator()
        rule.onNodeWithContentDescription("Ändringar väntar på att synkas").assertDoesNotExist()
    }
}

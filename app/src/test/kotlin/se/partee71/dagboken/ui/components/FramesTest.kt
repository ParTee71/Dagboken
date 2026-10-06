package se.partee71.dagboken.ui.components

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureLightAndDarkPaused
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.common.ArchiveEvent
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.runEditScreenContract
import se.partee71.dagboken.ui.runListScreenContract
import se.partee71.dagboken.ui.theme.AppColors
import se.partee71.dagboken.ui.theme.AppTypography
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.dagboken.ui.theme.Tone

/** Ramarna testas grundligt en gång här; skärmarna kör bara kontrakten (skill testing-strategy). */
@RunWith(RobolectricTestRunner::class)
class FramesTest {

    @get:Rule
    val rule = createComposeRule()

    private val empty = EmptyContent(R.drawable.ic_activity, "Inga aktivitetstyper än", "Promenad, yoga, städning – det du brukar göra.")
    private val add = AddAction("Lägg till", {})
    private val items = listOf("Promenad" to "Rörelse", "Yoga" to "Rörelse", "Städning" to "Hemma")

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun group(item: Pair<String, String>) = ListGroup(item.second, tone = if (item.second.startsWith("Hemma")) Tone.Positive else Tone.Primary)

    @Test
    fun `EntityListScreen uppfyller listkontraktet`() = rule.runListScreenContract("Promenad", "Promenad", empty.title, add.label) { state, onAdd, onRetry ->
        EntityListScreen("Aktivitetstyper", state, empty, AddAction(add.label, onAdd), key = { it }, onRetry = onRetry) { ItemRow(it) }
    }

    @Test
    fun `utan lägg till finns ingen knapp, en grupp kan sakna räknare och sidfoten står sist – också i tomt läge`() {
        var state by mutableStateOf<ListUiState<Pair<String, String>>>(ListUiState.Empty)
        rule.setContent {
            DagbokenTheme {
                EntityListScreen(
                    "Dagbok",
                    state,
                    EmptyContent(R.drawable.ic_book, "Inga poster än", "Logga med plusknappen."),
                    add = null,
                    key = { it.first },
                    group = { ListGroup(it.second, showCount = false) },
                    footer = { ItemRow("Sidfot") },
                ) { ItemRow(it.first) }
            }
        }
        rule.onNodeWithText("Inga poster än").assertIsDisplayed()
        rule.onNodeWithText("Lägg till").assertDoesNotExist()
        rule.onNodeWithText("Sidfot").assertIsDisplayed()
        state = ListUiState.Content(items)
        rule.onNodeWithText("Rörelse").assertIsDisplayed()
        rule.onNodeWithText("2").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Sidfot"))
        val last = rule.onNodeWithText("Städning").fetchSemanticsNode().positionInRoot
        val footer = rule.onNodeWithText("Sidfot").fetchSemanticsNode().positionInRoot
        assertTrue(footer.y > last.y, "sidfoten står efter grupperna")
    }

    @Test
    fun `grupperad lista visar en rubrik med antal per grupp`() {
        rule.setContent {
            DagbokenTheme {
                EntityListScreen("Aktivitetstyper", ListUiState.Content(items), empty, add, key = { it.first }, group = ::group) { ItemRow(it.first) }
            }
        }
        rule.onNodeWithText("Rörelse").assertIsDisplayed()
        rule.onNodeWithText("2").assertIsDisplayed()
        rule.onNodeWithText("Hemma").assertIsDisplayed()
    }

    @Test
    fun `undergrupper, två kolumner, en hopfälld grupp och rubrik överst`() {
        val rows = listOf("A1" to "a", "A2" to "a", "B1" to "b", "Ö1" to "ö", "Ö2" to "ö", "Ö3" to "ö", "D1" to "d")
        val groups = mapOf(
            "a" to ListGroup("Recept", count = "1 / 2"),
            "b" to ListGroup("Recept"),
            "ö" to ListGroup("Vid behov", columns = 2),
            "d" to ListGroup("Dolda", collapsible = true),
        )
        rule.setContent {
            DagbokenTheme {
                EntityListScreen(
                    "Mediciner",
                    ListUiState.Content(rows),
                    empty,
                    add,
                    key = { it.first },
                    group = { groups.getValue(if (it.second == "b") "a" else it.second) },
                    subtitle = "sön 4 okt",
                    header = { ItemRow("Rubrikplats") },
                    subgroup = { row -> ListSubgroup(if (row.second == "b") "Kväll" else "Morgon", AppColors.swatch(0)).takeIf { row.second in setOf("a", "b") } },
                    archive = ListArchive(showToggle = false),
                ) { ItemRow(it.first) }
            }
        }
        rule.onNodeWithText("Rubrikplats").assertIsDisplayed()
        rule.onNodeWithText("sön 4 okt").assertIsDisplayed()
        rule.onNodeWithText("1 / 2").assertIsDisplayed()
        rule.onNodeWithText("Morgon").assertIsDisplayed()
        rule.onNodeWithText("Kväll").assertIsDisplayed()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ö2"))
        val left = rule.onNodeWithText("Ö1").fetchSemanticsNode().positionInRoot
        val right = rule.onNodeWithText("Ö2").fetchSemanticsNode().positionInRoot
        assertEquals(left.y, right.y, "två kolumner: samma rad")
        rule.onNodeWithText("D1").assertDoesNotExist()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Dolda"))
        rule.onNodeWithText("Dolda").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("D1"))
        rule.onNodeWithText("D1").assertExists()
    }

    @Test
    fun `en utfälld grupp står kvar efter att skärmen återskapats`() {
        val restore = StateRestorationTester(rule)
        restore.setContent {
            DagbokenTheme {
                EntityListScreen("Mediciner", ListUiState.Content(listOf("A1", "D1")), empty, add, key = { it }, group = { ListGroup(if (it == "D1") "Dolda" else "Övrigt", collapsible = it == "D1") }) { ItemRow(it) }
            }
        }
        rule.onNodeWithText("D1").assertDoesNotExist()
        rule.onNodeWithText("Dolda").performClick()
        rule.onNodeWithText("D1").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("D1").assertIsDisplayed()
    }

    @Test
    fun `filter står fast under rubriken i alla lägen`() {
        var state by mutableStateOf<ListUiState<String>>(ListUiState.Empty)
        rule.setContent {
            DagbokenTheme {
                EntityListScreen("Listor", state, empty, add, key = { it }, filter = { ItemRow("Filterrad") }) { ItemRow(it) }
            }
        }
        rule.onNodeWithText("Filterrad").assertIsDisplayed()
        state = ListUiState.Error(DataError.Offline)
        rule.onNodeWithText("Filterrad").assertIsDisplayed()
        state = ListUiState.Content(listOf("Promenad"))
        rule.onNodeWithText("Filterrad").assertIsDisplayed()
        rule.onNodeWithText("Promenad").assertIsDisplayed()
    }

    @Test
    fun `samma fel två gånger i rad visas två gånger`() {
        var archive by mutableStateOf(ListArchive())
        var shown = 0
        rule.setContent {
            DagbokenTheme {
                EntityListScreen("Listor", ListUiState.Content(listOf("Promenad")), empty, add, key = { it }, archive = archive.copy(onEvent = { if (it == ArchiveEvent.ErrorShown) shown++ })) { ItemRow(it) }
            }
        }
        repeat(2) {
            archive = ListArchive(failure = Failure(DataError.Offline))
            rule.waitForIdle()
            rule.onNodeWithText(string(R.string.error_offline)).assertIsDisplayed()
            rule.mainClock.advanceTimeBy(SNACKBAR_GONE_MILLIS)
            rule.waitForIdle()
        }
        assertEquals(2, shown, "samma sorts fel igen visas igen")
    }

    @Test
    fun `Visa arkiverade finns i menyn där arkivering finns`() {
        var toggles = 0
        rule.setContent {
            DagbokenTheme {
                EntityListScreen("Aktivitetstyper", ListUiState.Content(listOf("Promenad")), empty, add, key = { it }, archive = ListArchive { if (it == ArchiveEvent.ToggleArchived) toggles++ }) { ItemRow(it) }
            }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Visa arkiverade").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `EntityEditScreen uppfyller redigeringskontraktet`() {
        val editor = EditorState("", Validator { if (it.isBlank()) mapOf("name" to R.string.error_not_signed_in) else emptyMap() })
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = { update("name") { "x" }; update("name") { "" } },
            makeValid = { update("name") { "Promenad" } },
            invalidMessage = string(R.string.error_not_signed_in),
        ) { state, effects, onSave, onClose ->
            EntityEditScreen("Ny aktivitetstyp", state, effects, onSave, onClose) {
                AppTextField(state.value, { v -> editor.update("name") { v } }, "Namn", error = state.errorFor("name")?.let { string(it) })
            }
        }
    }

    @Test
    fun `radering i redigeringens meny kräver bekräftelse`() {
        var deleted = 0
        var archived = 0
        rule.setContent {
            DagbokenTheme {
                EntityEditScreen(
                    "Promenad", EditorUiState("Promenad"), emptyFlow(), {}, {},
                    onArchive = { archived++ },
                    delete = DeleteAction("Radera Promenad?", "Det går inte att ångra.") { deleted++ },
                ) {}
            }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(0, deleted)
        rule.onNodeWithText("Radera Promenad?").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(1, deleted)
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, archived)
    }

    @Test
    fun `EntityEditScreen - lyckad sparning går till onSaved, bakåt till onClose`() {
        var saved = 0
        var closed = 0
        val effects = kotlinx.coroutines.flow.MutableSharedFlow<EditorEffect>(extraBufferCapacity = 1)
        rule.setContent {
            DagbokenTheme { EntityEditScreen("Ny händelse", EditorUiState("Migrän"), effects, {}, { closed++ }, onSaved = { saved++ }) {} }
        }
        rule.runOnIdle { effects.tryEmit(EditorEffect.Done) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1 to 1, saved to closed)
    }

    @Test
    fun `EntityEditScreen - ett sparfel visas med sin Failure-text i snackbaren`() {
        val effects = kotlinx.coroutines.flow.MutableSharedFlow<EditorEffect>(extraBufferCapacity = 1)
        rule.setContent { DagbokenTheme { EntityEditScreen("Ny händelse", EditorUiState("Migrän"), effects, {}, {}) {} } }
        rule.runOnIdle { effects.tryEmit(EditorEffect.Failed(Failure(DataError.Unknown, R.string.option_name_duplicate))) }
        rule.onNodeWithText(string(R.string.option_name_duplicate)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.error_unknown)).assertDoesNotExist()
    }

    @Test
    fun `ConfirmDialog - nej-knappen och tryck utanför kan betyda olika saker`() {
        var declined = 0
        var dismissed = 0
        rule.setContent {
            DagbokenTheme {
                ConfirmDialog("Avsluta receptet?", "…", "Avsluta", {}, { dismissed++ }, dismissLabel = "Behåll aktivt", onDecline = { declined++ })
            }
        }
        rule.onNodeWithText("Behåll aktivt").performClick()
        assertEquals(1 to 0, declined to dismissed)
        // Bakåt (som tryck utanför) stänger bara – inget av valen görs.
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1 to 1, declined to dismissed)
    }

    @Test
    fun `EntityEditScreen - arkivera med osparade ändringar frågar först (NFR-10)`() {
        var archived = 0
        rule.setContent {
            DagbokenTheme { EntityEditScreen("Promenad", EditorUiState("Promenad 50", isValid = true, isDirty = true), emptyFlow(), {}, {}, onArchive = { archived++ }) {} }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        rule.onNodeWithText("Fortsätt redigera").performClick()
        assertEquals(0, archived)
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        rule.onNodeWithText("Släng").performClick()
        assertEquals(1, archived)
    }

    @Test
    fun `LoadErrorState - något som inte finns längre har inget Försök igen`() {
        rule.setContent { DagbokenTheme { LoadErrorState("Kunde inte hämta uppgifterna", DataError.NotFound, {}) } }
        rule.onNodeWithText("Det finns inte längre", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Försök igen").assertDoesNotExist()
    }

    @Test
    fun `EntityEditScreen - Återställ i menyn för något arkiverat`() {
        var restored = 0
        rule.setContent {
            DagbokenTheme { EntityEditScreen("Promenad", EditorUiState("Promenad"), emptyFlow(), {}, {}, onRestore = { restored++ }) {} }
        }
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").assertDoesNotExist()
        rule.onNodeWithText("Återställ").performClick()
        assertEquals(1, restored)
    }

    @Test
    fun `EntityDetailScreen - lägen, försök igen och meny`() {
        var state by mutableStateOf<DetailUiState<String>>(DetailUiState.Loading)
        var retries = 0
        var edits = 0
        var archived = 0
        rule.setContent {
            DagbokenTheme {
                EntityDetailScreen(
                    state, header = { DetailHeader(it, "3 incheckningar") }, onBack = {}, onEdit = { edits++ },
                    menu = { listOf(AppMenuItem("Arkivera", { archived++ })) }, onRetry = { retries++ },
                ) { Text("Incheckningar") }
            }
        }
        rule.onNodeWithContentDescription("Laddar").assertExists()
        rule.onNodeWithContentDescription("Redigera").assertDoesNotExist()
        state = DetailUiState.Error(DataError.Offline)
        rule.onNodeWithText("Kunde inte hämta uppgifterna").assertIsDisplayed()
        rule.onNodeWithText("Försök igen").performClick()
        assertEquals(1, retries)
        state = DetailUiState.Content("Förkylning")
        rule.onNodeWithText("Förkylning").assertIsDisplayed()
        rule.onNodeWithText("Incheckningar").assertIsDisplayed()
        rule.onNodeWithContentDescription("Redigera").performClick()
        rule.onNodeWithContentDescription("Fler val").performClick()
        rule.onNodeWithText("Arkivera").performClick()
        assertEquals(1, edits)
        assertEquals(1, archived)
    }

    @Test
    fun `EntityDetailScreen med titel i toppraden har inget centrerat huvud`() {
        var back = 0
        rule.setContent {
            DagbokenTheme {
                EntityDetailScreen(DetailUiState.Content("Förkylning"), header = null, onBack = { back++ }, title = "Tema") { ItemRow(it) }
            }
        }
        rule.onNodeWithText("Tema").assertIsDisplayed()
        rule.onNodeWithText("Förkylning").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1, back)
    }

    @Test
    fun `EntityDetailScreen - innehåll och fel`() {
        captureLightAndDark("EntityDetailScreen_innehall") {
            EntityDetailScreen(
                DetailUiState.Content("Förkylning"), header = { DetailHeader(it, "sedan fre 2 okt · pågående") }, onBack = {}, onEdit = {},
                menu = { listOf(AppMenuItem("Avsluta episoden", {})) },
                leading = { Text("🤧", style = AppTypography.headline) },
            ) {
                AppCard {
                    SectionHeader("Incheckningar", icon = R.drawable.ic_thermometer, count = "1")
                    ItemRow("sön 4 okt · 08:15", subtitle = "Svårighetsgrad 4 · hosta, snuva", navigates = true, onClick = {})
                }
            }
        }
        captureLightAndDark("EntityDetailScreen_fel") {
            EntityDetailScreen<String>(DetailUiState.Error(DataError.Offline), header = { DetailHeader(it) }, onBack = {}) {}
        }
        captureLightAndDark("EntityDetailScreen_finns_inte") {
            EntityDetailScreen<String>(DetailUiState.Error(DataError.NotFound), header = { DetailHeader(it) }, onBack = {}) {}
        }
    }

    @Test
    fun `UpcomingScreen - undersidan har tillbakapil som anropar onBack, fliken har ingen`() {
        var back = 0
        var sub by mutableStateOf(true)
        rule.setContent {
            DagbokenTheme {
                if (sub) UpcomingScreen("Export och import", R.drawable.ic_download, "Kommer snart.", onBack = { back++ })
                else UpcomingScreen("Idag", R.drawable.ic_sun, "Kommer snart.")
            }
        }
        rule.onNodeWithText("Snart här").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1, back)
        sub = false
        rule.onNodeWithContentDescription("Tillbaka").assertDoesNotExist()
    }

    @Test
    fun `UpcomingScreen - undersida`() {
        captureLightAndDark("UpcomingScreen_undersida") {
            UpcomingScreen("Export och import", R.drawable.ic_download, "Spara en kopia av dagboken eller läs in en tidigare.", onBack = {})
        }
    }

    @Test
    fun `EntityListScreen - lägen`() {
        rule.captureLightAndDarkPaused("EntityListScreen_laddar") { EntityListScreen<String>("Aktivitetstyper", ListUiState.Loading, empty, add, key = { it }) {} }
        captureLightAndDark("EntityListScreen_tom") { EntityListScreen<String>("Aktivitetstyper", ListUiState.Empty, empty, add, key = { it }) {} }
        captureLightAndDark("EntityListScreen_fel") { EntityListScreen<String>("Aktivitetstyper", ListUiState.Error(DataError.Offline), empty, add, key = { it }) {} }
        captureLightAndDark("EntityListScreen_innehall") {
            EntityListScreen("Aktivitetstyper", ListUiState.Content(items), empty, add.copy(menu = listOf(AppMenuItem("Visa arkiverade", {}))), key = { it.first }, group = ::group) { ItemRow(it.first, subtitle = "Favorit", navigates = true, onClick = {}) }
        }
    }

    @Test
    fun `EntityEditScreen - ogiltig och giltig`() {
        captureLightAndDark("EntityEditScreen_ogiltig") {
            EntityEditScreen("Ny aktivitetstyp", EditorUiState("", mapOf("name" to R.string.error_unknown), isValid = false, isDirty = true), emptyFlow(), {}, {}) {
                AppTextField("", {}, "Namn", error = "Ange ett namn")
                LabeledGroup("Sort") { ChoiceChips(listOf("Aktivitet", "Symptom", "Händelse"), "Aktivitet", {}, { it }) }
            }
        }
        captureLightAndDark("EntityEditScreen_giltig") {
            EntityEditScreen("Ny aktivitetstyp", EditorUiState("Promenad", isValid = true, isDirty = true), emptyFlow(), {}, {}, onArchive = {}) {
                AppTextField("Promenad", {}, "Namn")
                SwitchRow("Favorit", true, {}, subtitle = "Visas först i snabbvalen")
            }
        }
    }

    @Test
    fun `EntityEditScreen - släng ändringar`() = rule.captureScreenLightAndDark("EntityEditScreen_slang", open = { onNodeWithContentDescription("Tillbaka").clickWithoutRipple() }) {
        EntityEditScreen("Promenad", EditorUiState("Promenad 50", isValid = true, isDirty = true), emptyFlow(), {}, {}) {
            AppTextField("Promenad 50", {}, "Namn")
        }
    }
}

/** Längre än en kort snackbar står kvar. */
private const val SNACKBAR_GONE_MILLIS = 10_000L

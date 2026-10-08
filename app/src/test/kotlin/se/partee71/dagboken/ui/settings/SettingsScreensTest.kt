package se.partee71.dagboken.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.dagboken.R
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.reminders.ReminderAccess
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.EditorEffect
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.common.EditorState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.common.Validator
import se.partee71.dagboken.ui.components.ListArchive
import se.partee71.dagboken.ui.runEditScreenContract
import se.partee71.dagboken.ui.runListScreenContract
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Inställningsarkets underskärmar (NAV-9): kontrakten för ramarna och det som är unikt för varje
 * skärm. Skärmdumparna ljust + mörkt ligger i `SettingsScreenshotTest`.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int, vararg args: Any) = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    @Test
    fun `Profil uppfyller redigeringskontraktet (NFR-10)`() {
        val editor = EditorState(ProfileForm(), ProfileForm.validator(1906..2026))
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = { update(ProfileForm.BIRTH_YEAR) { it.copy(birthYear = "1800") } },
            makeValid = { update(ProfileForm.BIRTH_YEAR) { it.copy(birthYear = "1971") } },
            invalidMessage = string(R.string.profile_birth_year_invalid),
        ) { state, effects, onSave, onClose ->
            ProfileScreen(state, effects, { if (it == ProfileEvent.Save) onSave() }, onClose)
        }
    }

    @Test
    fun `Profil - könet väljs med chips i ordningen Kvinna, Man, Ej angivet`() {
        val events = mutableListOf<ProfileEvent>()
        rule.setContent { DagbokenTheme { ProfileScreen(EditorUiState(ProfileForm("1971", Sex.UNSPECIFIED)), emptyFlow(), { events += it }, {}) } }
        rule.onNodeWithText("Kvinna").performClick()
        rule.onNodeWithText("Man").performClick()
        rule.onNodeWithText("Ej angivet").assertIsDisplayed()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.SexChanged(Sex.FEMALE), ProfileEvent.SexChanged(Sex.MALE)), events)
    }

    @Test
    fun `Påminnelser uppfyller redigeringskontraktet – alla värden är giltiga`() {
        val editor = EditorState(ReminderSettings(), Validator { emptyMap() })
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = null,
            makeValid = { update { it.copy(medsEnabled = true) } },
            invalidMessage = null,
        ) { state, effects, onSave, onClose ->
            RemindersScreen(state, effects, { if (it == RemindersEvent.Save) onSave() }, onClose)
        }
    }

    @Test
    fun `Påminnelser - sex medicintider, fyra måendetillfällen och periodslut (NOT-4, NOT-13, NOT-18)`() {
        val events = mutableListOf<RemindersEvent>()
        rule.setContent { DagbokenTheme { RemindersScreen(EditorUiState(ReminderSettings()), emptyFlow(), { events += it }, {}) } }
        listOf("Morgon", "Förmiddag", "Eftermiddag", "Kväll", "Natt", "Efter frukost", "Kvällsmat", "Läggdags").forEach {
            rule.onNodeWithText(it).assertExists()
        }
        rule.onNodeWithText("Vid behov").assertDoesNotExist()
        rule.onNodeWithText("07:00").assertExists()
        rule.onNodeWithText("22:00").assertExists()
        rule.onNodeWithText("09:00").assertExists()
        rule.onNodeWithText("Medicinpåminnelser").performClick()
        rule.onNodeWithContentDescription("Morgon").performClick()
        assertEquals(RemindersEvent.MedsEnabledChanged(true), events[0])
        assertEquals(false, (events[1] as RemindersEvent.SlotChanged).reminder.enabled)
    }

    @Test
    fun `Påminnelser - saknade behörigheter visas överst med genväg till systeminställningarna (NOT-16)`() {
        val opened = mutableListOf<String>()
        var access by mutableStateOf(ReminderAccess(notifications = false, exactAlarms = false))
        rule.setContent {
            DagbokenTheme {
                RemindersScreen(
                    EditorUiState(ReminderSettings()), emptyFlow(), {}, {},
                    access = access,
                    onOpenNotificationSettings = { opened += "notiser" },
                    onOpenExactAlarmSettings = { opened += "exakta" },
                )
            }
        }
        rule.onNodeWithText(string(R.string.reminders_notifications_off)).performClick()
        rule.onNodeWithText(string(R.string.reminders_exact_off)).performClick()
        assertEquals(listOf("notiser", "exakta"), opened)

        access = ReminderAccess()
        rule.onNodeWithText(string(R.string.reminders_notifications_off)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.reminders_exact_off)).assertDoesNotExist()
    }

    @Test
    fun `Tema - auto visar starttimmarna och ogiltig ordning som fältfel (SET-1, SET-2)`() {
        val events = mutableListOf<ThemeEvent>()
        val invalid = ThemeForm(ThemeSettings(mode = ThemeMode.AUTO, lightStartHour = 21, darkStartHour = 7), hoursInvalid = true)
        rule.setContent { DagbokenTheme { ThemeScreen(DetailUiState.Content(invalid), null, { events += it }, {}) } }
        rule.onNodeWithContentDescription("Ljust från, 21:00", substring = true).assertExists()
        rule.onNodeWithContentDescription("Mörkt från, 07:00, Ljust måste börja före mörkt", substring = true).assertExists()
        rule.onNodeWithText("Mörkt").performClick()
        assertEquals(listOf<ThemeEvent>(ThemeEvent.ModeChosen(ThemeMode.DARK)), events)
    }

    @Test
    fun `Tema - ljust och mörkt har inga starttimmar`() {
        rule.setContent { DagbokenTheme { ThemeScreen(DetailUiState.Content(ThemeForm(ThemeSettings(mode = ThemeMode.LIGHT))), null, {}, {}) } }
        rule.onNodeWithText("LÄGE").assertIsDisplayed()
        rule.onNodeWithText("Ändringen syns direkt i hela appen.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Ljust från", substring = true).assertDoesNotExist()
    }

    private val promenad = Option("activity-promenad", OptionKind.ACTIVITY, "Promenad", favorite = true)

    @Test
    fun `Listor uppfyller listkontraktet (NFR-1)`() = rule.runListScreenContract(promenad, "Promenad", "Inga aktiviteter än", "Ny aktivitet") { state, onAdd, onRetry ->
        ListsScreen(state, OptionKind.ACTIVITY, ListArchive(), { if (it == ListsEvent.Retry) onRetry() }, {}, onAdd, {})
    }

    @Test
    fun `Listor - valet av lista syns även i en tom lista, stjärnan och raden har var sin åtgärd`() {
        val events = mutableListOf<ListsEvent>()
        val opened = mutableListOf<Option>()
        var state by mutableStateOf<ListUiState<Option>>(ListUiState.Empty)
        rule.setContent { DagbokenTheme { ListsScreen(state, OptionKind.EVENT, ListArchive(), { events += it }, {}, {}, { opened += it }) } }
        rule.onNodeWithText("Inga händelsetyper än").assertIsDisplayed()
        rule.onNodeWithText("Symptom").performClick()
        assertEquals(listOf<ListsEvent>(ListsEvent.KindChosen(OptionKind.SYMPTOM)), events)

        state = ListUiState.Content(listOf(promenad))
        rule.onNodeWithContentDescription("Ta bort Promenad som favorit").performClick()
        rule.onNodeWithText("Promenad").performClick()
        assertEquals(ListsEvent.FavoriteToggled(promenad), events.last())
        assertEquals(listOf(promenad), opened)
    }

    @Test
    fun `Listor - ett arkiverat alternativ har Arkiverad som pill, inte som undertext`() {
        val archived = promenad.copy(archived = true)
        rule.setContent { DagbokenTheme { ListsScreen(ListUiState.Content(listOf(archived)), OptionKind.ACTIVITY, ListArchive(showing = true), {}, {}, {}, {}) } }
        rule.onNodeWithText("Arkiverad").assertIsDisplayed()
        rule.onNodeWithText("Promenad").assertIsDisplayed()
    }

    @Test
    fun `Nytt alternativ och namnbyte uppfyller redigeringskontraktet`() {
        val editor = EditorState("", optionNameValidator({ listOf(promenad) }, exceptId = null))
        rule.runEditScreenContract(
            editor = editor,
            makeInvalid = { update(OPTION_NAME) { "promenad" } },
            makeValid = { update(OPTION_NAME) { "Cykling" } },
            invalidMessage = string(R.string.option_name_duplicate),
        ) { state, effects, onSave, onClose ->
            OptionEditScreen(OptionKind.ACTIVITY, isNew = true, archived = null, state, effects, { if (it == OptionEditEvent.Save) onSave() }, onClose)
        }
        rule.onNodeWithText("Ny aktivitet").assertIsDisplayed()
    }

    @Test
    fun `Om Dagboken visar version, licenser och integritet`() {
        rule.setContent { DagbokenTheme { AboutScreen(DetailUiState.Content(AboutInfo("4.0.0", 400, "SIL Open Font License")), {}) } }
        rule.onNodeWithText("Om Dagboken").assertIsDisplayed()
        rule.onNodeWithText("4.0.0 (bygge 400)").assertIsDisplayed()
        rule.onNodeWithText("SIL Open Font License").assertDoesNotExist()
        rule.onNodeWithText("Licenser").performClick()
        rule.onNodeWithText("SIL Open Font License").assertExists()
        rule.onNodeWithText("Integritet").assertExists()
    }

    @Test
    fun `Export och import har Spara som JSON och Importera backup och går tillbaka`() {
        var back = 0
        rule.setContent { DagbokenTheme { ExportImportScreen(ExportImportUiState(), {}, { "dagboken-export.json" }, onBack = { back++ }) } }
        rule.onNodeWithText("Export och import").assertIsDisplayed()
        rule.onNodeWithText("Spara som JSON").assertIsDisplayed()
        rule.onNodeWithText("Importera backup").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tillbaka").performClick()
        assertEquals(1, back)
    }

    @Test
    fun `en nekad dubblett visas som Finns redan i listan – i formuläret och i listan`() {
        val effects = MutableSharedFlow<EditorEffect>(extraBufferCapacity = 1)
        var listError by mutableStateOf(ListArchive())
        var showList by mutableStateOf(false)
        rule.setContent {
            DagbokenTheme {
                if (showList) {
                    ListsScreen(ListUiState.Content(listOf(promenad)), OptionKind.ACTIVITY, listError, {}, {}, {}, {})
                } else {
                    OptionEditScreen(OptionKind.ACTIVITY, isNew = false, archived = true, EditorUiState("Promenad"), effects, {}, {})
                }
            }
        }
        rule.runOnIdle { effects.tryEmit(EditorEffect.Failed(Failure(DataError.Unknown, R.string.option_name_duplicate))) }
        rule.onNodeWithText("Finns redan i listan").assertIsDisplayed()

        showList = true
        listError = ListArchive(failure = Failure(DataError.Unknown, R.string.option_name_duplicate))
        rule.onNodeWithText("Finns redan i listan").assertIsDisplayed()
    }
}

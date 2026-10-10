package se.partee71.dagboken.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.reminders.ReminderAccess
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.EditorUiState
import se.partee71.dagboken.ui.common.ListUiState
import se.partee71.dagboken.ui.components.ListArchive
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.testing.clickWithoutRipple
import se.partee71.dagboken.ui.migration.MigrationSamples

/** Inställningsarkets underskärmar i ljust och mörkt (NFR-20: bredvid mockupen i PR:en). Påhittad data. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `Profil`() = rule.captureLightAndDark("Settings_Profil") {
        ProfileScreen(EditorUiState(ProfileForm("1971", Sex.FEMALE), isDirty = true), emptyFlow(), {}, {})
    }

    @Test
    fun `Påminnelser`() = rule.captureLightAndDark("Settings_Paminnelser") {
        val reminders = ReminderSettings(medsEnabled = true).let { r ->
            r.copy(
                medSlots = r.medSlots.map { if (it.slot == Slot.NIGHT) it.copy(enabled = false) else it },
                screeningOccasions = r.screeningOccasions.map { if (it.occasion == Occasion.BREAKFAST) it.copy(enabled = true, time = LocalTime(8, 30)) else it },
            )
        }
        RemindersScreen(EditorUiState(reminders), emptyFlow(), {}, {})
    }

    @Test
    fun `Påminnelser - behörigheter saknas`() = rule.captureLightAndDark("Settings_Paminnelser_behorighet") {
        RemindersScreen(EditorUiState(ReminderSettings(medsEnabled = true)), emptyFlow(), {}, {}, access = ReminderAccess(notifications = false, exactAlarms = false))
    }

    @Test
    fun `Tema - auto`() = rule.captureLightAndDark("Settings_Tema_auto") {
        ThemeScreen(DetailUiState.Content(ThemeForm(ThemeSettings(mode = ThemeMode.AUTO))), null, {}, {})
    }

    @Test
    fun `Tema - ogiltiga starttimmar`() = rule.captureLightAndDark("Settings_Tema_fel") {
        ThemeScreen(DetailUiState.Content(ThemeForm(ThemeSettings(mode = ThemeMode.AUTO, lightStartHour = 21, darkStartHour = 6), hoursInvalid = true)), null, {}, {})
    }

    @Test
    fun `Listor`() = rule.captureLightAndDark("Settings_Listor") {
        val options = listOf(
            Option("a", OptionKind.ACTIVITY, "Promenad", favorite = true),
            Option("b", OptionKind.ACTIVITY, "Yoga"),
            Option("c", OptionKind.ACTIVITY, "Städning", favorite = true),
            Option("d", OptionKind.ACTIVITY, "Simning", archived = true),
        )
        ListsScreen(ListUiState.Content(options), OptionKind.ACTIVITY, ListArchive(showing = true), {}, {}, {}, {})
    }

    @Test
    fun `Listor - tom`() = rule.captureLightAndDark("Settings_Listor_tom") {
        ListsScreen(ListUiState.Empty, OptionKind.SYMPTOM, ListArchive(), {}, {}, {}, {})
    }

    @Test
    fun `Byt namn`() = rule.captureLightAndDark("Settings_Alternativ_bytnamn") {
        OptionEditScreen(OptionKind.ACTIVITY, isNew = false, archived = false, EditorUiState("Långpromenad", isDirty = true), emptyFlow(), {}, {})
    }

    @Test
    fun `Om Dagboken`() = rule.captureLightAndDark("Settings_Om") {
        AboutScreen(DetailUiState.Content(AboutInfo("4.0.0", 400, "SIL Open Font License, version 1.1", LocalDate(2026, 10, 7))), {})
    }

    @Test
    fun `Export och import`() = rule.captureLightAndDark("Settings_ExportImport") {
        ExportImportScreen(ExportImportUiState(), {}, { "dagboken-export-2026-10-08.json" }, onBack = {})
    }

    @Test
    fun `Export och import - sparar`() = rule.captureLightAndDark("Settings_ExportImport_sparar") {
        ExportImportScreen(ExportImportUiState(exporting = true), {}, { "dagboken-export-2026-10-08.json" }, onBack = {})
    }

    @Test
    fun `Export och import - välj varifrån`() = rule.captureScreenLightAndDark(
        "Settings_ExportImport_ark",
        open = { onNodeWithText("Importera backup").clickWithoutRipple() },
    ) {
        ExportImportScreen(ExportImportUiState(), {}, { "dagboken-export-2026-10-08.json" }, onBack = {})
    }

    @Test
    @Config(qualifiers = "w390dp-h1700dp-xxhdpi")
    fun `Export och import - granska importen`() = rule.captureLightAndDark("Settings_ExportImport_granska") {
        ExportImportScreen(ExportImportUiState(import = MigrationSamples.importReview), {}, { "dagboken-export-2026-10-08.json" }, onBack = {})
    }
}

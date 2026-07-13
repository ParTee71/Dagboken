package se.partee71.dagboken.ui.historik

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase

@RunWith(AndroidJUnit4::class)
class HistorikScreenTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    private lateinit var db: AppDatabase
    private lateinit var aktivRepo: AktiviteterRepository
    private lateinit var medicRepo: MedicinerRepository
    private lateinit var handelserRepo: HandelserRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository
    private lateinit var vm: HistorikViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        aktivRepo = AktiviteterRepository(db.aktivitetDao())
        medicRepo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = NoteRepository(db.noteDao()),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        handelserRepo = HandelserRepository(db.handelseDao())
        sjukdomarRepo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), Dispatchers.IO)
        vm = HistorikViewModel(aktivRepo, medicRepo, handelserRepo, sjukdomarRepo)
    }

    @After fun tearDown() {
        // Cancel the ViewModel's collectors first: its Room-backed flows use
        // WhileSubscribed(5000), so the DB collector outlives the test unless
        // stopped, and querying the closed in-memory DB throws
        // "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        db.close()
    }

    private fun setContent(
        onEditAktivitet: (String, String) -> Unit = { _, _ -> },
        onEditMedicin: (String) -> Unit = {},
        onEditHandelse: (String) -> Unit = {},
        onOpenSjukdomsEpisod: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                HistorikScreen(
                    onBack               = {},
                    onEditAktivitet      = onEditAktivitet,
                    onEditMedicin        = onEditMedicin,
                    onEditHandelse       = onEditHandelse,
                    onOpenSjukdomsEpisod = onOpenSjukdomsEpisod,
                    vm                   = vm,
                )
            }
        }
    }

    @Test fun empty_state_shown_when_no_entries() {
        setContent()
        composeRule.onNodeWithText("Ingen historik ännu").assertIsDisplayed()
    }

    @Test fun entries_from_all_sources_are_displayed() {
        runBlocking {
            aktivRepo.save(
                Aktivitet(
                    id = "a1", timestamp = "x", datum = "2026-01-01", tid = "08:00",
                    aktivitet = "Promenad", energy = 3, stress = 2, somatiska = 0, symptom = "",
                ),
            )
            medicRepo.saveMedicin(
                Medicin(
                    id = "m1", timestamp = "x", datum = "2026-01-01", tid = "09:00",
                    namn = "Ibuprofen", dos = "400", enhet = "mg", tidpunkt = "Morgon", tagen = true,
                ),
            )
            handelserRepo.save(
                Handelse(
                    id = "h1", timestamp = "x", datum = "2026-01-01", tid = "10:00",
                    typ = "Migrän", svarighetsgrad = 5, varaktighetMinuter = 30, triggers = "", atgarder = "",
                ),
            )
            sjukdomarRepo.saveEpisod(
                SjukdomsEpisod(id = "ep1", typ = "Förkylning", startDatum = "2026-01-01", slutDatum = ""),
            )
            sjukdomarRepo.saveIncheckning(
                SjukdomsIncheckning(id = "i1", episodId = "ep1", datum = "2026-01-01", tid = "11:00", svarighetsgrad = 4, symptom = "", somatiska = 0),
            )
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Promenad").assertIsDisplayed()
        composeRule.onNodeWithText("Ibuprofen").assertIsDisplayed()
        composeRule.onNodeWithText("Migrän").assertIsDisplayed()
        composeRule.onNodeWithText("Förkylning").assertIsDisplayed()
    }

    @Test fun tapping_a_medicin_entry_invokes_onEditMedicin() {
        runBlocking {
            medicRepo.saveMedicin(
                Medicin(
                    id = "m1", timestamp = "x", datum = "2026-01-01", tid = "09:00",
                    namn = "Ibuprofen", dos = "400", enhet = "mg", tidpunkt = "Morgon", tagen = true,
                ),
            )
        }
        var editedId: String? = null
        setContent(onEditMedicin = { id -> editedId = id })
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ibuprofen").performClick()
        assertEquals("m1", editedId)
    }

    @Test fun long_press_opens_delete_menu_and_confirm_removes_the_entry() {
        runBlocking {
            aktivRepo.save(
                Aktivitet(
                    id = "a1", timestamp = "x", datum = "2026-01-01", tid = "08:00",
                    aktivitet = "Promenad", energy = 3, stress = 2, somatiska = 0, symptom = "",
                ),
            )
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Promenad").performTouchInput { longClick() }
        composeRule.onNodeWithText("Ta bort").performClick()

        composeRule.onNodeWithText("Ta bort post?").assertIsDisplayed()
        composeRule.onNodeWithText("Ta bort").performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun toggling_a_filter_chip_hides_that_type() {
        runBlocking {
            aktivRepo.save(
                Aktivitet(
                    id = "a1", timestamp = "x", datum = "2026-01-01", tid = "08:00",
                    aktivitet = "Promenad", energy = 3, stress = 2, somatiska = 0, symptom = "",
                ),
            )
            medicRepo.saveMedicin(
                Medicin(
                    id = "m1", timestamp = "x", datum = "2026-01-01", tid = "09:00",
                    namn = "Ibuprofen", dos = "400", enhet = "mg", tidpunkt = "Morgon", tagen = true,
                ),
            )
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Aktivitet").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Ibuprofen").assertIsDisplayed()
    }

    // ─── Vy-läge / kalender (HIST-6) ────────────────────────────────────────────

    @Test fun switching_to_calendar_view_hides_the_list_and_shows_the_calendar_empty_state() {
        runBlocking {
            aktivRepo.save(
                Aktivitet(
                    id = "a1", timestamp = "x", datum = "2026-01-01", tid = "08:00",
                    aktivitet = "Promenad", energy = 3, stress = 2, somatiska = 0, symptom = "",
                ),
            )
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Kalender").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Välj en dag i kalendern för att se poster"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Välj en dag i kalendern för att se poster")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }
    }

    @Test fun switching_back_to_list_view_restores_the_list() {
        runBlocking {
            aktivRepo.save(
                Aktivitet(
                    id = "a1", timestamp = "x", datum = "2026-01-01", tid = "08:00",
                    aktivitet = "Promenad", energy = 3, stress = 2, somatiska = 0, symptom = "",
                ),
            )
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Kalender").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Välj en dag i kalendern för att se poster"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Välj en dag i kalendern för att se poster")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Lista").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Promenad")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Promenad").assertIsDisplayed()
    }
}

package se.partee71.dagboken.ui.home

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel
import se.partee71.dagboken.ui.mediciner.MedicinerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var aktivRepo: AktiviteterRepository
    private lateinit var medicRepo: MedicinerRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var sjukdomarRepo: SjukdomarRepository
    private lateinit var vm: HomeViewModel
    private lateinit var screeningVm: AktiviteterViewModel
    private lateinit var medicinerVm: MedicinerViewModel

    private val today get() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                 .allowMainThreadQueries().build()
        aktivRepo = AktiviteterRepository(db.aktivitetDao())
        noteRepo  = NoteRepository(db.noteDao())
        medicRepo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = noteRepo,
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        authRepo      = FirebaseAuthRepository(ctx)
        prefs         = PreferencesRepository(ctx)
        sjukdomarRepo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), Dispatchers.IO)
        runBlocking {
            prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
            prefs.setMedsNotificationsEnabled(false)
        }
        vm          = HomeViewModel(aktivRepo, medicRepo, authRepo, prefs, sjukdomarRepo)
        screeningVm = AktiviteterViewModel(aktivRepo, noteRepo, prefs)
        medicinerVm = MedicinerViewModel(medicRepo, noteRepo, CheckCooldownUseCase(), CheckDailyLimitUseCase())
    }

    @After fun tearDown() {
        runBlocking {
            prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
            prefs.setMedsNotificationsEnabled(false)
        }
        db.close()
    }

    private fun setContent() {
        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onNavigateToSettings    = {},
                    onNavigateToTrender     = {},
                    onNavigateToSjukdomar   = {},
                    onAddAktivitet          = {},
                    onAddMedicin            = {},
                    onAddHandelse           = {},
                    onAddFavorit            = {},
                    onEditFavorit           = {},
                    snackbarHostState       = SnackbarHostState(),
                    vm                      = vm,
                    screeningVm             = screeningVm,
                    medicinerVm             = medicinerVm,
                )
            }
        }
    }

    // ─── Sparkline döljs < 2 punkter ─────────────────────────────────────────

    @Test fun sparkline_fallback_text_shown_when_fewer_than_2_screening_points() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Logga din första screening för att se trender")
            .assertIsDisplayed()
    }

    // ─── Försenat-kort ────────────────────────────────────────────────────────

    @Test fun Forsenat_card_shown_when_screening_notification_time_has_passed() {
        runBlocking {
            prefs.setScreeningEventConfigs(listOf(ScreeningEventConfig(enabled = true, time = "00:01")))
        }
        setContent()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Försenat")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Försenat").assertIsDisplayed()
        composeRule.onNodeWithText("Efter frukost").assertIsDisplayed()
    }

    // ─── Bock markerar tagen ──────────────────────────────────────────────────

    @Test fun toggleMedicinTagen_updates_tagen_count_displayed_in_card() {
        val med = Medicin(
            id        = "test-med-1",
            timestamp = "${today}T00:01:00.000Z",
            datum     = today,
            tid       = "00:01",
            namn      = "Metformin",
            dos       = "500",
            enhet     = "mg",
            tidpunkt  = "Morgon",
            tagen     = false,
        )
        runBlocking { medicRepo.saveMedicin(med) }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Metformin")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Metformin").assertIsDisplayed()

        composeRule.runOnUiThread { vm.toggleMedicinTagen(med) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Metformin")).fetchSemanticsNodes().isEmpty()
        }
    }

    // ─── Hela dagslistan visas, inte bara försenade ──────────────────────────

    @Test fun non_overdue_medicine_is_shown_in_checklist() {
        val med = Medicin(
            id = "future-med", timestamp = "${today}T23:00:00.000Z", datum = today, tid = "23:00",
            namn = "Vitamin D", dos = "1", enhet = "tablett", tidpunkt = "Kväll", tagen = false,
        )
        runBlocking { medicRepo.saveMedicin(med) }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Vitamin D")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Vitamin D").assertIsDisplayed()
    }

    // ─── Inline screening-loggning ────────────────────────────────────────────

    @Test fun tapping_screening_row_expands_inline_form_and_save_logs_it() {
        runBlocking { prefs.setScreeningEventConfigs(listOf(ScreeningEventConfig(enabled = true, time = "08:00"))) }
        setContent()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Efter frukost")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Efter frukost").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Spara")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Spara").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Loggad")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Loggad").assertIsDisplayed()
    }

    // ─── Global FAB ───────────────────────────────────────────────────────────

    @Test fun fab_opens_menu_with_four_quick_add_options() {
        setContent()
        composeRule.onNodeWithContentDescription("Lägg till").performClick()
        composeRule.onNodeWithText("Logga aktivitet").assertIsDisplayed()
        composeRule.onNodeWithText("Logga engångsdos").assertIsDisplayed()
        composeRule.onNodeWithText("Ny vid behov-favorit").assertIsDisplayed()
        composeRule.onNodeWithText("Ny händelse").assertIsDisplayed()
    }

    // ─── Vid behov — snabbdosering ────────────────────────────────────────────

    @Test fun favorite_marked_favorit_is_shown_as_quick_dose_chip() {
        runBlocking {
            medicRepo.saveFavorit(
                Favorit(
                    id = "fav1", namn = "Paracetamol", dos = "500", enhet = "mg",
                    tidpunkt = "Vid behov", minTidMellan = 0, isFavorite = true,
                )
            )
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").assertIsDisplayed()
    }

    @Test fun tapping_favorit_chip_logs_a_dose() {
        runBlocking {
            medicRepo.saveFavorit(
                Favorit(
                    id = "fav1", namn = "Ipren", dos = "400", enhet = "mg",
                    tidpunkt = "Vid behov", minTidMellan = 0, isFavorite = true,
                )
            )
        }
        setContent()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("Ipren")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ipren").performClick()
        composeRule.waitUntil(10_000) {
            runBlocking { medicRepo.allMediciner.first().any { it.namn == "Ipren" && it.tagen } }
        }
    }
}

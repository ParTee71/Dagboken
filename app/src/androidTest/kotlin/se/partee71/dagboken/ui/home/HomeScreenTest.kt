package se.partee71.dagboken.ui.home

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.auth.FirebaseAuthRepository
import se.partee71.dagboken.data.datastore.DEFAULT_SCREENING_EVENTS
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.datastore.ScreeningEventConfig
import se.partee71.dagboken.data.repository.AktiviteterRepository
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var aktivRepo: AktiviteterRepository
    private lateinit var medicRepo: MedicinerRepository
    private lateinit var authRepo: FirebaseAuthRepository
    private lateinit var prefs: PreferencesRepository
    private lateinit var vm: HomeViewModel

    private val today get() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

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
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
        )
        authRepo = FirebaseAuthRepository(ctx)
        prefs    = PreferencesRepository(ctx)
        runBlocking {
            prefs.setScreeningEventConfigs(DEFAULT_SCREENING_EVENTS)
            prefs.setMedsNotificationsEnabled(false)
        }
        vm = HomeViewModel(aktivRepo, medicRepo, authRepo, prefs)
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
                    onNavigateToAktiviteter = {},
                    onNavigateToMediciner   = {},
                    onNavigateToSettings    = {},
                    onNavigateToDiagram     = {},
                    snackbarHostState       = SnackbarHostState(),
                    vm                      = vm,
                )
            }
        }
    }

    // ─── Sparkline döljs < 2 punkter ─────────────────────────────────────────

    @Test fun `sparkline fallback text shown when fewer than 2 screening points`() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Logga din första screening för att se trender")
            .assertIsDisplayed()
    }

    // ─── Försenat-kort ────────────────────────────────────────────────────────

    @Test fun `Forsenat card shown when screening notification time has passed`() {
        runBlocking {
            prefs.setScreeningEventConfigs(listOf(ScreeningEventConfig(enabled = true, time = "00:01")))
        }
        setContent()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Försenat")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Försenat").assertIsDisplayed()
        composeRule.onNodeWithText("Daglig screening").assertIsDisplayed()
    }

    // ─── Bock markerar tagen ──────────────────────────────────────────────────

    @Test fun `toggleMedicinTagen updates tagen count displayed in card`() {
        val med = Medicin(
            id        = "test-med-1",
            timestamp = "${today}T07:00:00.000Z",
            datum     = today,
            tid       = "07:00",
            namn      = "Metformin",
            dos       = "500",
            enhet     = "mg",
            tidpunkt  = "Morgon",
            tagen     = false,
            anteckning = "",
        )
        runBlocking { medicRepo.saveMedicin(med) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("0 av 1 tagna")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("0 av 1 tagna").assertIsDisplayed()

        vm.toggleMedicinTagen(med)
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("1 av 1 tagna")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("1 av 1 tagna").assertIsDisplayed()
    }
}

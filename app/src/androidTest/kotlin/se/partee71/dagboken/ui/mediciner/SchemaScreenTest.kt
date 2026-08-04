package se.partee71.dagboken.ui.mediciner

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Recept
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.domain.usecase.LogVidBehovDosUseCase
import se.partee71.dagboken.util.retryOnRenderGlitch
import java.time.LocalDate

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class SchemaScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: MedicinerRepository
    private lateinit var vm: MedicinerViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = NoteRepository(db.noteDao()),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        val noteRepo = NoteRepository(db.noteDao())
        vm = MedicinerViewModel(
            repo, noteRepo,
            LogVidBehovDosUseCase(repo, noteRepo, CheckCooldownUseCase(), CheckDailyLimitUseCase()),
        )
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        // Stop the ViewModel's Room-flow collectors (WhileSubscribed(5000))
        // before closing the DB, or they query the closed in-memory DB and throw
        // "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent(onBack: () -> Unit = {}, onAddRecept: () -> Unit = {}) {
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    SchemaScreen(onBack = onBack, onAddRecept = onAddRecept, onEditRecept = {}, vm = vm)
                }
            }
        }
    }

    @Test fun empty_state_shown_when_no_recept() = retryOnRenderGlitch {
        setUp()
        try {
            setContent()
            composeRule.onNodeWithText("Recept & scheman").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun existing_recept_is_listed() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.saveRecept(
                    Recept(
                        id = "r1", namn = "Levaxin", dos = "50", enhet = "mcg",
                        tidpunkter = listOf("Morgon"), upprepning = "dagligen", dagar = emptyList(),
                        aktiv = true, skapad = "2026-01-01",
                    )
                )
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Levaxin")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Levaxin").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun ended_recept_shows_its_end_date_as_avslutat() = retryOnRenderGlitch {
        setUp()
        try {
            val slut = LocalDate.now().minusDays(2)
            runBlocking {
                repo.saveRecept(
                    Recept(
                        id = "r1", namn = "Amoxicillin", dos = "500", enhet = "mg",
                        tidpunkter = listOf("Morgon"), upprepning = "dagligen", dagar = emptyList(),
                        aktiv = false, skapad = slut.minusDays(9).toString(),
                        startDatum = slut.minusDays(9).toString(), slutDatum = slut.toString(),
                    )
                )
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Avslutat", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasText("Avslutat", substring = true)).assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun running_period_is_shown_on_the_recept_card() = retryOnRenderGlitch {
        setUp()
        try {
            val start = LocalDate.now()
            val slut  = start.plusDays(9)
            runBlocking {
                repo.saveRecept(
                    Recept(
                        id = "r2", namn = "Prednisolon", dos = "5", enhet = "mg",
                        tidpunkter = listOf("Morgon"), upprepning = "dagligen", dagar = emptyList(),
                        aktiv = true, skapad = start.toString(),
                        startDatum = start.toString(), slutDatum = slut.toString(),
                    )
                )
            }
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Prednisolon")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasText(" – ", substring = true)).assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun fab_invokes_onAddRecept() = retryOnRenderGlitch {
        setUp()
        try {
            var added = false
            setContent(onAddRecept = { added = true })
            composeRule.onNodeWithContentDescription("Ny").performClick()
            assert(added) { "Expected onAddRecept to be invoked" }
        } finally {
            tearDown()
        }
    }

    @Test fun back_button_invokes_onBack() = retryOnRenderGlitch {
        setUp()
        try {
            var backCalled = false
            setContent(onBack = { backCalled = true })
            composeRule.onNodeWithContentDescription("Tillbaka").performClick()
            assert(backCalled) { "Expected onBack to be invoked" }
        } finally {
            tearDown()
        }
    }
}

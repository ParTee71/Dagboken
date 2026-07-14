package se.partee71.dagboken.ui.mediciner

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
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

@RunWith(AndroidJUnit4::class)
class SchemaScreenTest {

    val composeRule = createComposeRule()

    // Retry outermost so a swiftshader render-glitch flake re-runs with a
    // fresh @Before/@After lifecycle instead of failing the build.
    @get:Rule
    val flakyRetry: org.junit.rules.RuleChain =
        org.junit.rules.RuleChain
            .outerRule(se.partee71.dagboken.util.RetryTestRule())
            .around(composeRule)

    private lateinit var db: AppDatabase
    private lateinit var repo: MedicinerRepository
    private lateinit var vm: MedicinerViewModel

    @Before fun setUp() {
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
        vm = MedicinerViewModel(repo, NoteRepository(db.noteDao()), CheckCooldownUseCase(), CheckDailyLimitUseCase())
    }

    @After fun tearDown() {
        // Stop the ViewModel's Room-flow collectors (WhileSubscribed(5000))
        // before closing the DB, or they query the closed in-memory DB and throw
        // "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        db.close()
    }

    private fun setContent(onBack: () -> Unit = {}, onAddRecept: () -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                SchemaScreen(onBack = onBack, onAddRecept = onAddRecept, onEditRecept = {}, vm = vm)
            }
        }
    }

    @Test fun empty_state_shown_when_no_recept() {
        setContent()
        composeRule.onNodeWithText("Recept & scheman").assertIsDisplayed()
    }

    @Test fun existing_recept_is_listed() {
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
    }

    @Test fun fab_invokes_onAddRecept() {
        var added = false
        setContent(onAddRecept = { added = true })
        composeRule.onNodeWithContentDescription("Ny").performClick()
        assert(added) { "Expected onAddRecept to be invoked" }
    }

    @Test fun back_button_invokes_onBack() {
        var backCalled = false
        setContent(onBack = { backCalled = true })
        composeRule.onNodeWithContentDescription("Tillbaka").performClick()
        assert(backCalled) { "Expected onBack to be invoked" }
    }
}

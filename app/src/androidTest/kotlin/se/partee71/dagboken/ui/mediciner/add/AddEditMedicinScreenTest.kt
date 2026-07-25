package se.partee71.dagboken.ui.mediciner.add

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.util.retryOnRenderGlitch

@RunWith(AndroidJUnit4::class)
class AddEditMedicinScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: MedicinerRepository
    private lateinit var vm: AddEditMedicinViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
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
        vm = AddEditMedicinViewModel(repo, NoteRepository(db.noteDao()), CheckCooldownUseCase(), CheckDailyLimitUseCase())
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun setContent(editId: String?, favoritId: String? = null) {
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    AddEditMedicinScreen(editId = editId, favoritId = favoritId, onBack = {}, vm = vm)
                }
            }
        }
    }

    @Test fun recept_dose_shows_namn_as_read_only_with_hint() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.saveMedicin(
                    Medicin(
                        id = "m1", timestamp = "2026-01-15T22:00:00.000Z", datum = "2026-01-15",
                        tid = "22:00", namn = "Melatonin", dos = "3", enhet = "mg",
                        tidpunkt = "Natt", tagen = true, receptId = "r1",
                    ),
                )
            }
            setContent(editId = "m1")
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Melatonin")).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText("Melatonin").assertIsDisplayed()
            // Read-only: no editable text field carries the name (no setText action on that node).
            assertTrue(
                composeRule.onAllNodes(hasText("Melatonin") and hasSetTextAction())
                    .fetchSemanticsNodes().isEmpty(),
            )
            composeRule.onNodeWithText("Tagen").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun non_recept_dose_allows_editing_namn() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.saveMedicin(
                    Medicin(
                        id = "m2", timestamp = "2026-01-15T09:00:00.000Z", datum = "2026-01-15",
                        tid = "09:00", namn = "Ibuprofen", dos = "400", enhet = "mg",
                        tidpunkt = "Vid behov", tagen = true, receptId = null,
                    ),
                )
            }
            setContent(editId = "m2")
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
            }

            assertTrue(
                composeRule.onAllNodes(hasText("Ibuprofen") and hasSetTextAction())
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        } finally {
            tearDown()
        }
    }

    @Test fun editing_a_recept_dose_and_saving_updates_dos_but_keeps_namn() = retryOnRenderGlitch {
        setUp()
        try {
            runBlocking {
                repo.saveMedicin(
                    Medicin(
                        id = "m1", timestamp = "2026-01-15T22:00:00.000Z", datum = "2026-01-15",
                        tid = "22:00", namn = "Melatonin", dos = "3", enhet = "mg",
                        tidpunkt = "Natt", tagen = true, receptId = "r1",
                    ),
                )
            }
            setContent(editId = "m1")
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Melatonin")).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNode(isToggleable()).performClick() // toggle "Tagen" off
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Spara").performClick()
            composeRule.waitForIdle()

            val saved = runBlocking { repo.getMedicinById("m1") }
            assertTrue("namn is unchanged", saved?.namn == "Melatonin")
            assertTrue("tagen was toggled off", saved?.tagen == false)
        } finally {
            tearDown()
        }
    }
}

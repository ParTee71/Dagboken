package se.partee71.dagboken.ui.mediciner

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import org.junit.Assert.assertEquals
import org.junit.Before
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

@RunWith(AndroidJUnit4::class)
class HistorikTabTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: MedicinerRepository
    private lateinit var vm: MedicinerViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db   = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                   .allowMainThreadQueries().build()
        repo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        vm = MedicinerViewModel(repo, NoteRepository(db.noteDao()), CheckCooldownUseCase(), CheckDailyLimitUseCase())
    }

    @After fun tearDown() {
        composeRule.waitForIdle()
        vm.viewModelScope.cancel()
        db.close()
    }

    private fun medicin(
        id: String,
        namn: String,
        receptId: String? = null,
        datum: String = "2026-01-15",
        tid: String = "09:00",
    ) = Medicin(
        id = id, timestamp = "${datum}T$tid:00.000Z", datum = datum, tid = tid,
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = true, anteckning = "", receptId = receptId,
    )

    private fun setContent() {
        composeRule.setContent { MaterialTheme { HistorikTab(vm = vm, onEdit = {}) } }
    }

    // ─── Tomt tillstånd ───────────────────────────────────────────────────────

    @Test fun empty_state_shows_placeholder_text() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Inga mediciner loggade").assertIsDisplayed()
    }

    // ─── Visar poster ─────────────────────────────────────────────────────────

    @Test fun shows_entries_from_repository() {
        runBlocking { repo.saveMedicin(medicin("m1", "Ibuprofen")) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ibuprofen").assertIsDisplayed()
    }

    // ─── Filterchips ──────────────────────────────────────────────────────────

    @Test fun deselecting_Recept_chip_hides_scheduled_entries() {
        runBlocking {
            repo.saveMedicin(medicin("m1", "Ibuprofen", receptId = "r1"))
            repo.saveMedicin(medicin("m2", "Paracetamol", receptId = null))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ibuprofen").assertIsDisplayed()
        composeRule.onNodeWithText("Paracetamol").assertIsDisplayed()

        composeRule.onNodeWithText("Recept").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ibuprofen").assertIsNotDisplayed()
        composeRule.onNodeWithText("Paracetamol").assertIsDisplayed()
    }

    @Test fun Vid_behov_chip_stays_selected_when_it_is_the_only_remaining_filter() {
        runBlocking { repo.saveMedicin(medicin("m1", "Alvedon", receptId = null)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Alvedon")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Recept").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Vid behov").performClick()
        composeRule.waitForIdle()

        assertEquals(setOf("vid_behov"), vm.historyFilter.value)
        composeRule.onNodeWithText("Vid behov").assertIsSelected()
    }

    // ─── Redigera ─────────────────────────────────────────────────────────────

    @Test fun clicking_edit_in_menu_invokes_onEdit_with_id() {
        runBlocking { repo.saveMedicin(medicin("m1", "Alvedon")) }
        var editedId: String? = null
        composeRule.setContent {
            MaterialTheme { HistorikTab(vm = vm, onEdit = { editedId = it }) }
        }
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Alvedon")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Alternativ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Redigera").performClick()
        composeRule.waitForIdle()

        assertEquals("m1", editedId)
    }

    // ─── Ta bort ──────────────────────────────────────────────────────────────

    @Test fun deleting_a_dose_without_receptId_removes_it_after_confirmation() {
        runBlocking { repo.saveMedicin(medicin("m1", "Alvedon", receptId = null)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Alvedon")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Alternativ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort?").assertIsDisplayed()

        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitUntil(3000) {
            runBlocking { repo.getMedicinById("m1") } == null
        }
        assertEquals(null, runBlocking { repo.getMedicinById("m1") })
    }

    @Test fun deleting_a_scheduled_dose_marks_it_as_skipped_instead_of_removing_it() {
        runBlocking { repo.saveMedicin(medicin("m1", "Ibuprofen", receptId = "r1")) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Alternativ").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hoppa över dos?").assertIsDisplayed()

        composeRule.onNodeWithText("Hoppa över").performClick()
        composeRule.waitUntil(3000) {
            runBlocking { repo.getMedicinById("m1") }?.skipped == true
        }
        val stillThere = runBlocking { repo.getMedicinById("m1") }
        assertEquals(true, stillThere?.skipped)
    }
}

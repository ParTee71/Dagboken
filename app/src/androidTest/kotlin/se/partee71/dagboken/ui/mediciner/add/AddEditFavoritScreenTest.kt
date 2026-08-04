package se.partee71.dagboken.ui.mediciner.add

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.R
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class AddEditFavoritScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: AddEditFavoritViewModel
    private lateinit var ctx: Context
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            noteRepo           = NoteRepository(db.noteDao()),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        vm = AddEditFavoritViewModel(repo, NoteRepository(db.noteDao()))

        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    AddEditFavoritScreen(editId = null, onBack = {}, vm = vm)
                }
            }
        }
    }

    private fun tearDown() {
        // Cancel any in-flight viewModelScope coroutine before closing the DB,
        // so it can't query the closed in-memory DB and throw
        // "attempt to re-open an already-closed SQLiteDatabase".
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    @Test fun cooldown_and_max_per_day_sliders_show_default_values_and_update_on_tap() = retryOnRenderGlitch {
        setUp()
        try {
            // Default form: minTidMellan = 4h, maxDoserPerDag = 0 (unlimited)
            composeRule.onNodeWithText("4 tim").assertIsDisplayed()
            composeRule.onNodeWithText("Obegränsat").assertIsDisplayed()

            // Tap "+" on the second slider (max per day) to bump 0 -> 1
            composeRule.onAllNodesWithContentDescription(ctx.getString(R.string.increase))[1]
                .performClick()

            composeRule.onNodeWithText("1 ggr").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun note_field_is_shown_and_persists_on_save() = retryOnRenderGlitch {
        setUp()
        try {
            composeRule.onNode(hasText("Namn") and hasSetTextAction()).performTextInput("Paracetamol")
            composeRule.onNode(hasText("Dos") and hasSetTextAction()).performTextInput("500")

            composeRule.onNodeWithText("Lägg till en anteckning…").assertIsDisplayed()
            composeRule.onNodeWithText("Lägg till en anteckning…").performClick()
            composeRule.onNode(hasText("Lägg till en anteckning…") and hasSetTextAction())
                .performTextInput("Max 3/dag")

            composeRule.onNodeWithText("Spara").performClick()
            composeRule.waitForIdle()

            val notes = runBlocking { db.noteDao().getAll() }
            assertEquals("FAVORIT", notes.single().target)
            assertEquals("Max 3/dag", notes.single().text)
        } finally {
            tearDown()
        }
    }
}

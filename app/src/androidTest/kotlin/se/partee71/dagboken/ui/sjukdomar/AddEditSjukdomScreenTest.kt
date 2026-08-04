package se.partee71.dagboken.ui.sjukdomar

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.di.dagbokenJson
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Skärmen var otestad. Testet täcker särskilt att sparandet är klart innan skärmen
 * stängs — spara-och-navigera-tillbaka är annars den väg där en avbruten skrivning
 * går obemärkt förbi (NFR-12).
 *
 * Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
 */
@RunWith(AndroidJUnit4::class)
class AddEditSjukdomScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: SjukdomarRepository
    private lateinit var vm: AddEditSjukdomViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val notes = NoteRepository(db.noteDao())
        repo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), notes)
        vm = AddEditSjukdomViewModel(repo, notes, PreferencesRepository(ctx, dagbokenJson()))
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun show(backCount: IntArray = IntArray(1)) {
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    AddEditSjukdomScreen(editId = null, onBack = { backCount[0]++ }, vm = vm)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `save is disabled until a type has been entered`() = retryOnRenderGlitch {
        setUp()
        try {
            show()
            composeRule.onNodeWithText("Ny sjukdomsepisod").assertIsDisplayed()
            composeRule.onNodeWithText("Spara").assertIsNotEnabled()

            composeRule.onNodeWithText("Typ av sjukdom").performTextInput("Förkylning")
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Spara").assertIsEnabled()
        } finally {
            tearDown()
        }
    }

    /** Regressionsskydd för NFR-12: posten ska finnas i databasen när skärmen stängs. */
    @Test fun `episode is persisted before the screen navigates back`() = retryOnRenderGlitch {
        setUp()
        try {
            val backCount = IntArray(1)
            show(backCount)

            composeRule.onNodeWithText("Typ av sjukdom").performTextInput("Influensa")
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Spara").performClick()
            composeRule.waitForIdle()

            assertEquals(1, backCount[0])
            val saved = runBlocking { repo.all.first() }
            assertEquals(1, saved.size)
            assertEquals("Influensa", saved.single().typ)
        } finally {
            tearDown()
        }
    }

    @Test fun `leaving without changes does not create an episode`() = retryOnRenderGlitch {
        setUp()
        try {
            show()
            composeRule.waitForIdle()

            assertEquals(0, runBlocking { repo.all.first() }.size)
        } finally {
            tearDown()
        }
    }
}

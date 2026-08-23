package se.partee71.dagboken.ui.sjukdomar

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
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
import se.partee71.dagboken.data.datastore.PreferencesRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.repository.SjukdomarRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.di.dagbokenJson
import se.partee71.dagboken.domain.model.SjukdomsEpisod
import se.partee71.dagboken.domain.model.SjukdomsIncheckning
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Detaljskärmen fick redigerbara kort i #192: både episodkortet och incheckningskorten
 * öppnas för redigering vid tryck, och följer kortstandarden i övrigt (NFR-15/NFR-16).
 *
 * Samma testupplägg som [SjukdomarScreenTest] — se den för förklaring (#112).
 */
@RunWith(AndroidJUnit4::class)
class SjukdomsEpisodDetailScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: SjukdomarRepository
    private lateinit var noteRepo: NoteRepository
    private lateinit var vm: SjukdomsEpisodViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        noteRepo = NoteRepository(db.noteDao())
        repo = SjukdomarRepository(db.sjukdomsEpisodDao(), db.sjukdomsIncheckningDao(), noteRepo)
        vm = SjukdomsEpisodViewModel(
            SavedStateHandle(mapOf("episodId" to "e1")),
            repo,
            noteRepo,
            PreferencesRepository(ctx, dagbokenJson()),
        )
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
    }

    private fun tearDown() {
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    private fun seed(medIncheckning: Boolean = true) = runBlocking {
        repo.saveEpisod(SjukdomsEpisod(id = "e1", typ = "Migrän", startDatum = "2026-01-10", slutDatum = ""))
        if (medIncheckning) {
            repo.saveIncheckning(
                SjukdomsIncheckning(
                    id = "i1", episodId = "e1", datum = "2026-01-10", tid = "10:00",
                    svarighetsgrad = 7, symptom = "Huvudvärk:3", somatiska = 3,
                ),
            )
        }
    }

    private fun setContent(
        onEditEpisod: (String) -> Unit = {},
        onEditIncheckning: (String, String) -> Unit = { _, _ -> },
    ) {
        scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    SjukdomsEpisodDetailScreen(
                        onBack            = {},
                        onAddIncheckning  = {},
                        onEditEpisod      = onEditEpisod,
                        onEditIncheckning = onEditIncheckning,
                        snackbarHostState = SnackbarHostState(),
                        vm                = vm,
                    )
                }
            }
        }
    }

    /** Långsamt svep förbi den positionella tröskeln — se DagbokenEntryCardTest. */
    private fun TouchInjectionScope.slowSwipeLeft() {
        down(centerRight)
        var x = right
        repeat(10) {
            advanceEventTime(16)
            x -= width / 12f
            moveTo(Offset(x, center.y))
        }
        advanceEventTime(16)
        up()
    }

    private fun awaitIncheckning() = composeRule.waitUntil(20_000) {
        composeRule.onAllNodes(hasText("Huvudvärk: 3")).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun tapping_an_incheckning_opens_it_for_editing() = retryOnRenderGlitch {
        setUp()
        try {
            seed()
            var edited: Pair<String, String>? = null
            setContent(onEditIncheckning = { episodId, id -> edited = episodId to id })
            awaitIncheckning()

            composeRule.onNodeWithText("Huvudvärk: 3").performClick()
            assertEquals("e1" to "i1", edited)
        } finally {
            tearDown()
        }
    }

    @Test fun incheckning_menu_offers_edit_and_delete() = retryOnRenderGlitch {
        setUp()
        try {
            seed()
            var edited: Pair<String, String>? = null
            setContent(onEditIncheckning = { episodId, id -> edited = episodId to id })
            awaitIncheckning()

            // Episodkortet har också en ⋮; incheckningens är den andra i trädet.
            composeRule.onAllNodesWithContentDescription("Alternativ")[1].performClick()
            composeRule.onNodeWithText("Ta bort").assertIsDisplayed()
            composeRule.onNodeWithText("Redigera").performClick()
            assertEquals("e1" to "i1", edited)
        } finally {
            tearDown()
        }
    }

    @Test fun swiping_an_incheckning_asks_for_confirmation_and_cancel_keeps_it() = retryOnRenderGlitch {
        setUp()
        try {
            seed()
            setContent()
            awaitIncheckning()

            composeRule.onNodeWithText("Huvudvärk: 3").performTouchInput { slowSwipeLeft() }
            composeRule.onNodeWithText("Ta bort incheckning").assertIsDisplayed()
            composeRule.onNodeWithText("Avbryt").performClick()

            composeRule.onNodeWithText("Huvudvärk: 3").assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun swiping_an_incheckning_and_confirming_removes_it() = retryOnRenderGlitch {
        setUp()
        try {
            seed()
            setContent()
            awaitIncheckning()

            composeRule.onNodeWithText("Huvudvärk: 3").performTouchInput { slowSwipeLeft() }
            composeRule.onNodeWithText("Ta bort incheckning").assertIsDisplayed()
            composeRule.onNodeWithText("Ta bort").performClick()

            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Huvudvärk: 3")).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            tearDown()
        }
    }

    @Test fun tapping_the_episod_card_opens_it_for_editing() = retryOnRenderGlitch {
        setUp()
        try {
            seed(medIncheckning = false)
            var editedId: String? = null
            setContent(onEditEpisod = { id -> editedId = id })
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Migrän")).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText("Migrän").performClick()
            assertEquals("e1", editedId)
        } finally {
            tearDown()
        }
    }

    @Test fun the_episod_card_has_no_delete_in_its_menu() = retryOnRenderGlitch {
        setUp()
        try {
            seed(medIncheckning = false)
            setContent()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("Migrän")).fetchSemanticsNodes().isNotEmpty()
            }

            // Episoden raderas från listan (SjukdomarScreen), inte inifrån sin egen detaljvy.
            composeRule.onNodeWithContentDescription("Alternativ").performClick()
            composeRule.onNodeWithText("Redigera").assertIsDisplayed()
            composeRule.onNodeWithText("Ta bort").assertDoesNotExist()
        } finally {
            tearDown()
        }
    }
}

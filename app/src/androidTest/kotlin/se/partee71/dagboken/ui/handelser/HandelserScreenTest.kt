package se.partee71.dagboken.ui.handelser

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.HandelserRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Handelse

@RunWith(AndroidJUnit4::class)
class HandelserScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: HandelserRepository
    private lateinit var vm: HandelserViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db   = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                   .allowMainThreadQueries().build()
        repo = HandelserRepository(db.handelseDao())
        vm   = HandelserViewModel(repo)
    }

    @After fun tearDown() { db.close() }

    private fun handelse(
        id: String,
        typ: String = "Yrsel",
        datum: String = "2026-06-21",
        tid: String = "10:00",
        svarighetsgrad: Int = 5,
    ) = Handelse(
        id = id, timestamp = "${datum}T${tid}:00.000Z",
        datum = datum, tid = tid, typ = typ,
        svarighetsgrad = svarighetsgrad, varaktighetMinuter = 0,
        triggers = "", atgarder = "", anteckning = "",
    )

    private fun setContent(
        onAddNew: () -> Unit = {},
        onEdit: (String) -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                HandelserScreen(
                    onAddNew             = onAddNew,
                    onEdit               = onEdit,
                    onNavigateToSettings = onNavigateToSettings,
                    snackbarHostState    = SnackbarHostState(),
                    vm                   = vm,
                )
            }
        }
    }

    // Event type text appears twice when events exist: once in the card (non-clickable Text)
    // and once in the TypFilterRow chip (clickable FilterChip). Targeting the card specifically
    // requires `hasClickAction().not()` to exclude the chip.
    private fun cardText(typ: String) = hasText(typ) and hasClickAction().not()

    // ─── empty state ─────────────────────────────────────────────────────────

    @Test fun `empty state shows placeholder text`() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Inga händelser loggade ännu.", substring = true)
            .assertIsDisplayed()
    }

    // ─── date filter chips ────────────────────────────────────────────────────

    @Test fun `all date filter chips are displayed`() {
        setContent()
        composeRule.waitForIdle()
        listOf("Allt", "7 dagar", "30 dagar", "90 dagar").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test fun `Allt chip is selected by default`() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Allt").assertIsSelected()
    }

    @Test fun `clicking a day filter chip selects it`() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("30 dagar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("30 dagar").assertIsSelected()
    }

    // ─── event card content ───────────────────────────────────────────────────

    @Test fun `saved event shows its typ in the card`() {
        runBlocking { repo.save(handelse(id = "h1", typ = "Hjärtklappning")) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(cardText("Hjärtklappning")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(cardText("Hjärtklappning")).assertIsDisplayed()
    }

    @Test fun `saved event shows severity chip with correct value`() {
        runBlocking { repo.save(handelse(id = "h1", svarighetsgrad = 7)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("7/10")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("7/10").assertIsDisplayed()
    }

    @Test fun `multiple saved events are all displayed`() {
        runBlocking {
            repo.save(handelse(id = "h1", typ = "Yrsel"))
            repo.save(handelse(id = "h2", typ = "Blodtrycksfall"))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(cardText("Yrsel")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(cardText("Yrsel")).assertIsDisplayed()
        composeRule.onNode(cardText("Blodtrycksfall")).assertIsDisplayed()
    }

    // ─── type filter row ──────────────────────────────────────────────────────

    @Test fun `type filter row shows Alla typer chip when events exist`() {
        runBlocking { repo.save(handelse(id = "h1", typ = "Yrsel")) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Alla typer")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Alla typer").assertIsDisplayed()
    }

    @Test fun `type filter row does not appear when no events exist`() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Alla typer").assertDoesNotExist()
    }

    // ─── FAB and navigation callbacks ─────────────────────────────────────────

    @Test fun `tapping the FAB invokes onAddNew`() {
        var called = false
        setContent(onAddNew = { called = true })
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Ny händelse").performClick()
        assertTrue(called)
    }

    @Test fun `tapping the settings icon invokes onNavigateToSettings`() {
        var called = false
        setContent(onNavigateToSettings = { called = true })
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Inställningar").performClick()
        assertTrue(called)
    }

    // ─── delete via ViewModel ─────────────────────────────────────────────────

    @Test fun `deleting an event removes it from the list`() {
        val h = handelse(id = "h1", typ = "Andnöd")
        runBlocking { repo.save(h) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Andnöd")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnUiThread { vm.delete(h) }

        // Both the card and the type chip for "Andnöd" must be gone after delete
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Andnöd")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Inga händelser loggade ännu.", substring = true)
            .assertIsDisplayed()
    }

    @Test fun `deleting one event leaves others visible`() {
        val keep   = handelse(id = "h1", typ = "Yrsel")
        val remove = handelse(id = "h2", typ = "Blodtrycksfall")
        runBlocking { repo.save(keep); repo.save(remove) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Blodtrycksfall")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnUiThread { vm.delete(remove) }

        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Blodtrycksfall")).fetchSemanticsNodes().isEmpty()
        }
        // "Yrsel" still appears in both the card and the type chip — target the card
        composeRule.onNode(cardText("Yrsel")).assertIsDisplayed()
    }
}

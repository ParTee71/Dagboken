package se.partee71.dagboken.ui.mediciner

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.usecase.CheckCooldownUseCase
import se.partee71.dagboken.domain.usecase.CheckDailyLimitUseCase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class IdagTabTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: MedicinerRepository
    private lateinit var vm: MedicinerViewModel

    private val today get() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
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

    @After fun tearDown() { vm.viewModelScope.cancel(); db.close() }

    private fun medicin(
        id: String = "m1",
        namn: String = "Ibuprofen",
        tagen: Boolean = false,
    ) = Medicin(
        id = id, timestamp = java.time.Instant.now().toString(),
        datum = today, tid = "07:00",
        namn = namn, dos = "400", enhet = "mg", tidpunkt = "Morgon",
        tagen = tagen, anteckning = "",
    )

    private fun favorit(
        id: String = "fav1",
        namn: String = "Paracetamol",
        minTidMellan: Int = 0,
        maxDoserPerDag: Int = 0,
        isFavorite: Boolean = true,
    ) = Favorit(
        id = id, namn = namn, dos = "500", enhet = "mg",
        tidpunkt = "Vid behov", anteckning = "",
        minTidMellan = minTidMellan, maxDoserPerDag = maxDoserPerDag,
        isFavorite = isFavorite,
    )

    private fun setContent(onEdit: (String) -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                val snackbarState = remember { SnackbarHostState() }
                val snackMsg by vm.snackbar.collectAsState()
                LaunchedEffect(snackMsg) {
                    snackMsg?.let { snackbarState.showSnackbar(it); vm.clearSnackbar() }
                }
                Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        IdagTab(vm = vm, onEdit = onEdit)
                    }
                }
            }
        }
    }

    // ─── Del 1 — dölj tagna ──────────────────────────────────────────────────

    @Test fun taken_med_is_hidden_after_icon_tap() {
        runBlocking { repo.saveMedicin(medicin()) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Markera Ibuprofen som tagen").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ibuprofen").assertDoesNotExist()
    }

    @Test fun visa_tagna_button_shows_count_of_hidden_meds() {
        runBlocking { repo.saveMedicin(medicin()) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Markera Ibuprofen som tagen").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Visa tagna (1)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Visa tagna (1)").assertIsDisplayed()
    }

    @Test fun visa_tagna_reveals_taken_meds_in_list() {
        runBlocking { repo.saveMedicin(medicin()) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Markera Ibuprofen som tagen").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Visa tagna (1)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Visa tagna (1)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ibuprofen").assertIsDisplayed()
    }

    @Test fun all_taken_shows_completion_state() {
        runBlocking { repo.saveMedicin(medicin()) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Markera Ibuprofen som tagen").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Alla medicinerna för idag är tagna ✓"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Alla medicinerna för idag är tagna ✓").assertIsDisplayed()
    }

    // ─── Del 2 — animerad ikonknapp ──────────────────────────────────────────

    @Test fun RadioButtonUnchecked_icon_shown_for_untaken_med() {
        runBlocking { repo.saveMedicin(medicin(tagen = false)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Markera Ibuprofen som tagen").assertIsDisplayed()
    }

    @Test fun CheckCircle_icon_shown_for_taken_med() {
        runBlocking {
            repo.saveMedicin(medicin(id = "m1", namn = "Ibuprofen", tagen = false))
            repo.saveMedicin(medicin(id = "m2", namn = "Aspirin", tagen = false))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Markera Ibuprofen som tagen").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Visa tagna (1)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Visa tagna (1)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Markera Ibuprofen som ej tagen").assertIsDisplayed()
    }

    // ─── Del 3 — favoritsnabbval ─────────────────────────────────────────────

    @Test fun favourites_row_visible_when_allFavoriter_non_empty() {
        runBlocking {
            repo.saveMedicin(medicin())
            repo.saveFavorit(favorit())
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Vid behov")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Vid behov").assertIsDisplayed()
        composeRule.onNodeWithText("Paracetamol").assertIsDisplayed()
    }

    @Test fun favourites_row_hidden_when_allFavoriter_empty() {
        runBlocking { repo.saveMedicin(medicin()) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Vid behov").assertDoesNotExist()
    }

    @Test fun favourites_row_hidden_when_favoriter_are_not_favorite_marked() {
        runBlocking {
            repo.saveMedicin(medicin())
            repo.saveFavorit(favorit(isFavorite = false))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Vid behov").assertDoesNotExist()
        composeRule.onNodeWithText("Paracetamol").assertDoesNotExist()
    }

    @Test fun tapping_favourite_card_triggers_quickDos() {
        runBlocking {
            repo.saveMedicin(medicin())
            repo.saveFavorit(favorit())
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").performClick()
        composeRule.waitUntil(3000) { vm.snackbar.value != null }
        assertEquals("Paracetamol 500 mg loggad", vm.snackbar.value)
    }

    @Test fun cooldown_dialog_shown_when_cooldown_active_in_IdagTab() {
        runBlocking {
            repo.saveMedicin(medicin())
            repo.saveFavorit(favorit(id = "fav2", namn = "Tramadol", minTidMellan = 6))
            repo.saveMedicin(
                Medicin(
                    id = "dose1", timestamp = java.time.Instant.now().toString(),
                    datum = today, tid = "10:00",
                    namn = "Tramadol", dos = "500", enhet = "mg", tidpunkt = "Vid behov",
                    tagen = true, anteckning = "",
                )
            )
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Tramadol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tramadol").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("För tidigt")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("För tidigt").assertIsDisplayed()
        composeRule.onNodeWithText("Ta ändå").assertIsDisplayed()
        composeRule.onNodeWithText("Avbryt").assertIsDisplayed()
    }

    @Test fun long_press_on_favourite_card_navigates_to_edit_without_delete_menu() {
        var editedId: String? = null
        runBlocking {
            repo.saveMedicin(medicin())
            repo.saveFavorit(favorit())
        }
        setContent(onEdit = { editedId = it })
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        composeRule.waitForIdle()
        assertEquals("fav1", editedId)
        composeRule.onNodeWithText("Ta bort").assertDoesNotExist()
    }
}

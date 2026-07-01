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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class VidBehovTabTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: MedicinerRepository
    private lateinit var vm: MedicinerViewModel

    private val today get() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

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

    @After fun tearDown() { db.close() }

    private fun favorit(
        id: String = "fav1",
        namn: String = "Paracetamol",
        dos: String = "500",
        enhet: String = "mg",
        maxDoserPerDag: Int = 0,
        minTidMellan: Int = 0,
        isFavorite: Boolean = true,
    ) = Favorit(
        id = id, namn = namn, dos = dos, enhet = enhet,
        tidpunkt = "Vid behov", anteckning = "",
        minTidMellan = minTidMellan, maxDoserPerDag = maxDoserPerDag,
        isFavorite = isFavorite,
    )

    private fun dosToday(namn: String, id: String = "dose1") = Medicin(
        id = id, timestamp = java.time.Instant.now().toString(),
        datum = today, tid = "10:00",
        namn = namn, dos = "500", enhet = "mg", tidpunkt = "Vid behov",
        tagen = true, anteckning = "",
    )

    private fun setContent() {
        composeRule.setContent {
            MaterialTheme {
                val snackbarState = remember { SnackbarHostState() }
                val snackMsg by vm.snackbar.collectAsState()
                LaunchedEffect(snackMsg) {
                    snackMsg?.let { snackbarState.showSnackbar(it); vm.clearSnackbar() }
                }
                Scaffold(snackbarHost = { SnackbarHost(snackbarState) }) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        VidBehovTab(vm = vm, onEdit = {})
                    }
                }
            }
        }
    }

    // ─── Tomt tillstånd ───────────────────────────────────────────────────────

    @Test fun empty_state_shows_Inga_favoriter_sparade() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Inga favoriter sparade").assertIsDisplayed()
    }

    @Test fun hint_texts_are_shown_when_favorites_exist() {
        runBlocking { repo.saveFavorit(favorit()) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tryck för att logga en dos").assertIsDisplayed()
        composeRule.onNodeWithText("Håll inne för att redigera eller ta bort").assertIsDisplayed()
    }

    // ─── Tryck loggar dos ─────────────────────────────────────────────────────

    @Test fun tapping_favorit_card_logs_a_dose_when_no_limit_or_cooldown() {
        runBlocking { repo.saveFavorit(favorit(maxDoserPerDag = 0, minTidMellan = 0)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").performClick()
        composeRule.waitUntil(3000) { vm.snackbar.value != null }
        assertEquals("Paracetamol 500 mg loggad", vm.snackbar.value)
    }

    @Test fun tapping_favorit_card_saves_dose_to_database() {
        runBlocking { repo.saveFavorit(favorit(namn = "Ibuprofen", maxDoserPerDag = 0, minTidMellan = 0)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ibuprofen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ibuprofen").performClick()
        composeRule.waitUntil(3000) { vm.snackbar.value != null }
        val count = runBlocking { repo.countDailyDoses(today, "Ibuprofen") }
        assertEquals(1, count)
    }

    // ─── Cooldown → dialog ───────────────────────────────────────────────────

    @Test fun tapping_favorit_within_cooldown_shows_warning_dialog() {
        runBlocking {
            // minTidMellan = 6 h; log a dose right now so cooldown is active
            repo.saveFavorit(favorit(namn = "Tramadol", minTidMellan = 6))
            repo.saveMedicin(dosToday("Tramadol"))
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

    @Test fun cooldown_dialog_dismiss_clears_the_warning() {
        runBlocking {
            repo.saveFavorit(favorit(namn = "Morfin", minTidMellan = 8))
            repo.saveMedicin(dosToday("Morfin"))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Morfin")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Morfin").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("För tidigt")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Avbryt").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("För tidigt").assertDoesNotExist()
    }

    @Test fun cooldown_dialog_Ta_anda_logs_dose() {
        runBlocking {
            repo.saveFavorit(favorit(namn = "Ketamin", minTidMellan = 4))
            repo.saveMedicin(dosToday("Ketamin"))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Ketamin")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ketamin").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("För tidigt")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ta ändå").performClick()
        composeRule.waitUntil(3000) { vm.snackbar.value != null }
        val count = runBlocking { repo.countDailyDoses(today, "Ketamin") }
        assertEquals(2, count)
    }

    // ─── Blockerad → snackbar ─────────────────────────────────────────────────

    @Test fun blocked_by_daily_limit_shows_snackbar() {
        runBlocking {
            repo.saveFavorit(favorit(namn = "Kodein", maxDoserPerDag = 1, minTidMellan = 0))
            repo.saveMedicin(dosToday("Kodein"))
        }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Kodein")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kodein").performClick()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Max 1 doser/dag nådda för Kodein"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Max 1 doser/dag nådda för Kodein").assertIsDisplayed()
    }

    // ─── Långtryck → meny ─────────────────────────────────────────────────────

    @Test fun long_press_on_favorit_card_shows_dropdown_menu() {
        runBlocking { repo.saveFavorit(favorit()) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Redigera").assertIsDisplayed()
        composeRule.onNodeWithText("Favoritmarkera").assertIsDisplayed()
        composeRule.onNodeWithText("Ta bort").assertIsDisplayed()
    }

    @Test fun long_press_menu_shows_unmark_label_for_favorite_med() {
        runBlocking { repo.saveFavorit(favorit(isFavorite = true)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort favoritmarkering").assertIsDisplayed()
    }

    @Test fun tapping_favorite_toggle_in_menu_updates_repository() {
        runBlocking { repo.saveFavorit(favorit(isFavorite = true)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Paracetamol")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ta bort favoritmarkering").performClick()
        composeRule.waitUntil(3000) {
            runBlocking { repo.getFavoritById("fav1")?.isFavorite == false }
        }
        val updated = runBlocking { repo.getFavoritById("fav1") }
        assertEquals(false, updated?.isFavorite)
    }

    // ─── Icke-favoriter → "Fler"-lista ────────────────────────────────────────

    @Test fun non_favorite_med_is_not_shown_as_a_chip() {
        runBlocking { repo.saveFavorit(favorit(isFavorite = false)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Fler (1)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Paracetamol").assertDoesNotExist()
        composeRule.onNodeWithText("Fler (1)").assertIsDisplayed()
    }

    @Test fun tapping_more_list_item_logs_a_dose() {
        runBlocking { repo.saveFavorit(favorit(isFavorite = false)) }
        setContent()
        composeRule.waitUntil(3000) {
            composeRule.onAllNodes(hasText("Fler (1)")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Fler (1)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Paracetamol — 500 mg").performClick()
        composeRule.waitUntil(3000) { vm.snackbar.value != null }
        assertEquals("Paracetamol 500 mg loggad", vm.snackbar.value)
    }
}

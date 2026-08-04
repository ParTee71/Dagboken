package se.partee71.dagboken.ui.mediciner.add

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
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.repository.NoteRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase
import se.partee71.dagboken.util.retryOnRenderGlitch

// Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
@RunWith(AndroidJUnit4::class)
class AddEditReceptScreenTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: AddEditReceptViewModel
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    private fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
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
        vm = AddEditReceptViewModel(repo, NoteRepository(db.noteDao()))

        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    AddEditReceptScreen(editId = null, onBack = {}, vm = vm)
                }
            }
        }
    }

    private fun tearDown() {
        vm.viewModelScope.cancel()
        db.close()
        scenario.close()
    }

    @Test fun period_section_defaults_to_tills_vidare() = retryOnRenderGlitch {
        setUp()
        try {
            composeRule.onNodeWithText("Period").performScrollTo().assertIsDisplayed()
            composeRule.onNode(hasText("Ingen slutdag", substring = true))
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun choosing_a_length_shows_the_computed_end_date() = retryOnRenderGlitch {
        setUp()
        try {
            composeRule.onNodeWithText("Längd").performScrollTo().performClick()
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("T.o.m.", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Antal dagar").performScrollTo().assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun add_dosperiod_shows_a_dosperiod_row() = retryOnRenderGlitch {
        setUp()
        try {
            composeRule.onNodeWithText("Lägg till dosperiod").performScrollTo().performClick()
            composeRule.waitUntil(20_000) { vm.form.value.dosperioder.size == 1 }
            composeRule.onNodeWithContentDescription("Ta bort dosperiod")
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            tearDown()
        }
    }

    @Test fun overlapping_dosperioder_show_a_validation_message() = retryOnRenderGlitch {
        setUp()
        try {
            scenario.onActivity {
                vm.updateForm {
                    copy(
                        namn = "Prednisolon", dos = "10",
                        dosperioder = listOf(
                            Dosperiod("d1", "2026-05-01", "2026-05-05", "10", "mg"),
                            Dosperiod("d2", "2026-05-05", "2026-05-10", "5", "mg"),
                        ),
                    )
                }
            }
            composeRule.waitUntil(20_000) {
                composeRule.onAllNodes(hasText("överlappa", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasText("överlappa", substring = true))
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            tearDown()
        }
    }
}

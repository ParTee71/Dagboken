package se.partee71.dagboken.ui.mediciner.add

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.R
import se.partee71.dagboken.data.repository.MedicinerRepository
import se.partee71.dagboken.data.room.AppDatabase
import se.partee71.dagboken.domain.usecase.EnsureTodayEntriesUseCase

@RunWith(AndroidJUnit4::class)
class AddEditFavoritScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: AddEditFavoritViewModel
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val repo = MedicinerRepository(
            db                 = db,
            medicinDao         = db.medicinDao(),
            receptDao          = db.receptDao(),
            favoritDao         = db.favoritDao(),
            ensureTodayEntries = EnsureTodayEntriesUseCase(),
            json               = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
        )
        vm = AddEditFavoritViewModel(repo)

        composeRule.setContent {
            MaterialTheme {
                AddEditFavoritScreen(editId = null, onBack = {}, vm = vm)
            }
        }
    }

    @After fun tearDown() { db.close() }

    @Test fun cooldown_and_max_per_day_sliders_show_default_values_and_update_on_tap() {
        // Default form: minTidMellan = 4h, maxDoserPerDag = 0 (unlimited)
        composeRule.onNodeWithText("4 tim").assertIsDisplayed()
        composeRule.onNodeWithText("Obegränsat").assertIsDisplayed()

        // Tap "+" on the second slider (max per day) to bump 0 -> 1
        composeRule.onAllNodesWithContentDescription(ctx.getString(R.string.increase))[1]
            .performClick()

        composeRule.onNodeWithText("1 ggr").assertIsDisplayed()
    }
}

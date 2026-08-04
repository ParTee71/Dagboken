package se.partee71.dagboken.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.partee71.dagboken.data.datastore.SymptomOption
import se.partee71.dagboken.util.retryOnRenderGlitch

/**
 * Delad symptomgradering (regel 4). Testad särskilt för att raden med logg-poster har
 * tre intilliggande ikonknappar, varav en raderar — tryckytorna måste hålla 48 dp.
 *
 * Migrerad enligt POC i #112 — se SjukdomarScreenTest för fullständig förklaring.
 */
@RunWith(AndroidJUnit4::class)
class SymptomLogCardTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val options = listOf(
        SymptomOption("Huvudvärk", isFavorite = true),
        SymptomOption("Yrsel"),
    )

    private fun launch(
        scores: Map<String, Int>,
        onScoresChange: (Map<String, Int>) -> Unit = {},
        onToggleFavorite: (String) -> Unit = {},
    ): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity {
            it.setContent {
                MaterialTheme {
                    SymptomLogCard(
                        symptomOptions   = options,
                        scores           = scores,
                        onScoresChange   = onScoresChange,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
            }
        }
        return scenario
    }

    /** Kortet börjar ihopfällt; innehållet visas först efter att rubriken tryckts. */
    private fun expand() {
        composeRule.onNodeWithText("Symptom").performClick()
        composeRule.waitForIdle()
    }

    @Test fun `logged symptoms are listed with their score`() = retryOnRenderGlitch {
        val scenario = launch(scores = mapOf("Huvudvärk" to 4))
        try {
            expand()
            composeRule.onNodeWithText("Huvudvärk").assertIsDisplayed()
            composeRule.onNodeWithText("4/10").assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }

    @Test fun `deleting a logged symptom reports the remaining scores`() = retryOnRenderGlitch {
        var latest: Map<String, Int>? = null
        val scenario = launch(
            scores = mapOf("Huvudvärk" to 4, "Yrsel" to 2),
            onScoresChange = { latest = it },
        )
        try {
            expand()
            composeRule.onAllNodesWithContentDescription("Ta bort")[0].performClick()
            composeRule.waitForIdle()

            assertEquals(mapOf("Yrsel" to 2), latest)
        } finally {
            scenario.close()
        }
    }

    @Test fun `favorite toggle reports the symptom name`() = retryOnRenderGlitch {
        var toggled: String? = null
        val scenario = launch(scores = mapOf("Huvudvärk" to 4), onToggleFavorite = { toggled = it })
        try {
            expand()
            composeRule.onAllNodesWithContentDescription("Favorit")[0].performClick()
            composeRule.waitForIdle()

            assertEquals("Huvudvärk", toggled)
        } finally {
            scenario.close()
        }
    }

    /** Regressionsskydd: knapparna var tidigare 36 dp, under Material/a11y-minimum. */
    @Test fun `row action buttons meet the minimum touch target`() = retryOnRenderGlitch {
        val scenario = launch(scores = mapOf("Huvudvärk" to 4))
        try {
            expand()
            listOf("Favorit", "Redigera", "Ta bort").forEach { label ->
                composeRule.onAllNodesWithContentDescription(label)[0]
                    .assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp)
            }
        } finally {
            scenario.close()
        }
    }
}
